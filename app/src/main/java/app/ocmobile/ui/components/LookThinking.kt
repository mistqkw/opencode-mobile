package app.ocmobile.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/*
 * «Агент работает» — у каждого вида свой характер, а не только свой цвет:
 * Terminal — спиннер TUI и секунды; Hairline — моно-подпись и бегущий отрезок по линии 1px;
 * Notebook — подпись выводится от руки с кареткой; Messenger — пузырь «печатает…»;
 * Cards — карточка-заготовка ответа; ChatGPT — пульсирующая точка; Kimi — маскот думает.
 */

/** Индикатор под вид; null — вид пользуется общим (значки Claude). */
@Composable
internal fun lookThinking(work: Work, modifier: Modifier): Boolean {
    when (Oc.look.id) {
        "terminal" -> TerminalThinking(work, modifier)
        "hairline" -> HairlineThinking(work, modifier)
        "notebook" -> NotebookThinking(work, modifier)
        "messenger" -> MessengerThinking(work, modifier)
        "cards" -> CardsThinking(work, modifier)
        "chatgpt" -> ChatGptThinking(work, modifier)
        "kimi" -> KimiThinking(work, modifier)
        else -> return false
    }
    return true
}

/** Секунды с начала работы. */
@Composable
private fun elapsed(): Long {
    val start = remember { System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(start) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    return (now - start) / 1000
}

private fun mmss(s: Long) = "%d:%02d".format(s / 60, s % 60)

private val Work.verb get() = when (this) {
    Work.Think -> "thinking"; Work.Read -> "reading files"; Work.Write -> "typing"; Work.Edit -> "editing code"
    Work.Run -> "running a command"; Work.Search -> "searching"; Work.Web -> "browsing the web"; Work.Plan -> "planning"
    Work.Wait -> "waiting for the provider"; Work.Other -> "working"
}

// ---------- Terminal ----------

@Composable
private fun TerminalThinking(work: Work, modifier: Modifier) {
    val c = Oc.c
    val frames = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"
    val t = rememberInfiniteTransition(label = "tui")
    val f by t.animateFloat(0f, frames.length.toFloat(), infiniteRepeatable(tween(800, easing = LinearEasing)), label = "f")
    val s = elapsed()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Txt(frames[f.toInt().coerceIn(0, frames.length - 1)].toString(), size = 15.sp, color = c.accent, family = Oc.mono)
        Spacer(Modifier.width(10.dp))
        Txt(work.label, size = 15.sp, family = Oc.mono)
        Txt("  ${s}s · stop to interrupt", size = 13.sp, color = c.textDim, family = Oc.mono, maxLines = 1)
    }
}

// ---------- Hairline ----------

@Composable
private fun HairlineThinking(work: Work, modifier: Modifier) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "hair")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "x")
    val s = elapsed()
    Column(modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(c.accent))
            Spacer(Modifier.width(10.dp))
            Txt(work.label.uppercase(), size = 11.sp, family = Oc.label)
            Spacer(Modifier.width(10.dp))
            Txt(mmss(s), size = 11.sp, color = c.textDim, family = Oc.label)
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.width(180.dp).height(1.dp)) {
            drawRect(c.border)
            val seg = size.width * 0.22f
            drawRect(c.accent, Offset((size.width - seg) * x, 0f), Size(seg, size.height))
        }
    }
}

// ---------- Notebook ----------

