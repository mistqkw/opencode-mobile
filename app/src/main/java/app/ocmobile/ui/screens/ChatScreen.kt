package app.ocmobile.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.arr
import app.ocmobile.api.long
import app.ocmobile.api.str
import app.ocmobile.data.ChatMessage
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.ComposerHost
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.ImageViewer
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.PulsingLogo
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.rememberComposerState
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
fun ChatScreen(id: String, nav: Nav, onMenu: () -> Unit) {
    val c = Oc.c
    val ctx = LocalContext.current
    val hub = Graph.chats
    val scope = rememberCoroutineScope()
    val chat = remember(id) { hub.chat(id) }
    val sessions by hub.sessions.collectAsState()
    val statuses by hub.statuses.collectAsState()
    val retries by hub.retries.collectAsState()
    val perms by hub.permissions.collectAsState()
    val questions by hub.questions.collectAsState()
    val session = sessions.firstOrNull { it.id == id } ?: hub.session(id)
    val incognito = hub.isIncognito(id)
    val busy = statuses[id] == "busy" || statuses[id] == "retry"
    val state = rememberComposerState()
    val listState = rememberLazyListState()
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDiff by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ChatMessage?>(null) }
    var selectText by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    val pinned = Graph.prefs.pinnedSet()
    var stick by remember { mutableStateOf(true) }

    LaunchedEffect(id) {
        if (chat.directory == null) chat.directory = hub.session(id)?.directory
        chat.load()
    }

    // Сессия «инкогнито» исчезает, когда экран закрывается.
    DisposableEffect(id) {
        onDispose { if (hub.isIncognito(id)) Graph.scope.launch { hub.dropIncognito(id) } }
    }

    // «Прилипание» к низу: пока пользователь не отлистал вверх, держим конец чата на экране.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (moving, forward) ->
            if (moving) stick = !forward else if (!forward) stick = true
        }
    }
    val last = chat.messages.lastOrNull()
    val stamp = chat.messages.size * 1_000_000 + (last?.parts?.size ?: 0) * 10_000 +
        (last?.parts?.lastOrNull()?.let { p -> p.text.length + System.identityHashCode(p.raw) % 97 } ?: 0)
    LaunchedEffect(stamp, busy, retries[id]) {
        if (stick && chat.messages.isNotEmpty()) listState.scrollToItem(chat.messages.size)
    }

    fun imageList(): List<String> = chat.messages.flatMap { m ->
        m.parts.flatMap { p ->
            buildList {
                if (isImagePart(p)) p.raw.str("url")?.let(::add)
                p.raw["state"]?.let { st -> (st as? JsonObject)?.arr("attachments")?.forEach { a ->
                    val o = a as? JsonObject; if (o?.str("mime")?.startsWith("image/") == true) o.str("url")?.let(::add)
                } }
            }
        }
    }

    fun beginEdit(m: ChatMessage) { editing = m; state.attachments.clear(); state.text = userText(m) }
    fun retry(m: ChatMessage) {
        val parent = chat.messages.firstOrNull { it.id == m.info.parentID } ?: return
        scope.launch { runCatching { hub.edit(ctx, id, parent, userText(parent), emptyList()) }.onFailure { hub.toasts.tryEmit(it.message ?: "Retry failed") } }
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        TopBar(
            title = null,
            left = { if (incognito) IconBtn(Ic.Close, { nav.pop() }) else IconBtn(Ic.Menu, onMenu) },
            right = { if (!incognito) IconBtn(Ic.Plus, { nav.root(app.ocmobile.ui.Route.Home) }) },
        )
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(CircleShape).clickable(enabled = !incognito) { menu = true }.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (incognito) { Ico(Ic.Ghost, tint = c.textDim, size = 18.dp); Spacer(Modifier.width(6.dp)) }
                Txt(if (incognito) "Incognito session" else session?.displayTitle.orEmpty(), size = 15.sp, color = c.textDim, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (!incognito) { Spacer(Modifier.width(4.dp)); Ico(Ic.ChevronDown, tint = c.textDim, size = 16.dp) }
            }
            DropdownMenu(menu, { menu = false }, containerColor = c.surface) {
                val items = listOf(
                    "Rename" to { rename = true },
                    (if (id in pinned) "Unpin" else "Pin") to { Graph.prefs.togglePin(id) },
                    "Changes" to { showDiff = true },
                    "Fork" to { scope.launch { runCatching { Graph.api.forkSession(id, chat.directory, null) }.getOrNull()?.let { nav.openChat(it.id) } } },
                    "Compact context" to { scope.launch { runCatching { Graph.api.summarize(id, chat.directory, Graph.prefs.providerId, Graph.prefs.modelId) } } },
                    "Archive" to { scope.launch { runCatching { Graph.api.archiveSession(id, chat.directory) }; nav.pop() } },
                    "Delete" to { confirmDelete = true },
                )
                items.forEach { (label, act) ->
                    DropdownMenuItem(text = { Txt(label, size = 16.sp, color = if (label == "Delete") c.danger else c.text) }, onClick = { menu = false; act() })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (chat.loading && chat.messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(32.dp) }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)) {
                    items(chat.messages, key = { it.id }) { m ->
                        val open = { u: String -> val l = imageList(); viewer = l to l.indexOf(u).coerceAtLeast(0) }
                        Appear(animate = chat.live && m.id !in chat.initialIds, modifier = Modifier.animateItem(fadeOutSpec = null)) {
                            if (m.info.isUser) UserBubble(m, open, onLong = { actionsFor = m })
                            else AssistantBlock(
                                m, m === chat.messages.lastOrNull(), open, onLong = { actionsFor = m },
                                onRetry = if (m === chat.messages.lastOrNull() && !busy && m.info.time.completed != null) ({ retry(m) }) else null,
                            )
                        }
                    }
                    item(key = "tail") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            chat.loadError?.let { Txt(it, size = 14.sp, color = c.danger) }
                            if (busy) app.ocmobile.ui.components.ThinkingIndicator(
                                if (statuses[id] == "retry") app.ocmobile.ui.components.Work.Wait else workKind(chat.messages.lastOrNull()),
                                Modifier.padding(vertical = 4.dp),
                            )
                            retries[id]?.let { RetryCard(it, nav, onStop = { scope.launch { runCatching { Graph.api.abort(id, chat.directory) } } }) }
                        }
                    }
                }
            }
            if (!stick) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).size(40.dp).clip(CircleShape).background(c.surfaceHigh)
                        .border(1.dp, c.border, CircleShape).clickable { stick = true; scope.launch { listState.animateScrollToItem(chat.messages.size) } },
                    contentAlignment = Alignment.Center,
                ) { Ico(Ic.ChevronDown, size = 22.dp) }
            }
        }
        perms.filter { it.sessionID == id }.forEach { PermissionCard(it, chat.directory) }
        questions.filter { it.sessionID == id }.forEach { QuestionCard(it, chat.directory) }
        editing?.let {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.Edit, tint = c.textDim, size = 18.dp); Spacer(Modifier.width(10.dp))
                Txt("Editing message — session rewinds to it", size = 14.sp, color = c.textDim, modifier = Modifier.weight(1f))
                IconBtn(Ic.Close, { editing = null; state.clear() }, size = 40.dp, iconSize = 18.dp)
            }
        }
        Box(Modifier.navigationBarsPadding()) {
            ComposerHost(
                state = state, nav = nav, busy = busy, sessionId = id,
                placeholder = if (editing != null) "Edit your message" else "Reply to OpenCode…",
                dashed = incognito,
                onSend = {
                    val text = state.text.trim()
                    val atts = state.attachments.toList()
                    val e = editing
                    state.clear(); stick = true; editing = null
                    scope.launch {
                        runCatching { if (e != null) hub.edit(ctx, id, e, text, atts) else hub.send(ctx, id, text, atts) }
                            .onFailure { hub.toasts.tryEmit(it.message ?: "Send failed") }
                    }
                },
                onStop = { scope.launch { runCatching { Graph.api.abort(id, chat.directory) } } },
            )
        }
    }

    viewer?.let { (urls, i) -> ImageViewer(urls, i) { viewer = null } }

    actionsFor?.let { m ->
        MessageActionsSheet(
            m, onDismiss = { actionsFor = null },
            onSelect = { selectText = messageText(m) },
            onEdit = if (m.info.isUser) ({ beginEdit(m) }) else null,
            onRetry = if (!m.info.isUser) ({ retry(m) }) else null,
            onFork = { scope.launch { runCatching { Graph.api.forkSession(id, chat.directory, m.id) }.getOrNull()?.let { nav.openChat(it.id) } } },
            onDelete = { scope.launch { runCatching { Graph.api.deleteMessage(id, chat.directory, m.id) }.onSuccess { chat.messages.remove(m) } } },
        )
    }
    selectText?.let { SelectTextSheet(it) { selectText = null } }

    if (rename) {
        var t by remember { mutableStateOf(session?.title.orEmpty()) }
        OcDialog("Rename session", { rename = false }, "Save", {
            rename = false
            scope.launch { runCatching { Graph.api.renameSession(id, chat.directory, t) } }
        }) { OcTextField(t, { t = it }) }
    }
    if (confirmDelete) {
        OcDialog("Delete session?", { confirmDelete = false }, "Delete", {
            confirmDelete = false
            scope.launch { runCatching { Graph.api.deleteSession(id, chat.directory) }; hub.forget(id); hub.refreshSessions(); nav.pop() }
        }, danger = true) { Txt("This permanently removes the conversation and its history.", size = 16.sp, color = c.textDim) }
    }
    if (showDiff) DiffSheet(id, chat.directory) { showDiff = false }
}

