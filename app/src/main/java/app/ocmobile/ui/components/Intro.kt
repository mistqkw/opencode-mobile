package app.ocmobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Oc
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Анимация входа: у каждого вида своя. Показывается при холодном старте поверх приложения
 * (пока поднимается сервер), по тапу пропускается. Только линии, точки и плоский цвет — без свечений.
 */
object Intro {
    /** Уже показали в этом процессе. */
    var played = false
    /** Повторить (из настроек «Preview» и для отладки). */
    val replay = mutableIntStateOf(0)
}

/** Отрезок времени [a, b] → 0..1 с плавным входом и выходом. */
private fun seg(t: Float, a: Float, b: Float): Float {
    val k = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

/** Пружинка с перелётом для «выскакивания». */
private fun pop(t: Float, a: Float, b: Float): Float {
    val k = ((t - a) / (b - a)).coerceIn(0f, 1f)
    if (k == 0f) return 0f
    val s = 1.70158f * 1.4f
    val x = k - 1
    return x * x * ((s + 1) * x + s) + 1
}

@Composable
fun IntroHost() {
    val replay by Intro.replay
    var show by remember { mutableStateOf(!Intro.played && Graph.prefs.introEnabled) }
    LaunchedEffect(replay) { if (replay > 0) show = true }
    if (!show) return
    val look = Oc.look.id
    val dur = when (look) { "terminal" -> 2300; "kimi" -> 2200; "notebook" -> 2300; else -> 2000 }
    val t = remember(replay) { Animatable(0f) }
    val fade = remember(replay) { Animatable(1f) }
    LaunchedEffect(replay) {
        Intro.played = true
        t.snapTo(0f); fade.snapTo(1f)
        t.animateTo(1f, tween(dur, easing = LinearEasing))
        fade.animateTo(0f, tween(320))
        show = false
    }
    val p = t.value
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }
            .clickable(remember { MutableInteractionSource() }, null) { show = false },
    ) {
        when (look) {
            "terminal" -> TerminalIntro(p)
            "desktop" -> DesktopIntro(p)
            "hairline" -> HairlineIntro(p)
            "chatgpt" -> ChatGptIntro(p)
            "kimi" -> KimiIntro(p)
            "notebook" -> NotebookIntro(p)
            else -> ClaudeIntro(p)
        }
    }
}

// ---------- Claude: лучи распускаются цветком, сворачиваются в знак, название проявляется по буквам ----------

@Composable
private fun ClaudeIntro(t: Float) {
    val c = Oc.c
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(140.dp)) {
                val cx = size.width / 2; val cy = size.height / 2
                val grow = seg(t, 0f, 0.32f); val fold = seg(t, 0.36f, 0.56f)
                val n = 12
                val rot = 50f * seg(t, 0f, 0.56f)
                rotate(rot, Offset(cx, cy)) {
                    for (i in 0 until n) {
                        val a = i * 2 * PI.toFloat() / n
                        // лучи разной длины, как лепестки
                        val len = size.minDimension * (if (i % 2 == 0) 0.46f else 0.34f) * grow * (1 - fold)
                        val inner = size.minDimension * 0.06f * (1 - fold)
                        if (len > inner) drawLine(
                            c.accent, Offset(cx + cos(a) * inner, cy + sin(a) * inner), Offset(cx + cos(a) * len, cy + sin(a) * len),
                            size.minDimension * 0.05f, StrokeCap.Round,
                        )
                    }
                }
                // знак OpenCode: мягкая рамка с «окном», нижняя половина окна чуть тоньше
                val m = pop(t, 0.42f, 0.66f)
                if (m > 0f) {
                    val w = size.minDimension * 0.42f * m; val h = w * 1.25f
                    val l = cx - w / 2; val top = cy - h / 2
                    drawRoundRect(c.accent, Offset(l, top), Size(w, h), CornerRadius(w * 0.16f))
                    val hw = w * 0.5f; val hh = h * 0.6f
                    drawRoundRect(c.bg, Offset(cx - hw / 2, top + h * 0.2f), Size(hw, hh), CornerRadius(w * 0.06f))
                    drawRoundRect(c.accent.copy(alpha = 0.45f), Offset(cx - hw / 2, top + h * 0.2f + hh * 0.33f), Size(hw, hh * 0.67f), CornerRadius(w * 0.06f))
                }
            }
            Spacer(Modifier.height(18.dp))
            StaggerText("OpenCode", t, 0.6f, 0.86f, 34, Oc.heading, c.text)
        }
    }
}

