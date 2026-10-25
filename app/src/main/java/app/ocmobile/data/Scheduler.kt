package app.ocmobile.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.ocmobile.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Calendar

/** Задача по расписанию: в заданное время создаёт сессию в проекте и отправляет в неё промпт. */
@Serializable
data class ScheduledTask(
    val id: String,
    val title: String,
    val prompt: String,
    val directory: String,
    val hour: Int,
    val minute: Int,
    val repeat: String,          // once | daily | weekly
    val enabled: Boolean = true,
    val lastRun: Long = 0,
)

object Scheduler {
    private val json = Json { ignoreUnknownKeys = true }
    val tasks = MutableStateFlow<List<ScheduledTask>>(emptyList())

    fun load(ctx: Context) {
        tasks.value = runCatching { json.decodeFromString(ListSerializer(ScheduledTask.serializer()), Graph.prefs.scheduledJson) }.getOrDefault(emptyList())
        tasks.value.forEach { arm(ctx, it) }
    }

    private fun save(ctx: Context, l: List<ScheduledTask>) {
        Graph.prefs.scheduledJson = json.encodeToString(ListSerializer(ScheduledTask.serializer()), l)
        tasks.value = l
    }

    fun upsert(ctx: Context, t: ScheduledTask) {
        save(ctx, tasks.value.filterNot { it.id == t.id } + t)
        if (t.enabled) arm(ctx, t) else cancel(ctx, t.id)
    }

    fun remove(ctx: Context, id: String) { cancel(ctx, id); save(ctx, tasks.value.filterNot { it.id == id }) }

    fun nextTime(t: ScheduledTask, from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.HOUR_OF_DAY, t.hour); set(Calendar.MINUTE, t.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, if (t.repeat == "weekly") 7 else 1)
        return c.timeInMillis
    }

    private fun pending(ctx: Context, id: String) =
        PendingIntent.getBroadcast(ctx, id.hashCode(), Intent(ctx, Receiver::class.java).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun arm(ctx: Context, t: ScheduledTask) {
        if (!t.enabled) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime(t), pending(ctx, t.id))
    }

    private fun cancel(ctx: Context, id: String) { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx, id)) }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getStringExtra("id") ?: return
            val t = tasks.value.firstOrNull { it.id == id } ?: return
            val app = ctx.applicationContext
            val pr = goAsync()
            Graph.scope.launch {
                try {
                    runCatching {
                        val s = Graph.chats.createSession(t.directory)
                        Graph.chats.send(app, s.id, t.prompt, emptyList())
                    }.onFailure { Graph.chats.toasts.tryEmit("Scheduled task failed: ${it.message}") }
                    val done = t.copy(lastRun = System.currentTimeMillis(), enabled = t.repeat != "once")
                    upsert(app, done)
                } finally { pr.finish() }
            }
        }
    }
}
