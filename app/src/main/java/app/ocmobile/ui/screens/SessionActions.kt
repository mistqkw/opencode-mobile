package app.ocmobile.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.ocmobile.Graph
import app.ocmobile.api.Session
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.rememberMotionEnabled
import kotlinx.coroutines.launch

/**
 * Long-press menu for a session in lists, like Claude's: a small card that pops out under the row
 * (or above it near the bottom of the screen) with pin, rename, archive and delete.
 * Call it inside the row, so the menu knows where the row is.
 */
@Composable
fun SessionActionsMenu(s: Session, open: Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pinned = s.id in Graph.prefs.pinnedSet()
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val dir = s.directory.ifBlank { null }
    val c = Oc.c

    if (rename) {
        var t by remember { mutableStateOf(s.title) }
        val done = { rename = false; onDismiss() }
        OcDialog("Rename session", done, "Save", {
            scope.launch { runCatching { Graph.api.renameSession(s.id, dir, t) }; Graph.chats.refreshSessions() }
            done()
        }) { OcTextField(t, { t = it }) }
    }
    // no "are you sure?": the chat goes at once and "Undo" at the bottom brings it back
    if (delete) { androidx.compose.runtime.LaunchedEffect(Unit) { Graph.chats.deleteWithUndo(listOf(s.id)); delete = false; onDismiss() } }
    if (!open || rename || delete) return

    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val edge = with(LocalDensity.current) { 12.dp.roundToPx() }
    var below by remember { mutableStateOf(true) }
    val place = remember(gap, edge) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.right - popupContentSize.width).coerceIn(edge, (windowSize.width - popupContentSize.width - edge).coerceAtLeast(edge))
                val under = anchorBounds.bottom + gap
                val fits = under + popupContentSize.height <= windowSize.height - edge
                below = fits
                return IntOffset(x, if (fits) under else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(edge))
            }
        }
    }
    val motion = rememberMotionEnabled()
    Popup(popupPositionProvider = place, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val p = remember { Animatable(if (motion) 0f else 1f) }
        LaunchedEffect(Unit) { p.animateTo(1f, tween(130)) }
        Column(
            Modifier
                .graphicsLayer {
                    alpha = p.value; scaleX = 0.92f + 0.08f * p.value; scaleY = scaleX
                    transformOrigin = TransformOrigin(1f, if (below) 0f else 1f)
                }
                .width(androidx.compose.foundation.layout.IntrinsicSize.Max).widthIn(min = 210.dp).clip(Oc.round(22.dp)).background(c.surfaceHigh).border(1.dp, c.border, Oc.round(22.dp))
                .padding(vertical = 6.dp),
        ) {
            MenuItem(Ic.Pin, if (pinned) "Unpin" else "Pin") { Graph.prefs.togglePin(s.id); onDismiss() }
            MenuItem(Ic.Edit, "Rename") { rename = true }
            MenuItem(Ic.Archive, "Archive") {
                scope.launch { runCatching { Graph.api.archiveSession(s.id, dir) }; Graph.chats.refreshSessions() }
                onDismiss()
            }
            MenuItem(Ic.Trash, "Delete", c.danger) { delete = true }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: Color = Oc.c.text, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().tap(scale = 0.98f, onClick = onClick).padding(start = 20.dp, end = 28.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, tint = tint, size = 22.dp)
        Spacer(Modifier.width(18.dp))
        Txt(label, size = 18.sp, color = tint)
    }
}
