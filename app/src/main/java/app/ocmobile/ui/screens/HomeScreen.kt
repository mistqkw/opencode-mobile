package app.ocmobile.ui.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import app.ocmobile.data.CHAT_DIR
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.ComposerHost
import app.ocmobile.ui.components.Heading
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.Logo
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.rememberComposerState
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.lookBackdrop
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(nav: Nav, onMenu: () -> Unit, code: Boolean = false) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberComposerState()
    var sending by remember { mutableStateOf(false) }
    var voiceHome by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var ghost by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    val activeDevice by Graph.devices.activeId.collectAsState()
    val connected by Graph.events.connected.collectAsState()
    var dirRev by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(activeDevice, connected) {
        if (activeDevice.isNotEmpty() && connected && Graph.prefs.currentDir.isBlank()) {
            runCatching { Graph.api.serverDirectory() }.getOrNull()?.let { Graph.prefs.currentDir = it; dirRev++ }
        }
    }
    val dirName = remember(dirRev, picker) { Graph.prefs.currentDir.trimEnd('/').substringAfterLast('/').ifBlank { "/" } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()

    // анимация отправки: текст из поля ввода «перелетает» на место пузыря в чате, остальное гаснет
    var flightText by remember { mutableStateOf<String?>(null) }
    var composerRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val prog = remember { androidx.compose.animation.core.Animatable(0f) }

    // «Поделиться → Ask OpenCode»: текст и файлы — сразу в поле ввода
    // забирает только экран, который сейчас виден: «Поделиться» может открыть второй экземпляр поверх старого
    val life = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(life) {
        life.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { app.ocmobile.data.ShareInbox.pending.collect { sh ->
            if (sh == null) return@collect
            state.text = if (state.text.isBlank()) sh.text else state.text.trimEnd() + "\n" + sh.text
            sh.uris.forEach { u -> state.attachments.add(app.ocmobile.data.Attachments.describe(ctx, u)) }
            app.ocmobile.data.ShareInbox.pending.value = null
        } }
    }
    Box(Modifier.fillMaxSize().lookBackdrop()) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.graphicsLayer { alpha = 1f - prog.value }) {
            TopBar(
                left = { Row(verticalAlignment = Alignment.CenterVertically) { IconBtn(Ic.Menu, onMenu); app.ocmobile.ui.components.TopModelPill(state) } },
                right = {
                    app.ocmobile.ui.components.UsageRing(null, nav)
                    IconBtn(Ic.Ghost, { ghost = !ghost }, tint = if (ghost) c.text else c.textDim)
                    if (code) Row(
                        Modifier.padding(end = 8.dp).clip(CircleShape).clickable { picker = true }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(Ic.Folder, size = 20.dp); Spacer(Modifier.width(8.dp)); Txt(dirName, size = 16.sp, maxLines = 1)
                    }
                },
            )
        }
        app.ocmobile.ui.components.InterruptedBanner { nav.openChat(it) }
        Column(Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = 1f - prog.value * 1.6f; translationY = -prog.value * 40f }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            androidx.compose.animation.AnimatedContent(ghost, label = "home-mode", transitionSpec = { fadeIn() togetherWith fadeOut() }) { g ->
                androidx.compose.animation.AnimatedVisibility(
                    shown, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)) +
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(240)) { it / 8 },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (g) {
                            Ico(Ic.Ghost, tint = c.textDim, size = 56.dp)
                            Spacer(Modifier.height(22.dp))
                            Txt("Incognito chats stay out of your recents and are deleted when you leave them.", Modifier.padding(horizontal = 40.dp), size = 18.sp, color = c.textDim, align = TextAlign.Center)
                        } else {
                            if (Oc.look.id == "kimi") {
                                app.ocmobile.ui.components.KimiHero(code) { nav.push(app.ocmobile.ui.Route.Providers) }
                            } else if (Oc.look.id in lookHomes) {
                                LookHome(Oc.look.id, code) { state.text = it }
                            } else if (Oc.desktop) {
                                // desktop: тусклая надпись без подписей, под полем — чипы проекта
                                app.ocmobile.ui.components.PixelWordmark(Modifier.padding(horizontal = 20.dp).graphicsLayer { alpha = 0.32f })
                            } else {
                                app.ocmobile.ui.components.PixelWordmark(Modifier.padding(horizontal = 20.dp))
                                Spacer(Modifier.height(28.dp))
                                ModeLine(code)
                                Spacer(Modifier.height(20.dp))
                                TipLine(code)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
        Column(
            Modifier.navigationBarsPadding().onGloballyPositioned { composerRect = it.boundsInRoot() }
                .graphicsLayer { alpha = 1f - prog.value * 1.4f; translationY = prog.value * 90f },
        ) {
            if (Oc.look.id == "kimi" && !ghost && !state.text.startsWith("/")) app.ocmobile.ui.components.KimiQuickChips(nav, code) { state.text = it }
            ComposerHost(
                state = state, nav = nav, busy = sending, dashed = ghost,
                placeholder = if (Oc.look.id == "kimi") "Ask me. Give me a task." else if (code) "Describe a task…" else "Chat with OpenCode…",
                onStop = {},
                onVoice = if (code || ghost) null else ({ voiceHome = true }),
                onSend = {
                    val text = state.text.trim()
                    val atts = state.attachments.toList()
                    state.clear(); sending = true; flightText = text
                    scope.launch {
                        try {
                            val dir = if (code || Graph.devices.isRemote) Graph.prefs.currentDir else CHAT_DIR
                            if (!Graph.devices.isRemote) Graph.paths.host(dir).mkdirs()
                            val flight = launch { prog.animateTo(1f, androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                            val s = Graph.chats.createSession(dir)
                            if (ghost) Graph.chats.markIncognito(s.id)
                            Graph.chats.send(ctx, s.id, text, atts)
                            flight.join()
                            nav.openChatInstant(s.id)
                        } catch (e: Exception) {
                            Graph.chats.toasts.tryEmit(e.message ?: "Could not start the chat")
                            state.text = text; state.attachments.addAll(atts)
                            flightText = null; prog.snapTo(0f)
                        } finally { sending = false }
                    }
                },
            )
            if (Oc.desktop) DesktopChips(code, dirName, onProject = { picker = true }, onMemory = { nav.push(Route.Memory) })
        }
    }
    if (voiceHome) VoiceMode(null) { sid -> voiceHome = false; sid?.let { nav.openChatInstant(it) } }
    flightText?.let { text ->
        // положение и размер пузыря: из поля ввода — в правый верхний угол, где он окажется в чате
        val bubbleMaxW = screenW * 0.88f
        val maxTextPx = with(density) { (bubbleMaxW - 36.dp).roundToPx() }
        val bodyFont = Oc.body
        val layout = remember(text, maxTextPx) {
            measurer.measure(text, androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontFamily = bodyFont), constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxTextPx))
        }
        val toW = if (Oc.terminal) screenW - 32.dp else with(density) { layout.size.width.toDp() } + 36.dp
        val toH = with(density) { layout.size.height.toDp() } + 24.dp
        val e = prog.value
        val fromW = with(density) { composerRect.width.toDp() }
        val fromH = 64.dp
        val fromX = with(density) { composerRect.left.toDp() }
        val fromY = with(density) { composerRect.top.toDp() }
        val toX = if (Oc.terminal) 16.dp else screenW - 16.dp - toW
        val toY = 168.dp
        fun lerpDp(a: androidx.compose.ui.unit.Dp, b: androidx.compose.ui.unit.Dp) = a + (b - a) * e
        Box(
            Modifier.offset(lerpDp(fromX, toX), lerpDp(fromY, toY)).size(lerpDp(fromW, toW), lerpDp(fromH, toH))
                .clip(Oc.round(lerpDp(32.dp, 22.dp)))
                .background(androidx.compose.ui.graphics.lerp(c.surface, c.userBubble, e)),
        ) {
            Text(
                text, color = c.text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = Oc.body,
                modifier = Modifier.padding(horizontal = lerpDp(24.dp, 18.dp), vertical = lerpDp(18.dp, 12.dp)),
            )
        }
    }
    }
    if (picker) ProjectPicker({ picker = false })
}

