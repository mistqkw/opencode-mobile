package app.ocmobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.Session
import app.ocmobile.ui.components.ChoiceRow
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch

/** Лист по долгому нажатию на сессию в списках: закрепить, переименовать, в архив, удалить. */
@Composable
fun SessionActionsSheet(s: Session, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pinned = s.id in Graph.prefs.pinnedSet()
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val dir = s.directory.ifBlank { null }

    if (rename) {
        var t by remember { mutableStateOf(s.title) }
        OcDialog("Rename session", onDismiss, "Save", {
            scope.launch { runCatching { Graph.api.renameSession(s.id, dir, t) }; Graph.chats.refreshSessions() }
            onDismiss()
        }) { OcTextField(t, { t = it }) }
        return
    }
    if (delete) {
        OcDialog("Delete session?", onDismiss, "Delete", {
            scope.launch { runCatching { Graph.api.deleteSession(s.id, dir) }; Graph.chats.forget(s.id); Graph.chats.refreshSessions() }
            onDismiss()
        }, danger = true) { Txt("This permanently removes the conversation and its history.", size = 16.sp, color = Oc.c.textDim) }
        return
    }
    OcSheet(onDismiss, title = s.displayTitle.take(40)) {
        Column(Modifier.padding(bottom = 12.dp)) {
            ChoiceRow(if (pinned) "Unpin" else "Pin", false, { Graph.prefs.togglePin(s.id); onDismiss() }, icon = Ic.Pin)
            ChoiceRow("Rename", false, { rename = true }, icon = Ic.Edit)
            ChoiceRow("Archive", false, {
                scope.launch { runCatching { Graph.api.archiveSession(s.id, dir) }; Graph.chats.refreshSessions() }
                onDismiss()
            }, icon = Ic.Archive)
            ChoiceRow("Delete", false, { delete = true }, icon = Ic.Trash)
        }
    }
}
