package app.ocmobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.layout.onSizeChanged

class FloatItem(val label: String, val icon: ImageVector? = null, val danger: Boolean = false, val selected: Boolean = false, val act: () -> Unit)

/** Where the last menu was opened from (window coordinates), so it can float right next to it. */
object MenuAnchor {
    var rect: Rect? = null
    /** The last finger-down anywhere in the app, for windows opened by an ordinary tap. */
    var touch: androidx.compose.ui.geometry.Offset? = null

    /** The place for the window opening now: a marked element if one was set, else the last touch. */
    fun take(): Rect? = (rect ?: touch?.let { Rect(it, androidx.compose.ui.geometry.Size.Zero) }).also { rect = null }
}

/** Notes every finger-down inside this layout in [MenuAnchor.touch] (in window coordinates), without taking the touch. */
fun Modifier.trackTouches(): Modifier = composed {
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    onGloballyPositioned { origin = it.localToWindow(androidx.compose.ui.geometry.Offset.Zero) }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    e.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { MenuAnchor.touch = origin + it.position }
                }
            }
        }
}

/** Remembers this element's place; call [MenuAnchor] with it right before opening a menu. */
fun Modifier.anchorTo(set: (Rect) -> Unit): Modifier = onGloballyPositioned { set(it.boundsInWindow()) }

/** Long press that also tells [MenuAnchor] where the press happened. */
fun Modifier.longPressAnchored(onLong: () -> Unit): Modifier = composed {
    var r by remember { mutableStateOf<Rect?>(null) }
    anchorTo { r = it }.longPress { MenuAnchor.rect = r; onLong() }
}

/**
 * A card floating next to what opened it, like the chat's ⋯ menu: the chat shows a little through it.
 * Opens below the anchor, or above when there's no room below; [alignEnd] lines it up with the anchor's right edge.
 */
@Composable
fun FloatMenu(onDismiss: () -> Unit, items: List<FloatItem>, header: String? = null, anchor: Rect? = null, alignEnd: Boolean = false) {
    val c = Oc.c
    val at = remember { anchor ?: MenuAnchor.take() }
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val edge = with(LocalDensity.current) { 10.dp.roundToPx() }
    var above by remember { mutableStateOf(false) }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val shape = RoundedCornerShape(22.dp)
    val place = remember(at) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val a = at ?: return IntOffset((windowSize.width - popupContentSize.width) / 2, (windowSize.height - popupContentSize.height) / 2)
                val x = (if (alignEnd) a.right.toInt() - popupContentSize.width else a.left.toInt()).coerceIn(edge, (windowSize.width - popupContentSize.width - edge).coerceAtLeast(edge))
                val below = a.bottom.toInt() + gap
                val up = below + popupContentSize.height > windowSize.height - edge
                above = up
                val y = if (up) (a.top.toInt() - gap - popupContentSize.height).coerceAtLeast(edge) else below
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = place, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            shown,
            enter = fadeIn(tween(110)) + scaleIn(tween(130), initialScale = 0.92f, transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, if (above) 1f else 0f)),
        ) {
            Column(
                Modifier.width(260.dp).clip(shape).background(c.surface.copy(alpha = 0.94f))
                    .border(1.dp, c.border, shape).padding(vertical = 8.dp),
            ) {
                if (header != null) {
                    Txt(header, Modifier.padding(horizontal = 22.dp, vertical = 6.dp), size = 13.sp, color = c.textDim, maxLines = 1)
                    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(c.border))
                }
                items.forEach { item ->
                    val col = if (item.danger) c.danger else c.text
                    Row(
                        Modifier.fillMaxWidth().clickable { onDismiss(); item.act() }.padding(horizontal = 22.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.icon?.let { Ico(it, tint = col, size = 22.dp); Spacer(Modifier.width(20.dp)) }
                        Txt(item.label, Modifier.weight(1f), size = 17.sp, color = col, maxLines = 1)
                        if (item.selected) Ico(app.ocmobile.ui.theme.Ic.Check, tint = c.text, size = 20.dp)
                    }
                }
            }
        }
    }
}