/** Слово по буквам: каждая поднимается и проявляется чуть позже предыдущей. */
@Composable
private fun StaggerText(text: String, t: Float, a: Float, b: Float, sizeSp: Int, family: androidx.compose.ui.text.font.FontFamily, color: Color, weight: FontWeight? = null) {
    Row {
        text.forEachIndexed { i, ch ->
            val k = seg(t, a + (b - a) * 0.6f * i / text.length, a + (b - a) * (0.4f + 0.6f * i / text.length))
            Text(
                ch.toString(), Modifier.graphicsLayer { alpha = k; translationY = (1 - k) * 18.dp.toPx() },
                fontFamily = family, fontSize = sizeSp.sp, color = color, fontWeight = weight,
            )
        }
    }
}

// ---------- Terminal: печатаем команду, логотип TUI проявляется сканом по колонкам ----------

private val tuiLeft = listOf("                   ", "█▀▀█ █▀▀█ █▀▀█ █▀▀▄", "█__█ █__█ █^^^ █__█", "▀▀▀▀ █▀▀▀ ▀▀▀▀ ▀~~▀")
private val tuiRight = listOf("             ▄     ", "█▀▀▀ █▀▀█ █▀▀█ █▀▀█", "█___ █__█ █__█ █^^^", "▀▀▀▀ ▀▀▀▀ ▀▀▀▀ ▀▀▀▀")

@Composable
private fun TerminalIntro(t: Float) {
    val c = Oc.c
    val bg = Color(0xFF0A0A0A)
    val cmd = "opencode"
    val typed = (seg(t, 0.05f, 0.3f) * cmd.length).toInt()
    val blink = ((t * 2300 / 450).toInt() % 2) == 0
    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.align(Alignment.Center).padding(horizontal = 20.dp), horizontalAlignment = Alignment.Start) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFF5C9CF5))) { append("~ ") }
                    withStyle(SpanStyle(color = Color(0xFF808080))) { append("$ ") }
                    withStyle(SpanStyle(color = Color(0xFFEEEEEE))) { append(cmd.take(typed)) }
                    if (t < 0.36f && blink) withStyle(SpanStyle(color = Color(0xFFFAB283))) { append("█") }
                },
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 15.sp,
            )
            Spacer(Modifier.height(26.dp))
            val scan = seg(t, 0.38f, 0.7f)
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val cols = tuiLeft[0].length * 2 + 1
                val cw = size.width / cols
                val hh = cw * 1.05f // половина клетки по высоте
                val y0 = (size.height - hh * 2 * 4) / 2
                val shadow = lerp(bg, Color(0xFF808080), 0.25f)
                fun line(rows: List<String>, x0: Int, fg: Color) {
                    rows.forEachIndexed { r, row ->
                        row.forEachIndexed { k, ch ->
                            val col = x0 + k
                            if (col.toFloat() / cols > scan) return@forEachIndexed
                            val x = col * cw; val y = y0 + r * hh * 2
                            fun top(col: Color) = drawRect(col, Offset(x, y), Size(cw + 0.5f, hh + 0.5f))
                            fun bottom(col: Color) = drawRect(col, Offset(x, y + hh), Size(cw + 0.5f, hh + 0.5f))
                            when (ch) {
                                '█' -> { top(fg); bottom(fg) }
                                '▀' -> top(fg)
                                '▄' -> bottom(fg)
                                '_' -> { top(shadow); bottom(shadow) }
                                '^' -> { top(fg); bottom(shadow) }
                                '~' -> top(shadow)
                            }
                        }
                    }
                }
                line(tuiLeft, 0, Color(0xFF808080))
                line(tuiRight, tuiLeft[0].length + 1, Color(0xFFEEEEEE))
                // курсор сканера
                if (scan in 0.01f..0.99f) drawRect(Color(0xFFFAB283), Offset(scan * size.width, y0), Size(cw * 0.6f, hh * 8))
            }
            Spacer(Modifier.height(22.dp))
            val info = seg(t, 0.72f, 0.86f)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFFFAB283))) { append("Build ") }
                    withStyle(SpanStyle(color = Color(0xFF808080))) { append("· starting the server on this phone…") }
                },
                Modifier.graphicsLayer { alpha = info; translationX = (1 - info) * -12.dp.toPx() },
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp,
            )
        }
    }
}

