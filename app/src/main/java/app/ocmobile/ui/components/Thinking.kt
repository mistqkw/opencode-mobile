package app.ocmobile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.ui.theme.Oc
import kotlin.math.PI
import kotlin.math.sin

/** Что делает агент: от этого зависит вид и подпись индикатора. */
enum class Work(val label: String) {
    Think("Thinking"), Read("Reading files"), Write("Writing"), Edit("Editing code"),
    Run("Running a command"), Search("Searching"), Web("Browsing the web"), Plan("Planning"), Wait("Waiting for the provider"), Other("Working"),
}

/**
 * Индикатор «агент работает»: маленький значок, свой для каждого вида работы, и подпись с бликом.
 * Значки плавно двигаются (без мигания) и чуть-чуть: они не должны отвлекать от текста.
 */
@Composable
fun ThinkingIndicator(work: Work, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WorkGlyph(work, 18.dp)
        Spacer(Modifier.width(9.dp))
        AnimatedContent(
            work.label, label = "thinking-label",
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 }) togetherWith (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 }) },
        ) { text -> ShimmerText(text) }
    }
}

@Composable
fun WorkGlyph(work: Work, size: Dp) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "glyph")
    val period = when (work) { Work.Think -> 1100; Work.Read -> 1400; Work.Write -> 1800; Work.Edit -> 1600; Work.Run -> 1200; Work.Search -> 1700; Work.Web -> 2000; Work.Plan -> 1500; Work.Wait -> 2400; else -> 1300 }
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "ph")
    val accent = c.accent
    val dim = c.textDim
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.11f
        fun wave(x: Float) = (sin(x * 2 * PI).toFloat() + 1f) / 2f
        when (work) {
            // три точки подпрыгивают по очереди
            Work.Think, Work.Other -> for (i in 0 until 3) {
                val k = wave(ph - i / 5f)
                drawCircle(accent.copy(alpha = 0.4f + 0.6f * k), w * 0.095f, Offset(w * (0.22f + 0.28f * i), h * (0.58f - 0.22f * k)))
            }
            // строки текста, по которым скользит «взгляд»
            Work.Read -> {
                for (i in 0 until 3) drawRoundRect(dim.copy(alpha = 0.5f), Offset(w * 0.1f, h * (0.2f + 0.28f * i)), Size(w * (0.8f - 0.15f * (i % 2)), sw), CornerRadius(sw))
                val x = w * (0.1f + 0.7f * ph)
                drawRoundRect(accent, Offset(x, h * 0.12f), Size(w * 0.1f, h * 0.76f), CornerRadius(sw))
            }
            // строки печатаются одна за другой
            Work.Write -> for (i in 0 until 3) {
                val p = ((ph * 3f) - i).coerceIn(0f, 1f)
                val keep = if (ph > 0.92f) (1f - ph) / 0.08f else 1f
                drawRoundRect(accent.copy(alpha = keep), Offset(w * 0.1f, h * (0.2f + 0.28f * i)), Size(w * (0.8f * p).coerceAtLeast(sw), sw), CornerRadius(sw))
            }
            // диф: красная строка сокращается, зелёная вырастает
            Work.Edit -> {
                val k = wave(ph)
                drawRoundRect(c.danger.copy(alpha = 0.9f), Offset(w * 0.1f, h * 0.3f), Size(w * (0.85f - 0.55f * k), sw), CornerRadius(sw))
                drawRoundRect(c.ok.copy(alpha = 0.9f), Offset(w * 0.1f, h * 0.62f), Size(w * (0.3f + 0.55f * k), sw), CornerRadius(sw))
            }
            // «>» и бегущий подчёркивающий курсор
            Work.Run -> {
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * .12f, h * .2f); lineTo(w * .45f, h * .46f); lineTo(w * .12f, h * .72f) }
                drawPath(p, accent, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val x = w * (0.55f + 0.25f * wave(ph))
                drawLine(dim, Offset(x, h * .78f), Offset(x + w * .2f, h * .78f), sw, StrokeCap.Round)
            }
            // кружок-лупа ходит по дуге
            Work.Search -> {
                val a = ph * 2 * PI
                val cx = w * 0.5f + w * 0.22f * kotlin.math.cos(a).toFloat()
                val cy = h * 0.5f + h * 0.22f * sin(a).toFloat()
                drawCircle(dim.copy(alpha = 0.35f), w * 0.36f, Offset(w / 2, h / 2), style = Stroke(sw * 0.7f))
                drawCircle(accent, w * 0.13f, Offset(cx, cy))
            }
            // волны расходятся от точки
            Work.Web -> for (i in 0 until 2) {
                val k = (ph + i * 0.5f) % 1f
                drawCircle(accent.copy(alpha = 1f - k), w * (0.12f + 0.38f * k), Offset(w / 2, h / 2), style = Stroke(sw * 0.8f))
            }
            // чек-лист: галочки появляются по очереди
            Work.Plan -> for (i in 0 until 3) {
                val on = ((ph * 3.4f).toInt() > i)
                drawRoundRect(if (on) c.ok else dim.copy(alpha = 0.5f), Offset(w * 0.1f, h * (0.14f + 0.29f * i)), Size(w * 0.16f, w * 0.16f), CornerRadius(sw * 0.6f))
                drawRoundRect(dim.copy(alpha = 0.5f), Offset(w * 0.4f, h * (0.2f + 0.29f * i)), Size(w * 0.5f, sw), CornerRadius(sw))
            }
            // ожидание: медленная дуга
            Work.Wait -> drawArc(dim, ph * 360f, 240f, false, topLeft = Offset(sw, sw), size = Size(w - 2 * sw, h - 2 * sw), style = Stroke(sw, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun PixelWave(size: Dp, color: Color) {
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "phase")
    Canvas(Modifier.size(size)) {
        val n = 3
        val gap = this.size.minDimension * 0.1f
        val cell = (this.size.minDimension - gap * (n - 1)) / n
        for (y in 0 until n) for (x in 0 until n) {
            // волна идёт по диагонали; масштаб и яркость плавно меняются
            val k = (sin((phase - (x + y) / 6f) * 2 * PI).toFloat() + 1f) / 2f
            val s = 0.45f + 0.55f * k
            val d = cell * s
            val cx = x * (cell + gap) + cell / 2
            val cy = y * (cell + gap) + cell / 2
            drawRoundRect(color.copy(alpha = 0.35f + 0.65f * k), Offset(cx - d / 2, cy - d / 2), Size(d, d), CornerRadius(cell * 0.18f))
        }
    }
}

@Composable
private fun ShimmerText(text: String) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val w = 260f
    Text(
        text,
        style = TextStyle(
            fontSize = 14.sp, fontFamily = Oc.body,
            brush = Brush.linearGradient(
                0f to c.textDim, 0.5f to c.text, 1f to c.textDim,
                start = Offset(x * w - w / 2, 0f), end = Offset(x * w + w / 2, 0f),
            ),
        ),
    )
}
