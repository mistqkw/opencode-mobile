package app.ocmobile.ui.screens

import app.ocmobile.ui.components.tap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import app.ocmobile.ui.components.DiffView
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
import app.ocmobile.ui.theme.lookBackdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.scrollBy
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
    // an error card's "Change model" opens this chat's picker
    val pickReq = app.ocmobile.ui.components.UiRequests.pickModel
    val pickStart = remember(id) { pickReq }
    LaunchedEffect(pickReq) { if (pickReq > pickStart) state.showModel = true }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDiff by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ChatMessage?>(null) }
    var selectText by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    val pinned = Graph.prefs.pinnedSet()
    var stick by remember { mutableStateOf(true) }
    var voice by remember { mutableStateOf(false) }
    var sendingNow by remember { mutableStateOf(false) }
    // what you were typing stays with the chat (across leaving it and closing the app) until you send or erase it
    LaunchedEffect(id) {
        (Graph.chats.drafts.remove(id) ?: Graph.prefs.draft(id).takeIf { it.isNotEmpty() })?.let { state.text = it }
        snapshotFlow { state.text }.collectLatest { t ->
            kotlinx.coroutines.delay(300)
            if (!incognito && editing == null) Graph.prefs.setDraft(id, t)
        }
    }
    DisposableEffect(id) { onDispose { if (!incognito && editing == null) Graph.prefs.setDraft(id, state.text) } }

    LaunchedEffect(id) {
        if (chat.directory == null) chat.directory = hub.session(id)?.directory
        chat.load()
    }

    // An incognito session disappears when the screen closes.
    DisposableEffect(id) {
        hub.viewing = id; hub.seen(id)
        onDispose { if (hub.viewing == id) hub.viewing = null; if (hub.isIncognito(id)) Graph.scope.launch { hub.dropIncognito(id) } }
    }

    // "Sticking" to the bottom: drag the list and it unsticks; reach the end (yourself or with the button) and it sticks again.
    // "At the bottom" is near it, not exactly at it: while a reply grows, the end moves away under
    // the finger, and an exact check left the chat unstuck after you had scrolled all the way down.
    // But only on the way down: a small pull up from the bottom stays where you left it.
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun nearEnd(): Boolean {
        val i = listState.layoutInfo
        val last = i.visibleItemsInfo.lastOrNull() ?: return true
        if (last.index < i.totalItemsCount - 1) return false
        return last.offset + last.size - (i.viewportEndOffset - i.afterContentPadding) < with(density) { 160.dp.toPx() }
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            when (it) {
                is androidx.compose.foundation.interaction.DragInteraction.Start -> stick = false
                is androidx.compose.foundation.interaction.DragInteraction.Stop, is androidx.compose.foundation.interaction.DragInteraction.Cancel ->
                    if (listState.lastScrolledForward && nearEnd()) stick = true
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }.collect { if (!it) stick = true }
    }
    // a fling that ends by the bottom sticks too
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (!it && !stick && listState.lastScrolledForward && nearEnd()) stick = true }
    }
    // While stuck, the bottom is caught up smoothly frame by frame, not with a jump on every incoming chunk.
    LaunchedEffect(listState) {
        var lastViewport = listState.layoutInfo.viewportEndOffset
        while (true) {
            snapshotFlow { stick && listState.canScrollForward }.first { it }
            androidx.compose.runtime.withFrameNanos { }
            val info = listState.layoutInfo
            // the keyboard is moving and the list got shorter: follow it in the same frame, not behind it
            val viewport = info.viewportEndOffset
            val resized = lastViewport != 0 && viewport != lastViewport
            lastViewport = viewport
            val seen = info.visibleItemsInfo.lastOrNull() ?: continue
            // the end is far away (opened an old chat, tapped "down"): go straight there
            if (seen.index < info.totalItemsCount - 2) { listState.scrollToItem(info.totalItemsCount - 1); continue }
            // the last chunk is still past the screen edge: catch up smoothly, not with a jump
            val gap = if (seen.index < info.totalItemsCount - 1) info.mainAxisItemSpacing + 1 else 0
            val rest = (seen.offset + seen.size + gap - (info.viewportEndOffset - info.afterContentPadding)).toFloat()
            if (rest > 0.5f) listState.scrollBy(if (resized) rest else maxOf(2f, rest * 0.4f))
        }
    }
    // first time the chat is shown: straight to the end, without scrolling through the whole history
    LaunchedEffect(chat.messages.isNotEmpty()) {
        if (chat.messages.isNotEmpty()) listState.scrollToItem(chat.messages.size)
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

    // links to environment files (/root/…) open as files, the rest as usual
    val sysUri = androidx.compose.ui.platform.LocalUriHandler.current
    val uris = remember(sysUri, id) {
        object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) {
                when {
                    uri.startsWith("/") && Graph.devices.isRemote -> app.ocmobile.ui.components.FileView.open(app.ocmobile.ui.components.ViewSrc.Remote(uri, hub.session(id)?.directory))
                    uri.startsWith("/") -> app.ocmobile.ui.components.OutFiles.host(uri)?.let { app.ocmobile.ui.components.FileView.open(app.ocmobile.ui.components.ViewSrc.Local(it)) } ?: hub.toasts.tryEmit("File not found: $uri")
                    // web pages open in the app's own browser, like in Claude
                    uri.startsWith("http://") || uri.startsWith("https://") -> nav.push(app.ocmobile.ui.Route.Preview(uri, null))
                    else -> runCatching { sysUri.openUri(uri) }
                }
            }
        }
    }
    val selBar = remember { app.ocmobile.ui.components.ChatTextToolbar() }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides uris) {
    // the messages run under the top bar and the input, which float over them half see-through:
    // no edge the text stops at, and the padding keeps the first and last lines clear of both
    val dens = androidx.compose.ui.platform.LocalDensity.current
    var topPad by remember { mutableStateOf(0.dp) }
    var bottomPad by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize().lookBackdrop().imePadding()) {
        Box(Modifier.fillMaxSize()) {
            if (chat.loading && chat.messages.isEmpty()) {
                // the shape of a conversation while it loads: a short question on the right, a reply under it
                Column(Modifier.fillMaxSize().padding(top = topPad).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Row { Spacer(Modifier.weight(0.4f)); app.ocmobile.ui.components.Skeleton(Modifier.weight(0.6f), lines = 1, height = 40.dp) }
                    app.ocmobile.ui.components.Skeleton(lines = 4)
                    Row { Spacer(Modifier.weight(0.5f)); app.ocmobile.ui.components.Skeleton(Modifier.weight(0.5f), lines = 1, height = 40.dp) }
                    app.ocmobile.ui.components.Skeleton(lines = 3)
                }
            } else {
                // one reply's steps arrive as separate messages: merge them into one turn. Rebuilt only when
                // messages come or go, not on every chunk of text (the text is read inside each item)
                val turns by remember(chat) {
                    androidx.compose.runtime.derivedStateOf {
                        ArrayList<List<ChatMessage>>().apply {
                            chat.messages.forEach { m ->
                                val prev = lastOrNull()
                                if (prev != null && !m.info.isUser && !prev.last().info.isUser) set(lastIndex, prev + m) else add(listOf(m))
                            }
                        }
                    }
                }
                // the messages read at Kimi's size (measured off their chat: ~17.5 sp text, 29 dp lines),
                // a step above the rest of the app; the app's text size setting still scales it
                val dens = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalTextToolbar provides selBar,
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, dens.fontScale * CHAT_TEXT),
                    app.ocmobile.ui.components.LocalChatText provides true,
                ) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = topPad + 8.dp, bottom = bottomPad + 12.dp)) {
                    items(turns, key = { it.first().id }) { turn ->
                        val m = turn.last()
                        val head = turn.first()
                        val open = { u: String -> val l = imageList(); viewer = l to l.indexOf(u).coerceAtLeast(0) }
                        // our own bubble already appeared as a draft (zzzz-local-…); the real one from the server replaces it without appearing twice
                        Appear(animate = chat.live && head.id !in chat.initialIds && !(m.info.isUser && !m.id.startsWith("zzzz-local")), modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) {
                            if (m.info.isUser) UserBubble(m, open, onSelect = { selectText = userText(m) }, onEdit = { beginEdit(m) })
                            else AssistantBlock(
                                m, m === chat.messages.lastOrNull(), open, onLong = { actionsFor = m },
                                parts = if (turn.size == 1) m.parts else turn.flatMap { it.parts },
                                onRetry = if (m === chat.messages.lastOrNull() && !busy && m.info.time.completed != null) ({ retry(m) }) else null,
                            )
                        }
                    }
                    item(key = "tail") {
                        // "Thinking…" fades in and out: without it the chat tail jumped in height
                        Column(Modifier.fillMaxWidth().animateContentSize(tween(140)).padding(horizontal = 20.dp)) {
                            chat.loadError?.let { Txt(it, size = 14.sp, color = c.danger) }
                            // right after a reply ends the mascot reacts for a moment, by its eyes
                            val outcome = app.ocmobile.ui.components.MascotSignals.outcome
                            var reacting by remember { mutableStateOf(false) }
                            LaunchedEffect(outcome) { if (outcome != null && System.currentTimeMillis() - outcome.at < 1000) { reacting = true; kotlinx.coroutines.delay(1800); reacting = false } }
                            if (!busy && reacting && outcome != null && outcome.sessionId == id && Oc.look.id == "cloud") Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                app.ocmobile.ui.components.CloudOutcomeGlyph(outcome.ok, 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Txt(if (outcome.ok) "Done" else "That didn't work", size = 15.sp, color = c.textDim)
                            }
                            if (busy) app.ocmobile.ui.components.ThinkingIndicator(
                                if (statuses[id] == "retry") app.ocmobile.ui.components.Work.Wait else workKind(chat.messages.lastOrNull()),
                                Modifier.padding(vertical = 4.dp),
                            )
                            // while the agent works you can go play with the mascot
                            if (busy && Oc.look.id == "cloud") Txt(
                                "Play with cl0udly while you wait ›", size = 13.sp, color = c.textDim,
                                modifier = Modifier.clip(Oc.round(8.dp)).clickable { nav.push(app.ocmobile.ui.Route.Mascot) }.padding(vertical = 4.dp),
                            )
                            retries[id]?.let { RetryCard(it, nav, onStop = { scope.launch { runCatching { Graph.api.abort(id, chat.directory) } } }) }
                        }
                    }
                }
                }
            }
            // "down": by the right edge so it doesn't cover the text, a 48 dp target around a 40 dp circle
            androidx.compose.animation.AnimatedVisibility(
                !stick, Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = bottomPad + 8.dp),
                enter = androidx.compose.animation.fadeIn(tween(140)) + androidx.compose.animation.scaleIn(tween(140), initialScale = 0.8f),
                exit = androidx.compose.animation.fadeOut(tween(140)) + androidx.compose.animation.scaleOut(tween(140), targetScale = 0.8f),
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Scroll down") { stick = true; scope.launch { listState.animateScrollToItem(chat.messages.size) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceHigh).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
                        Ico(Ic.ChevronDown, size = 22.dp, contentDescription = "Scroll down")
                    }
                }
            }
        }
        // the top darkens the messages that go under it, fading out a little below the bar instead of a hard edge
        val shade = c.bg
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topPad = with(dens) { it.height.toDp() } }
                .drawBehind {
                    val h = size.height + 36.dp.toPx()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to shade.copy(alpha = 0.92f), 0.55f to shade.copy(alpha = 0.75f), 1f to shade.copy(alpha = 0f), endY = h,
                        ),
                        size = androidx.compose.ui.geometry.Size(size.width, h),
                    )
                },
        ) {
            TopBar(
                title = null,
                left = { Row(verticalAlignment = Alignment.CenterVertically) { if (incognito) IconBtn(Ic.Close, { nav.pop() }) else app.ocmobile.ui.components.MenuBtn(onMenu); app.ocmobile.ui.components.TopModelPill(state) } },
                right = {
                    app.ocmobile.ui.components.UsageRing(chat, nav)
                    if (!incognito) {
                        // no title over the chat: rename, pin, memory, changes, fork, compact, archive and delete live here
                        Box {
                            // the same round button as the usage ring next to it, so the two sit as a pair
                            if (Oc.look.id == "kimi" || Oc.look.id == "cloud") {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.7f)).border(1.dp, c.border, CircleShape).tap { menu = true },
                                    contentAlignment = Alignment.Center,
                                ) { Ico(Ic.More, size = 22.dp) }
                            } else IconBtn(Ic.More, { menu = true })
                            if (menu) ChatMenu(
                                onDismiss = { menu = false },
                                items = listOfNotNull(
                                    MenuItem("Rename", Ic.Edit) { rename = true },
                                    MenuItem(if (id in pinned) "Unpin" else "Pin", Ic.Pin) { Graph.prefs.togglePin(id) },
                                    if (!Graph.prefs.memoryEnabled) null else Graph.prefs.memoryOn(id).let { on ->
                                        MenuItem(if (on) "Turn off memory here" else "Turn on memory here", Ic.Sparkle) {
                                            hub.setChatMemory(id, !on)
                                            hub.toasts.tryEmit(if (!on) "Memory is on in this chat" else "This chat won't read or save memory")
                                        }
                                    },
                                    MenuItem("Timeline", Ic.Clock) { showTimeline = true },
                                    MenuItem("Changes", Ic.Diff) { showDiff = true },
                                    MenuItem("Fork", Ic.Branch) { scope.launch { runCatching { Graph.api.forkSession(id, chat.directory, null) }.getOrNull()?.let { nav.openChat(it.id) } } },
                                    MenuItem("Compact context", Ic.Scroll) { scope.launch { runCatching { Graph.api.summarize(id, chat.directory, Graph.prefs.providerId, Graph.prefs.modelId) } } },
                                    MenuItem("Archive", Ic.Archive) { scope.launch { runCatching { Graph.api.archiveSession(id, chat.directory) }; nav.pop() } },
                                    MenuItem("Delete", Ic.Trash, danger = true) { confirmDelete = true },
                                ),
                            )
                        }
                    }
                },
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomPad = with(dens) { it.height.toDp() } }) {
            // the chat's own requests first, then its helpers' (they would wait unseen otherwise)
            val allS by hub.allSessions.collectAsState()
            val myPerm = perms.firstOrNull { it.sessionID == id } ?: perms.firstOrNull { p -> allS[p.sessionID]?.parentID == id }
            val myQuestion = questions.firstOrNull { it.sessionID == id }
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
            app.ocmobile.ui.components.InterruptedCard(id)
            if (myQuestion == null && myPerm == null) app.ocmobile.ui.components.PreviewChips(chat, id, nav)
            if (myQuestion == null && myPerm == null) app.ocmobile.ui.components.HelperClouds(id)
            if (myQuestion != null || myPerm != null) Box(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                androidx.compose.animation.AnimatedContent(myQuestion?.id ?: myPerm?.id, label = "ask", transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(160)) { it / 4 }) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90))
                }) { _ ->
                    if (myQuestion != null) QuestionPanel(myQuestion, chat.directory)
                    else if (myPerm != null && myPerm.permission == "task") app.ocmobile.ui.components.TaskPermissionPanel(myPerm, chat.directory)
                    else if (myPerm != null) Column {
                        if (myPerm.sessionID != id) Txt("${app.ocmobile.data.Team.display(allS[myPerm.sessionID]?.agent)} asks", Modifier.padding(start = 24.dp, bottom = 6.dp), size = 13.sp, color = Oc.c.accent)
                        PermissionPanel(myPerm, chat.directory)
                    }
                }
            } else Box(Modifier.navigationBarsPadding()) {
                ComposerHost(
                    state = state, nav = nav, busy = busy || sendingNow, sessionId = id,
                    placeholder = if (sendingNow) "Sending…" else if (editing != null) "Edit your message" else "Reply to ${if (app.ocmobile.data.Persona.on) "cl0udly" else "OpenCode"}…",
                    dashed = incognito, floating = true,
                    onSend = {
                        val text = state.text.trim()
                        val atts = state.attachments.toList()
                        val e = editing
                        state.clear(); stick = true; editing = null; sendingNow = true
                        scope.launch {
                            runCatching { if (e != null) hub.edit(ctx, id, e, text, atts) else hub.send(ctx, id, text, atts) }
                                .onFailure { hub.toasts.tryEmit(it.message ?: "Send failed") }
                            sendingNow = false
                        }
                    },
                    onStop = { scope.launch { runCatching { Graph.api.abort(id, chat.directory) } } },
                    onVoice = if (incognito) null else ({ voice = true }),
                )
            }
        }
    }

    app.ocmobile.ui.components.SelectionBarHost(
        selBar,
        onReply = { t -> state.text = t.trim().lines().joinToString("\n") { "> $it" } + "\n\n" + state.text },
        onRead = { t -> Speaker.toggle(ctx, t) },
    )
    }

    viewer?.let { (urls, i) -> ImageViewer(urls, i) { viewer = null } }
    if (voice) VoiceMode(id) { voice = false }

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
    // no "are you sure?": leave the chat, it disappears, and "Undo" at the bottom brings it back
    if (confirmDelete) LaunchedEffect(Unit) { confirmDelete = false; nav.pop(); hub.deleteWithUndo(listOf(id)) }
    if (showDiff) DiffSheet(id, chat.directory) { showDiff = false }
    if (showTimeline) TimelineSheet(chat) { showTimeline = false }
}

