package app.ocmobile.ui.components

import androidx.compose.animation.core.Animatable
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
 * Вид Kimi — повторяет устройство приложения Kimi: круглые кнопки с обводкой в верхней панели,
 * модель выпадает карточкой сверху, «+» открывает лист с крупными плитками и строками-разделами,
 * над полем ввода — горизонтальные чипы быстрых действий.
 */

val isKimi: Boolean @Composable get() = Oc.look.id == "kimi"

/** Внутри верхней панели: кнопки рисуются кругами с обводкой (только в Kimi). */
val LocalInTopBar = compositionLocalOf { false }

/** Круглая кнопка Kimi: тёмный круг с тонкой обводкой. */
@Composable
fun KimiCircleBtn(icon: ImageVector, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 48.dp, tint: Color = Oc.c.text) {
    val c = Oc.c
    Box(
        Modifier.size(size).clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.7f)).border(1.dp, c.border, CircleShape).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = tint, size = 22.dp) }
}

/** Пилюля модели в шапке: «OpenCode» и серым — модель, как «Kimi K2.8 Preview». */
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
        Txt("OpenCode", size = 17.sp, maxLines = 1)
        Spacer(Modifier.width(7.dp))
        Txt(name.removeSuffix(" Free"), Modifier.weight(1f, fill = false), size = 17.sp, color = c.textDim, maxLines = 1)
    }
}

/**
 * Голубой шар-маскот с глазами. Живой: нажатие — подпрыгивает и улыбается,
 * если тыкать часто — злится и трясётся, потом остывает; долгое нажатие — засыпает до следующего касания.
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
                // радость: глаза-дуги «^ ^»
                Mood.Happy -> drawArc(
                    Color.White, 205f, 130f, false, Offset(ex - ew * 0.8f, ey - ew * 0.3f), androidx.compose.ui.geometry.Size(ew * 1.6f, ew * 1.6f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(ew * 0.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
                // злость: верх глаза скошен к переносице
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
                // сон: закрытые глаза-чёрточки
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

/** Чипы быстрых действий над полем ввода (горизонтальная прокрутка). */
@Composable
fun KimiQuickChips(nav: Nav, code: Boolean, onPick: (String) -> Unit) {
    val c = Oc.c
    val items: List<Triple<String, ImageVector, () -> Unit>> = listOf(
        Triple("New project", Ic.FilePlus) { nav.push(Route.Projects) },
        Triple("Code", Ic.Code) { nav.root(Route.Code) },
        Triple("Scheduled", Ic.Clock2) { nav.root(Route.Scheduled) },
        Triple("Write", Ic.Edit) { onPick("Help me write ") },
        Triple("Search the web", Ic.Globe) { onPick("Search the web for ") },
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, icon, act) ->
            Row(
                Modifier.height(50.dp).clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.75f)).tap(scale = 0.96f, onClick = act).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(icon, tint = c.textDim, size = 20.dp)
                Spacer(Modifier.width(9.dp))
                Txt(label, size = 16.sp)
            }
        }
    }
}

// ---------- выбор модели: карточка выпадает сверху ----------

@Composable
fun KimiModelPanel(onDismiss: () -> Unit, onMore: () -> Unit) {
    val c = Oc.c
    val prefs = Graph.prefs
    val providers by Graph.catalog.providers.collectAsState()
    var page by remember { mutableStateOf("main") }
    val current = Graph.catalog.model(prefs.providerId, prefs.modelId)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    val featured = remember(providers, prefs.recentModels, prefs.providerId, prefs.modelId) {
        val out = LinkedHashMap<String, Pair<String, ModelInfo>>()
        fun add(pid: String, m: ModelInfo?) { if (m != null) out.putIfAbsent("$pid/${m.id}", pid to m) }
        add(prefs.providerId, Graph.catalog.model(prefs.providerId, prefs.modelId))
        prefs.recentModels.split(',').filter { it.contains('/') }.forEach { k -> add(k.substringBefore('/'), Graph.catalog.model(k.substringBefore('/'), k.substringAfter('/'))) }
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
                    .heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
            ) {
                when (page) {
                    "effort" -> {
                        KimiPanelRow("Default", "Let the model decide", prefs.variant.isBlank()) { prefs.variant = ""; page = "main" }
                        current?.variantNames.orEmpty().forEach { n ->
                            KimiPanelRow(n.replaceFirstChar { it.uppercase() }, if (n == "high" || n == "max") "Thinks longer, answers slower" else null, prefs.variant == n) { prefs.variant = n; page = "main" }
                        }
                    }
                    "agent" -> Graph.catalog.agents.collectAsState().value.forEach { a ->
                        KimiPanelRow(a.name.replaceFirstChar { it.uppercase() }, a.description, prefs.agent == a.name) { prefs.agent = a.name; page = "main" }
                    }
                    else -> {
                        if (featured.isEmpty()) Txt("No connected providers yet.", Modifier.padding(24.dp), color = c.textDim)
                        featured.forEach { (pid, m) ->
                            val sel = prefs.providerId == pid && prefs.modelId == m.id
                            KimiPanelRow(m.name.ifBlank { m.id }, describe(m), sel) {
                                pickModel(pid, m)
                                onDismiss()
                            }
                        }
                        KimiDivider()
                        KimiNavRow("More models", null, onMore)
                        KimiDivider()
                        if (current != null && current.variantNames.isNotEmpty())
                            KimiNavRow("Reasoning level", prefs.variant.ifBlank { "Standard" }.replaceFirstChar { it.uppercase() }) { page = "effort" }
                        KimiNavRow("Mode", prefs.agent.replaceFirstChar { it.uppercase() }) { page = "agent" }
                    }
                }
            }
        }
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

