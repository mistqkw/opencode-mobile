package app.ocmobile.ui.components

import app.ocmobile.ui.theme.kimiLike
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ocmobile.Graph
import app.ocmobile.api.ModelInfo
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch

/*
 * The Kimi look follows how the Kimi app is built: outlined round buttons in the top bar,
 * the model drops down as a card from the top, "+" opens a sheet with large tiles and section rows,
 * horizontal quick-action chips above the input.
 */

val isKimi: Boolean @Composable get() = Oc.look.id.kimiLike

/** Inside the top bar: buttons are drawn as outlined circles (only in Kimi). */
val LocalInTopBar = compositionLocalOf { false }

/** Kimi round button: a dark circle with a thin outline. */
@Composable
fun KimiCircleBtn(icon: ImageVector, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 48.dp, tint: Color = Oc.c.text, contentDescription: String? = app.ocmobile.ui.theme.iconLabel(icon)) {
    val c = Oc.c
    Box(
        Modifier.size(size).clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.7f)).border(1.dp, c.border, CircleShape).tap(onClick = onClick)
            .semantics { role = androidx.compose.ui.semantics.Role.Button; if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { TopBarIcon(icon, tint, 22.dp) }
}

/** Model pill in the header: "OpenCode" and the model in grey, like "Kimi K2.8 Preview". */
@Composable
fun KimiModelPill(onClick: () -> Unit) {
    val c = Oc.c
    val prefs = Graph.prefs
    val providers by Graph.catalog.providers.collectAsState()
    val name = providers.firstOrNull { it.id == prefs.providerId }?.models?.get(prefs.modelId)?.name?.takeIf { it.isNotBlank() } ?: prefs.modelId.ifBlank { "Model" }
    Row(
        Modifier.padding(start = 8.dp).height(48.dp).widthIn(max = 196.dp).clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.7f))
            .border(1.dp, c.border, CircleShape).tap(scale = 0.97f, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("OpenCode", size = 17.sp, maxLines = 1)   // the model runs through OpenCode, so that's what we say, in any look
        Spacer(Modifier.width(7.dp))
        Txt(name.removeSuffix(" Free"), Modifier.weight(1f, fill = false), size = 17.sp, color = c.textDim, maxLines = 1)
    }
}

/**
 * A light blue ball mascot with eyes. Alive: a tap makes it hop and smile,
 * tapping often makes it angry and shake, then it cools down; a long press puts it to sleep until the next touch.
 */
