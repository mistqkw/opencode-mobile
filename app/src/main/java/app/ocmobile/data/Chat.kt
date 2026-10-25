package app.ocmobile.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocmobile.api.MessageInfo
import app.ocmobile.api.OcJson
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.TimeInfo
import app.ocmobile.api.Part
import app.ocmobile.api.arr
import app.ocmobile.api.obj
import app.ocmobile.api.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class ChatMessage(val id: String, info: MessageInfo) {
    var info by mutableStateOf(info)
    val parts = mutableStateListOf<Part>()

    fun upsertPart(p: Part) {
        val i = parts.indexOfFirst { it.id == p.id }
        if (i >= 0) parts[i] = p else parts.add(p)
    }
}

data class Todo(val content: String, val status: String, val priority: String)

/** Состояние открытого чата. Все изменения идут из главного потока (события SSE и загрузка). */
class Chat(val id: String, private val api: OpenCodeApi) {
    var directory: String? = null
    val messages = mutableStateListOf<ChatMessage>()
    var loading by mutableStateOf(true)
    var loadError by mutableStateOf<String?>(null)
    var todos by mutableStateOf<List<Todo>>(emptyList())
    /** Сообщения, пришедшие при загрузке: им не нужна анимация появления. */
    val initialIds = HashSet<String>()
    var live = false

    /** Мгновенно показывает отправленное сообщение, пока сервер его не подтвердил. */
    fun addLocalUser(text: String): String {
        val id = "zzzz-local-" + System.nanoTime()
        val raw = kotlinx.serialization.json.buildJsonObject {
            put("id", kotlinx.serialization.json.JsonPrimitive("$id-p")); put("messageID", kotlinx.serialization.json.JsonPrimitive(id))
            put("type", kotlinx.serialization.json.JsonPrimitive("text")); put("text", kotlinx.serialization.json.JsonPrimitive(text))
        }
        val m = ChatMessage(id, MessageInfo(id = id, sessionID = this.id, role = "user", time = TimeInfo(created = System.currentTimeMillis())))
        m.parts.add(Part(raw))
        messages.add(m)
        return id
    }

    fun dropLocal(id: String) { messages.removeAll { it.id == id } }

    fun truncateFrom(messageId: String) { messages.removeAll { it.id >= messageId } }

    suspend fun load() {
        try {
            val list = api.messages(id, directory, 400)
            val fresh = list.map { m -> ChatMessage(m.info.id, m.info).also { it.parts.addAll(m.parts) } }
            val locals = messages.filter { it.id.startsWith("zzzz-local-") }
            messages.clear()
            messages.addAll(fresh)
            // сообщение, которое мы показали сразу, а сервер ещё не вернул, не должно мигнуть и исчезнуть
            locals.forEach { l -> if (fresh.none { it.info.isUser && it.parts.firstOrNull()?.text == l.parts.firstOrNull()?.text && it.id > "" && (it.info.time.created >= l.info.time.created - 5000) }) messages.add(l) }
            initialIds.clear(); initialIds.addAll(fresh.map { it.id })
            live = true
            loadError = null
            runCatching { api.todos(id, directory) }.getOrNull()?.let { todos = parseTodos(it) }
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load the chat"
        } finally {
            loading = false
        }
    }

    private val pending = LinkedHashMap<Pair<String, String>, StringBuilder>()
    private var flushQueued = false
    private val frameHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val flusher = Runnable { flushQueued = false; flushDeltas() }

    private fun flushDeltas() {
        if (pending.isEmpty()) return
        for ((key, sb) in pending) {
            val m = find(key.first) ?: continue
            val i = m.parts.indexOfFirst { it.id == key.second }
            if (i >= 0) m.parts[i] = m.parts[i].withText(m.parts[i].text + sb)
        }
        pending.clear()
    }

    fun onEvent(type: String, props: JsonObject) {
        // любое другое событие про части должно увидеть уже накопленный текст
        if (type != "message.part.delta") flushDeltas()
        when (type) {
            "message.updated" -> {
                val info = props.obj("info")?.let { runCatching { OcJson.decodeFromJsonElement(MessageInfo.serializer(), it) }.getOrNull() } ?: return
                val m = find(info.id)
                if (m != null) m.info = info else {
                    // настоящее сообщение пользователя заменяет «локальное» (то, что мы показали сразу)
                    if (info.role == "user") messages.removeAll { it.id.startsWith("zzzz-local-") }
                    insert(ChatMessage(info.id, info))
                }
            }
            "message.removed" -> props.str("messageID")?.let { mid -> messages.removeAll { it.id == mid } }
            "message.part.updated" -> {
                val part = props.obj("part")?.let(::Part) ?: return
                val m = find(part.messageID) ?: insert(ChatMessage(part.messageID, MessageInfo(id = part.messageID, sessionID = id, role = "assistant")))
                m.upsertPart(part)
            }
            "message.part.delta" -> {
                if (props.str("field") != "text") return
                val mid = props.str("messageID") ?: return
                val pid = props.str("partID") ?: return
                // Токены приходят десятками в секунду: копим и применяем не чаще одного раза за кадр.
                pending.getOrPut(mid to pid) { StringBuilder() }.append(props.str("delta") ?: "")
                if (!flushQueued) { flushQueued = true; frameHandler.postDelayed(flusher, 24) }
            }
            "message.part.removed" -> {
                val m = find(props.str("messageID") ?: return) ?: return
                val pid = props.str("partID")
                m.parts.removeAll { it.id == pid }
            }
            "todo.updated" -> props.arr("todos")?.let { todos = parseTodos(it) }
        }
    }

    private fun find(mid: String) = messages.firstOrNull { it.id == mid }

    /** Идентификаторы сообщений растут со временем, так что порядок — лексикографический. */
    private fun insert(m: ChatMessage): ChatMessage {
        var i = messages.size
        while (i > 0 && messages[i - 1].id > m.id) i--
        messages.add(i, m)
        return m
    }

    private fun parseTodos(a: kotlinx.serialization.json.JsonArray) = a.mapNotNull {
        val o = it as? JsonObject ?: return@mapNotNull null
        Todo(o.str("content") ?: "", o.str("status") ?: "pending", o.str("priority") ?: "medium")
    }
}