/** Отметка выбранного: маленькая «луна» — освещённая половина. */
@Composable
private fun Moon(modifier: Modifier) {
    val c = Oc.c
    Canvas(modifier) {
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

// ---------- лист «+»: плитки и разделы ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KimiAddSheet(
    nav: Nav,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onVoice: (() -> Unit)?,
) {
    val c = Oc.c
    val prefs = Graph.prefs
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); after() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.surfaceHigh, shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp), dragHandle = null) {
        Column(Modifier.navigationBarsPadding().padding(top = 26.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KimiTile("Camera", Ic.Camera) { onDismiss(); onCamera() }
                KimiTile("Photos", Ic.Image) { onDismiss(); onPhotos() }
                KimiTile("Local file", Ic.FileUp) { onDismiss(); onFiles() }
                if (onVoice != null) KimiTile("Call", Ic.Phone) { onDismiss(); onVoice() }
                KimiTile("Devices", Ic.Laptop) { close { nav.push(Route.Code) } }
            }
            Spacer(Modifier.height(18.dp))
            KimiSheetRow(Ic.Puzzle, "Connectors", "Connect apps and databases to automate what you do") { close { nav.push(Route.Connectors) } }
            KimiSheetRow(Ic.Scroll, "Skills", "Reuse specialised skills to get tasks done reliably") { close { nav.push(Route.Skills) } }
            KimiSheetRow(Ic.Clock2, "Scheduled", "OpenCode runs a task on its own at the time you pick") { close { nav.root(Route.Scheduled) } }
            KimiSheetRow(Ic.Shield, "Permissions", mapOf("ask" to "Asks before every change", "edits" to "Edits files freely, asks before commands", "auto" to "Runs everything without asking")[prefs.permissionMode] ?: "") { close { nav.push(Route.PermissionsCfg) } }
        }
    }
}

@Composable
private fun KimiTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    val c = Oc.c
    Column(
        Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)).background(c.text.copy(alpha = 0.07f)).tap(scale = 0.96f, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Ico(icon, size = 26.dp)
        Spacer(Modifier.height(10.dp))
        Txt(label, size = 16.sp, align = TextAlign.Center, maxLines = 2, lineHeight = 20.sp)
    }
}

@Composable
private fun KimiSheetRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = Oc.c
    Row(Modifier.fillMaxWidth().tap(scale = 1f, onClick = onClick).padding(horizontal = 26.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, tint = c.text, size = 24.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 18.sp, weight = FontWeight.SemiBold)
            Txt(sub, Modifier.padding(top = 3.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp)
        }
        Spacer(Modifier.width(10.dp))
        Ico(Ic.ChevronRight, tint = c.textDim, size = 20.dp)
    }
}

/** Пустой главный экран Kimi: шар, приветствие по имени и голубая пилюля под ним. */
@Composable
fun KimiHero(code: Boolean, onMore: () -> Unit) {
    val c = Oc.c
    val name = Graph.prefs.userName.trim().substringBefore(' ')
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KimiBall(Modifier.size(88.dp))
        Spacer(Modifier.height(22.dp))
        Txt(
            (if (name.isBlank()) "Hi." else "Hi, $name.") + "\n" + (if (code) "What are we building today?" else "What shall we do today?"),
            size = 25.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, lineHeight = 34.sp,
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.7f)).tap(scale = 0.96f, onClick = onMore).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Txt("More models", size = 16.sp, color = c.accent, weight = FontWeight.Medium)
        }
    }
}
