package app.ocmobile.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.str
import app.ocmobile.data.ChatMessage
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

fun userText(m: ChatMessage) = m.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
fun assistantText(m: ChatMessage) = m.parts.filter { it.type == "text" }.joinToString("\n\n") { it.text }
fun messageText(m: ChatMessage) = if (m.info.isUser) userText(m) else assistantText(m)

/** Меню долгого нажатия на сообщение — как в Claude: копировать, выделить, править, повторить. */
@Composable
fun MessageActionsSheet(
    m: ChatMessage,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onFork: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Oc.c
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val text = messageText(m)
    fun done(f: () -> Unit) = { onDismiss(); f() }
    OcSheet(onDismiss) {
        Column(Modifier.padding(bottom = 20.dp)) {
            if (text.isNotBlank()) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(16.dp)) {
                    Txt(text, size = 15.sp, color = c.textDim, maxLines = 3)
                }
                Spacer(Modifier.height(8.dp))
            }
            SettingsGroup {
                if (text.isNotBlank()) {
                    SettingsRow("Copy", icon = Ic.Copy, onClick = done { clip.setText(AnnotatedString(text)); Graph.chats.toasts.tryEmit("Copied") })
                    SettingsRow("Select text", icon = Ic.IBeam, onClick = done(onSelect))
                }
                onEdit?.let { SettingsRow("Edit", subtitle = "Rewinds the session to this message", icon = Ic.Edit, onClick = done(it)) }
                onRetry?.let { SettingsRow("Retry", icon = Ic.Refresh, onClick = done(it)) }
                SettingsRow("Fork from here", icon = Ic.Branch, onClick = done(onFork))
                if (text.isNotBlank()) SettingsRow("Share", icon = Ic.Share, onClick = done {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                })
                SettingsRow("Delete message", icon = Ic.Trash, titleColor = c.danger, onClick = done(onDelete))
            }
        }
    }
}

@Composable
fun SelectTextSheet(text: String, onDismiss: () -> Unit) {
    val c = Oc.c
    OcSheet(onDismiss, "Select text", skipHalf = false) {
        Box(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SelectionContainer { Text(text, color = c.text, fontSize = 17.sp, lineHeight = 26.sp, fontFamily = Oc.body) }
        }
    }
}
