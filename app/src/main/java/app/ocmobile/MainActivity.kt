package app.ocmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.ocmobile.ui.OcRoot
import app.ocmobile.ui.theme.OcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // no saved state: the app was started fresh, not brought back from the background
        if (savedInstanceState == null) app.ocmobile.ui.NavMemory.startFresh()
        setContent { OcTheme { OcRoot() } }
        handle(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: android.content.Intent?) {
        // cl0udly://connect?… — a QR code from a computer, opened by the camera or as a link
        if (i?.action == android.content.Intent.ACTION_VIEW) i.dataString?.let { app.ocmobile.data.ConnectLink.offer(it) }
        i?.getStringExtra("session")?.let { Graph.chats.openRequest.value = it }
        if (i?.action == android.content.Intent.ACTION_SEND || i?.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
            app.ocmobile.data.ShareInbox.take(this, i)
            Graph.chats.openRequest.value = "route:home"
        }
        // debug build only: adb am start --es dev_edition kimi --es dev_route settings|home|drawer
        if (BuildConfig.DEBUG) {
            i?.getStringExtra("dev_edition")?.let { Graph.prefs.edition = it }
            i?.getStringExtra("dev_route")?.let { Graph.chats.openRequest.value = "route:$it" }
            i?.getStringExtra("dev_holiday")?.let { app.ocmobile.ui.components.MascotSignals.forced = app.ocmobile.ui.components.Holiday.parse(it) }
        }
    }

    override fun onStart() { super.onStart(); Graph.chats.foreground = true; app.ocmobile.data.LiveNotify.onForeground() }
    override fun onStop() { super.onStop(); Graph.chats.foreground = false; app.ocmobile.data.LiveNotify.onBackground() }

    override fun onResume() {
        super.onResume()
        // Android may have killed the server in the background: start it again unless it was stopped by hand.
        val s = Graph.runtime.state.value
        if (Graph.prefs.autoStartServer && !Graph.runtime.userStopped && (s is app.ocmobile.runtime.RuntimeState.Crashed || s is app.ocmobile.runtime.RuntimeState.Stopped)) Graph.runtime.startServer()
    }
}
