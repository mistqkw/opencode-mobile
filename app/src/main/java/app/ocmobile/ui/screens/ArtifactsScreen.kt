package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.long
import app.ocmobile.api.str
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.CodeBlock
import app.ocmobile.ui.components.EmptyState
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/** Файл, который агент создал или изменил в какой-то из сессий. */
private data class Artifact(val file: String, val sessionTitle: String, val updated: Long, val adds: Long, val dels: Long, val content: String?, val patch: String?)

/** Вкладка Artifacts: файлы из последних сессий (по данным diff каждой сессии). */
@Composable
fun ArtifactsScreen(nav: Nav, onMenu: () -> Unit) {
    val c = Oc.c
    val sessions by Graph.chats.sessions.collectAsState()
    var items by remember { mutableStateOf<List<Artifact>?>(null) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Artifact?>(null) }

    LaunchedEffect(sessions.size, Graph.devices.activeId.collectAsState().value) {
        val recent = sessions.filter { (it.summary?.long("files") ?: 0L) > 0 }.take(14)
        items = coroutineScope {
            recent.map { s ->
                async {
                    runCatching { Graph.api.diff(s.id, s.directory) }.getOrNull().orEmpty().mapNotNull { it as? JsonObject }.map { f ->
                        Artifact(f.str("file") ?: "?", s.displayTitle, s.time.updated, f.long("additions") ?: 0, f.long("deletions") ?: 0, f.str("after"), f.str("patch"))
                    }
                }
            }.awaitAll().flatten()
        }
    }
    val list = items?.filter { query.isBlank() || it.file.contains(query, true) || it.sessionTitle.contains(query, true) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Artifacts", left = { IconBtn(Ic.Menu, onMenu) })
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(30.dp)).border(1.5.dp, c.border, Oc.round(30.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(Ic.Search, tint = c.textDim, size = 26.dp); Spacer(Modifier.width(16.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Txt("Search artifacts", size = 19.sp, color = c.textDim)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 19.sp), cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth())
            }
        }
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(32.dp) }
            list.isEmpty() -> EmptyState(Ic.Puzzle, "No artifacts yet", "Files that OpenCode creates in your sessions will show up here.", Modifier.padding(top = 80.dp))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.file + it.sessionTitle }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(Oc.round(30.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .22f)).border(1.dp, c.border.copy(alpha = .6f), Oc.round(30.dp))
                            .tap(scale = 0.985f) { open = a }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(width = 126.dp, height = 84.dp).clip(Oc.round(18.dp)).background(c.surface), contentAlignment = Alignment.Center) {
                            Txt(a.file.substringAfterLast('.', "").uppercase().take(5).ifBlank { "FILE" }, size = 20.sp, color = c.textDim)
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(a.file.substringAfterLast('/'), size = 20.sp, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Ico(Ic.Code, tint = c.textDim, size = 18.dp); Spacer(Modifier.width(8.dp))
                                Txt(a.sessionTitle, size = 16.sp, color = c.textDim, maxLines = 1)
                            }
                            Spacer(Modifier.height(4.dp))
                            Txt("Edited ${ago(a.updated)} · +${a.adds} −${a.dels}", size = 15.sp, color = c.textDim, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    open?.let { a ->
        val clip = LocalClipboardManager.current
        OcSheet({ open = null }, a.file.substringAfterLast('/'), skipHalf = false) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Txt(a.file, size = 14.sp, color = Oc.c.textDim)
                a.content?.let { CodeBlock(it, a.file.substringAfterLast('.', "")) }
                if (a.content == null) a.patch?.let { DiffView(it) }
                a.content?.let { Row { app.ocmobile.ui.components.SmallButton("Copy", { clip.setText(AnnotatedString(it)); Graph.chats.toasts.tryEmit("Copied") }) } }
            }
        }
    }
}