/** «Build · Модель  Провайдер» — как строка режима в терминальном OpenCode. */
@Composable
private fun ModeLine(code: Boolean) {
    val c = Oc.c
    val prefs = Graph.prefs
    val providers by Graph.catalog.providers.collectAsState()
    val provider = providers.firstOrNull { it.id == prefs.providerId }
    val model = provider?.models?.get(prefs.modelId)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(if (code) prefs.agent.replaceFirstChar { it.uppercase() } else "Chat", size = 15.sp, color = c.link, family = Oc.mono)
            Txt("  ·  ", size = 15.sp, color = c.textDim, family = Oc.mono)
            Txt(model?.name?.ifBlank { null } ?: if (providers.isEmpty()) "…" else prefs.modelId.ifBlank { "no model" }, size = 15.sp, family = Oc.mono, maxLines = 1)
        }
        provider?.let { Txt(it.name, Modifier.padding(top = 4.dp), size = 13.sp, color = c.textDim, family = Oc.mono, maxLines = 1) }
    }
}

private val tips = listOf(
    "Say \"remember that…\" — OpenCode keeps it on a memory shelf.",
    "Hold a message to copy it, edit it or select text.",
    "Tap the ghost for a chat that leaves no trace.",
    "Scheduled runs a prompt every day at a time you pick.",
    "Switch the model right from the composer.",
    "Attach screenshots and photos — OpenCode can read them.",
)

