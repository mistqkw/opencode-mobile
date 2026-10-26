package app.ocmobile.data

import android.content.Context
import android.net.Uri
import app.ocmobile.Graph
import app.ocmobile.api.EventHub
import app.ocmobile.api.OcJson
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.PermissionReq
import app.ocmobile.api.QuestionReq
import app.ocmobile.api.Session
import app.ocmobile.api.obj
import app.ocmobile.api.parsePermission
import app.ocmobile.api.parseQuestion
import app.ocmobile.api.str
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Список сессий, статусы, запросы разрешений и открытые чаты — всё, что приходит по SSE. */
class ChatHub(private val api: OpenCodeApi, private val events: EventHub, private val prefs: Prefs) {
    private val all = MutableStateFlow<Map<String, Session>>(emptyMap())
    val sessions = MutableStateFlow<List<Session>>(emptyList())
    val statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val permissions = MutableStateFlow<List<PermissionReq>>(emptyList())
    val questions = MutableStateFlow<List<QuestionReq>>(emptyList())
    val loaded = MutableStateFlow(false)
    /** Причина повторных попыток по сессиям: лимит бесплатного тарифа, ошибка провайдера и т. п. */
    val retries = MutableStateFlow<Map<String, RetryInfo>>(emptyMap())
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val chats = HashMap<String, Chat>()

    fun start() {
        // Сессии «инкогнито» не переживают перезапуск: остатки удаляем при старте.
        Graph.scope.launch {
            delay(4000)
            incognitoSet().forEach { id -> runCatching { dropIncognito(id) } }
        }
        // Смена устройства (Remote control): всё, что относилось к прежнему серверу, сбрасываем.
        Graph.scope.launch {
            Graph.devices.activeId.drop(1).collect {
                chats.clear(); all.value = emptyMap(); sessions.value = emptyList(); statuses.value = emptyMap()
                permissions.value = emptyList(); questions.value = emptyList(); retries.value = emptyMap(); loaded.value = false
            }
        }
        Graph.scope.launch { events.events.collect(::handle) }
        Graph.scope.launch {
            events.generation.collect { g ->
                if (g > 0) {
                    refreshSessions()
                    chats.values.forEach { c -> launch { c.load() } }
                }
            }
        }
    }

    fun session(id: String): Session? = all.value[id]

    fun chat(id: String): Chat = chats.getOrPut(id) {
        Chat(id, api).also { it.directory = session(id)?.directory }
    }

    fun forget(id: String) { chats.remove(id) }

    suspend fun refreshSessions() {
        try {
            val list = api.listSessions(limit = 500)
            all.value = list.associateBy { it.id }
            publish()
            loaded.value = true
            // подтягиваем то, что ждёт ответа, в каждом проекте, где есть сессии
            val dirs = list.map { it.directory }.distinct().take(12)
            val perms = ArrayList<PermissionReq>()
            val qs = ArrayList<QuestionReq>()
            val st = HashMap<String, String>()
            for (d in dirs) {
                runCatching { perms += api.permissions(d) }
                runCatching { qs += api.questions(d) }
                runCatching { api.statuses(d).forEach { (k, v) -> v.str("type")?.takeIf { it != "idle" }?.let { t -> st[k] = t } } }
            }
            permissions.value = perms
            questions.value = qs
            statuses.value = st
        } catch (_: Exception) { }
    }

    fun incognitoSet(): Set<String> = prefs.incognitoIds.split(',').filter { it.isNotBlank() }.toSet()
    fun markIncognito(id: String) { prefs.incognitoIds = (incognitoSet() + id).joinToString(",") }
    fun isIncognito(id: String) = id in incognitoSet()

    /** Удаляет сессию «инкогнито» и забывает о ней. */
    suspend fun dropIncognito(id: String) {
        val dir = session(id)?.directory
        runCatching { api.deleteSession(id, dir) }
        prefs.incognitoIds = (incognitoSet() - id).joinToString(",")
        chats.remove(id)
        all.update { it - id }
        publish()
    }

    private fun publish() {
        val hidden = incognitoSet()
        sessions.value = all.value.values
            .filter { it.parentID == null && it.time.archived == null && it.id !in hidden }
            .sortedByDescending { it.time.updated }
    }

    private fun upsert(s: Session) {
        all.update { it + (s.id to s) }
        chats[s.id]?.directory = s.directory
        publish()
    }

