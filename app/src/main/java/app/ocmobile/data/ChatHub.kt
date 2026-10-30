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
    /** Текст, который положить в поле ввода при открытии чата (например, «/навык »). */
    val drafts = mutableMapOf<String, String>()
    fun draft(id: String, text: String) { drafts[id] = text }
    val permissions = MutableStateFlow<List<PermissionReq>>(emptyList())
    val questions = MutableStateFlow<List<QuestionReq>>(emptyList())
    val loaded = MutableStateFlow(false)
    /** Причина повторных попыток по сессиям: лимит бесплатного тарифа, ошибка провайдера и т. п. */
    val retries = MutableStateFlow<Map<String, RetryInfo>>(emptyMap())
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val chats = HashMap<String, Chat>()

    /** Задачи, которые оборвались, когда сервер остановился: в чате предлагаем продолжить или отменить. */
    val interrupted = MutableStateFlow(prefs.interruptedIds.split(',').filter { it.isNotBlank() }.toSet())

    private fun saveBusy() { prefs.busyIds = statuses.value.filterValues { it != "idle" }.keys.joinToString(",") }

    fun dismissInterrupted(id: String) {
        interrupted.update { it - id }
        prefs.interruptedIds = interrupted.value.joinToString(",")
    }

    suspend fun resumeInterrupted(ctx: android.content.Context, id: String) {
        dismissInterrupted(id)
        send(ctx, id, "The server was stopped in the middle of your last task. Check what is already done and continue from where you stopped.", emptyList())
    }

    /** Была занята до остановки, а теперь стоит: если последний ответ не дописан или оборван — значит, прервали. */
    private suspend fun findInterrupted(nowBusy: Set<String>) {
        val was = prefs.busyIds.split(',').filter { it.isNotBlank() }.toSet()
        val found = was.filter { it !in nowBusy && it in all.value && !isIncognito(it) }.filter { id ->
            val last = runCatching { api.messages(id, session(id)?.directory, 4) }.getOrNull()?.lastOrNull { !it.info.isUser }?.info
            last == null || last.time.completed == null || last.error != null
        }
        if (found.isNotEmpty()) {
            interrupted.update { it + found }
            prefs.interruptedIds = interrupted.value.joinToString(",")
        }
    }

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
            val (auto, manual) = perms.partition { autoAllows(it.permission) }
            permissions.value = manual
            auto.forEach { r -> Graph.scope.launch { runCatching { api.replyPermission(r.id, session(r.sessionID)?.directory, "once") } } }
            questions.value = qs
            findInterrupted(st.keys)
            statuses.value = st
            saveBusy()
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
                val was = statuses.value[id]
                statuses.update { if (t == "idle") it - id else it + (id to t) }
                saveBusy()
                if (t != "idle" && id in interrupted.value) dismissInterrupted(id)
                if (t == "idle" && was != null) finished(id)
                val st = p.obj("status")
                if (t == "retry" && st != null) {
                    val a = st.obj("action")
                    retries.update { it + (id to RetryInfo(st.str("message") ?: "Retrying…", a?.str("label"), a?.str("link"), a?.str("title"), st.str("next")?.toLongOrNull())) }
                    if (st.str("message")?.contains("Free usage", ignoreCase = true) == true && prefs.providerId != FreeModels.POLLINATIONS_ID) fallToPollinations(id)
                } else retries.update { it - id }
            }
            "session.idle" -> p.str("sessionID")?.let { id -> val was = statuses.value[id]; statuses.update { it - id }; saveBusy(); if (was != null) finished(id) }
            "permission.asked" -> {
                val r = parsePermission(p)
                // режим уже разрешает это — отвечаем сами, пользователя не дёргаем (старые сессии, правила агента)
                if (autoAllows(r.permission)) {
                    val dir = e.directory ?: session(r.sessionID)?.directory
                    Graph.scope.launch { runCatching { api.replyPermission(r.id, dir, "once") }.onFailure { permissions.update { l -> l.filterNot { it.id == r.id } + r } } }
                } else permissions.update { l -> l.filterNot { it.id == r.id } + r }
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

    /** Что текущий режим разрешает без вопроса. */
    fun autoAllows(permission: String, mode: String = prefs.permissionMode) = when (mode) {
        "auto" -> true
        "edits" -> permission in setOf("edit", "write", "patch", "multiedit", "read", "list", "glob", "grep", "todowrite", "todoread")
        else -> false
    }

    /** Правила разрешений для сессии по выбранному режиму. «Auto» — явное «разрешить всё»: без него OpenCode спрашивает про bash. */
    fun permissionRules(mode: String = prefs.permissionMode): JsonArray? {
        val base = when (mode) {
            "ask" -> listOf(rule("*", "ask"))
            "edits" -> listOf(rule("*", "allow"), rule("bash", "ask"), rule("webfetch", "ask"))
            else -> listOf(rule("*", "allow"))
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

    /** Сессии, которым правила уже выставлены под текущий режим (режим меняется — выставляем заново). */
    private val ruled = HashMap<String, String>()

    suspend fun send(ctx: Context, sessionId: String, text: String, atts: List<Attachment>, keepParts: List<JsonObject> = emptyList()) {
        val dir = session(sessionId)?.directory
        if (ruled[sessionId] != prefs.permissionMode) runCatching {
            api.setSessionPermission(sessionId, dir, permissionRules() ?: JsonArray(emptyList()))
            ruled[sessionId] = prefs.permissionMode
        }
        val localId = if (keepParts.isEmpty()) chat(sessionId).addLocalUser(text) else null
        val text = expandSlash(text)
        // свежая память — в инструкции до запроса (агент мог поменять её в прошлом ответе)
        if (prefs.memoryEnabled && !Graph.devices.isRemote) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Memory.sync() }
        try {
            val attParts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { atts.map { Attachments.toPart(ctx, it) } }
            val parts = buildJsonArray {
                if (text.isNotBlank() || (keepParts.isEmpty() && attParts.isEmpty())) add(buildJsonObject { put("type", "text"); put("text", text) })
                keepParts.forEach { add(it) }
                attParts.forEach { add(it) }
            }
            api.promptAsync(sessionId, dir, parts, prefs.providerId, prefs.modelId, prefs.agent, prefs.variant)
        } catch (e: Exception) {
            localId?.let { chat(sessionId).dropLocal(it) }
            throw e
        }
    }

    /**
     * «/имя аргументы»: навык — просим агента загрузить его (инструмент skill), команда — подставляем её шаблон.
     * Неизвестное «/…» уходит как есть.
     */
    fun expandSlash(text: String): String {
        if (!text.startsWith("/")) return text
        val name = text.drop(1).substringBefore(' ').substringBefore('\n')
        val args = text.drop(1 + name.length).trim()
        val c = Graph.catalog.commands.value.firstOrNull { it.name == name } ?: return text
        return if (c.source == "skill") "Use the \"$name\" skill." + (if (args.isNotBlank()) "\n\n$args" else "")
        else c.template?.replace("\$ARGUMENTS", args)?.takeIf { it.isNotBlank() } ?: text
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
        } + m.parts.filter { it.type == "text" && Attachments.parseAttached(it.text) != null }.map { p ->
            buildJsonObject { put("type", "text"); put("text", p.text); put("synthetic", true) }
        }
        send(ctx, sessionId, text, atts, keep)
    }

    /** Приложение на экране — его ставит MainActivity. */
    @Volatile var foreground = false
    /** Открыть чат по нажатию на уведомление. */
    val openRequest = MutableStateFlow<String?>(null)

    /** Агент закончил: в приложении — лёгкая вибрация, в фоне — уведомление с началом ответа. */
    private fun finished(id: String) {
        if (isIncognito(id)) return
        val ctx = Graph.app
        if (foreground) {
            if (prefs.haptics) runCatching {
                val v = ctx.getSystemService(android.os.Vibrator::class.java)
                v?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
            }
            return
        }
        Graph.scope.launch {
            delay(400)
            val msg = chats[id]?.messages?.lastOrNull { !it.info.isUser }
            val text = msg?.parts?.filter { it.type == "text" }?.joinToString(" ") { it.text }?.replace(Regex("[`*#>_]"), "")?.trim()
                ?.takeIf { it.isNotBlank() } ?: msg?.info?.errorMessage ?: "The task is done"
            val title = session(id)?.displayTitle?.ifBlank { null } ?: "OpenCode"
            runCatching {
                val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
                if (nm.getNotificationChannel("replies") == null) nm.createNotificationChannel(android.app.NotificationChannel("replies", "Replies", android.app.NotificationManager.IMPORTANCE_DEFAULT))
                val open = android.app.PendingIntent.getActivity(
                    ctx, id.hashCode(), android.content.Intent(ctx, app.ocmobile.MainActivity::class.java).putExtra("session", id).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
                nm.notify(id.hashCode(), android.app.Notification.Builder(ctx, "replies")
                    .setSmallIcon(app.ocmobile.R.drawable.ic_stat).setContentTitle(title).setContentText(text.take(160))
                    .setStyle(android.app.Notification.BigTextStyle().bigText(text.take(600)))
                    .setContentIntent(open).setAutoCancel(true).build())
            }
        }
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
