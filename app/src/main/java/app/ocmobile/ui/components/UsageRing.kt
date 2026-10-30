package app.ocmobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ocmobile.Graph
import app.ocmobile.data.Chat
import app.ocmobile.data.ContextInfo
import app.ocmobile.data.ContextUsage
import app.ocmobile.data.UsageWindow
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.lookBackdrop

/*
 * Кружок расхода в верхней панели, как в Claude Code: заполняется по 5-часовому лимиту.
 * По нажатию — меню: лимиты 5 ч / неделя / месяц, чем занят контекст чата и сколько потрачено.
 * У каждого вида своё оформление: терминал — текстом, Hairline — линиями, Notebook — пером, Kimi — кольцами.
 */

@Composable
fun UsageRing(chat: Chat?, nav: Nav) {
    val c = Oc.c
    val look = Oc.look.id
    val snap by Graph.usage.snapshot.collectAsState()
    var open by remember { mutableStateOf(false) }
    val f = snap.fiveHour.fraction.toFloat()
    val col = levelColor(f, ringColor())
    val onClick = { Graph.usage.recompute(); open = true }
    when (look) {
        "terminal" -> Box(Modifier.height(44.dp).clip(RoundedCornerShape(6.dp)).tap(onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Txt("[${(f * 100).toInt()}%]", size = 14.sp, family = Oc.mono, color = col)
        }
        "kimi" -> Box(
            Modifier.size(48.dp).clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.7f)).border(1.dp, c.border, CircleShape).tap(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Ring(f, col, Modifier.size(22.dp), 3.dp) }
        else -> Box(Modifier.size(44.dp).clip(CircleShape).tap(onClick = onClick), contentAlignment = Alignment.Center) {
            Ring(f, col, Modifier.size(if (look == "hairline") 20.dp else 22.dp), if (look == "hairline") 1.5.dp else 2.5.dp, square = look == "hairline")
        }
    }
    if (open) UsagePanel(chat, nav) { open = false }
}

@Composable
private fun ringColor(): Color = when (Oc.look.id) {
    "desktop" -> Color(0xFF3B82F6)
    "chatgpt" -> Oc.c.text
    "terminal" -> Oc.c.link
    else -> Oc.c.accent
}

@Composable
private fun levelColor(f: Float, normal: Color) = when {
    f >= 0.9f -> Oc.c.danger
    f >= 0.75f -> Oc.c.warn
    else -> normal
}

