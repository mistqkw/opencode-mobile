package app.ocmobile.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.ui.components.CH
import app.ocmobile.ui.components.CW
import app.ocmobile.ui.components.CloudMood
import app.ocmobile.ui.components.INK
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.drawIconCloud
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/*
 * More games with the mascot, next to ping-pong: catch its rain, find it under a cup, repeat what it
 * shows. The cloud only moves, looks and changes its eyes (never grows or changes shape). A finger
 * on the phone and the mouse on the computer play the same way; the computer has the same file.
 */

private val DROP = Color(0xFF9F9DF2)

/** The cloud in the right colors for the theme. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.cloud(x: Float, y: Float, w: Float, mood: CloudMood, look: Float, dark: Boolean, bg: Color) =
    drawIconCloud(x, y, w, mood, look.coerceIn(-0.035f, 0.035f), 0f, if (dark) bg else INK, eyeDy = 0.05f, outline = if (dark) null else INK)

/** Where the finger or mouse is, as a share of the width, while it's over the field (pressed or not). */
private fun Modifier.trackX(onX: (Float) -> Unit) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            if (e.type == PointerEventType.Move || e.type == PointerEventType.Press) e.changes.firstOrNull()?.let { onX((it.position.x / size.width).coerceIn(0f, 1f)); it.consume() }
        }
    }
}

/**
 * Catch the rain: the cloud drifts along the top and lets drops fall; you move the cup along the
 * bottom. Each catch is a point and the rain gets faster; three missed drops end the round.
 */
@Composable
fun RainGame() {
    val c = Oc.c
    var w by remember { mutableFloatStateOf(0f) }; var h by remember { mutableFloatStateOf(0f) }
    var cup by remember { mutableFloatStateOf(0.5f) }
    var cloudX by remember { mutableFloatStateOf(0.5f) }
    val drops = remember { mutableStateListOf<Offset>() }   // x as a share, y in px
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var missed by remember { mutableIntStateOf(0) }
    var mood by remember { mutableStateOf(CloudMood.Idle) }
    var over by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(w, over) {
        if (w == 0f || over) return@LaunchedEffect
        var last = 0L; var t = 0f; var next = 0.8f
        while (!over) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.033f)
            last = now; t += dt
            val speed = 1f + score * 0.06f
            // it drifts in a lazy wave and drops rain where it is
            cloudX = 0.5f + 0.38f * sin(t * (0.7f + score * 0.02f)) * (0.6f + 0.4f * sin(t * 0.37f))
            next -= dt
            if (next <= 0f) { drops += Offset(cloudX + (Math.random().toFloat() - 0.5f) * 0.08f, h * 0.17f); next = (0.9f / speed).coerceAtLeast(0.28f) }
            val fall = h * 0.42f * speed * dt
            val cupY = h - h * 0.12f
            val keep = ArrayList<Offset>(drops.size)
            for (d in drops.toList()) {
                val y = d.y + fall
                when {
                    y >= cupY && y < cupY + fall + 4f && abs(d.x - cup) < 0.09f -> { score++; mood = CloudMood.Happy; if (score > best) best = score }
                    y > h -> { missed++; mood = CloudMood.Surprised; if (missed >= 3) over = true }
                    else -> keep += Offset(d.x, y)
                }
            }
            drops.clear(); drops.addAll(keep)
            tick++
        }
    }
    LaunchedEffect(mood) { if (mood != CloudMood.Idle) { delay(500); mood = CloudMood.Idle } }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { w = it.width.toFloat(); h = it.height.toFloat() }.trackX { cup = it.coerceIn(0.08f, 0.92f) }) {
            tick
            if (w == 0f) return@Canvas
            val cw = size.width * 0.26f
            cloud(cloudX * size.width - cw / 2, size.height * 0.04f, cw, mood, (cup - cloudX) * 0.06f, c.isDark, c.bg)
            val r = 5.dp.toPx()
            drops.forEach { d -> drawRoundRect(DROP, Offset(d.x * size.width - r, d.y - r * 1.6f), Size(r * 2, r * 3.2f), CornerRadius(r)) }
            // the cup: three lines, open at the top
            val cupW = size.width * 0.18f; val cupH = 26.dp.toPx(); val cx = cup * size.width; val cy = size.height - size.height * 0.12f
            val s = Stroke(2.dp.toPx())
            drawLine(c.text, Offset(cx - cupW / 2, cy), Offset(cx - cupW / 2 + 8f, cy + cupH), s.width)
            drawLine(c.text, Offset(cx + cupW / 2, cy), Offset(cx + cupW / 2 - 8f, cy + cupH), s.width)
            drawLine(c.text, Offset(cx - cupW / 2 + 8f, cy + cupH), Offset(cx + cupW / 2 - 8f, cy + cupH), s.width)
        }
        Txt("$score", Modifier.align(Alignment.TopStart).padding(20.dp), size = 28.sp)
        Txt("best $best  ·  ${"●".repeat((3 - missed).coerceAtLeast(0))}${"○".repeat(missed.coerceAtMost(3))}", Modifier.align(Alignment.TopEnd).padding(24.dp), size = 13.sp, color = c.textDim)
        if (over) Box(Modifier.align(Alignment.Center).padding(24.dp)) {
            app.ocmobile.ui.components.Pill("Again", { drops.clear(); score = 0; missed = 0; over = false }, filled = true)
        }
    }
}