/** Soft appearance of a new message: opacity and a slide from below. */
@Composable
private fun Appear(animate: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // with animations off a new message is simply there
    val motion = app.ocmobile.ui.theme.rememberMotionEnabled()
    val p = remember { Animatable(if (animate && motion) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate && motion) p.animateTo(1f, tween(130, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
    Box(modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 36f }) { content() }
}

@Composable
private fun DiffSheet(id: String, directory: String?, onDismiss: () -> Unit) {
    val c = Oc.c
    var files by remember { mutableStateOf<List<JsonObject>?>(null) }
    LaunchedEffect(id) { files = runCatching { Graph.api.diff(id, directory).mapNotNull { it as? JsonObject } }.getOrDefault(emptyList()) }
    val scope = rememberCoroutineScope()
    val kept = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }   // file → keep | undone
    var confirm by remember { mutableStateOf<JsonObject?>(null) }
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
                    // review: keep or revert (only for the server on the phone, the files are here)
                    val name = f.str("file") ?: ""
                    when (kept[name]) {
                        "keep" -> Txt("Kept", size = 13.sp, color = c.ok, modifier = Modifier.padding(top = 6.dp, start = 6.dp))
                        "undone" -> Txt("Undone — the file is back as it was", size = 13.sp, color = c.textDim, modifier = Modifier.padding(top = 6.dp, start = 6.dp))
                        else -> if (!Graph.devices.isRemote) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            app.ocmobile.ui.components.SmallButton("Keep", { kept[name] = "keep" }, filled = true)
                            app.ocmobile.ui.components.SmallButton("Undo", { confirm = f })
                        }
                    }
                }
            }
        }
    }
    confirm?.let { f ->
        val name = f.str("file") ?: ""
        OcDialog("Undo changes to ${name.substringAfterLast('/')}?", { confirm = null }, "Undo", {
            confirm = null
            scope.launch {
                app.ocmobile.data.Review.undo(directory, name, f.str("before"), f.str("patch"))
                    .onSuccess { kept[name] = "undone" }
                    .onFailure { Graph.chats.toasts.tryEmit(it.message ?: "Couldn't undo") }
            }
        }, danger = true) {
            Txt("The file goes back to how it was before the agent changed it in this chat. The agent won't know unless you tell it.", size = 15.sp, color = c.textDim, lineHeight = 21.sp)
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
                app.ocmobile.ui.components.SmallButton("No account", { scope.launch { runCatching { app.ocmobile.data.FreeModels.enablePollinations() }; Graph.chats.toasts.tryEmit("Switched to GPT-OSS 20B (Pollinations). Send again.") } })
            } else app.ocmobile.ui.components.SmallButton("Connect provider", { nav.push(app.ocmobile.ui.Route.Providers) }, filled = true)
            r.link?.let { l -> app.ocmobile.ui.components.SmallButton(r.label?.replaceFirstChar { it.uppercase() } ?: "Open", { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(l)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }) }
        }
        app.ocmobile.ui.components.SmallButton("Stop", onStop, Modifier.padding(top = 10.dp).fillMaxWidth(), filled = true, danger = true)
    }
}