/** Мягкое появление нового сообщения: прозрачность и сдвиг снизу. */
@Composable
private fun Appear(animate: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) p.animateTo(1f, tween(320)) }
    Box(modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 36f }) { content() }
}

@Composable
private fun DiffSheet(id: String, directory: String?, onDismiss: () -> Unit) {
    val c = Oc.c
    var files by remember { mutableStateOf<List<JsonObject>?>(null) }
    LaunchedEffect(id) { files = runCatching { Graph.api.diff(id, directory).mapNotNull { it as? JsonObject } }.getOrDefault(emptyList()) }
    OcSheet(onDismiss, "Changes", skipHalf = false) {
        val list = files
        if (list == null) Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Spinner(28.dp) }
        else if (list.isEmpty()) Txt("No file changes in this session yet.", color = c.textDim, modifier = Modifier.padding(24.dp))
        else LazyColumn(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list) { f ->
                var open by remember { mutableStateOf(false) }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().clip(Oc.round(16.dp)).background(c.surface).clickable { open = !open }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(Ic.File, tint = c.textDim, size = 20.dp); Spacer(Modifier.width(10.dp))
                        Txt(f.str("file") ?: "", size = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Txt("+${f.long("additions") ?: 0}", size = 14.sp, color = c.ok); Spacer(Modifier.width(8.dp))
                        Txt("−${f.long("deletions") ?: 0}", size = 14.sp, color = c.danger)
                    }
                    if (open) f.str("patch")?.let { Box(Modifier.padding(top = 8.dp)) { DiffView(it) } }
                }
            }
        }
    }
}