    private fun handle(e: EventHub.Event) {
        val p = e.props
        when (e.type) {
            "session.created", "session.updated" -> p.obj("info")?.let {
                runCatching { OcJson.decodeFromJsonElement(Session.serializer(), it) }.getOrNull()?.let(::upsert)
            }
            "session.deleted" -> p.obj("info")?.str("id")?.let { id -> all.update { it - id }; publish() }
            "session.status" -> {
                val id = p.str("sessionID") ?: return
                val t = p.obj("status")?.str("type") ?: return
                statuses.update { if (t == "idle") it - id else it + (id to t) }
                val st = p.obj("status")
                if (t == "retry" && st != null) {
                    val a = st.obj("action")
                    retries.update { it + (id to RetryInfo(st.str("message") ?: "Retrying…", a?.str("label"), a?.str("link"), a?.str("title"), st.str("next")?.toLongOrNull())) }
                    if (st.str("message")?.contains("Free usage", ignoreCase = true) == true && prefs.providerId != FreeModels.POLLINATIONS_ID) fallToPollinations(id)
                } else retries.update { it - id }
            }
            "session.idle" -> p.str("sessionID")?.let { id -> statuses.update { it - id } }
            "permission.asked" -> {
                val r = parsePermission(p)
                permissions.update { l -> l.filterNot { it.id == r.id } + r }
            }
            "permission.replied" -> p.str("requestID")?.let { rid -> permissions.update { l -> l.filterNot { it.id == rid } } }
            "question.asked" -> {
                val q = parseQuestion(p)
                questions.update { l -> l.filterNot { it.id == q.id } + q }
            }
            "question.replied", "question.rejected" -> p.str("requestID")?.let { rid -> questions.update { l -> l.filterNot { it.id == rid } } }
            "session.error" -> {
                val msg = p.obj("error")?.obj("data")?.str("message") ?: p.obj("error")?.str("name")
                if (msg != null) toasts.tryEmit(msg)
            }
            else -> {
                val sid = p.str("sessionID") ?: p.obj("info")?.str("sessionID") ?: p.obj("part")?.str("sessionID")
                if (sid != null) chats[sid]?.onEvent(e.type, p)
            }
        }
    }

    // ---------- отправка ----------

    /** Правила разрешений для новой сессии по выбранному режиму. */
    fun permissionRules(mode: String = prefs.permissionMode): JsonArray? {
        val base = when (mode) {
            "ask" -> listOf(rule("*", "ask"))
            "edits" -> listOf(rule("bash", "ask"), rule("webfetch", "ask"))
            else -> emptyList()
        }
        // память лежит вне проекта: к ней агент ходит без вопросов (правила в конце — они главнее)
        val mem = if (prefs.memoryEnabled) listOf("external_directory", "read", "edit").flatMap { perm ->
            listOf("$MEMORY_DIR/*", "$MEMORY_DIR/**").map { rule(perm, "allow", it) }
        } else emptyList()
        val all = base + mem
        return if (all.isEmpty()) null else buildJsonArray { all.forEach { add(it) } }
    }

    fun isChat(s: Session) = s.directory.trimEnd('/') == CHAT_DIR
    /** Обычные чаты — в меню и «Recents»; проектные — во вкладке Code. На удалённом устройстве такого деления нет. */
    fun chatsOnly(l: List<Session>) = if (Graph.devices.isRemote) l else l.filter { isChat(it) }
    fun codeOnly(l: List<Session>) = if (Graph.devices.isRemote) l else l.filterNot { isChat(it) }

    private fun rule(permission: String, action: String, pattern: String = "*") = buildJsonObject {
        put("permission", permission); put("pattern", pattern); put("action", action)
    }

    suspend fun send(ctx: Context, sessionId: String, text: String, atts: List<Attachment>, keepParts: List<JsonObject> = emptyList()) {
        val dir = session(sessionId)?.directory
        val localId = if (keepParts.isEmpty()) chat(sessionId).addLocalUser(text) else null
        val parts = buildJsonArray {
            add(buildJsonObject { put("type", "text"); put("text", text) })
            keepParts.forEach { add(it) }
            atts.forEach { add(Attachments.toFilePart(ctx, it)) }
        }
        try {
            api.promptAsync(sessionId, dir, parts, prefs.providerId, prefs.modelId, prefs.agent, prefs.variant)
        } catch (e: Exception) {
            localId?.let { chat(sessionId).dropLocal(it) }
            throw e
        }
    }

    /**
     * Правка сообщения (и «повторить»): откатываем сессию до этого сообщения — вместе с файловыми
     * изменениями агента — и отправляем новый текст; прикреплённые файлы оригинала сохраняются.
     */
    suspend fun edit(ctx: Context, sessionId: String, m: ChatMessage, text: String, atts: List<Attachment>) {
        val dir = session(sessionId)?.directory
        runCatching { api.abort(sessionId, dir) }
        api.revert(sessionId, dir, m.id)
        chats[sessionId]?.truncateFrom(m.id)
        val keep = m.parts.filter { it.type == "file" }.map { p ->
            buildJsonObject {
                put("type", "file")
                p.raw.str("mime")?.let { put("mime", it) }
                p.raw.str("filename")?.let { put("filename", it) }
                p.raw.str("url")?.let { put("url", it) }
            }
        }
        send(ctx, sessionId, text, atts, keep)
    }

    private var switching = false

    /** Zen ответил лимитом: переключаемся на Pollinations (без аккаунта) и заново отправляем последнее сообщение. */
    private fun fallToPollinations(id: String) {
        if (switching) return
        switching = true
        Graph.scope.launch {
            try {
                val last = chats[id]?.messages?.lastOrNull { it.info.isUser } ?: return@launch
                val text = last.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
                if (text.isBlank()) return@launch
                FreeModels.enablePollinations()
                toasts.tryEmit("Zen is capped — switched to GPT-OSS 20B (Pollinations)")
                edit(Graph.app, id, last, text, emptyList())
            } catch (e: Exception) {
                toasts.tryEmit(e.message ?: "Could not switch the model")
            } finally { switching = false }
        }
    }

    suspend fun createSession(directory: String): Session {
        val s = api.createSession(directory, permission = permissionRules())
        upsert(s)
        return s
    }
}

data class RetryInfo(val message: String, val label: String?, val link: String?, val title: String?, val nextAt: Long? = null)

data class Attachment(val uri: Uri, val mime: String, val name: String) {
    val isImage get() = mime.startsWith("image/")
}
