package app.ocmobile.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.ocmobile.Graph
import app.ocmobile.MainActivity
import app.ocmobile.R
import app.ocmobile.api.EventHub
import app.ocmobile.api.obj
import app.ocmobile.api.parsePermission
import app.ocmobile.api.str
import kotlinx.coroutines.launch

/**
 * Агент на экране блокировки: пока приложение свёрнуто, а сессия работает, висит уведомление
 * с текущим шагом («Editing Composer.kt») и счётчиком шагов и файлов. На Android 16 оно становится
 * Live Update (чип в строке состояния). Когда агент просит разрешение — отдельное уведомление
 * с кнопками Allow / Always / Deny, отвечать можно прямо с экрана блокировки.
 */
object LiveNotify {
    private const val CH_LIVE = "live"
    private const val CH_ASK = "ask"

    private class Run(var step: String = "Thinking…", var steps: Int = 0, val files: MutableSet<String> = linkedSetOf(), val started: Long = System.currentTimeMillis())
    private val runs = HashMap<String, Run>()

    private val ctx: Context get() = Graph.app
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    fun start() {
        if (nm.getNotificationChannel(CH_LIVE) == null) nm.createNotificationChannel(NotificationChannel(CH_LIVE, "Agent at work", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(false) })
        if (nm.getNotificationChannel(CH_ASK) == null) nm.createNotificationChannel(NotificationChannel(CH_ASK, "Agent needs your OK", NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
        Graph.scope.launch { Graph.events.events.collect { runCatching { handle(it) } } }
    }

    /** Приложение снова на экране — уведомления о ходе работы больше не нужны. */
    fun onForeground() = runs.keys.forEach { nm.cancel(liveId(it)) }

    /** Приложение свернули — показать то, что сейчас работает. */
    fun onBackground() = runs.keys.toList().forEach(::show)

    private fun liveId(sid: String) = 0x4000 + (sid.hashCode() and 0xfff)
    private fun askId(rid: String) = 0x6000 + (rid.hashCode() and 0xfff)

    private fun handle(e: EventHub.Event) {
        val p = e.props
        when (e.type) {
            "session.status" -> {
                val sid = p.str("sessionID") ?: return
                if (p.obj("status")?.str("type") == "idle") done(sid) else { runs.getOrPut(sid) { Run() }; show(sid) }
            }
            "session.idle" -> p.str("sessionID")?.let(::done)
            "message.part.updated" -> {
                val part = p.obj("part") ?: return
                val sid = part.str("sessionID") ?: return
                val r = runs[sid] ?: return
                when (part.str("type")) {
                    "tool" -> {
                        val tool = part.str("tool") ?: return
                        val st = part.obj("state")
                        val input = st?.obj("input")
                        when (st?.str("status")) {
                            "running", "pending" -> r.step = label(tool, input)
                            "completed" -> {
                                r.steps++
                                if (tool in setOf("edit", "write", "apply_patch", "patch", "multiedit")) input?.str("filePath")?.let { r.files += it }
                            }
                        }
                    }
                    "reasoning" -> r.step = "Thinking…"
                    "text" -> r.step = "Writing the answer"
                }
                show(sid)
            }
            "permission.asked" -> {
                val req = parsePermission(p)
                if (!Graph.chats.autoAllows(req.permission) && !Graph.chats.foreground && Graph.prefs.liveNotify) ask(req, e.directory)
            }
            "permission.replied" -> p.str("requestID")?.let { nm.cancel(askId(it)) }
            "question.asked" -> if (!Graph.chats.foreground && Graph.prefs.liveNotify) question(p)
            "question.replied", "question.rejected" -> p.str("requestID")?.let { nm.cancel(askId(it)) }
        }
    }

    private fun done(sid: String) { runs.remove(sid); nm.cancel(liveId(sid)) }

    private fun base(name: String) = name.substringAfterLast('/')

    private fun label(tool: String, input: kotlinx.serialization.json.JsonObject?): String = when {
        tool == "bash" -> input?.str("description")?.let { it.replaceFirstChar { c -> c.uppercase() } } ?: "Running a command"
        tool == "read" -> "Reading " + (input?.str("filePath")?.let(::base) ?: "a file")
        tool in setOf("edit", "write", "apply_patch", "patch", "multiedit") -> "Editing " + (input?.str("filePath")?.let(::base) ?: "files")
        tool in setOf("glob", "grep", "list") -> "Searching the code"
        tool == "webfetch" -> "Reading a web page"
        tool == "websearch" -> "Searching the web"
        tool == "task" -> "Working with a subagent"
        tool == "todowrite" -> "Planning the steps"
        tool == "skill" -> "Using a skill"
        tool.startsWith("${PhoneBridge.NAME}_") -> "Using the phone"
        else -> "Using $tool"
    }

    private fun openChat(sid: String) = PendingIntent.getActivity(
        ctx, sid.hashCode(), Intent(ctx, MainActivity::class.java).putExtra("session", sid).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(kind: String, rid: String, sid: String, dir: String?) = PendingIntent.getBroadcast(
        ctx, (kind + rid).hashCode(),
        Intent(ctx, Action::class.java).putExtra("kind", kind).putExtra("rid", rid).putExtra("sid", sid).putExtra("dir", dir),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun show(sid: String) {
        val r = runs[sid] ?: return
        if (!Graph.prefs.liveNotify || Graph.chats.foreground || Graph.chats.isIncognito(sid)) { nm.cancel(liveId(sid)); return }
        val title = Graph.chats.session(sid)?.displayTitle?.ifBlank { null } ?: "OpenCode"
        val counts = listOfNotNull(
            "${r.steps} ${if (r.steps == 1) "step" else "steps"}",
            r.files.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "file" else "files"}" },
        ).joinToString(" · ")
        val b = Notification.Builder(ctx, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(r.step).setContentText(title).setSubText(counts)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
            .setUsesChronometer(true).setWhen(r.started).setShowWhen(true)
            .setContentIntent(openChat(sid))
            .addAction(Notification.Action.Builder(null, "Stop", action("stop", "", sid, Graph.chats.session(sid)?.directory)).build())
            .setPublicVersion(Notification.Builder(ctx, CH_LIVE).setSmallIcon(R.drawable.ic_stat).setContentTitle("OpenCode is working").setContentText(counts).build())
            .setProgress(0, 0, true)
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16: Live Update — чип в строке состояния и место наверху шторки
            b.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
            b.setShortCriticalText(if (r.files.isNotEmpty()) "${r.files.size} files" else "Working")
            runCatching { b.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(b, true) }
        }
        nm.notify(liveId(sid), b.build())
    }

    private fun ask(req: app.ocmobile.api.PermissionReq, eventDir: String?) {
        val dir = eventDir ?: Graph.chats.session(req.sessionID)?.directory
        val what = when (req.permission) {
            "bash" -> "Run: " + (req.metadata.str("command") ?: req.patterns.joinToString(" "))
            "edit", "write" -> "Edit " + req.patterns.joinToString(", ") { base(it) }
            "webfetch" -> "Open " + req.patterns.joinToString(", ")
            "external_directory" -> "Work outside the project: " + req.patterns.joinToString(", ")
            else -> req.permission.replaceFirstChar { it.uppercase() } + " " + req.patterns.joinToString(", ")
        }.take(300)
        val title = Graph.chats.session(req.sessionID)?.displayTitle?.ifBlank { null } ?: "OpenCode"
        nm.notify(askId(req.id), Notification.Builder(ctx, CH_ASK)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("OpenCode needs your OK").setContentText(what).setSubText(title)
            .setStyle(Notification.BigTextStyle().bigText(what))
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(Notification.Builder(ctx, CH_ASK).setSmallIcon(R.drawable.ic_stat).setContentTitle("OpenCode needs your OK").build())
            .setContentIntent(openChat(req.sessionID)).setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Allow", action("once", req.id, req.sessionID, dir)).build())
            .addAction(Notification.Action.Builder(null, "Always", action("always", req.id, req.sessionID, dir)).build())
            .addAction(Notification.Action.Builder(null, "Deny", action("reject", req.id, req.sessionID, dir)).build())
            .build())
    }

    private fun question(p: kotlinx.serialization.json.JsonObject) {
        val q = app.ocmobile.api.parseQuestion(p)
        val text = q.questions.firstOrNull()?.question ?: "OpenCode has a question"
        nm.notify(askId(q.id), Notification.Builder(ctx, CH_ASK)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("OpenCode is asking you").setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openChat(q.sessionID)).setAutoCancel(true).build())
    }

    /** Кнопки уведомлений: ответ на разрешение или остановка сессии. */
    class Action : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val kind = i.getStringExtra("kind") ?: return
            val rid = i.getStringExtra("rid").orEmpty()
            val sid = i.getStringExtra("sid").orEmpty()
            val dir = i.getStringExtra("dir")
            val pr = goAsync()
            Graph.scope.launch {
                try {
                    if (kind == "stop") { runCatching { Graph.api.abort(sid, dir) }; done(sid) }
                    else {
                        Graph.chats.permissions.value = Graph.chats.permissions.value.filterNot { it.id == rid }
                        runCatching { Graph.api.replyPermission(rid, dir, kind) }
                        c.getSystemService(NotificationManager::class.java).cancel(askId(rid))
                    }
                } finally { pr.finish() }
            }
        }
    }
}
