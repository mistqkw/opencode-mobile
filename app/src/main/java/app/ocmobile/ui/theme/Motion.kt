package app.ocmobile.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/**
 * Whether endless motion is welcome: off when the system's animator scale is 0 (animations turned off
 * in developer or accessibility settings) or battery saver is on. Then breathing, blinking, shimmer,
 * spinners and rain show a still frame and transitions are a short fade.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val ctx = LocalContext.current
    val scale = remember { runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) }
    val pm = remember { ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var saver by remember { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(pm) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { saver = pm?.isPowerSaveMode == true }
        }
        ctx.registerReceiver(r, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    return scale > 0f && !saver
}

/**
 * A timer loop (delay-based) that runs only while the app is on screen. Compose already stops frame
 * animations in the background; loops built on delay() don't stop by themselves.
 */
@Composable
fun WhileStarted(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, *keys) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block) }
}
