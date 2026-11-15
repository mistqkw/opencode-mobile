package app.ocmobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

/** The bright blue of a finished task: the same in every look, so it reads at a glance. */
val DoneBlue = Color(0xFF3D8BFF)

/**
 * A chat's state as a dot: while the agent works it breathes slowly (2.4 s in, 2.4 s out),
 * once the task is done and not yet seen it stays bright blue and still.
 */
@Composable
fun WorkDot(working: Boolean, size: Dp = 9.dp, modifier: Modifier = Modifier) {
    val c = Oc.c
    if (!working) return Box(modifier.size(size).clip(CircleShape).background(DoneBlue))
    val t = rememberInfiniteTransition(label = "work-dot")
    val a by t.animateFloat(0.18f, 0.85f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
    Box(modifier.size(size).alpha(a).clip(CircleShape).background(c.text))
}

/** The menu button with a dot at its corner: breathing while any chat works, bright blue when one finished unseen. */
@Composable
fun MenuBtn(onMenu: () -> Unit) {
    val busy = Graph.chats.statuses.collectAsState().value.values.any { it != "idle" }
    val done = Graph.chats.doneUnseen.collectAsState().value.isNotEmpty()
    Box {
        IconBtn(Ic.Menu, onMenu)
        if (busy || done) WorkDot(
            working = !done, size = 9.dp,
            modifier = Modifier.align(Alignment.TopEnd).offset((-5).dp, 5.dp).border(2.dp, Oc.c.bg, CircleShape),
        )
    }
}
