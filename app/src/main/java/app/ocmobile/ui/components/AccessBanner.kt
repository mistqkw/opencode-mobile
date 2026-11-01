package app.ocmobile.ui.components

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

/**
 * A calm hint on home: if Android may put the server to sleep (no battery optimization
 * exemption) or notifications are off, long tasks get cut off and Allow / Deny don't arrive.
 * Tapping leads to the right setting; the cross hides the hint for good. Checked every time you return to the app.
 */
@Composable
fun AccessBanner() {
    val c = Oc.c
    val ctx = LocalContext.current
    val prefs = Graph.prefs
    var hidden by remember { mutableStateOf(prefs.accessBannerHidden) }
    var rev by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) rev++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    if (hidden || Graph.devices.isRemote) return
    @Suppress("UNUSED_EXPRESSION") rev
    val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    if (notif && battery) return
    val text = when {
        !battery && !notif -> "Allow background work and notifications so tasks don't stop"
        !battery -> "Allow background work so long tasks don't stop"
        else -> "Turn on notifications for answers and Allow / Deny"
    }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape)
            .tap(scale = 0.98f) {
                val i = if (!battery) @Suppress("BatteryLife") Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(if (!battery) Ic.Zap else Ic.Bell, tint = c.textDim, size = 17.dp); Spacer(Modifier.width(10.dp))
        Txt(text, Modifier.weight(1f), size = 14.sp, maxLines = 2)
        IconBtn(Ic.Close, { prefs.accessBannerHidden = true; hidden = true }, tint = c.textDim, size = 38.dp, iconSize = 18.dp)
    }
}