/** Кольцо прогресса. В Hairline — квадратная рамка, которая обводится по часовой. */
@Composable
private fun Ring(f: Float, color: Color, modifier: Modifier, stroke: Dp, square: Boolean = false) {
    val track = Oc.c.text.copy(alpha = 0.12f)
    Canvas(modifier) {
        val w = stroke.toPx()
        if (square) {
            drawRect(track, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
            // обводка периметра: верх → право → низ → лево
            val side = size.width - w; var left = f * 4 * side
            val pts = listOf(Offset(w / 2, w / 2), Offset(size.width - w / 2, w / 2), Offset(size.width - w / 2, size.height - w / 2), Offset(w / 2, size.height - w / 2), Offset(w / 2, w / 2))
            for (i in 0 until 4) {
                if (left <= 0) break
                val a = pts[i]; val b = pts[i + 1]; val k = (left / side).coerceAtMost(1f)
                drawLine(color, a, Offset(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k), w)
                left -= side
            }
        } else {
            drawCircle(track, size.minDimension / 2 - w / 2, style = Stroke(w))
            if (f > 0.004f) drawArc(color, -90f, 360f * f.coerceAtMost(1f), false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

// ---------- меню ----------

private fun k(n: Long) = when {
    n >= 1_000_000 -> "%.1fM".format(java.util.Locale.US, n / 1_000_000.0)
    n >= 10_000 -> "%.0fk".format(java.util.Locale.US, n / 1000.0)
    n >= 1000 -> "%.1fk".format(java.util.Locale.US, n / 1000.0)
    else -> "$n"
}

private fun money(x: Double) = if (x >= 100) "$%.0f".format(java.util.Locale.US, x) else "$%.2f".format(java.util.Locale.US, x)

private fun resets(w: UsageWindow): String {
    val at = w.resetAt ?: return "No usage yet"
    if (at <= System.currentTimeMillis()) return "Resetting now"
    val soon = at - System.currentTimeMillis() < 24 * 3600_000L
    return "Resets " + java.text.SimpleDateFormat(if (soon) "'at' h:mm a" else "EEE h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(at))
}

/** Цвета долей контекста — плоские, без свечения. */
@Composable
private fun partColor(key: String): Color {
    val c = Oc.c
    return when (key) {
        "prompt" -> c.textDim
        "tools" -> Color(0xFF6E8FB5)
        "mcp" -> Color(0xFF4FB3A9)
        "skills" -> Color(0xFFE0A84F)
        "memory" -> Color(0xFFD97757)
        "messages" -> if (Oc.look.id == "chatgpt") Color(0xFF8E7CF0) else c.accent
        "buffer" -> c.textDim.copy(alpha = 0.35f)
        else -> c.textDim.copy(alpha = 0.5f)
    }
}

/** Типографика меню под вид. */
private class PanelFonts(val title: FontFamily?, val label: FontFamily?, val upper: Boolean, val italic: Boolean)

@Composable
private fun fonts(): PanelFonts = when (Oc.look.id) {
    "terminal" -> PanelFonts(Oc.mono, Oc.mono, false, false)
    "hairline" -> PanelFonts(Oc.label, Oc.label, true, false)
    "notebook" -> PanelFonts(Oc.reply, Oc.reply, false, true)
    "claude" -> PanelFonts(Oc.heading, null, false, false)
    else -> PanelFonts(null, null, false, false)
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, size: TextUnit = 15.sp, color: Color = Oc.c.text, weight: FontWeight? = null, align: TextAlign? = null) {
    val f = fonts()
    if (f.italic) androidx.compose.material3.Text(text, modifier, color = color, fontSize = size, fontFamily = f.label, fontStyle = FontStyle.Italic, textAlign = align, maxLines = 1)
    else Txt(if (f.upper) text.uppercase() else text, modifier, size = if (f.upper) (size.value - 3).sp else size, color = color, weight = weight, family = f.label, maxLines = 1, align = align)
}

@Composable
private fun UsagePanel(chat: Chat?, nav: Nav, onDismiss: () -> Unit) {
    val c = Oc.c
    val look = Oc.look.id
    val prefs = Graph.prefs
    val snap by Graph.usage.snapshot.collectAsState()
    var ctx by remember { mutableStateOf<ContextInfo?>(null) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
    LaunchedEffect(chat?.id, chat?.messages?.size) {
        if (chat == null) return@LaunchedEffect
        val lastA = chat.messages.lastOrNull { !it.info.isUser && it.info.providerName != null }?.info
        val pid = lastA?.providerName ?: prefs.providerId
        val mid = lastA?.modelName ?: prefs.modelId
        val window = Graph.catalog.model(pid, mid)?.limit?.context ?: 0L
        val tools = ContextUsage.tools(pid, mid, chat.directory)
        ctx = ContextUsage.compute(chat.messages.toList(), tools, window, chat.directory)
    }
    val plan = if (prefs.limitProviders.contains("opencode-go")) "OpenCode Go" else "Your limits"
    val windows = listOf("5-hour limit" to snap.fiveHour, "Weekly · all models" to snap.week, "Monthly" to snap.month)

    val shape: Shape = when (look) {
        "terminal" -> RectangleShape
        "hairline" -> RoundedCornerShape(2.dp)
        "notebook" -> RoundedCornerShape(6.dp)
        "desktop" -> RoundedCornerShape(12.dp)
        "messenger" -> RoundedCornerShape(14.dp)
        "chatgpt" -> RoundedCornerShape(24.dp)
        "kimi" -> RoundedCornerShape(32.dp)
        else -> RoundedCornerShape(20.dp)
    }
    val bg = when (look) { "terminal", "hairline", "notebook" -> c.bg; "chatgpt", "kimi" -> c.surfaceHigh; "cards" -> Color.Transparent; else -> c.surface }
    val border = look !in setOf("chatgpt", "messenger", "cards")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f * enter.value)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
            Column(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 62.dp, start = 10.dp, end = 10.dp).widthIn(max = 440.dp).fillMaxWidth()
                    .graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * -16.dp.toPx() }
                    .clip(shape).background(bg)
                    .then(if (border) Modifier.border(1.dp, c.border, shape) else Modifier)
                    .then(if (look == "notebook") Modifier.lookBackdrop() else Modifier)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .heightIn(max = 660.dp).verticalScroll(rememberScrollState())
                    .padding(if (look == "cards") 0.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (look == "cards") 8.dp else 0.dp),
            ) {
                Section(look) {
                    // шапка
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (look) {
                            "terminal" -> Txt("$ usage --limits", Modifier.weight(1f), size = 13.sp, family = Oc.mono, color = c.textDim)
                            "kimi" -> Txt("Usage", Modifier.weight(1f), size = 20.sp, weight = FontWeight.SemiBold)
                            "notebook" -> androidx.compose.material3.Text("Usage so far", Modifier.weight(1f), fontSize = 20.sp, fontFamily = Oc.reply, fontStyle = FontStyle.Italic, color = c.text)
                            "claude" -> Heading("Usage", Modifier.weight(1f), size = 20.sp)
                            else -> Label("Plan usage limits · $plan", Modifier.weight(1f), size = 14.sp, color = c.textDim)
                        }
                        Label(if (look == "terminal") "all →" else "See all →", Modifier.clip(CircleShape).tap { onDismiss(); nav.push(Route.Usage) }.padding(6.dp), size = 14.sp)
                    }
                    if (look == "kimi" || look == "claude" || look == "notebook") Label(plan, size = 13.sp, color = c.textDim)
                    Spacer(Modifier.height(14.dp))
                    if (look == "kimi") Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        windows.forEach { (label, w) -> KimiLimit(label.substringBefore(" ·").removeSuffix(" limit"), w, Modifier.weight(1f)) }
                    } else windows.forEachIndexed { i, (label, w) ->
                        if (i > 0) Spacer(Modifier.height(14.dp))
                        LimitRow(label, w)
                    }
                }
                if (look != "cards") Divider(look)
                Section(look) { ContextBlock(chat, ctx) }
                if (chat != null) {
                    if (look != "cards") Divider(look)
                    Section(look) { ChatStats(ctx) }
                }
            }
        }
    }
}

