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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.rotate
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
    if (lookThinking(work, modifier)) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WorkGlyph(work, 22.dp)
        Spacer(Modifier.width(10.dp))
        AnimatedContent(
            work.label, label = "thinking-label",
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 }) togetherWith (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 }) },
        ) { text -> ShimmerText(text) }
    }
}

@Composable
fun WorkGlyph(work: Work, size: Dp) {
    // «Думаю» каждый раз разное: шар, спираль, тор или созвездие
    val think = remember { kotlin.random.Random.nextInt(4) }
    when (work) {
        Work.Think, Work.Other -> when (think) { 0 -> OrbGlyph(size); 1 -> HelixGlyph(size); 2 -> TorusGlyph(size); else -> ConstellationGlyph(size) }
        Work.Write -> PenGlyph(size)
        Work.Read -> BookGlyph(size)
        Work.Edit -> FlipCardGlyph(size)
        Work.Run -> CubeGlyph(size)
        Work.Search -> LensGlyph(size)
        Work.Web -> GlobeGlyph(size)
        Work.Plan -> ChecklistGlyph(size)
        Work.Wait -> HourglassGlyph(size)
    }
}

// ---------- общие помощники 3D ----------

private class P3(val x: Float, val y: Float, val z: Float)

/** Поворот вокруг Y на [ay], затем вокруг X на [ax]. */
private fun P3.rot(ay: Float, ax: Float): P3 {
    val x1 = x * kotlin.math.cos(ay) + z * sin(ay)
    val z1 = -x * sin(ay) + z * kotlin.math.cos(ay)
    val y1 = y * kotlin.math.cos(ax) - z1 * sin(ax)
    val z2 = y * sin(ax) + z1 * kotlin.math.cos(ax)
    return P3(x1, y1, z2)
}

@Composable
private fun phase(ms: Int, label: String): Float {
    val t = rememberInfiniteTransition(label = label)
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = label)
    return ph
}

private val TAU = (2 * PI).toFloat()

/** Точки в 3D: ближние крупнее и ярче. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.dots3d(pts: List<P3>, color: Color, scale: Float, dot: Float) {
    val cx = size.width / 2; val cy = size.height / 2
    pts.sortedBy { it.z }.forEach { p ->
        val d = ((p.z + 1f) / 2f).coerceIn(0f, 1f)
        drawCircle(color.copy(alpha = 0.25f + 0.75f * d), dot * (0.65f + 0.6f * d), Offset(cx + p.x * scale, cy + p.y * scale))
    }
}

/** Спираль ДНК: две нити точек крутятся вокруг вертикальной оси. */
@Composable
private fun HelixGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(2600, "helix")
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension
        val pts = ArrayList<P3>()
        for (i in 0 until 9) {
            val y = -1f + 2f * i / 8f
            val a = ph * TAU + i * 0.7f
            pts += P3(kotlin.math.cos(a) * 0.6f, y, sin(a) * 0.6f)
            pts += P3(kotlin.math.cos(a + PI.toFloat()) * 0.6f, y, sin(a + PI.toFloat()) * 0.6f)
        }
        dots3d(pts.map { it.rot(0f, 0.25f) }, c.accent, w * 0.42f, w * 0.055f)
    }
}

/** Тор: кольцо точек наклонено и вращается, по нему бежит волна толщины. */
@Composable
private fun TorusGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(3000, "torus")
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension
        val n = 16
        val pts = List(n) { i ->
            val a = i * TAU / n
            val r = 0.85f + 0.12f * sin(a * 3 - ph * TAU * 2)
            P3(kotlin.math.cos(a) * r, 0f, sin(a) * r).rot(ph * TAU, 0.9f + 0.3f * sin(ph * TAU))
        }
        dots3d(pts, c.accent, w * 0.44f, w * 0.06f)
    }
}

/** Созвездие: точки дрейфуют, ближайшие соединяются тонкими линиями. */
@Composable
private fun ConstellationGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(4200, "stars")
    val seeds = remember { List(7) { floatArrayOf(kotlin.random.Random.nextFloat() * TAU, 0.35f + kotlin.random.Random.nextFloat() * 0.55f, 0.6f + kotlin.random.Random.nextFloat() * 0.8f) } }
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension; val cx = this.size.width / 2; val cy = this.size.height / 2
        val ps = seeds.map { (a0, r, sp) -> val a = a0 + ph * TAU * sp; Offset(cx + kotlin.math.cos(a) * r * w * 0.42f, cy + sin(a * 1.3f) * r * w * 0.42f) }
        for (i in ps.indices) for (j in i + 1 until ps.size) {
            val d = (ps[i] - ps[j]).getDistance() / w
            if (d < 0.45f) drawLine(c.accent.copy(alpha = (1f - d / 0.45f) * 0.7f), ps[i], ps[j], w * 0.035f, StrokeCap.Round)
        }
        ps.forEach { drawCircle(c.accent, w * 0.055f, it) }
    }
}

