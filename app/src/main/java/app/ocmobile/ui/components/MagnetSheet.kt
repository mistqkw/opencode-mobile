package app.ocmobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A bottom sheet with two resting places, half and full, that pull like magnets: while you drag, the
 * sheet sticks to a resting place it comes near (with a light tick), and when you let go a stiff spring
 * snaps it to the place your flick points at. Above the top it stretches with resistance; a flick down
 * from half closes it. The content's own scroll hands over to the sheet at its top. Every sheet in the
 * app goes through here, so they all pull the same way; [half] = false keeps only the full place.
 */

/** Closes the sheet around it with its slide-down, then runs [after]; outside a sheet it just runs it. */
val LocalSheetClose = compositionLocalOf<(after: () -> Unit) -> Unit> { { it() } }

@Composable
fun MagnetSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    half: Boolean = true,
    color: Color? = null,
    corner: Dp = 32.dp,
    handle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // cl0udly: no sheet from the bottom, a card that opens where you tapped
    if (Oc.look.id == "cloud") { FloatSheet(onDismiss, title, onBack, content); return }
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val motion = app.ocmobile.ui.theme.rememberMotionEnabled()
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val density = LocalDensity.current
            val h = constraints.maxHeight.toFloat()
            val top = with(density) { 56.dp.toPx() }
            val magnet = with(density) { 56.dp.toPx() }
            var sheetH by remember { mutableFloatStateOf(0f) }
            // where the sheet's top edge rests: full shows all of it (or up to near the top), half shows ~55 % of the screen
            val full = (h - sheetH).coerceAtLeast(top)
            val halfAt = maxOf(full, h * 0.45f)
            val anchors = if (!half || halfAt - full < magnet) listOf(full) else listOf(full, halfAt)
            val pos = remember { Animatable(h) }
            var raw by remember { mutableFloatStateOf(h) }
            var stuck by remember { mutableStateOf<Float?>(null) }
            var closing by remember { mutableStateOf(false) }
            var dragging by remember { mutableStateOf(false) }
            // which resting place it's at (0 = full); kept when the places move, so it follows them
            var rest by remember { mutableStateOf(-1) }

            fun close(after: () -> Unit = {}) {
                if (closing) return
                closing = true
                scope.launch { pos.animateTo(h, if (motion) tween(170) else tween(0)); onDismiss(); after() }
            }
            androidx.activity.compose.BackHandler { if (onBack != null) onBack() else close() }

            // the finger's position, made sticky near the anchors and stretchy above the top
            fun shown(r: Float): Float {
                if (r < full) return full - (full - r) * 0.3f
                val a = anchors.minByOrNull { abs(it - r) } ?: return r
                val d = r - a
                return if (abs(d) < magnet) a + d * (abs(d) / magnet) else r
            }
            fun dragBy(dy: Float) {
                if (closing) return
                dragging = true
                raw = (raw + dy).coerceIn(top - magnet, h)
                val a = anchors.firstOrNull { abs(it - raw) < magnet * 0.35f }
                if (a != null && a != stuck) haptic()
                stuck = a
                scope.launch { pos.snapTo(shown(raw)) }
            }
            fun settle(v: Float) {
                dragging = false
                if (closing) return
                val aim = raw + v * 0.16f
                val lowest = anchors.last()
                if (aim > lowest + (h - lowest) * 0.35f || v > 2600f && raw > lowest - magnet) return close()
                val target = anchors.minByOrNull { abs(it - aim) } ?: full
                raw = target; stuck = target; rest = anchors.indexOf(target)
                // a stiff spring with a little give: it lands like it was pulled in
                scope.launch { pos.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 700f), initialVelocity = v) }
            }

            // opening, and later whenever the places move (content grew, a sub-page, the keyboard): glide along
            LaunchedEffect(anchors) {
                if (sheetH <= 0f || closing || dragging) return@LaunchedEffect
                val first = rest < 0
                rest = if (first) anchors.lastIndex else rest.coerceAtMost(anchors.lastIndex)
                val target = anchors[rest]
                raw = target; stuck = target
                when {
                    !motion -> pos.snapTo(target)
                    first -> pos.animateTo(target, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
                    else -> pos.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 900f))
                }
            }

            val nested = remember(anchors, full) {
                object : NestedScrollConnection {
                    // finger up while the sheet isn't fully out: the sheet goes up first
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < 0 && raw > full + 0.5f && source == NestedScrollSource.UserInput) { dragBy(available.y); return Offset(0f, available.y) }
                        return Offset.Zero
                    }
                    // finger down with the content at its top: the sheet follows
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (available.y > 0 && source == NestedScrollSource.UserInput) { dragBy(available.y); return Offset(0f, available.y) }
                        return Offset.Zero
                    }
                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (abs(raw - full) > 0.5f) { settle(available.y); return available }
                        dragging = false
                        return Velocity.Zero
                    }
                }
            }

            // the dim behind follows the sheet; a tap on it closes
            val lowest = anchors.lastOrNull() ?: h
            Box(
                Modifier.fillMaxSize()
                    .background(c.scrim.copy(alpha = c.scrim.alpha * ((h - pos.value) / (h - lowest).coerceAtLeast(1f)).coerceIn(0f, 1f)))
                    .clickable(remember { MutableInteractionSource() }, null) { close() },
            )
            val shape = if (Oc.terminal) RectangleShape else RoundedCornerShape(topStart = corner, topEnd = corner)
            // the whole sheet drags; lists inside scroll first and hand over at their top (nested above)
            Column(
                Modifier.offset { IntOffset(0, pos.value.toInt()) }.fillMaxWidth()
                    .heightIn(max = with(density) { (h - top).toDp() })
                    .onSizeChanged { sheetH = it.height.toFloat() }
                    .clip(shape).background(color ?: c.bg)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .nestedScroll(nested)
                    .draggable(
                        rememberDraggableState { dragBy(it) }, Orientation.Vertical,
                        onDragStarted = { raw = pos.value }, onDragStopped = { v -> settle(v) },
                    ),
            ) {
                if (handle) Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 4.dp).width(40.dp).height(5.dp).clip(CircleShape).background(c.border))
                if (title != null) Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp)) {
                    IconBtn(if (onBack != null) Ic.Back else Ic.Close, onBack ?: { close() }, Modifier.align(Alignment.CenterStart))
                    Txt(title, Modifier.align(Alignment.Center), size = 20.sp, weight = FontWeight.Medium)
                }
                CompositionLocalProvider(LocalSheetClose provides { after -> close(after) }) {
                    Column(Modifier.navigationBarsPadding(), content = content)
                }
            }
        }
    }
}