@Composable
private fun Section(look: String, content: @Composable ColumnScope.() -> Unit) {
    val c = Oc.c
    if (look == "cards") Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(16.dp), content = content)
    else Column(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun Divider(look: String) {
    val c = Oc.c
    when (look) {
        "terminal" -> Txt("─".repeat(60), Modifier.padding(vertical = 10.dp), size = 12.sp, family = Oc.mono, color = c.border, maxLines = 1)
        "notebook" -> Canvas(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(6.dp)) {
            // волнистая черта пером
            val p = androidx.compose.ui.graphics.Path(); var x = 0f; p.moveTo(0f, size.height / 2)
            while (x < size.width) { p.quadraticTo(x + 6.dp.toPx(), 0f, x + 12.dp.toPx(), size.height / 2); p.quadraticTo(x + 18.dp.toPx(), size.height, x + 24.dp.toPx(), size.height / 2); x += 24.dp.toPx() }
            clipRect { drawPath(p, c.accent.copy(alpha = 0.5f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)) }
        }
        else -> Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(1.dp).background(c.border))
    }
}

/** Строка лимита: название, когда отпустит, процент и полоса. */
@Composable
private fun LimitRow(label: String, w: UsageWindow) {
    val c = Oc.c
    val look = Oc.look.id
    val f = w.fraction.toFloat()
    val col = levelColor(f, ringColor())
    if (look == "terminal") {
        val n = 18; val filled = (f * n).toInt()
        Txt("%-8s".format(label.substringBefore(" ").replace("-hour", "h").lowercase()) + "█".repeat(filled) + "░".repeat(n - filled) + "  %3d%%".format((f * 100).toInt()), size = 13.sp, family = Oc.mono, color = col, maxLines = 1)
        Txt("        ${money(w.used)} of ${money(w.limit)} · ${resets(w).lowercase()}", size = 12.sp, family = Oc.mono, color = c.textDim, maxLines = 1)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label(label, Modifier.weight(1f), size = 15.sp)
        Label(resets(w), size = 13.sp, color = c.textDim)
        Spacer(Modifier.width(10.dp))
        Label("${(f * 100).toInt()}%", size = 14.sp, color = if (f >= 0.75f) col else c.textDim)
    }
    Spacer(Modifier.height(7.dp))
    val h = when (look) { "hairline" -> 2.dp; "notebook" -> 3.dp; "chatgpt" -> 4.dp; "messenger" -> 5.dp; else -> 5.dp }
    val shape = if (look == "hairline") RectangleShape else CircleShape
    Box(Modifier.fillMaxWidth().height(h).clip(shape).background(if (look == "hairline") c.border else c.surfaceHigh)) {
        if (f > 0.004f) Box(Modifier.fillMaxWidth(f.coerceAtMost(1f)).height(h).clip(shape).background(col))
    }
    Spacer(Modifier.height(5.dp))
    Label("${money(w.used)} of ${money(w.limit)}", size = 12.sp, color = c.textDim)
}

