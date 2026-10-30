package app.ocmobile.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
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

/**
 * Панель над выделенным текстом — как в Claude: Copy, Select all, Reply, Read aloud.
 * Подменяет системную через LocalTextToolbar; для «Reply» и «Read aloud» выделение берём через копирование.
 */
class ChatTextToolbar : TextToolbar {
    var rect by mutableStateOf<Rect?>(null)
    var copy: (() -> Unit)? = null
    var selectAll: (() -> Unit)? = null
    override var status by mutableStateOf(TextToolbarStatus.Hidden)
        private set

    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
        this.rect = rect; copy = onCopyRequested; selectAll = onSelectAllRequested
        status = TextToolbarStatus.Shown
    }

    override fun hide() { status = TextToolbarStatus.Hidden; rect = null }
}

@Composable
fun SelectionBarHost(bar: ChatTextToolbar, onReply: (String) -> Unit, onRead: (String) -> Unit) {
    val r = bar.rect ?: return
    if (bar.status != TextToolbarStatus.Shown) return
    val c = Oc.c
    val ctx = LocalContext.current
    val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
    fun selected(after: (String) -> Unit) {
        bar.copy?.invoke()
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
        bar.hide()
        if (t.isNotBlank()) after(t)
    }
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (r.center.x - popupContentSize.width / 2f).toInt().coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
                val above = r.top.toInt() - popupContentSize.height - gap
                val y = if (above > gap * 6) above else (r.bottom.toInt() + gap * 4).coerceAtMost(windowSize.height - popupContentSize.height - gap)
                return IntOffset(x, y)
            }
        },
        properties = PopupProperties(focusable = false),
    ) {
        Row(Modifier.clip(Oc.round(12.dp)).background(c.surfaceHigh).border(1.dp, c.border, Oc.round(12.dp))) {
            @Composable fun item(label: String, onClick: () -> Unit) {
                Box(Modifier.tap(scale = 1f, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) { Txt(label, size = 15.sp) }
            }
            bar.copy?.let { cp -> item("Copy") { cp(); bar.hide(); app.ocmobile.Graph.chats.toasts.tryEmit("Copied") } }
            bar.selectAll?.let { sa -> item("Select all") { sa() } }
            if (bar.copy != null) {
                item("Reply") { selected(onReply) }
                item("Read aloud") { selected(onRead) }
            }
        }
    }
}