/**
 * cl0udly's windows: instead of a sheet rising from the bottom, a card that opens right where you tapped,
 * below the finger or above it, whichever side has room, as if it grew out of the screen. Too tall for
 * either side, it slides to fit and scrolls inside. Back and a tap outside close it.
 */
@Composable
fun FloatSheet(
    onDismiss: () -> Unit,
    title: String?,
    onBack: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = Oc.c
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val motion = app.ocmobile.ui.theme.rememberMotionEnabled()
    val at = remember { MenuAnchor.take() }
    val open = remember { androidx.compose.animation.core.Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    fun close(after: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch { open.animateTo(0f, if (motion) tween(110) else tween(0)); onDismiss(); after() }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { open.animateTo(1f, if (motion) tween(150, easing = androidx.compose.animation.core.FastOutSlowInEasing) else tween(0)) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false),
    ) {
        androidx.activity.compose.BackHandler { if (onBack != null) onBack() else close() }
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize().trackTouches()
                // a light dim, so the screen stays readable around the card
                .background(c.scrim.copy(alpha = c.scrim.alpha * 0.35f * open.value))
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { close() }
                .systemBarsPadding().imePadding(),
        ) {
            val dens = LocalDensity.current
            val h = constraints.maxHeight.toFloat()
            val w = constraints.maxWidth.toFloat()
            val margin = with(dens) { 10.dp.toPx() }
            val gap = with(dens) { 8.dp.toPx() }
            // the finger in this box's coordinates (it starts below the status bar)
            val boxTop = androidx.compose.foundation.layout.WindowInsets.systemBars.getTop(dens).toFloat()
            val fy = at?.let { (it.top + it.bottom) / 2 - boxTop } ?: (h / 2)
            val fx = at?.let { (it.left + it.right) / 2 } ?: (w / 2)
            var cardH by remember { mutableStateOf(0f) }
            val roomBelow = h - fy - gap - margin
            val roomAbove = fy - gap - margin
            val below = at == null || cardH <= roomBelow || roomBelow >= roomAbove
            val wanted = when {
                at == null -> (h - cardH) / 2
                below -> fy + gap
                else -> fy - gap - cardH
            }
            val top = wanted.coerceIn(margin, (h - margin - cardH).coerceAtLeast(margin))
            // moves smoothly when the card grows (a sub-page) instead of jumping
            val shownTop by androidx.compose.animation.core.animateFloatAsState(top, if (cardH > 0f && open.value >= 1f) tween(140) else tween(0), label = "top")
            val shape = RoundedCornerShape(26.dp)
            Column(
                Modifier.offset { IntOffset(0, shownTop.toInt()) }
                    .padding(horizontal = 12.dp).fillMaxWidth()
                    .heightIn(max = with(dens) { (h - margin * 2).toDp() })
                    .onSizeChanged { cardH = it.height.toFloat() }
                    .graphicsLayer {
                        alpha = open.value
                        val s = 0.94f + 0.06f * open.value
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin((fx / w).coerceIn(0f, 1f), if (below) 0f else 1f)
                    }
                    .clip(shape).background(c.surface.copy(alpha = 0.96f)).border(1.dp, c.border, shape)
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}
                    .animateContentSize(tween(140)),
            ) {
                if (title != null) Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp)) {
                    IconBtn(if (onBack != null) app.ocmobile.ui.theme.Ic.Back else app.ocmobile.ui.theme.Ic.Close, onBack ?: { close() }, Modifier.align(Alignment.CenterStart), size = 40.dp)
                    Txt(title, Modifier.align(Alignment.Center), size = 17.sp, weight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1)
                } else Spacer(Modifier.height(8.dp))
                androidx.compose.runtime.CompositionLocalProvider(LocalSheetClose provides { after -> close(after) }) {
                    Column(Modifier.padding(bottom = 6.dp), content = content)
                }
            }
        }
    }
}