/**
 * Cups: the cloud hides under one of three cups, they swap places, you tap the one it's under.
 * Every right guess adds swaps and speed; a wrong one starts over.
 */
@Composable
fun CupsGame() {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    // slot of each cup (0..2) as an animated position; cup 0 hides the cloud
    val pos = remember { List(3) { Animatable(it.toFloat()) } }
    val slot = remember { IntArray(3) { it } }      // where each cup is, whole numbers; pos only draws it
    val lift = remember { Animatable(1f) }      // 1 = cups up, the cloud is seen
    var level by remember { mutableIntStateOf(1) }
    var best by remember { mutableIntStateOf(1) }
    var phase by remember { mutableStateOf("ready") }   // ready, shuffling, guess, shown
    var line by remember { mutableStateOf("Watch where I hide. Tap to start.") }
    var mood by remember { mutableStateOf(CloudMood.Idle) }
    var picked by remember { mutableIntStateOf(-1) }

    fun round() = scope.launch {
        phase = "shuffling"; picked = -1; mood = CloudMood.Wink; line = "Watch closely…"
        lift.animateTo(0f, tween(260)); delay(200)
        val swaps = 3 + level * 2
        val ms = (420 - level * 30).coerceAtLeast(150)
        repeat(swaps) {
            val a = (0..2).random(); var b = (0..2).random(); while (b == a) b = (0..2).random()
            val ca = slot.indexOf(a); val cb = slot.indexOf(b)
            slot[ca] = b; slot[cb] = a
            kotlinx.coroutines.coroutineScope {
                launch { pos[ca].animateTo(b.toFloat(), tween(ms)) }
                launch { pos[cb].animateTo(a.toFloat(), tween(ms)) }
            }
        }
        phase = "guess"; line = "Where am I?"
    }

    fun guess(at: Int) = scope.launch {
        val cupAt = slot.indexOf(at)
        picked = cupAt
        phase = "shown"
        lift.animateTo(1f, tween(220))
        if (cupAt == 0) { level++; if (level > best) best = level; mood = CloudMood.Happy; line = "You found me! Faster now." }
        else { level = 1; mood = CloudMood.Angry; line = "Nope, I was over here." }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.type != PointerEventType.Press) continue
                    val x = e.changes.first().position.x / size.width
                    when (phase) {
                        "ready", "shown" -> round()
                        // taken at once, so a second quick tap can't guess twice
                        "guess" -> { phase = "shown"; guess((x * 3).toInt().coerceIn(0, 2)) }
                    }
                }
            }
        }) {
            val slotW = size.width / 3
            val cupW = slotW * 0.62f; val cupH = cupW * 0.8f
            val floor = size.height * 0.62f
            // the cloud, small, under cup 0
            val cloudW = cupW * 0.8f
            val cx = (pos[0].value + 0.5f) * slotW
            cloud(cx - cloudW / 2, floor - cloudW * CH / CW, cloudW, mood, 0f, c.isDark, c.bg)
            pos.forEachIndexed { i, p ->
                val x = (p.value + 0.5f) * slotW - cupW / 2
                val y = floor - cupH - lift.value * cupH * 0.9f
                val col = if (phase == "shown" && i == picked) (if (i == 0) c.accent else c.danger) else c.text
                drawRoundRect(c.surface, Offset(x, y), Size(cupW, cupH), CornerRadius(14f))
                drawRoundRect(col, Offset(x, y), Size(cupW, cupH), CornerRadius(14f), style = Stroke(2.dp.toPx()))
            }
            drawLine(c.border, Offset(0f, floor + 2f), Offset(size.width, floor + 2f), 1.dp.toPx())
        }
        Txt("level $level  ·  best $best", Modifier.align(Alignment.TopEnd).padding(24.dp), size = 13.sp, color = c.textDim)
        Txt(line, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp), size = 16.sp, color = c.textDim)
    }
}