/** Kimi: три кольца рядом — 5 ч, неделя, месяц. */
@Composable
private fun KimiLimit(label: String, w: UsageWindow, modifier: Modifier) {
    val c = Oc.c
    val f = w.fraction.toFloat()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Ring(f, levelColor(f, c.accent), Modifier.fillMaxSize(), 6.dp)
            Txt("${(f * 100).toInt()}%", size = 16.sp, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Txt(label, size = 14.sp, weight = FontWeight.Medium, maxLines = 1)
        Txt("${money(w.used)} / ${money(w.limit)}", size = 12.sp, color = c.textDim, maxLines = 1)
    }
}

/** Контекст: сетка 20×5 (клетка — 1 % окна) и легенда по долям. */
@Composable
private fun ContextBlock(chat: Chat?, ctx: ContextInfo?) {
    val c = Oc.c
    val look = Oc.look.id
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (look) {
            "terminal" -> Txt("$ context", Modifier.weight(1f), size = 13.sp, family = Oc.mono, color = c.textDim)
            "claude" -> Heading("Context", Modifier.weight(1f), size = 18.sp)
            "kimi" -> Txt("Context", Modifier.weight(1f), size = 18.sp, weight = FontWeight.SemiBold)
            else -> Label("Context window", Modifier.weight(1f), size = 14.sp, color = c.textDim)
        }
        if (ctx != null && ctx.window > 0) Label("${k(ctx.used)} / ${k(ctx.window)} · ${(ctx.fraction * 100).toInt()}%", size = 13.sp, color = c.textDim)
    }
    Spacer(Modifier.height(12.dp))
    when {
        chat == null -> { Label("Open a chat to see what fills its context.", size = 14.sp, color = c.textDim); return }
        ctx == null -> { Label("Counting…", size = 14.sp, color = c.textDim); return }
        ctx.window <= 0 -> { Label("This model doesn't report its window size.", size = 14.sp, color = c.textDim); return }
    }
    ctx!!
    // доли по порядку: занятое, свободное, запас под автосжатие (в конце, как в Claude Code)
    val shares = ctx.parts.filter { it.tokens > 0 }.map { it.key to it.tokens } + listOf("free" to ctx.free, "buffer" to ctx.buffer)
    val cells = ArrayList<String>(100)
    var acc = 0.0
    shares.forEach { (key, t) ->
        acc += t * 100.0 / ctx.window
        while (cells.size < kotlin.math.round(acc).toInt().coerceAtMost(100)) cells.add(key)
    }
    while (cells.size < 100) cells.add("buffer")
    val colors = (ctx.parts.map { it.key } + listOf("free", "buffer")).associateWith { partColor(it) }
    ContextGrid(cells, colors)
    Spacer(Modifier.height(14.dp))
    val rows = ctx.parts.map { Triple(it.key, it.label, it.tokens) } + listOf(Triple("free", "Free space", ctx.free), Triple("buffer", "Autocompact buffer", ctx.buffer))
    rows.forEach { (key, label, t) ->
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Marker(colors.getValue(key), key == "free")
            Spacer(Modifier.width(10.dp))
            Label(label, Modifier.weight(1f), size = 14.sp)
            Label(k(t), size = 14.sp, color = c.textDim)
            Spacer(Modifier.width(10.dp))
            Label("%.1f%%".format(java.util.Locale.US, t * 100.0 / ctx.window), Modifier.width(52.dp), size = 13.sp, color = c.textDim, align = TextAlign.End)
        }
    }
    if (!ctx.measured) { Spacer(Modifier.height(6.dp)); Label("Estimated until the first reply comes in.", size = 12.sp, color = c.textDim) }
}