@Composable
private fun RetryCard(r: app.ocmobile.data.RetryInfo, nav: Nav, onStop: () -> Unit) {
    val c = Oc.c
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(Oc.round(22.dp)).background(c.surface).border(1.dp, c.border, Oc.round(22.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Warning, tint = c.warn, size = 22.dp); Spacer(Modifier.width(10.dp))
            Txt(r.title ?: "The provider is not answering", size = 16.sp, weight = androidx.compose.ui.text.font.FontWeight.Medium)
        }
        Txt(r.message, size = 15.sp, color = c.textDim, modifier = Modifier.padding(top = 8.dp), lineHeight = 21.sp)
        if (r.message.contains("Free usage", ignoreCase = true)) {
            Txt(
                "The built-in free models are capped by OpenCode's server. The reset is not always applied, so waiting may not help. Switch to another free model below.",
                size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 8.dp), lineHeight = 20.sp,
            )
        }
        r.nextAt?.let { at ->
            var left by androidx.compose.runtime.remember(at) { androidx.compose.runtime.mutableLongStateOf(at - System.currentTimeMillis()) }
            androidx.compose.runtime.LaunchedEffect(at) { while (left > 0) { kotlinx.coroutines.delay(1000); left = at - System.currentTimeMillis() } }
            if (left > 0) {
                val m = left / 60000
                Txt("Next attempt in " + (if (m >= 60) "${m / 60} h ${m % 60} min" else if (m >= 1) "$m min" else "${left / 1000} s"), size = 14.sp, color = c.text, modifier = Modifier.padding(top = 8.dp))
            }
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.message.contains("Free usage", ignoreCase = true)) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                app.ocmobile.ui.components.SmallButton("Free options", { nav.push(app.ocmobile.ui.Route.Login) }, filled = true)
                if (app.ocmobile.data.FreeModels.copilotAvailable())
                    app.ocmobile.ui.components.SmallButton("Copilot", { scope.launch { runCatching { app.ocmobile.data.FreeModels.enableCopilot() }; Graph.chats.toasts.tryEmit("Switched to GPT-4.1 (Copilot). Send again.") } })
                app.ocmobile.ui.components.SmallButton("No account", { scope.launch { runCatching { app.ocmobile.data.FreeModels.enablePollinations() }; Graph.chats.toasts.tryEmit("Switched to GPT-OSS 20B (Pollinations). Send again.") } })
            } else app.ocmobile.ui.components.SmallButton("Connect provider", { nav.push(app.ocmobile.ui.Route.Providers) }, filled = true)
            r.link?.let { l -> app.ocmobile.ui.components.SmallButton(r.label?.replaceFirstChar { it.uppercase() } ?: "Open", { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(l)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }) }
        }
        app.ocmobile.ui.components.SmallButton("Stop", onStop, Modifier.padding(top = 10.dp).fillMaxWidth(), filled = true, danger = true)
    }
}

/** Что агент делает прямо сейчас — по последней части последнего ответа. */
private fun workKind(m: app.ocmobile.data.ChatMessage?): app.ocmobile.ui.components.Work {
    if (m == null || m.info.isUser) return app.ocmobile.ui.components.Work.Think
    val p = m.parts.lastOrNull() ?: return app.ocmobile.ui.components.Work.Think
    return when (p.type) {
        "reasoning" -> app.ocmobile.ui.components.Work.Think
        "text" -> app.ocmobile.ui.components.Work.Write
        "tool" -> when (p.raw.str("tool")) {
            "read" -> app.ocmobile.ui.components.Work.Read
            "edit", "write", "patch", "multiedit" -> app.ocmobile.ui.components.Work.Edit
            "bash" -> app.ocmobile.ui.components.Work.Run
            "grep", "glob", "list" -> app.ocmobile.ui.components.Work.Search
            "webfetch", "websearch" -> app.ocmobile.ui.components.Work.Web
            "todowrite", "todoread" -> app.ocmobile.ui.components.Work.Plan
            else -> app.ocmobile.ui.components.Work.Other
        }
        else -> app.ocmobile.ui.components.Work.Other
    }
}