/** What the agent is doing right now, from the last part of the last reply. */
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

/** How much bigger the chat's text is than the rest of the app: 18 sp × 0.88 (the default text size) × 1.1 ≈ Kimi's 17.4 sp. */
private const val CHAT_TEXT = 1.1f

private class MenuItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val danger: Boolean = false, val act: () -> Unit)

/** The chat's menu: a card floating under the three dots, the chat showing just a little through it. */
@Composable
private fun ChatMenu(onDismiss: () -> Unit, items: List<MenuItem>) {
    val c = Oc.c
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    val shape = RoundedCornerShape(22.dp)
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopEnd,
        offset = with(dens) { androidx.compose.ui.unit.IntOffset(0, 56.dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            shown,
            enter = androidx.compose.animation.fadeIn(tween(110)) +
                androidx.compose.animation.scaleIn(tween(130), initialScale = 0.92f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
        ) {
            Column(
                Modifier.padding(end = 4.dp).width(260.dp).clip(shape).background(c.surface.copy(alpha = 0.94f))
                    .border(1.dp, c.border, shape).padding(vertical = 8.dp),
            ) {
                items.forEach { item ->
                    val col = if (item.danger) c.danger else c.text
                    Row(
                        Modifier.fillMaxWidth().clickable { onDismiss(); item.act() }.padding(horizontal = 22.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(item.icon, tint = col, size = 22.dp)
                        Spacer(Modifier.width(20.dp))
                        Txt(item.label, size = 17.sp, color = col, maxLines = 1)
                    }
                }
            }
        }
    }
}