/** Значок доли в легенде: форма — как клетка сетки этого вида. */
@Composable
private fun Marker(color: Color, free: Boolean) {
    val look = Oc.look.id
    if (look == "terminal") { Txt(if (free) "□" else "■", size = 14.sp, family = Oc.mono, color = if (free) Oc.c.textDim else color); return }
    val round = look in setOf("kimi", "chatgpt", "notebook", "messenger")
    Canvas(Modifier.size(12.dp)) {
        if (free) {
            if (round) drawCircle(color, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            else drawRect(color, Offset(0.5f.dp.toPx(), 0.5f.dp.toPx()), Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()), style = Stroke(1.dp.toPx()))
        } else if (round) drawCircle(color) else drawRoundRect(color, cornerRadius = CornerRadius(if (look == "hairline") 0f else 3.dp.toPx()))
    }
}

@Composable
private fun ContextGrid(cells: List<String>, colors: Map<String, Color>) {
    val look = Oc.look.id
    val c = Oc.c
    if (look == "terminal") {
        // как /context в Claude Code: строки символов
        Column {
            cells.chunked(20).forEach { row ->
                Row { row.forEach { key -> Txt(if (key == "free") "□" else if (key == "buffer") "▨" else "■", size = 16.sp, family = Oc.mono, color = if (key == "free") c.textDim.copy(alpha = 0.6f) else colors.getValue(key)) } }
            }
        }
        return
    }
    val round = look in setOf("kimi", "chatgpt", "notebook", "messenger")
    Canvas(Modifier.fillMaxWidth().aspectRatio(3.9f)) {
        // 20 × 5: клетка — 1 % окна
        val gap = (if (look == "hairline") 3 else 4).dp.toPx()
        val cell = minOf((size.width - gap * 19) / 20, (size.height - gap * 4) / 5)
        val left = (size.width - (cell * 20 + gap * 19)) / 2
        cells.forEachIndexed { i, key ->
            val x = left + (i % 20) * (cell + gap); val y = (i / 20) * (cell + gap)
            val col = colors.getValue(key)
            val free = key == "free"
            when {
                round && free -> drawCircle(col, cell / 2 - 0.75f.dp.toPx(), Offset(x + cell / 2, y + cell / 2), style = Stroke(1.dp.toPx()))
                round -> drawCircle(col, cell / 2, Offset(x + cell / 2, y + cell / 2))
                free -> drawRect(col, Offset(x + 0.5f, y + 0.5f), Size(cell - 1, cell - 1), style = Stroke(1.dp.toPx()))
                look == "hairline" -> drawRect(col, Offset(x, y), Size(cell, cell))
                else -> drawRoundRect(col, Offset(x, y), Size(cell, cell), CornerRadius(cell * 0.22f))
            }
        }
    }
}

/** Этот чат: сообщения, потраченное и токены. */
@Composable
private fun ChatStats(ctx: ContextInfo?) {
    val c = Oc.c
    val look = Oc.look.id
    val stats = listOf(
        "Messages" to (ctx?.let { "${it.messages}" } ?: "–"),
        "Spent" to (ctx?.let { money(it.cost) } ?: "–"),
        "Tokens in" to (ctx?.let { k(it.tokensIn) } ?: "–"),
        "Tokens out" to (ctx?.let { k(it.tokensOut) } ?: "–"),
    )
    when (look) {
        "terminal", "hairline" -> stats.forEach { (l, v) ->
            Row(Modifier.padding(vertical = 3.dp)) { Label(l, Modifier.weight(1f), size = 14.sp, color = c.textDim); Label(v, size = 14.sp) }
        }
        else -> {
            Label("This chat", size = 14.sp, color = c.textDim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.forEach { (l, v) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(if (look == "kimi") 20.dp else 12.dp))
                            .background(if (look == "chatgpt" || look == "kimi") c.text.copy(alpha = 0.06f) else c.surfaceHigh).padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Label(v, size = 16.sp, weight = FontWeight.SemiBold)
                        Label(l, size = 11.sp, color = c.textDim)
                    }
                }
            }
            if (ctx != null && ctx.yours > 0) { Spacer(Modifier.height(8.dp)); Label("${ctx.yours} from you, ${ctx.messages - ctx.yours} from OpenCode", size = 12.sp, color = c.textDim) }
        }
    }
}