@Composable
private fun NotebookThinking(work: Work, modifier: Modifier) {
    val c = Oc.c
    val text = work.label + "…"
    val t = rememberInfiniteTransition(label = "ink")
    // 0..0.7 пишем по букве, 0.7..1 держим; каретка мигает
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "ph")
    val caret by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "caret")
    val shown = ((ph / 0.7f).coerceAtMost(1f) * text.length).toInt()
    Column(modifier.padding(vertical = 2.dp)) {
        Text(
            buildAnnotatedString {
                append(text.take(shown))
                withStyle(SpanStyle(color = c.accent.copy(alpha = if (caret < 0.5f) 1f else 0f))) { append("|") }
            },
            fontFamily = Oc.reply, fontStyle = FontStyle.Italic, fontSize = 17.sp, color = c.textDim,
        )
        // чернильная волнистая черта растёт вместе с текстом
        Canvas(Modifier.width(150.dp).height(6.dp)) {
            val p = (ph / 0.7f).coerceAtMost(1f)
            val path = androidx.compose.ui.graphics.Path()
            val end = size.width * p
            var xx = 0f
            path.moveTo(0f, size.height / 2)
            while (xx < end) { xx += 3f; path.lineTo(xx.coerceAtMost(end), size.height / 2 + sin(xx / 9f) * size.height * 0.3f) }
            drawPath(path, c.accent.copy(alpha = 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

// ---------- Messenger ----------

@Composable
private fun MessengerThinking(work: Work, modifier: Modifier) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "typing")
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "ph")
    Row(modifier.padding(start = 0.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.clip(TypingShape).background(c.surface).padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Canvas(Modifier.width(34.dp).height(10.dp)) {
                for (i in 0 until 3) {
                    // волна: точка подпрыгивает и светлеет по очереди
                    val k = ((sin((ph - i * 0.18f) * 2 * PI).toFloat()) + 1f) / 2f
                    val r = size.height * 0.38f
                    val cx = r + i * (size.width - 2 * r) / 2
                    drawCircle(c.textDim.copy(alpha = 0.35f + 0.65f * k), r * (0.8f + 0.2f * k), Offset(cx, size.height / 2 - k * size.height * 0.25f))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Txt("OpenCode is ${work.verb}…", Modifier.padding(bottom = 10.dp), size = 13.sp, color = c.link)
    }
}

/** Пузырь с хвостиком слева — как у входящих в мессенджере. */
private val TypingShape = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val r = with(density) { 16.dp.toPx() }; val tl = with(density) { 7.dp.toPx() }
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(tl + r, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r); lineTo(w, h - r); quadraticTo(w, h, w - r, h)
            lineTo(0f, h); quadraticTo(tl, h - tl * 0.3f, tl, h - tl * 1.6f); lineTo(tl, r); quadraticTo(tl, 0f, tl + r, 0f); close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(p)
    }
}

// ---------- Cards ----------

@Composable
private fun CardsThinking(work: Work, modifier: Modifier) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "skel")
    val x by t.animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "x")
    val pulse by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val s = elapsed()
    Column(
        modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(c.link))
            Spacer(Modifier.width(8.dp))
            Txt(work.label, Modifier.weight(1f), size = 13.sp, color = c.textDim, weight = FontWeight.Medium)
            Txt("${s}s", size = 12.sp, color = c.textDim)
        }
        Spacer(Modifier.height(12.dp))
        listOf(0.92f, 0.78f, 0.5f).forEach { f ->
            Canvas(Modifier.fillMaxWidth(f).height(9.dp)) {
                val w = size.width
                drawRoundRect(
                    Brush.linearGradient(
                        0f to c.surfaceHigh, 0.5f to c.border, 1f to c.surfaceHigh,
                        start = Offset(x * w - w * 0.4f, 0f), end = Offset(x * w + w * 0.4f, 0f),
                    ),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------- ChatGPT ----------

@Composable
private fun ChatGptThinking(work: Work, modifier: Modifier) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "gpt")
    val k by t.animateFloat(0.72f, 1f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
    val x by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "x")
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).graphicsLayer { scaleX = k; scaleY = k }.clip(CircleShape).background(c.text))
        if (work != Work.Think) {
            Spacer(Modifier.width(12.dp))
            val w = 240f
            Text(
                work.label,
                style = TextStyle(
                    fontSize = 15.sp, fontFamily = Oc.body,
                    brush = Brush.linearGradient(0f to c.textDim, 0.5f to c.text, 1f to c.textDim, start = Offset(x * w - w / 2, 0f), end = Offset(x * w + w / 2, 0f)),
                ),
            )
        }
    }
}

// ---------- Kimi ----------

@Composable
private fun KimiThinking(work: Work, modifier: Modifier) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "kimi")
    val dots by t.animateFloat(0f, 4f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "dots")
    Row(modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        KimiMascot(Modifier.size(28.dp), c.accent, thinking = true)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clip(CircleShape).background(c.surfaceHigh).padding(horizontal = 14.dp, vertical = 7.dp)) {
            Txt(work.label + ".".repeat(dots.toInt().coerceAtMost(3)), size = 14.sp, color = c.textDim)
        }
    }
}

/**
 * Маскот Kimi: голубая «подушка» с глазами. Обычно моргает и смотрит по сторонам,
 * когда думает — поднимает взгляд вверх и щурится.
 */
@Composable
fun KimiMascot(modifier: Modifier, color: Color, thinking: Boolean = false) {
    val t = rememberInfiniteTransition(label = "mascot")
    val blink by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 3600
        1f at 0; 1f at 3300; 0.1f at 3420; 1f at 3560
    }), label = "blink")
    val look by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(if (thinking) 1200 else 2600), RepeatMode.Reverse), label = "look")
    Canvas(modifier) {
        val s = size.minDimension
        drawRoundRect(color, Offset.Zero, Size(s, s), CornerRadius(s * 0.42f))
        val ew = s * 0.11f
        val eh = s * (if (thinking) 0.16f else 0.24f) * blink
        val cy = s * (if (thinking) 0.4f else 0.47f)
        val dx = s * 0.05f * look
        for (x in listOf(s * 0.36f, s * 0.64f)) {
            drawRoundRect(Color.White, Offset(x + dx - ew / 2, cy - eh / 2), Size(ew, eh), CornerRadius(ew / 2))
        }
    }
}
