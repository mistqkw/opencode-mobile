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

    PickerFrame(
        onDismiss = onDismiss,
        title = when (page) { "perm" -> "Permission"; "agent" -> "Agent"; else -> if (Oc.terminal) "Commands" else "Add to session" },
        onBack = if (page != "main") ({ page = "main" }) else null,
        skipHalf = Oc.edition != "claude",
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = if (Oc.edition == "claude") 24.dp else 10.dp)) {
            when (page) {
                "perm" -> {
                    listOf(
                        Triple("ask", "Ask", "Confirm every file edit and command"),
                        Triple("edits", "Auto-edit", "Edit files freely, ask before commands and web"),
                        Triple("auto", "Auto", "Run everything without asking"),
                    ).forEach { (k, l, d) ->
                        PickRow(l, {
                            prefs.permissionMode = k
                            if (sessionId != null) scope.launch {
                                runCatching {
                                    Graph.api.setSessionPermission(sessionId, Graph.chats.session(sessionId)?.directory,
                                        Graph.chats.permissionRules(k) ?: kotlinx.serialization.json.JsonArray(emptyList()))
                                }
                            }
                            page = "main"
                        }, selected = prefs.permissionMode == k, subtitle = d)
                    }
                }
                "agent" -> agents.forEach { a ->
                    PickRow(a.name.replaceFirstChar { it.uppercase() }, { prefs.agent = a.name; page = "main" }, selected = prefs.agent == a.name, subtitle = a.description)
                }
                else -> if (Oc.edition == "claude") {
                    Row(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("Camera", Ic.Camera, Modifier.weight(1f), onCamera)
                        Tile("Photos", Ic.Image, Modifier.weight(1f), onPhotos)
                        Tile("Files", Ic.FileUp, Modifier.weight(1f), onFiles)
                    }
                    Spacer(Modifier.height(14.dp))
                    SettingsGroup {
                        SettingsRow("Permission", subtitle = permLabel[prefs.permissionMode], icon = Ic.FastForward, circleIcon = true, chevron = true, onClick = { page = "perm" })
                        SettingsRow("Agent", subtitle = prefs.agent.replaceFirstChar { it.uppercase() }, icon = Ic.Bot, circleIcon = true, chevron = true, onClick = { page = "agent" })
                        SettingsRow("Devices", icon = Ic.Laptop, circleIcon = true, chevron = true, onClick = { onDismiss(); nav.push(Route.Code) })
                    }
                    Spacer(Modifier.height(14.dp))
                    SettingsGroup {
                        SettingsRow("Plan mode", icon = Ic.ListCheck, circleIcon = true, trailing = {
                            OcSwitch(prefs.agent == "plan") { prefs.agent = if (it) "plan" else "build" }
                        })
                        SettingsRow("Connectors", icon = Ic.Puzzle, circleIcon = true, chevron = true, onClick = { onDismiss(); nav.push(Route.Connectors) })
                        SettingsRow("Skills", icon = Ic.Scroll, circleIcon = true, chevron = true, onClick = { onDismiss(); nav.push(Route.Skills) })
                        SettingsRow("Memory", icon = Ic.Scroll, circleIcon = true, trailing = {
                            OcSwitch(prefs.memoryEnabled) { prefs.memoryEnabled = it; app.ocmobile.data.Memory.sync() }
                        })
                    }
                } else {
                    // Terminal — список команд с подсказками справа; Desktop — компактное меню со значками
                    val t = Oc.terminal
                    PickRow(if (t) "Attach photo" else "Camera", onCamera, icon = Ic.Camera, trailing = if (t) "camera" else null)
                    PickRow(if (t) "Attach image" else "Photos", onPhotos, icon = Ic.Image, trailing = if (t) "photos" else null)
                    PickRow(if (t) "Attach file" else "Files", onFiles, icon = Ic.FileUp, trailing = if (t) "@" else null)
                    if (!t) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(c.border))
                    PickRow(if (t) "Switch permission" else "Permission", { page = "perm" }, icon = Ic.Shield, trailing = permLabel[prefs.permissionMode], chevron = !t)
                    PickRow(if (t) "Switch agent" else "Agent", { page = "agent" }, icon = Ic.Bot, trailing = if (t) "tab · " + prefs.agent else prefs.agent.replaceFirstChar { it.uppercase() }, chevron = !t)
                    PickRow(if (t) "Toggle plan mode" else "Plan mode", { prefs.agent = if (prefs.agent == "plan") "build" else "plan" }, icon = Ic.ListCheck, selected = !t && prefs.agent == "plan", trailing = if (t) (if (prefs.agent == "plan") "on" else "off") else null)
                    if (!t) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(c.border))
                    PickRow(if (t) "Connectors (MCP)" else "Connectors", { onDismiss(); nav.push(Route.Connectors) }, icon = Ic.Puzzle, chevron = !t)
                    PickRow("Skills", { onDismiss(); nav.push(Route.Skills) }, icon = Ic.Scroll, chevron = !t)
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Oc.c
    Column(
        modifier.clip(Oc.round(24.dp)).background(c.surface).clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Ico(icon, size = 26.dp) }
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

    PickerFrame(
        onDismiss = onDismiss,
        title = when (page) { "effort" -> "Effort"; "all" -> "More models"; else -> "Select model" },
        onBack = if (page != "main") ({ page = "main" }) else null,
        skipHalf = false,
    ) {
        if (page == "effort") {
            val names = current?.variantNames.orEmpty()
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                PickRow("Default", { prefs.variant = ""; page = "main" }, selected = prefs.variant.isBlank())
                names.forEach { n ->
                    PickRow(n.replaceFirstChar { it.uppercase() }, { prefs.variant = n; page = "main" }, selected = prefs.variant == n, badge = if (n == "medium") "Recommended" else null)
                }
                Txt("Higher effort means more thorough responses, but takes longer and uses your limits faster.", size = 15.sp, color = c.textDim, modifier = Modifier.padding(24.dp))
            }
            return@PickerFrame
        }
        val claudeEd = Oc.edition == "claude"
        if (claudeEd && page == "main") {
            ClaudeModelMain(current, onDismiss = onDismiss, onEffort = { page = "effort" }, onMore = { page = "all" })
            return@PickerFrame
        }
        PickSearch(query, { query = it }, "Search models")
        LazyColumn(Modifier.heightIn(max = 520.dp).padding(bottom = if (claudeEd) 24.dp else 8.dp)) {
            if (!claudeEd && current != null && current.variantNames.isNotEmpty()) item {
                PickRow("Effort", { page = "effort" }, trailing = prefs.variant.ifBlank { "Default" }.replaceFirstChar { it.uppercase() }, chevron = true)
            }
            providers.forEach { p ->
                val ms = p.models.values.filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) }
                    .sortedWith(compareByDescending<app.ocmobile.api.ModelInfo> { it.release_date ?: "" }.thenBy { it.name })
                if (ms.isNotEmpty()) {
                    item(key = "h-${p.id}") { PickHeader(p.name.ifBlank { p.id }) }
                    items(ms, key = { p.id + "/" + it.id }) { m ->
                        val sel = prefs.providerId == p.id && prefs.modelId == m.id
                        val meta = buildList {
                            m.limit?.context?.takeIf { it > 0 }?.let { add("${it / 1000}k ctx") }
                            if (m.supportsImages) add("images")
                            if (m.reasoning) add("reasoning")
                        }.joinToString(" · ")
                        PickRow(m.name.ifBlank { m.id }, {
                            pickModel(p.id, m)
                            if (m.variantNames.isEmpty()) onDismiss()
                        }, selected = sel, subtitle = meta.ifBlank { null })
                    }
                }
            }
            if (providers.isEmpty()) item { Txt("No connected providers yet. Connect one in Settings → Providers.", color = c.textDim, modifier = Modifier.padding(24.dp)) }
            if (claudeEd && current != null && current.variantNames.isNotEmpty()) item {
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


private fun pickModel(providerId: String, m: app.ocmobile.api.ModelInfo) {
    val prefs = Graph.prefs
    prefs.providerId = providerId; prefs.modelId = m.id
    if (prefs.variant !in m.variantNames) prefs.variant = ""
    val key = "$providerId/${m.id}"
    prefs.recentModels = (listOf(key) + prefs.recentModels.split(',').filter { it.isNotBlank() && it != key }).take(6).joinToString(",")
}

/** Главная страница выбора модели, как в Claude mobile: короткий список карточкой, подсказка, Effort и «More models». */
@Composable
private fun ClaudeModelMain(current: app.ocmobile.api.ModelInfo?, onDismiss: () -> Unit, onEffort: () -> Unit, onMore: () -> Unit) {
    val c = Oc.c
    val prefs = Graph.prefs
    val providers by Graph.catalog.providers.collectAsState()
    // сначала недавние и выбранная, потом самые свежие модели того же провайдера — всего четыре
    val featured = remember(providers, prefs.recentModels, prefs.providerId, prefs.modelId) {
        val out = LinkedHashMap<String, Pair<String, app.ocmobile.api.ModelInfo>>()
        fun add(pid: String, m: app.ocmobile.api.ModelInfo?) { if (m != null) out.putIfAbsent("$pid/${m.id}", pid to m) }
        add(prefs.providerId, Graph.catalog.model(prefs.providerId, prefs.modelId))
        prefs.recentModels.split(',').filter { it.contains('/') }.forEach { k -> add(k.substringBefore('/'), Graph.catalog.model(k.substringBefore('/'), k.substringAfter('/'))) }
        providers.firstOrNull { it.id == prefs.providerId }?.models?.values
            ?.sortedByDescending { it.release_date ?: "" }?.forEach { add(prefs.providerId, it) }
        out.values.take(4).toList()
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (featured.isEmpty()) {
            Txt("No connected providers yet. Connect one in Settings → Providers.", color = c.textDim, modifier = Modifier.padding(24.dp))
        } else SettingsGroup {
            featured.forEach { (pid, m) ->
                val sel = prefs.providerId == pid && prefs.modelId == m.id
                val meta = buildList {
                    m.limit?.context?.takeIf { it > 0 }?.let { add("${it / 1000}k context") }
                    if (m.supportsImages) add("images")
                    if (m.reasoning) add("reasoning")
                }.joinToString(" · ")
                Row(
                    Modifier.fillMaxWidth().background(c.surface).tap(scale = 1f) {
                        pickModel(pid, m)
                        if (m.variantNames.isEmpty()) onDismiss()
                    }.padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt(m.name.ifBlank { m.id }, Modifier.weight(1f, fill = false), size = 19.sp, maxLines = 1)
                        }
                        if (meta.isNotBlank()) Txt(meta, size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (sel) Ico(Ic.Check, tint = c.link, size = 24.dp)
                }
            }
        }
        Txt("Free models share one daily limit per network. Connect more providers in Settings → Providers.", size = 15.sp, color = c.textDim, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
        if (current != null && current.variantNames.isNotEmpty()) {
            Box(Modifier.padding(bottom = 14.dp)) {
                SettingsGroup {
                    Row(Modifier.fillMaxWidth().background(c.surface).tap(scale = 1f, onClick = onEffort).padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("Effort", size = 19.sp)
                        if (prefs.variant == "medium" || prefs.variant.isBlank()) { Spacer(Modifier.width(10.dp)); Chip("Recommended") }
                        Spacer(Modifier.weight(1f))
                        Txt(prefs.variant.ifBlank { "Default" }.replaceFirstChar { it.uppercase() }, size = 18.sp, color = c.textDim)
                        Spacer(Modifier.width(6.dp)); Ico(Ic.ChevronRight, tint = c.textDim, size = 22.dp)
                    }
                }
            }
        }
        SettingsGroup {
            SettingsRow("More models", chevron = true, onClick = onMore)
        }
    }
}