/** Книга: страница переворачивается слева направо (сжатие по ширине), строки на страницах. */
@Composable
private fun BookGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(1500, "book")
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height; val sw = w * 0.07f
        val cx = w / 2; val top = h * 0.22f; val bot = h * 0.8f
        // корешок и две страницы
        drawLine(c.textDim, Offset(cx, top), Offset(cx, bot), sw * 0.8f, StrokeCap.Round)
        for (i in 0 until 3) {
            val y = top + (bot - top) * (0.25f + 0.25f * i)
            drawLine(c.textDim.copy(alpha = 0.6f), Offset(w * 0.1f, y), Offset(cx - w * 0.08f, y), sw * 0.7f, StrokeCap.Round)
            drawLine(c.textDim.copy(alpha = 0.6f), Offset(cx + w * 0.08f, y), Offset(w * 0.9f, y), sw * 0.7f, StrokeCap.Round)
        }
        // переворачиваемый лист: ширина = cos угла
        val k = kotlin.math.cos(ease(ph) * PI.toFloat())
        val edge = cx + (w * 0.42f) * k
        drawLine(c.accent, Offset(cx, top), Offset(edge, top + (bot - top) * 0.04f * (1 - kotlin.math.abs(k))), sw, StrokeCap.Round)
        drawLine(c.accent, Offset(cx, bot), Offset(edge, bot - (bot - top) * 0.04f * (1 - kotlin.math.abs(k))), sw, StrokeCap.Round)
        drawLine(c.accent, Offset(edge, top), Offset(edge, bot), sw, StrokeCap.Round)
    }
}

/** Правка: карточка с кодом переворачивается в 3D, на обороте строки уже другие. */
@Composable
private fun FlipCardGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(1800, "flip")
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height; val sw = w * 0.075f
        val a = ease(ph) * PI.toFloat()
        val k = kotlin.math.cos(a)
        val half = w * 0.36f * kotlin.math.abs(k)
        val cx = w / 2
        val front = k > 0
        val left = cx - half; val right = cx + half
        drawRoundRect(if (front) c.textDim else c.accent, Offset(left, h * 0.16f), Size(right - left, h * 0.68f), CornerRadius(w * 0.08f), style = Stroke(sw))
        val lens = if (front) listOf(0.8f, 0.55f, 0.7f) else listOf(0.6f, 0.85f, 0.45f)
        lens.forEachIndexed { i, l ->
            val y = h * (0.33f + 0.17f * i)
            val x0 = left + (right - left) * 0.18f
            drawLine((if (front) c.textDim else c.accent).copy(alpha = 0.8f), Offset(x0, y), Offset(x0 + (right - left) * 0.64f * l, y), sw * 0.8f, StrokeCap.Round)
        }
    }
}

/** Команда: каркасный куб вращается, в вершинах — точки. */
@Composable
private fun CubeGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(2800, "cube")
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension; val cx = this.size.width / 2; val cy = this.size.height / 2; val s = w * 0.27f
        val v = listOf(-1f, 1f).flatMap { x -> listOf(-1f, 1f).flatMap { y -> listOf(-1f, 1f).map { z -> P3(x, y, z).rot(ph * TAU, 0.6f) } } }
        fun o(p: P3) = Offset(cx + p.x * s, cy + p.y * s)
        for (i in v.indices) for (j in i + 1 until v.size) {
            val bits = i xor j
            if (bits == 1 || bits == 2 || bits == 4) {
                val d = ((v[i].z + v[j].z) / 2 + 1.7f) / 3.4f
                drawLine(c.accent.copy(alpha = 0.2f + 0.6f * d), o(v[i]), o(v[j]), w * 0.04f, StrokeCap.Round)
            }
        }
        dots3d(v.map { P3(it.x / 1.7f, it.y / 1.7f, it.z / 1.7f) }, c.accent, s * 1.7f, w * 0.05f)
    }
}