// ---------- Desktop: официальный знак обводится линией, заливается, нижний блок поднимается ----------

@Composable
private fun DesktopIntro(t: Float) {
    val c = Oc.c
    val light = Color(0xFFF1ECEC); val dark = Color(0xFF4B4646)
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(width = 96.dp, height = 120.dp)) {
                val w = size.width; val h = size.height
                val trace = seg(t, 0f, 0.4f)
                val outer = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(0f, h); close() }
                val pm = PathMeasure().apply { setPath(outer, false) }
                val part = Path(); pm.getSegment(0f, pm.length * trace, part, true)
                val sw = w * 0.04f
                drawPath(part, light, style = Stroke(sw))
                val fill = seg(t, 0.36f, 0.52f)
                // рамка 240×300 с окном 120×180 — пропорции официального знака
                if (fill > 0f) {
                    val frame = Path().apply {
                        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
                        addRect(androidx.compose.ui.geometry.Rect(w * 0.25f, h * 0.2f, w * 0.75f, h * 0.8f))
                    }
                    drawPath(frame, light.copy(alpha = fill))
                }
                val rise = seg(t, 0.48f, 0.64f)
                if (rise > 0f) {
                    val bh = h * 0.4f * rise
                    drawRect(dark, Offset(w * 0.25f, h * 0.8f - bh), Size(w * 0.5f, bh))
                }
            }
            Spacer(Modifier.height(26.dp))
            val k = seg(t, 0.62f, 0.8f)
            Row(Modifier.graphicsLayer { alpha = k; translationY = (1 - k) * 10.dp.toPx() }, verticalAlignment = Alignment.Bottom) {
                Text("OpenCode", fontFamily = Oc.heading, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = c.text)
                Spacer(Modifier.width(8.dp))
                Text("Desktop", fontFamily = Oc.heading, fontSize = 26.sp, color = c.textDim)
            }
            Spacer(Modifier.height(22.dp))
            // тонкая полоса загрузки, бегущий отрезок
            val run = seg(t, 0.7f, 0.76f)
            Canvas(Modifier.width(140.dp).height(2.dp).graphicsLayer { alpha = run }) {
                drawRect(c.border)
                val x = ((t - 0.7f) / 0.3f).coerceIn(0f, 1f)
                drawRect(c.text, Offset(size.width * (x * 1.3f - 0.3f).coerceAtLeast(0f), 0f), Size(size.width * 0.3f, size.height))
            }
        }
    }
}

// ---------- Hairline: линии 1px расчерчивают экран, синий квадрат, широкий заголовок выезжает из-под линии ----------

