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

/** Session list, statuses, permission requests and open chats: everything that comes over SSE. */
class ChatHub(private val api: OpenCodeApi, private val events: EventHub, private val prefs: Prefs) {
    private val all = MutableStateFlow<Map<String, Session>>(emptyMap())
    val sessions = MutableStateFlow<List<Session>>(emptyList())
    val statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Text to put in the input when the chat opens (for example "/skill "). */
    val drafts = mutableMapOf<String, String>()
    fun draft(id: String, text: String) { drafts[id] = text }
    val permissions = MutableStateFlow<List<PermissionReq>>(emptyList())
    val questions = MutableStateFlow<List<QuestionReq>>(emptyList())
    val loaded = MutableStateFlow(false)
    /** Why sessions are retrying: free-tier limit, provider error and so on. */
    val retries = MutableStateFlow<Map<String, RetryInfo>>(emptyMap())
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val chats = HashMap<String, Chat>()

    /** Tasks cut off when the server stopped: the chat offers to continue or cancel. */
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

    /** Was busy before the stop and is idle now: if the last reply is unfinished or cut off, it was interrupted. */
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
        // Incognito sessions don't survive a restart: leftovers are deleted on start.
        Graph.scope.launch {
            delay(4000)
            incognitoSet().forEach { id -> runCatching { dropIncognito(id) } }
        }
        // Device switch (Remote control): everything tied to the previous server is reset.
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

    /** All known sessions, including child ones (team helpers). */
    val allSessions: kotlinx.coroutines.flow.StateFlow<Map<String, Session>> get() = all

    /** Child sessions of [id]: helpers the main agent started with the task tool. */
    fun children(id: String): List<Session> = all.value.values.filter { it.parentID == id }.sortedBy { it.time.created }

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
            // fetch what's waiting for an answer in every project that has sessions
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

    /** Deletes an incognito session and forgets it. */
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
                // the mode already allows this: answer ourselves, don't bother the user (old sessions, agent rules)
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

    // ---------- sending ----------

    /** What the current mode allows without asking. */
    fun autoAllows(permission: String, mode: String = prefs.permissionMode) = when (mode) {
        "auto" -> true
        "edits" -> permission in setOf("edit", "write", "patch", "multiedit", "read", "list", "glob", "grep", "todowrite", "todoread")
        else -> false
    }

    /** Permission rules for a session by the selected mode. "Auto" is an explicit "allow all": without it OpenCode asks about bash. */
    fun permissionRules(mode: String = prefs.permissionMode): JsonArray? {
        val base = when (mode) {
            "ask" -> listOf(rule("*", "ask"))
            "edits" -> listOf(rule("*", "allow"), rule("bash", "ask"), rule("webfetch", "ask"))
            else -> listOf(rule("*", "allow"))
        }
        // memory lives outside the project: the agent reaches it without asking (rules at the end win)
        val mem = if (prefs.memoryEnabled) listOf("external_directory", "read", "edit").flatMap { perm ->
            listOf("$MEMORY_DIR/*", "$MEMORY_DIR/**").map { rule(perm, "allow", it) }
        } else emptyList()
        val all = base + mem
        return if (all.isEmpty()) null else buildJsonArray { all.forEach { add(it) } }
    }

    fun isChat(s: Session) = s.directory.trimEnd('/') == CHAT_DIR
    /** Regular chats go to the menu and "Recents"; project ones to the Code tab. A remote device has no such split. */
    fun chatsOnly(l: List<Session>) = if (Graph.devices.isRemote) l else l.filter { isChat(it) }
    /**
     * Sessions for "Remote devices": on a computer, all of them; on this phone, projects and the chats
     * that are really about code (the same <> icon in the drawer, see ChatKind). Team helpers
     * and anonymous chats are left out.
     */
    fun codeOnly(l: List<Session>) = if (Graph.devices.isRemote) l
        else l.filter { s -> s.parentID.isNullOrBlank() && !isIncognito(s.id) && (!isChat(s) || ChatKind.of(s) == ChatKind.Code) }

