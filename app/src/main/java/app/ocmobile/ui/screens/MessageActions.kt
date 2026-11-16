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
import app.ocmobile.ui.components.FloatItem
import app.ocmobile.ui.components.FloatMenu
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

fun userText(m: ChatMessage) = m.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
fun assistantText(m: ChatMessage) = m.parts.filter { it.type == "text" }.joinToString("\n\n") { it.text }
fun messageText(m: ChatMessage) = if (m.info.isUser) userText(m) else assistantText(m)

/** The ⋯ / long-press menu of a message: a card floating right next to it, like the chat's ⋯ menu. */
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
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val text = messageText(m)
    // which model wrote the reply and what it cost: kept here, out of the buttons under the reply
    val header = if (m.info.isUser) null
        else listOfNotNull(m.info.modelName, m.info.cost?.takeIf { it > 0 }?.let { app.ocmobile.ui.components.Fmt.price(it) }).joinToString("   ").ifBlank { null }
    val items = buildList {
        if (text.isNotBlank()) {
            add(FloatItem("Copy", Ic.Copy) { clip.setText(AnnotatedString(text)); app.ocmobile.ui.components.copiedNote() })
            add(FloatItem("Select text", Ic.IBeam, act = onSelect))
        }
        onEdit?.let { add(FloatItem("Edit", Ic.Edit, act = it)) }
        onRetry?.let { add(FloatItem("Retry", Ic.Refresh, act = it)) }
        add(FloatItem("Fork from here", Ic.Branch, act = onFork))
        if (text.isNotBlank()) add(FloatItem("Share", Ic.Share) {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
        })
        add(FloatItem("Delete message", Ic.Trash, danger = true, act = onDelete))
    }
    FloatMenu(onDismiss, items, header)
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
