package app.ocmobile.ui.components

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.ocmobile.Graph
import app.ocmobile.data.Attachment
import app.ocmobile.data.Attachments
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

class ComposerState {
    var text by mutableStateOf("")
    val attachments = mutableStateListOf<Attachment>()
    var showAdd by mutableStateOf(false)
    var showModel by mutableStateOf(false)
    val canSend get() = text.isNotBlank() || attachments.isNotEmpty()
    fun clear() { text = ""; attachments.clear() }
}

@Composable
fun rememberComposerState() = remember { ComposerState() }

/** Поле ввода как в Claude: плюс, выбор модели, отправка; под ним листы «Add» и «Select model». */
@Composable
fun ComposerHost(
    state: ComposerState,
    nav: Nav,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    sessionId: String? = null,
    placeholder: String = "Chat with OpenCode…",
    dashed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val c = Oc.c
    val haptic = rememberHaptic()
    val prefs = Graph.prefs
    val catalog = Graph.catalog
    val providers by catalog.providers.collectAsState()

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.forEach { state.attachments.add(Attachments.describe(ctx, it)) }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            state.attachments.add(Attachments.describe(ctx, u))
        }
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { t ->
            state.text = if (state.text.isBlank()) t else state.text.trimEnd() + " " + t
        }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) state.attachments.add(Attachments.describe(ctx, u))
    }

    val model = providers.firstOrNull { it.id == prefs.providerId }?.models?.get(prefs.modelId)
    val modelName = model?.name?.takeIf { it.isNotBlank() } ?: prefs.modelId.ifBlank { "Select model" }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize()
            .clip(Oc.round(30.dp)).background(c.surface)
            .then(
                when {
                    dashed -> Modifier.dashedBorder(c.textDim, 30.dp)
                    Oc.terminal -> Modifier.drawBehind { drawRect(c.link, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                    Oc.desktop -> Modifier.border(1.dp, c.border, Oc.round(30.dp))
                    else -> Modifier.border(1.5.dp, c.border, Oc.round(30.dp))
                },
            ),
    ) {
        if (state.attachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.attachments.toList().forEach { a -> AttachmentChip(a) { state.attachments.remove(a) } }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 2.dp).heightIn(min = 26.dp, max = 200.dp)) {
            if (state.text.isEmpty()) Txt(placeholder, size = 17.sp, color = c.textDim)
            BasicTextField(
                value = state.text, onValueChange = { state.text = it },
                textStyle = TextStyle(color = c.text, fontSize = 17.sp, fontFamily = Oc.body),
                cursorBrush = SolidColor(c.text), maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Oc.edition == "claude") {
                IconBtn(Ic.Plus, { haptic(); state.showAdd = true }, size = 40.dp, bg = c.surfaceHigh)
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.widthIn(max = 230.dp).height(40.dp).clip(Oc.pill).background(c.surfaceHigh)
                        .tap(scale = 0.97f) { haptic(); state.showModel = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(modelName, size = 15.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (prefs.variant.isNotBlank()) { Spacer(Modifier.width(6.dp)); Txt(prefs.variant.replaceFirstChar { it.uppercase() }, size = 15.sp, color = c.textDim, maxLines = 1) }
                }
            } else {
                val providerName = providers.firstOrNull { it.id == prefs.providerId }?.name.orEmpty()
                IconBtn(Ic.Plus, { haptic(); state.showAdd = true }, size = 36.dp)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // агент: переключается нажатием, как Tab в терминале
                    Row(
                        Modifier.clip(Oc.round(10.dp)).tap(scale = 0.96f) { haptic(); prefs.agent = if (prefs.agent == "plan") "build" else "plan" }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(prefs.agent.replaceFirstChar { it.uppercase() }, size = 14.sp, color = if (Oc.terminal) c.link else c.text, maxLines = 1)
                        if (Oc.desktop) { Spacer(Modifier.width(2.dp)); Ico(Ic.ChevronDown, tint = c.textDim, size = 14.dp) }
                    }
                    Row(
                        Modifier.weight(1f, fill = false).clip(Oc.round(10.dp)).tap(scale = 0.96f) { haptic(); state.showModel = true }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(modelName, size = 14.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (Oc.terminal && providerName.isNotBlank()) { Spacer(Modifier.width(8.dp)); Txt(providerName, size = 13.sp, color = c.textDim, maxLines = 1, modifier = Modifier.widthIn(max = 90.dp)) }
                        if (Oc.desktop) { Spacer(Modifier.width(2.dp)); Ico(Ic.ChevronDown, tint = c.textDim, size = 14.dp) }
                    }
                    if (Oc.desktop && prefs.variant.isNotBlank()) {
                        Row(
                            Modifier.clip(Oc.round(10.dp)).tap(scale = 0.96f) { haptic(); state.showModel = true }.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt(prefs.variant.replaceFirstChar { it.uppercase() }, size = 14.sp, maxLines = 1)
                            Spacer(Modifier.width(2.dp)); Ico(Ic.ChevronDown, tint = c.textDim, size = 14.dp)
                        }
                    }
                }
            }
            if (Oc.edition == "claude") Spacer(Modifier.weight(1f))
            if (!state.canSend && !busy) {
                IconBtn(Ic.Mic, {
                    haptic()
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    runCatching { mic.launch(i) }.onFailure { Graph.chats.toasts.tryEmit("No speech recognizer on this device") }
                }, size = 40.dp, bg = c.surfaceHigh, tint = c.textDim)
                Spacer(Modifier.width(8.dp))
            }
            val ready = busy || state.canSend
            val sendBg by animateColorAsState(if (ready) c.text else c.surfaceHigh, label = "sendBg")
            Box(
                Modifier.size(40.dp).clip(Oc.pill).background(sendBg)
                    .tap(enabled = ready) { haptic(); if (busy && !state.canSend) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Ico(if (busy && !state.canSend) Ic.Stop else Ic.ArrowUp, tint = if (ready) c.bg else c.textDim, size = 20.dp)
            }
        }
    }

    if (state.showAdd) {
        AddSheet(
            onDismiss = { state.showAdd = false },
            sessionId = sessionId,
            nav = nav,
            onCamera = {
                state.showAdd = false
                val f = File(ctx.cacheDir, "camera").apply { mkdirs() }.let { File(it, "cam-${System.currentTimeMillis()}.jpg") }
                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                cameraUri = u
                camera.launch(u)
            },
            onPhotos = { state.showAdd = false; photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onFiles = { state.showAdd = false; files.launch(arrayOf("*/*")) },
        )
    }
    if (state.showModel) ModelSheet(onDismiss = { state.showModel = false })
}

@Composable
private fun AttachmentChip(a: Attachment, onRemove: () -> Unit) {
    val c = Oc.c
    Box {
        if (a.isImage) {
            AsyncImage(a.uri, null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(Oc.round(16.dp)).background(c.surfaceHigh))
        } else {
            Row(Modifier.height(72.dp).clip(Oc.round(16.dp)).background(c.surfaceHigh).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.File, tint = c.textDim, size = 24.dp); Spacer(Modifier.width(8.dp))
                Txt(a.name, size = 14.sp, maxLines = 2, modifier = Modifier.width(96.dp))
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(c.bg.copy(alpha = .85f)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Ico(Ic.Close, tint = c.text, size = 14.dp) }
    }
}

@Composable
private fun AddSheet(
    onDismiss: () -> Unit,
    sessionId: String?,
    nav: Nav,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
) {
    val c = Oc.c
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("main") }
    val agents by Graph.catalog.agents.collectAsState()
    val permLabel = mapOf("ask" to "Ask", "edits" to "Auto-edit", "auto" to "Auto")

    OcSheet(
        onDismiss = onDismiss,
        title = when (page) { "perm" -> "Permission"; "agent" -> "Agent"; else -> "Add to session" },
        onBack = if (page != "main") ({ page = "main" }) else null,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            when (page) {
                "perm" -> {
                    listOf(
                        Triple("ask", "Ask", "Confirm every file edit and command"),
                        Triple("edits", "Auto-edit", "Edit files freely, ask before commands and web"),
                        Triple("auto", "Auto", "Run everything without asking"),
                    ).forEach { (k, l, d) ->
                        ChoiceRow(l, prefs.permissionMode == k, {
                            prefs.permissionMode = k
                            if (sessionId != null) scope.launch {
                                runCatching {
                                    Graph.api.setSessionPermission(sessionId, Graph.chats.session(sessionId)?.directory,
                                        Graph.chats.permissionRules(k) ?: kotlinx.serialization.json.JsonArray(emptyList()))
                                }
                            }
                            page = "main"
                        }, subtitle = d)
                    }
                }
                "agent" -> agents.forEach { a ->
                    ChoiceRow(a.name.replaceFirstChar { it.uppercase() }, prefs.agent == a.name, { prefs.agent = a.name; page = "main" }, subtitle = a.description)
                }
                else -> {
                    Row(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("Camera", Ic.Camera, Modifier.weight(1f), onCamera)
                        Tile("Photos", Ic.Image, Modifier.weight(1f), onPhotos)
                        Tile("Files", Ic.FileUp, Modifier.weight(1f), onFiles)
                    }
                    Spacer(Modifier.height(14.dp))
                    SettingsGroup {
                        SettingsRow("Permission", subtitle = permLabel[prefs.permissionMode], icon = Ic.Shield, chevron = true, onClick = { page = "perm" })
                        SettingsRow("Agent", subtitle = prefs.agent.replaceFirstChar { it.uppercase() }, icon = Ic.Bot, chevron = true, onClick = { page = "agent" })
                    }
                    Spacer(Modifier.height(14.dp))
                    SettingsGroup {
                        SettingsRow("Plan mode", icon = Ic.ListCheck, trailing = {
                            OcSwitch(prefs.agent == "plan") { prefs.agent = if (it) "plan" else "build" }
                        })
                        SettingsRow("Connectors", icon = Ic.Puzzle, chevron = true, onClick = { onDismiss(); nav.push(Route.Connectors) })
                        SettingsRow("Skills", icon = Ic.Scroll, chevron = true, onClick = { onDismiss(); nav.push(Route.Skills) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Oc.c
    Column(
        modifier.clip(Oc.round(24.dp)).background(c.surface).clickable(onClick = onClick).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Ico(icon, size = 28.dp) }
        Spacer(Modifier.height(10.dp))
        Txt(label, size = 17.sp)
    }
}

@Composable
fun ModelSheet(onDismiss: () -> Unit) {
    val c = Oc.c
    val prefs = Graph.prefs
    val providers by Graph.catalog.providers.collectAsState()
    var page by remember { mutableStateOf("main") }
    var query by remember { mutableStateOf("") }
    val current = Graph.catalog.model(prefs.providerId, prefs.modelId)

    OcSheet(
        onDismiss = onDismiss,
        title = if (page == "effort") "Effort" else "Select model",
        onBack = if (page == "effort") ({ page = "main" }) else null,
        skipHalf = false,
    ) {
        if (page == "effort") {
            val names = current?.variantNames.orEmpty()
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                ChoiceRow("Default", prefs.variant.isBlank(), { prefs.variant = ""; page = "main" })
                names.forEach { n ->
                    ChoiceRow(n.replaceFirstChar { it.uppercase() }, prefs.variant == n, { prefs.variant = n; page = "main" }, badge = if (n == "medium") "Recommended" else null)
                }
                Txt("Higher effort means more thorough responses, but takes longer and uses your limits faster.", size = 15.sp, color = c.textDim, modifier = Modifier.padding(24.dp))
            }
            return@OcSheet
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { OcTextField(query, { query = it }, placeholder = "Search models") }
        LazyColumn(Modifier.heightIn(max = 520.dp).padding(bottom = 24.dp)) {
            providers.forEach { p ->
                val ms = p.models.values.filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) }
                    .sortedWith(compareByDescending<app.ocmobile.api.ModelInfo> { it.release_date ?: "" }.thenBy { it.name })
                if (ms.isNotEmpty()) {
                    item(key = "h-${p.id}") { SectionLabel(p.name.ifBlank { p.id }) }
                    items(ms, key = { p.id + "/" + it.id }) { m ->
                        val sel = prefs.providerId == p.id && prefs.modelId == m.id
                        val meta = buildList {
                            m.limit?.context?.takeIf { it > 0 }?.let { add("${it / 1000}k ctx") }
                            if (m.supportsImages) add("images")
                            if (m.reasoning) add("reasoning")
                        }.joinToString(" · ")
                        ChoiceRow(m.name.ifBlank { m.id }, sel, {
                            prefs.providerId = p.id; prefs.modelId = m.id
                            if (prefs.variant !in m.variantNames) prefs.variant = ""
                            if (m.variantNames.isEmpty()) onDismiss()
                        }, subtitle = meta.ifBlank { null })
                    }
                }
            }
            if (providers.isEmpty()) item { Txt("No connected providers yet. Connect one in Settings → Providers.", color = c.textDim, modifier = Modifier.padding(24.dp)) }
            if (current != null && current.variantNames.isNotEmpty()) item {
                Box(Modifier.padding(16.dp)) {
                    SettingsGroup {
                        SettingsRow("Effort", subtitle = prefs.variant.ifBlank { "Default" }.replaceFirstChar { it.uppercase() }, chevron = true, onClick = { page = "effort" })
                    }
                }
            }
        }
    }
}

private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    drawRoundRect(color, cornerRadius = CornerRadius(radius.toPx()), style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))))
}