@Composable
private fun HairlineIntro(t: Float) {
    val c = Oc.c
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val line = c.border
            val px = 1.dp.toPx()
            // три горизонтали и вертикаль, каждая со своей задержкой
            listOf(0.3f, 0.5f, 0.7f).forEachIndexed { i, fy ->
                val k = seg(t, 0.02f + i * 0.07f, 0.3f + i * 0.07f)
                val y = size.height * fy
                if (i % 2 == 0) drawRect(line, Offset(0f, y), Size(size.width * k, px))
                else drawRect(line, Offset(size.width * (1 - k), y), Size(size.width * k, px))
            }
            val kv = seg(t, 0.12f, 0.42f)
            drawRect(line, Offset(24.dp.toPx(), 0f), Size(px, size.height * kv))
            // синий квадрат на пересечении
            val q = pop(t, 0.36f, 0.5f)
            val s = 10.dp.toPx() * q
            drawRect(c.accent, Offset(24.dp.toPx() - s / 2, size.height * 0.5f - s / 2), Size(s, s))
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 40.dp).offset(y = (-6).dp)) {
            val label = "TEXFI / OPENCODE"
            val n = (seg(t, 0.3f, 0.52f) * label.length).toInt()
            Text(label.take(n), fontFamily = Oc.label, fontSize = 11.sp, color = c.textDim)
            Spacer(Modifier.height(10.dp))
            RiseLine("Open", t, 0.46f, 0.64f, c.text)
            RiseLine("Code.", t, 0.56f, 0.74f, c.accent)
        }
        // счётчик в углу, как в TexFi
        val pct = (seg(t, 0f, 0.92f) * 100).toInt()
        Text("%03d".format(pct), Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 40.dp), fontFamily = Oc.label, fontSize = 11.sp, color = c.textDim)
    }
}

/** Строка выезжает снизу из-под невидимой кромки. */
@Composable
private fun RiseLine(text: String, t: Float, a: Float, b: Float, color: Color) {
    val k = seg(t, a, b)
    Box(Modifier.clipToBounds()) {
        Text(
            text, Modifier.graphicsLayer { translationY = (1 - k) * 46.dp.toPx() },
            fontFamily = Oc.heading, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, color = color,
        )
    }
}

// ---------- ChatGPT: точка дышит, раскрывается в кольцо, под ним название ----------

@Composable
private fun ChatGptIntro(t: Float) {
    val c = Oc.c
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(96.dp)) {
                val cx = size.width / 2; val cy = size.height / 2
                val R = size.minDimension * 0.38f
                val appear = pop(t, 0f, 0.16f)
                // два вдоха
                val breathe = if (t in 0.16f..0.5f) 1f + 0.18f * sin(((t - 0.16f) / 0.34f) * 2 * PI.toFloat() * 2).coerceAtLeast(0f) else 1f
                val open = seg(t, 0.5f, 0.72f)
                val dot = size.minDimension * 0.13f
                // точка растёт в диск, диск становится кольцом
                val r = (dot + (R - dot) * open) * appear * breathe
                val sw = size.minDimension * 0.11f
                if (open < 1f) drawCircle(c.text, r, Offset(cx, cy), alpha = 1f - open)
                if (open > 0f) drawCircle(c.text, R, Offset(cx, cy), style = Stroke(sw * open))
                if (open > 0.4f) drawCircle(c.text, dot * seg(t, 0.62f, 0.76f), Offset(cx, cy))
            }
            Spacer(Modifier.height(24.dp))
            val k = seg(t, 0.72f, 0.88f)
            Text("OpenCode", Modifier.graphicsLayer { alpha = k; translationY = (1 - k) * 8.dp.toPx() }, fontFamily = Oc.heading, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = c.text)
        }
    }
}

// ---------- Kimi: маскот падает и пружинит, моргает, оглядывается, здоровается ----------