/** Поиск: линза скользит по сетке точек, точки под ней растут. */
@Composable
private fun LensGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(2200, "lens")
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension
        val lx = w * (0.5f + 0.26f * kotlin.math.cos(ph * TAU)); val ly = w * (0.5f + 0.2f * sin(ph * TAU * 2))
        val lr = w * 0.22f
        for (gx in 0 until 4) for (gy in 0 until 4) {
            val p = Offset(w * (0.17f + 0.22f * gx), w * (0.17f + 0.22f * gy))
            val near = (1f - ((p - Offset(lx, ly)).getDistance() / lr)).coerceIn(0f, 1f)
            drawCircle(if (near > 0f) c.accent else c.textDim.copy(alpha = 0.45f), w * (0.03f + 0.05f * near), p)
        }
        drawCircle(c.text.copy(alpha = 0.8f), lr, Offset(lx, ly), style = Stroke(w * 0.05f))
    }
}

/** Интернет: глобус из меридианов вращается, по экватору бежит точка. */
@Composable
private fun GlobeGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(3600, "globe")
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension; val cx = this.size.width / 2; val cy = this.size.height / 2; val r = w * 0.4f
        drawCircle(c.textDim.copy(alpha = 0.6f), r, Offset(cx, cy), style = Stroke(w * 0.035f))
        for (m in 0 until 3) {
            val k = kotlin.math.cos(ph * TAU + m * PI.toFloat() / 3)
            val rx = kotlin.math.abs(k) * r
            if (rx > 0.5f) drawOval(c.accent.copy(alpha = 0.35f + 0.5f * kotlin.math.abs(k)), Offset(cx - rx, cy - r), Size(rx * 2, r * 2), style = Stroke(w * 0.035f))
        }
        drawLine(c.textDim.copy(alpha = 0.6f), Offset(cx - r, cy), Offset(cx + r, cy), w * 0.035f)
        val a = ph * TAU * 2
        val z = sin(a)
        drawCircle(c.accent.copy(alpha = 0.4f + 0.6f * ((z + 1) / 2)), w * (0.05f + 0.03f * ((z + 1) / 2)), Offset(cx + kotlin.math.cos(a) * r, cy))
    }
}

/** План: точки по очереди падают в столбик, за каждой вырастает строка. */
@Composable
private fun ChecklistGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(2100, "plan")
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        for (i in 0 until 3) {
            val k = ease((ph * 4f - i).coerceIn(0f, 1f))
            val fade = if (ph > 0.88f) 1f - (ph - 0.88f) / 0.12f else 1f
            val y = h * (0.24f + 0.26f * i)
            drawCircle(c.accent.copy(alpha = fade), w * 0.07f, Offset(w * 0.18f, y - (1f - k) * h * 0.3f))
            drawLine(c.textDim.copy(alpha = 0.7f * k * fade), Offset(w * 0.36f, y), Offset(w * 0.36f + w * 0.5f * k, y), w * 0.07f, StrokeCap.Round)
        }
    }
}

/** Ожидание: песочные часы из точек, песок пересыпается, часы переворачиваются. */
@Composable
private fun HourglassGlyph(size: Dp) {
    val c = Oc.c; val ph = phase(3200, "glass")
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val flip = ph > 0.85f
        rotate(if (flip) ease((ph - 0.85f) / 0.15f) * 180f else 0f) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.25f, h * 0.12f); lineTo(w * 0.75f, h * 0.12f); lineTo(w * 0.52f, h * 0.5f)
                lineTo(w * 0.75f, h * 0.88f); lineTo(w * 0.25f, h * 0.88f); lineTo(w * 0.48f, h * 0.5f); close()
            }
            drawPath(p, c.textDim.copy(alpha = 0.7f), style = Stroke(w * 0.05f, join = StrokeJoin.Round))
            val k = (ph / 0.85f).coerceAtMost(1f)
            val top = ((1f - k) * 6).toInt(); val bottom = 6 - top
            for (i in 0 until top) drawCircle(c.accent, w * 0.045f, Offset(w * (0.4f + 0.1f * (i % 3)), h * (0.28f - 0.08f * (i / 3))))
            for (i in 0 until bottom) drawCircle(c.accent, w * 0.045f, Offset(w * (0.4f + 0.1f * (i % 3)), h * (0.78f - 0.08f * (i / 3))))
            if (k < 1f) drawCircle(c.accent, w * 0.035f, Offset(w * 0.5f, h * (0.5f + 0.25f * ((ph * 9f) % 1f))))
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
    val x by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val w = 260f
    Text(
        text,
        style = TextStyle(
            // как в Claude: подпись работы — с засечками (в своих редакциях — их шрифт)
            fontSize = 15.sp, fontFamily = Oc.reply,
            brush = Brush.linearGradient(
                0f to c.textDim, 0.5f to c.text, 1f to c.textDim,
                start = Offset(x * w - w / 2, 0f), end = Offset(x * w + w / 2, 0f),
            ),
        ),
    )
}


