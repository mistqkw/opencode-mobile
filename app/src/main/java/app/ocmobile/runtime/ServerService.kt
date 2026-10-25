package app.ocmobile.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import app.ocmobile.Graph
import app.ocmobile.MainActivity
import app.ocmobile.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Foreground-сервис, в котором живёт процесс `opencode serve`. */
class ServerService : Service() {
    companion object {
        const val ACTION_START = "app.ocmobile.START"
        const val ACTION_STOP = "app.ocmobile.STOP"
        private const val CHANNEL = "server"
        private const val ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(ID, notification("Starting server…"))
        if (intent?.action == ACTION_STOP) {
            Graph.runtime.stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        if (Graph.prefs.keepAwake && wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ocmobile:server").apply { setReferenceCounted(false); acquire() }
        }
        if (job == null) {
            job = scope.launch {
                launch {
                    Graph.runtime.state.collect { s ->
                        val text = when (s) {
                            is RuntimeState.Running -> "OpenCode ${s.version} · localhost:${Graph.prefs.serverPort}"
                            is RuntimeState.Crashed -> "Server crashed"
                            else -> "Starting server…"
                        }
                        getSystemService(NotificationManager::class.java).notify(ID, notification(text))
                    }
                }
                Graph.runtime.supervise()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Graph.runtime.requestStop()
        scope.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "OpenCode server", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("OpenCode")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }
}