@Composable
private fun KimiIntro(t: Float) {
    val c = Oc.c
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(120.dp)) {
                val s = size.minDimension * 0.66f
                // падение с отскоком: 0..0.22 падает, 0.22..0.42 два затухающих подскока
                val fall = seg(t, 0f, 0.22f)
                val bounce = if (t > 0.22f) {
                    val k = ((t - 0.22f) / 0.2f).coerceIn(0f, 1f)
                    kotlin.math.abs(sin(k * PI.toFloat() * 2)) * (1 - k) * 0.22f
                } else 0f
                val y = -size.height * 1.4f * (1 - fall) - s * bounce
                // сплющивание в момент касания
                val hit = if (t in 0.2f..0.3f) sin(((t - 0.2f) / 0.1f) * PI.toFloat()) else 0f
                val sx = 1f + 0.18f * hit; val sy = 1f - 0.16f * hit
                val w = s * sx; val h = s * sy
                val l = (size.width - w) / 2; val top = size.height - h - size.height * 0.08f + y
                drawRoundRect(c.accent, Offset(l, top), Size(w, h), CornerRadius(w * 0.42f))
                // глаза: моргнуть и посмотреть по сторонам
                val blink = if (t in 0.5f..0.56f) 1f - sin(((t - 0.5f) / 0.06f) * PI.toFloat()) * 0.9f else 1f
                val look = if (t > 0.58f) sin(((t - 0.58f) / 0.3f).coerceIn(0f, 1f) * PI.toFloat() * 2) else 0f
                val ew = w * 0.13f; val eh = h * 0.3f * blink
                val cy = top + h * 0.47f
                listOf(0.36f, 0.64f).forEach { fx ->
                    drawRoundRect(Color.White, Offset(l + w * fx + look * w * 0.07f - ew / 2, cy - eh / 2), Size(ew, eh), CornerRadius(ew / 2))
                }
            }
            Spacer(Modifier.height(18.dp))
            val hi = "Hi, I'm OpenCode"
            val n = (seg(t, 0.6f, 0.86f) * hi.length).toInt()
            Text(hi.take(n), fontFamily = Oc.heading, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = c.text)
        }
    }
}

// ---------- Notebook: название пишется от руки, перо идёт за строкой, затем волнистая черта и дата ----------

@Composable
private fun NotebookIntro(t: Float) {
    val c = Oc.c
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            // тетрадные линейки проявляются сверху вниз
            val step = 34.dp.toPx()
            var y = step * 3; var i = 0
            while (y < size.height) {
                val k = seg(t, 0.02f * i, 0.2f + 0.02f * i)
                drawRect(c.border.copy(alpha = 0.55f * k), Offset(0f, y), Size(size.width, 1f))
                y += step; i++
            }
            drawRect(c.accent.copy(alpha = 0.3f * seg(t, 0f, 0.25f)), Offset(12.dp.toPx(), 0f), Size(1.dp.toPx(), size.height * seg(t, 0f, 0.35f)))
        }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 32.dp)) {
            val write = seg(t, 0.12f, 0.62f)
            Box {
                Text(
                    "OpenCode", Modifier.drawWithReveal(write, c.accent),
                    fontFamily = Oc.heading, fontStyle = FontStyle.Italic, fontSize = 52.sp, color = c.text,
                )
            }
            Canvas(Modifier.width(220.dp).height(14.dp)) {
                val k = seg(t, 0.6f, 0.78f)
                val path = Path(); var x = 0f
                path.moveTo(0f, size.height / 2)
                while (x < size.width * k) { x += 3f; path.lineTo(x, size.height / 2 + sin(x / 11f) * size.height * 0.28f) }
                drawPath(path, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            Spacer(Modifier.height(10.dp))
            val d = seg(t, 0.74f, 0.9f)
            Text(
                java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.ENGLISH).format(java.util.Date()),
                Modifier.graphicsLayer { alpha = d }, fontFamily = Oc.reply, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = c.textDim,
            )
        }
    }
}

/** Текст открывается слева направо, на краю — перо (чернильная точка с наклонным штрихом). */
private fun Modifier.drawWithReveal(k: Float, ink: Color): Modifier = this.drawWithContent {
    val x = size.width * k
    clipRect(right = x) { this@drawWithContent.drawContent() }
    if (k in 0.001f..0.999f) {
        drawLine(ink, Offset(x, size.height * 0.78f), Offset(x + size.height * 0.28f, size.height * 0.2f), 3.dp.toPx(), StrokeCap.Round)
        drawCircle(ink, 3.dp.toPx(), Offset(x, size.height * 0.78f))
    }
}