/**
 * «Думаю»: шар из точек вращается в 3D, затем стягивается в четыре точки, они расходятся
 * и кружат по наклонной орбите, потом снова собираются в шар. Плоский цвет, без свечения.
 */
@Composable
fun OrbGlyph(size: Dp) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "orb")
    val time by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6400, easing = LinearEasing)), label = "time")
    val accent = c.accent
    val pts = remember {
        // точки по сфере (спираль Фибоначчи)
        val n = 34
        List(n) { i ->
            val y = 1f - 2f * (i + 0.5f) / n
            val r = kotlin.math.sqrt(1f - y * y)
            val a = i * 2.39996f
            floatArrayOf(kotlin.math.cos(a) * r, y, sin(a) * r)
        }
    }
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension
        val cx = this.size.width / 2; val cy = this.size.height / 2
        val R = w * 0.4f
        // 0..0.4 шар, 0.4..0.5 сжатие, 0.5..0.9 четыре точки, 0.9..1 обратно в шар
        val m = when {
            time < 0.4f -> 0f
            time < 0.5f -> ease((time - 0.4f) / 0.1f)
            time < 0.9f -> 1f
            else -> 1f - ease((time - 0.9f) / 0.1f)
        }
        val spin = time * 2 * PI.toFloat() * 3f
        val tilt = 0.45f
        val orbit = time * 2 * PI.toFloat() * 4f
        val items = pts.mapIndexed { i, p ->
            // поворот шара вокруг Y и наклон по X
            val x1 = p[0] * kotlin.math.cos(spin) + p[2] * sin(spin)
            val z1 = -p[0] * sin(spin) + p[2] * kotlin.math.cos(spin)
            val y1 = p[1] * kotlin.math.cos(tilt) - z1 * sin(tilt)
            val z2 = p[1] * sin(tilt) + z1 * kotlin.math.cos(tilt)
            // цель: одна из четырёх точек на наклонной орбите
            val k = i % 4
            val oa = orbit + k * PI.toFloat() / 2
            val ox = kotlin.math.cos(oa) * 0.85f
            val oz = sin(oa) * 0.85f
            val oy = -oz * 0.45f
            val x = x1 + (ox - x1) * m
            val y = y1 + (oy - y1) * m
            val z = z2 + (oz - z2) * m
            Triple(x, y, z)
        }.sortedBy { it.third }
        items.forEach { (x, y, z) ->
            val depth = (z + 1f) / 2f
            val rad = w * (0.035f + 0.03f * depth) * (1f + 0.9f * m)
            drawCircle(accent.copy(alpha = 0.3f + 0.7f * depth), rad, Offset(cx + x * R, cy + y * R))
        }
    }
}

private fun ease(x: Float): Float { val k = x.coerceIn(0f, 1f); return k * k * (3 - 2 * k) }

/** «Пишу»: перо выводит волнистую строку, строка растёт за ним, потом исчезает и всё сначала. */
@Composable
fun PenGlyph(size: Dp) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "pen")
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ph")
    val accent = c.accent
    val ink = c.text
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = w * 0.08f
        val prog = (ph / 0.8f).coerceAtMost(1f)
        val fade = if (ph > 0.8f) 1f - (ph - 0.8f) / 0.2f else 1f
        fun py(x: Float) = h * 0.78f + sin((x / w) * 4.5f * PI.toFloat()) * h * 0.07f
        // чернильный след
        val path = androidx.compose.ui.graphics.Path()
        val x0 = w * 0.04f; val x1 = x0 + (w * 0.52f) * prog
        path.moveTo(x0, py(x0))
        var x = x0
        while (x < x1) { x += w * 0.02f; path.lineTo(x.coerceAtMost(x1), py(x.coerceAtMost(x1))) }
        drawPath(path, ink.copy(alpha = 0.55f * fade), style = Stroke(sw * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // перо: наклонный корпус и кончик в конце следа
        val tipX = x1; val tipY = py(x1) - h * 0.02f
        val ang = -0.8f
        val len = h * 0.62f
        val bx = tipX + kotlin.math.cos(ang) * len * 0.0f + len * 0.42f
        val by = tipY - len * 0.86f
        val nib = androidx.compose.ui.graphics.Path().apply {
            moveTo(tipX, tipY)
            lineTo(tipX + w * 0.12f, tipY - h * 0.2f)
            lineTo(tipX + w * 0.04f, tipY - h * 0.25f)
            close()
        }
        drawPath(nib, accent)
        drawLine(accent, Offset(tipX + w * 0.08f, tipY - h * 0.22f), Offset(bx, by), sw * 1.3f, StrokeCap.Round)
    }
}