    private fun rule(permission: String, action: String, pattern: String = "*") = buildJsonObject {
        put("permission", permission); put("pattern", pattern); put("action", action)
    }

    /** Sessions whose rules are already set for the current mode (when the mode changes, we set them again). */
    private val ruled = HashMap<String, String>()

    suspend fun send(ctx: Context, sessionId: String, text: String, atts: List<Attachment>, keepParts: List<JsonObject> = emptyList()) {
        val dir = session(sessionId)?.directory
        if (ruled[sessionId] != prefs.permissionMode) runCatching {
            api.setSessionPermission(sessionId, dir, permissionRules() ?: JsonArray(emptyList()))
            ruled[sessionId] = prefs.permissionMode
        }
        val localId = if (keepParts.isEmpty()) chat(sessionId).addLocalUser(text) else null
        val text = expandSlash(text)
        // fresh memory goes into the instructions before the request (the agent may have changed it in the last reply)
        if (prefs.memoryEnabled && !Graph.devices.isRemote) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Memory.sync() }
        try {
            val attParts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { atts.map { Attachments.toPart(ctx, it) } }
            val parts = buildJsonArray {
                if (text.isNotBlank() || (keepParts.isEmpty() && attParts.isEmpty())) add(buildJsonObject { put("type", "text"); put("text", text) })
                keepParts.forEach { add(it) }
                attParts.forEach { add(it) }
            }
            // agent team: helpers in the config and a note to the main agent on how to use them
            val team = if (Team.on) { Team.sync(); Team.instruction() } else null
            // in the cl0udly look the agent is the cl0udly cloud (Persona), unless turned off
            // anonymous chat: tell the agent not to remember anything from it
            val anon = if (isIncognito(sessionId)) "This is an anonymous chat. Do not save anything from it to memory, notes or files outside the task." else null
            api.promptAsync(sessionId, dir, parts, prefs.providerId, prefs.modelId, prefs.agent, prefs.variant, listOfNotNull(Persona.instruction(), team, anon).joinToString("\n\n").ifBlank { null })
        } catch (e: Exception) {
            localId?.let { chat(sessionId).dropLocal(it) }
            throw e
        }
    }

    /**
     * "/name args": for a skill, ask the agent to load it (the skill tool); for a command, use its template.
     * An unknown "/…" is sent as is.
     */
    fun expandSlash(text: String): String {
        // skills in the middle of the text: ask the agent to load each one, the text stays as is
        if (!text.startsWith("/")) {
            val cmds = Graph.catalog.commands.value
            val skills = app.ocmobile.ui.components.SLASH_TOKEN.findAll(text).map { it.groupValues[2] }
                .filter { n -> cmds.any { it.name == n && it.source == "skill" } }.distinct().toList()
            return if (skills.isEmpty()) text else skills.joinToString("\n") { "Use the \"$it\" skill." } + "\n\n" + text
        }
        val name = text.drop(1).substringBefore(' ').substringBefore('\n')
        val args = text.drop(1 + name.length).trim()
        val c = Graph.catalog.commands.value.firstOrNull { it.name == name } ?: return text
        return if (c.source == "skill") "Use the \"$name\" skill." + (if (args.isNotBlank()) "\n\n$args" else "")
        else c.template?.replace("\$ARGUMENTS", args)?.takeIf { it.isNotBlank() } ?: text
    }

    /**
     * Editing a message (and "retry"): revert the session to that message, together with the agent's
     * file changes, and send the new text; the original's attachments are kept.
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

    /** The app is on screen: set by MainActivity. */
    @Volatile var foreground = false
    /** Open a chat from a notification tap. */
    val openRequest = MutableStateFlow<String?>(null)

    /** The agent finished: a light vibration in the app, a notification with the start of the reply in the background. */
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

    /** Zen answered with a limit: switch to Pollinations (no account) and resend the last message. */
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