@Composable
fun KimiBall(modifier: Modifier, thinking: Boolean = false) {
    val c = Oc.c
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    var mood by remember { mutableStateOf(Mood.Idle) }
    val taps = remember { ArrayDeque<Long>() }
    var calmJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val squash = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    fun calm(ms: Long) { calmJob?.cancel(); calmJob = scope.launch { kotlinx.coroutines.delay(ms); mood = Mood.Idle } }

    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "ball")
    val blink by t.animateFloat(1f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.keyframes {
        durationMillis = 4200; 1f at 0; 1f at 3900; 0.15f at 4020; 1f at 4160
    }), label = "blink")
    val look by t.animateFloat(-1f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(if (thinking) 1100 else 2800), androidx.compose.animation.core.RepeatMode.Reverse), label = "look")
    val breath by t.animateFloat(1f, 1.035f, androidx.compose.animation.core.infiniteRepeatable(tween(1600), androidx.compose.animation.core.RepeatMode.Reverse), label = "breath")

    Canvas(
        modifier
            .graphicsLayer {
                val b = if (mood == Mood.Sleep) breath else 1f
                scaleX = (2f - squash.value) * b; scaleY = squash.value * b
                translationX = shake.value * size.width
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { haptic(); calmJob?.cancel(); mood = Mood.Sleep },
                    onTap = {
                        val now = System.currentTimeMillis()
                        taps.addLast(now); while (taps.isNotEmpty() && now - taps.first() > 1800) taps.removeFirst()
                        if (Graph.prefs.haptics) haptic()
                        if (taps.size >= 5 || mood == Mood.Angry) {
                            mood = Mood.Angry; calm(2600)
                            scope.launch {
                                shake.snapTo(0f)
                                listOf(0.06f, -0.06f, 0.045f, -0.045f, 0.02f, 0f).forEach { shake.animateTo(it, tween(45)) }
                            }
                        } else {
                            mood = Mood.Happy; calm(1200)
                        }
                        scope.launch {
                            squash.snapTo(0.8f)
                            squash.animateTo(1f, androidx.compose.animation.core.spring(0.35f, 500f))
                        }
                    },
                )
            },
    ) {
        val r = size.minDimension / 2
        val cx = size.width / 2; val cy = size.height / 2
        drawCircle(c.border, r, Offset(cx, cy))
        drawCircle(c.accent, r * 0.9f, Offset(cx, cy))
        val ew = r * 0.17f
        val ey = cy - r * (if (thinking) 0.1f else 0.02f)
        listOf(-0.22f, 0.22f).forEach { fx ->
            val ex = cx + r * fx + (if (mood == Mood.Idle) look * r * 0.06f else 0f)
            when (mood) {
                // joy: arched "^ ^" eyes
                Mood.Happy -> drawArc(
                    Color.White, 205f, 130f, false, Offset(ex - ew * 0.8f, ey - ew * 0.3f), androidx.compose.ui.geometry.Size(ew * 1.6f, ew * 1.6f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(ew * 0.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
                // anger: the top of the eye slants toward the nose
                Mood.Angry -> {
                    val eh = r * 0.3f
                    val inner = if (fx < 0) 1 else -1
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(ex - ew / 2 * inner, ey - eh / 2)
                        lineTo(ex + ew / 2 * inner, ey - eh / 2 + eh * 0.38f)
                        lineTo(ex + ew / 2 * inner, ey + eh / 2 - ew / 2)
                        quadraticTo(ex + ew / 2 * inner, ey + eh / 2, ex, ey + eh / 2)
                        quadraticTo(ex - ew / 2 * inner, ey + eh / 2, ex - ew / 2 * inner, ey + eh / 2 - ew / 2)
                        close()
                    }
                    drawPath(p, Color.White)
                }
                // sleep: closed eyes as dashes
                Mood.Sleep -> drawLine(Color.White, Offset(ex - ew * 0.8f, ey + ew), Offset(ex + ew * 0.8f, ey + ew), ew * 0.6f, androidx.compose.ui.graphics.StrokeCap.Round)
                Mood.Idle -> {
                    val eh = r * 0.34f * blink
                    drawRoundRect(Color.White, Offset(ex - ew / 2, ey - eh / 2), androidx.compose.ui.geometry.Size(ew, eh), androidx.compose.ui.geometry.CornerRadius(ew / 2))
                }
            }
        }
    }
}

private enum class Mood { Idle, Happy, Angry, Sleep }

// ---------- model picker: a card drops from the top ----------

@Composable
fun KimiModelPanel(onDismiss: () -> Unit, onMore: () -> Unit, onTeam: (() -> Unit)? = null) {
    val c = Oc.c
    val prefs = Graph.prefs
    val providers by Graph.catalog.providers.collectAsState()
    var page by remember { mutableStateOf("main") }
    val scope = rememberCoroutineScope()
    val agents by Graph.catalog.agents.collectAsState()
    LaunchedEffect(Unit) { if (agents.isEmpty()) runCatching { Graph.catalog.refresh() } }
    val current = Graph.catalog.model(prefs.providerId, prefs.modelId)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    val featured = remember(providers, prefs.recentModels, prefs.providerId, prefs.modelId) {
        val out = LinkedHashMap<String, Pair<String, ModelInfo>>()
        fun add(pid: String, m: ModelInfo?) { if (m != null) out.putIfAbsent("$pid/${m.id}", pid to m) }
        // the order of the recent list, not "selected first": the picked model stays where it was
        prefs.recentModels.split(',').filter { it.contains('/') }.forEach { k -> add(k.substringBefore('/'), Graph.catalog.model(k.substringBefore('/'), k.substringAfter('/'))) }
        add(prefs.providerId, Graph.catalog.model(prefs.providerId, prefs.modelId))
        providers.firstOrNull { it.id == prefs.providerId }?.models?.values?.sortedByDescending { it.release_date ?: "" }?.forEach { add(prefs.providerId, it) }
        out.values.take(3).toList()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
            Column(
                Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                    .graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * -24.dp.toPx(); scaleY = 0.96f + 0.04f * enter.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f) }
                    .clip(RoundedCornerShape(32.dp)).background(c.surfaceHigh).border(1.dp, c.border, RoundedCornerShape(32.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .animateContentSize(tween(240, easing = FastOutSlowInEasing))
                    .padding(vertical = 10.dp),
            ) {
                // panel pages switch with a slide: deeper goes right to left, back goes left to right
                androidx.compose.animation.AnimatedContent(page, label = "panel", transitionSpec = {
                    val fwd = targetState != "main"
                    val dir = if (fwd) 1 else -1
                    (androidx.compose.animation.slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 3 * dir } + androidx.compose.animation.fadeIn(tween(200))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 3 * dir } + androidx.compose.animation.fadeOut(tween(140)))
                }) { pg ->
                    Column {
                        if (pg != "main") KimiBackRow(when (pg) { "effort" -> "Reasoning level"; "agent" -> "Mode"; "team" -> "Sub-agents"; else -> "More models" }) { page = "main" }
                        when (pg) {
                            "team" -> Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) { app.ocmobile.ui.screens.TeamPanel(onAll = onTeam, onCreated = onDismiss) }
                            "effort" -> Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                                KimiPanelRow("Default", "Let the model decide", prefs.variant.isBlank()) { prefs.variant = ""; page = "main" }
                                current?.variantNames.orEmpty().forEach { n ->
                                    KimiPanelRow(n.replaceFirstChar { it.uppercase() }, if (n == "high" || n == "max") "Thinks longer, answers slower" else null, prefs.variant == n) { prefs.variant = n; page = "main" }
                                }
                            }
                            "agent" -> Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                                // only primary agents; until the list comes from the server, Build and Plan
                                val list = agents.filter { it.mode != "subagent" && !it.hidden }.map { it.name to it.description }
                                    .ifEmpty { listOf("build" to "Makes changes and runs commands", "plan" to "Reads and plans, doesn't change files") }
                                list.forEach { (name, desc) ->
                                    KimiPanelRow(name.replaceFirstChar { it.uppercase() }, desc, prefs.agent == name) {
                                        prefs.agent = name
                                        scope.launch { runCatching { Graph.api.patchConfig(kotlinx.serialization.json.buildJsonObject { put("default_agent", kotlinx.serialization.json.JsonPrimitive(name)) }) } }
                                        page = "main"
                                    }
                                }
                            }
                            "all" -> {
                                var query by remember { mutableStateOf("") }
                                PickSearch(query, { query = it }, "Search models")
                                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 480.dp)) {
                                    providers.forEach { p ->
                                        val ms = p.models.values.filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) }
                                            .sortedWith(compareByDescending<ModelInfo> { it.release_date ?: "" }.thenBy { it.name })
                                        if (ms.isNotEmpty()) {
                                            item(key = "h-${p.id}") { Txt(p.name.ifBlank { p.id }, Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp), size = 14.sp, color = c.textDim) }
                                            items(ms.size, key = { p.id + "/" + ms[it].id }) { k ->
                                                val m = ms[k]
                                                KimiPanelRow(m.name.ifBlank { m.id }, describe(m), prefs.providerId == p.id && prefs.modelId == m.id) { pickModel(p.id, m); onDismiss() }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                                if (featured.isEmpty()) Txt("No connected providers yet.", Modifier.padding(24.dp), color = c.textDim)
                                featured.forEach { (pid, m) ->
                                    val sel = prefs.providerId == pid && prefs.modelId == m.id
                                    KimiPanelRow(m.name.ifBlank { m.id }, describe(m), sel) {
                                        pickModel(pid, m)
                                        onDismiss()
                                    }
                                }
                                KimiDivider()
                                KimiNavRow("More models", null) { page = "all" }
                                KimiDivider()
                                KimiNavRow("Sub-agents", teamLabel()) { page = "team" }; KimiDivider()
                                if (current != null && current.variantNames.isNotEmpty())
                                    KimiNavRow("Reasoning level", prefs.variant.ifBlank { "Standard" }.replaceFirstChar { it.uppercase() }) { page = "effort" }
                                KimiNavRow("Mode", prefs.agent.replaceFirstChar { it.uppercase() }) { page = "agent" }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Back" row on a nested panel page. */
@Composable
private fun KimiBackRow(title: String, onBack: () -> Unit) {
    val c = Oc.c
    Row(Modifier.fillMaxWidth().tap(scale = 1f, onClick = onBack).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(Ic.Back, tint = c.text, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        Txt(title, size = 19.sp, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun describe(m: ModelInfo): String = buildList {
    if (m.id.contains("free") || m.name.contains("Free", true)) add("Free")
    m.limit?.context?.takeIf { it > 0 }?.let { add(if (it >= 1_000_000) "${it / 1_000_000}M context" else "${it / 1000}k context") }
    if (m.reasoning) add("thinks before answering")
    if (m.supportsImages) add("sees images")
}.joinToString(" · ").replaceFirstChar { it.uppercase() }

@Composable
private fun KimiPanelRow(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    val c = Oc.c
    Row(Modifier.fillMaxWidth().tap(scale = 1f, onClick = onClick).padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 19.sp, weight = FontWeight.Medium, maxLines = 1)
            if (!sub.isNullOrBlank()) Txt(sub, Modifier.padding(top = 4.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp, maxLines = 2)
        }
        if (selected) { Spacer(Modifier.width(12.dp)); Moon(Modifier.size(26.dp)) }
    }
}

/** Selected mark: a small "moon", the lit half. */
@Composable
private fun Moon(modifier: Modifier) {
    val c = Oc.c
    // spins very slowly: one turn every 12 seconds
    val spin by androidx.compose.animation.core.rememberInfiniteTransition(label = "moon").animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(12000, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    Canvas(modifier.graphicsLayer { rotationZ = spin }) {
        val r = size.minDimension / 2
        drawCircle(Color(0xFF3A3A3A), r)
        drawArc(Color(0xFFD8D8D8), -90f, 180f, true)
        drawOval(Color(0xFFD8D8D8), Offset(size.width / 2 - r * 0.35f, 0f), androidx.compose.ui.geometry.Size(r * 0.7f, size.height))
    }
}

@Composable
private fun KimiNavRow(title: String, value: String?, onClick: () -> Unit) {
    val c = Oc.c
    Row(Modifier.fillMaxWidth().tap(scale = 1f, onClick = onClick).padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, Modifier.weight(1f), size = 19.sp, weight = FontWeight.Medium, maxLines = 1)
        if (value != null) { Txt(value, size = 18.sp, color = c.textDim, maxLines = 1); Spacer(Modifier.width(10.dp)) }
        Ico(Ic.ChevronRight, tint = c.textDim, size = 22.dp)
    }
}

@Composable
private fun KimiDivider() = Box(Modifier.padding(horizontal = 28.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Oc.c.text.copy(alpha = 0.1f)))

@Composable
fun KimiHero(code: Boolean, typing: Boolean = false, onMore: () -> Unit) {
    val c = Oc.c
    val name = Graph.prefs.userName.trim().substringBefore(' ')
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (Oc.look.id == "cloud") { if (Graph.prefs.mascotOn) PixelCloud(Modifier.size(width = 124.dp, height = 84.dp), watching = typing) }
        else KimiBall(Modifier.size(88.dp))
        Spacer(Modifier.height(22.dp))
        // cl0udly: a new greeting every time
        val cloud = Oc.look.id == "cloud"
        val hello = remember(code, cloud) { if (cloud) Greetings.pick(name, code) else null }
        Txt(
            hello ?: ((if (name.isBlank()) "Hi." else "Hi, $name.") + "\n" + (if (code) "What are we building today?" else "What shall we do today?")),
            size = 25.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, lineHeight = 34.sp,
        )
        Spacer(Modifier.height(18.dp))
        if (Oc.look.id != "cloud") Box(Modifier.clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.7f)).tap(scale = 0.96f, onClick = onMore).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Txt("More models", size = 16.sp, color = c.accent, weight = FontWeight.Medium)
        }
    }
}

/**
 * "Add to session" in the cl0udly and Kimi looks: the same floating panel as the model picker, only
 * rising from the input instead of dropping from the top bar, with the same pages sliding in and out.
 */
@Composable
fun KimiAddPanel(
    onDismiss: () -> Unit,
    sessionId: String?,
    nav: Nav,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onVoice: (() -> Unit)?,
) {
    val c = Oc.c
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("main") }
    val agents by Graph.catalog.agents.collectAsState()
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    var memHere by remember { mutableStateOf(prefs.memoryOn(sessionId)) }
    fun go(to: () -> Unit) { onDismiss(); to() }
    val permLabel = mapOf("ask" to "Ask", "edits" to "Auto-edit", "auto" to "Auto")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                    .graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 24.dp.toPx(); scaleY = 0.96f + 0.04f * enter.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                    .clip(RoundedCornerShape(32.dp)).background(c.surfaceHigh).border(1.dp, c.border, RoundedCornerShape(32.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .animateContentSize(tween(240, easing = FastOutSlowInEasing))
                    .padding(vertical = 10.dp),
            ) {
                androidx.compose.animation.AnimatedContent(page, label = "add", transitionSpec = {
                    val dir = if (targetState != "main") 1 else -1
                    (androidx.compose.animation.slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 3 * dir } + androidx.compose.animation.fadeIn(tween(200))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 3 * dir } + androidx.compose.animation.fadeOut(tween(140)))
                }) { pg ->
                    Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                        when (pg) {
                            "perm" -> {
                                KimiBackRow("Permission") { page = "main" }
                                listOf(
                                    Triple("ask", "Ask", "Confirm every file edit and command"),
                                    Triple("edits", "Auto-edit", "Edit files freely, ask before commands and web"),
                                    Triple("auto", "Auto", "Run everything without asking"),
                                ).forEach { (k, l, d) ->
                                    KimiPanelRow(l, d, prefs.permissionMode == k) {
                                        prefs.permissionMode = k
                                        if (sessionId != null) scope.launch {
                                            runCatching {
                                                Graph.api.setSessionPermission(sessionId, Graph.chats.session(sessionId)?.directory,
                                                    Graph.chats.permissionRules(k, sessionId) ?: kotlinx.serialization.json.JsonArray(emptyList()))
                                            }
                                        }
                                        page = "main"
                                    }
                                }
                            }
                            "agent" -> {
                                KimiBackRow("Agent") { page = "main" }
                                agents.filter { it.mode != "subagent" && !it.hidden }.ifEmpty { agents }.forEach { a ->
                                    KimiPanelRow(a.name.replaceFirstChar { it.uppercase() }, a.description, prefs.agent == a.name) { prefs.agent = a.name; page = "main" }
                                }
                            }
                            else -> {
                                // what to attach: three round buttons, like the input's own
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AddTile("Camera", Ic.Camera, Modifier.weight(1f)) { go(onCamera) }
                                    AddTile("Photos", Ic.Image, Modifier.weight(1f)) { go(onPhotos) }
                                    AddTile("Files", Ic.FileUp, Modifier.weight(1f)) { go(onFiles) }
                                }
                                KimiDivider()
                                KimiNavRow("Permission", permLabel[prefs.permissionMode]) { page = "perm" }
                                KimiNavRow("Agent", prefs.agent.replaceFirstChar { it.uppercase() }) { page = "agent" }
                                KimiToggleRow("Plan mode", prefs.agent == "plan") { prefs.agent = if (it) "plan" else "build" }
                                KimiDivider()
                                KimiNavRow("Sub-agents", teamLabel()) { go { nav.push(Route.Team) } }
                                KimiNavRow("Connectors", null) { go { nav.push(Route.Connectors) } }
                                KimiNavRow("Skills", null) { go { nav.push(Route.Skills) } }
                                KimiNavRow("Scheduled", null) { go { nav.root(Route.Scheduled) } }
                                KimiNavRow("Devices", null) { go { nav.push(Route.Code) } }
                                if (onVoice != null) KimiNavRow("Call", null) { go(onVoice) }
                                KimiDivider()
                                KimiToggleRow("Memory", prefs.memoryEnabled) { prefs.memoryEnabled = it; memHere = prefs.memoryOn(sessionId); app.ocmobile.data.Memory.sync() }
                                if (sessionId != null && prefs.memoryEnabled) KimiToggleRow("Memory in this chat", memHere) { memHere = it; Graph.chats.setChatMemory(sessionId, it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Oc.c
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(c.text.copy(alpha = 0.06f)).tap(scale = 0.95f, onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.text.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Ico(icon, size = 24.dp) }
        Spacer(Modifier.height(8.dp))
        Txt(label, size = 16.sp)
    }
}

@Composable
private fun KimiToggleRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().tap(scale = 1f) { onChange(!on) }.padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, Modifier.weight(1f), size = 19.sp, weight = FontWeight.Medium, maxLines = 1)
        OcSwitch(on, onChange)
    }
}

/** Three things to ask, under the greeting of a new chat; a different three every time. Tapping one puts it in the input. */
@Composable
fun AskIdeas(code: Boolean, onPick: (String) -> Unit) {
    val c = Oc.c
    val ideas = remember(code) { (if (code) CODE_IDEAS else CHAT_IDEAS).shuffled().take(3) }
    Column(Modifier.padding(horizontal = 32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ideas.forEach { t ->
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(c.text.copy(alpha = 0.06f)).tap(scale = 0.97f) { onPick(t) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Txt(t, size = 15.sp, color = c.textDim, maxLines = 1)
            }
        }
    }
}

private val CHAT_IDEAS = listOf(
    "Plan my week from a messy list", "Explain something like I'm new to it", "Write a short message for me",
    "Help me choose between two options", "Sum up a long text I paste", "Make a packing list for a trip",
    "Turn my notes into a clear plan", "Ask me questions to find a gift idea",
)

private val CODE_IDEAS = listOf(
    "Make a small web page with a button", "Find and fix a bug in my project", "Explain what this repo does",
    "Write a script that renames my photos", "Add tests to a file", "Set up a new project for me",
    "Make a tiny game I can play here", "Clean up messy code without changing it",
)