/**
 * Echo: four pads light up in a row the cloud shows (its eyes look at each one); repeat it. Each
 * round adds one more step.
 */
@Composable
fun EchoGame() {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val seq = remember { mutableStateListOf<Int>() }
    var lit by remember { mutableIntStateOf(-1) }
    var at by remember { mutableIntStateOf(0) }
    var showing by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(0) }
    var mood by remember { mutableStateOf(CloudMood.Idle) }
    var line by remember { mutableStateOf("Tap to start, then repeat after me.") }
    val pads = listOf(Color(0xFF9F9DF2), Color(0xFF7CC6A4), Color(0xFFF2B36D), Color(0xFFE88A9A))

    fun show() = scope.launch {
        showing = true; at = 0; lit = -1; line = "Watch…"; mood = CloudMood.Idle
        seq += (0..3).random()
        delay(500)
        val ms = (520 - seq.size * 20L).coerceAtLeast(240)
        for (p in seq) { lit = p; delay(ms); lit = -1; delay(120) }
        showing = false; line = "Your turn"
    }
    fun press(p: Int) = scope.launch {
        if (showing) return@launch
        if (seq.isEmpty()) { show(); return@launch }
        if (at >= seq.size) return@launch
        lit = p; delay(160); lit = -1
        if (seq[at] == p) {
            at++
            if (at == seq.size) { showing = true; if (seq.size > best) best = seq.size; mood = CloudMood.Happy; line = "Yes! ${seq.size} in a row"; delay(600); show() }
        } else { mood = CloudMood.Dizzy; line = "Oops. That was ${seq.size}. Tap to try again."; seq.clear() }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.type != PointerEventType.Press) continue
                    val pt = e.changes.first().position
                    val top = size.height * 0.36f
                    if (pt.y < top) { if (seq.isEmpty() && !showing) show(); continue }
                    val col = if (pt.x < size.width / 2) 0 else 1
                    val row = if (pt.y < top + (size.height - top) / 2) 0 else 1
                    press(row * 2 + col)
                }
            }
        }) {
            val top = size.height * 0.36f
            val cw = size.width * 0.32f
            // the eyes look toward the lit pad
            val look = when (lit) { 0, 2 -> -0.03f; 1, 3 -> 0.03f; else -> 0f }
            cloud(size.width / 2 - cw / 2, top * 0.32f, cw, mood, look, c.isDark, c.bg)
            val gap = 12.dp.toPx(); val m = 24.dp.toPx()
            val pw = (size.width - m * 2 - gap) / 2; val ph = (size.height - top - m * 2 - gap) / 2
            pads.forEachIndexed { i, col ->
                val x = m + (i % 2) * (pw + gap); val y = top + m + (i / 2) * (ph + gap)
                drawRoundRect(col.copy(alpha = if (lit == i) 1f else 0.22f), Offset(x, y), Size(pw, ph), CornerRadius(24f))
            }
        }
        Txt("best $best", Modifier.align(Alignment.TopEnd).padding(24.dp), size = 13.sp, color = c.textDim)
        Txt(line, Modifier.align(Alignment.TopCenter).padding(top = 8.dp), size = 15.sp, color = c.textDim)
    }
}