private val codeTips = listOf(
    "Pick the project in the corner before you start.",
    "Code → Add device lets you steer OpenCode on your computer.",
    "Permissions decide what OpenCode may change without asking.",
    "Artifacts collects the files OpenCode wrote for you.",
)

@Composable
private fun TipLine(code: Boolean) {
    val c = Oc.c
    var i by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(6000); i = (i + 1) % tips.size } }
    val tips = if (code) codeTips else tips
    Row(Modifier.padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.width(8.dp).height(8.dp).clip(CircleShape).background(c.warn))
        Spacer(Modifier.width(10.dp))
        Txt("Tip", size = 15.sp, color = c.warn, family = Oc.mono)
        Spacer(Modifier.width(10.dp))
        androidx.compose.animation.AnimatedContent(
            i, label = "tip",
            transitionSpec = { (fadeIn(androidx.compose.animation.core.tween(350)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(350)) { it / 3 }) togetherWith fadeOut(androidx.compose.animation.core.tween(200)) },
        ) { k -> Txt(tips[k], size = 15.sp, color = c.textDim, family = Oc.mono, maxLines = 2, lineHeight = 21.sp) }
    }
}


/** Строка под полем в desktop-версии: проект и Git (или память для обычного чата). */
@Composable
private fun DesktopChips(code: Boolean, dirName: String, onProject: () -> Unit, onMemory: () -> Unit) {
    val c = Oc.c
    val git = remember(dirName) {
        !Graph.devices.isRemote && runCatching { java.io.File(Graph.paths.host(Graph.prefs.currentDir), ".git").exists() }.getOrDefault(false)
    }
    @Composable fun chip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: (() -> Unit)?) {
        Row(
            Modifier.clip(Oc.pill).then(if (filled) Modifier.background(c.surfaceHigh) else Modifier)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(icon, tint = c.textDim, size = 15.dp); Spacer(Modifier.width(6.dp)); Txt(text, size = 13.sp, color = c.textDim, maxLines = 1)
        }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (code) {
            chip(dirName, Ic.Folder, false, onProject)
            Spacer(Modifier.width(8.dp))
            chip(if (git) "Git" else "No Git", Ic.Code, true, null)
        } else {
            chip("Chat", Ic.Chat, false, null)
            Spacer(Modifier.width(8.dp))
            chip(if (Graph.prefs.memoryEnabled) "Memory on" else "Memory off", Ic.Server, true, onMemory)
        }
    }
}
