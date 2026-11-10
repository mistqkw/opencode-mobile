package app.ocmobile.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The cl0udly look. The mascot is the cloud from the official icon (the author's drawing): a white body,
 * grey steps, two tall eyes. The sky above the bar is a still pixel ridge
 * in blue tones, dense on the left and thinning to the right, darkened toward the background.
 */

// ---------- mascot ----------

// icon geometry in its original units (cloud 748 × 428)
internal const val CW = 748f
internal const val CH = 428f
private val WHITE = Color(0xFFFFFFFF)
private val GREY = Color(0xFFBABABC)
internal val INK = Color(0xFF18181A)

enum class CloudMood { Idle, Happy, Angry, Sleep, Love, Wink, Surprised, Dizzy, Drowsy }

/**
 * A cloud in a box [w] wide (height by proportion), top left corner at [x], [y].
 * [eyeDx] is a sideways glance (share of width), [blink] 0..1 how closed the eyes are.
 */
internal fun DrawScope.drawIconCloud(
    x: Float, y: Float, w: Float, mood: CloudMood, eyeDx: Float, blink: Float, eye: Color,
    eyeDy: Float = 0f, outline: Color? = null,
) {
    val k = w / CW
    // start on a whole pixel and size the blocks from it: when moving, the cloud shifts as a whole
    // instead of rearranging a pixel here and there
    val x0r = x.roundToInt(); val y0r = y.roundToInt()
    fun sx(v: Float) = (x0r + (v * k).roundToInt()).toFloat()
    fun sy(v: Float) = (y0r + (v * k).roundToInt()).toFloat()
    fun r(x0: Float, y0: Float, x1: Float, y1: Float, col: Color) =
        drawRect(col, Offset(sx(x0), sy(y0)), Size(sx(x1) - sx(x0), sy(y1) - sy(y0)))
    // on a light background: a dark outline around the cloud
    if (outline != null) {
        val o = 22f
        r(-o, 113f - o, CW + o, CH + o, outline)
        r(152f - o, -o, 635f + o, 113f, outline)
    }
    r(0f, 113f, CW, CH, WHITE)
    r(152f, 0f, 565f, 114f, WHITE)
    r(565f, 0f, 635f, 113f, GREY)
    r(635f, 113f, 704f, 193f, GREY)
    r(0f, 213f, 62f, CH, GREY)
    r(62f, 333f, 127f, CH, GREY)

    val ew = 68f; val eh = 198f; val top = 110f + eyeDy * CW
    val dx = eyeDx * CW
    listOf(245f, 430f).forEachIndexed { i, ex0 ->
        val ex = ex0 + if (mood == CloudMood.Idle || mood == CloudMood.Wink || mood == CloudMood.Surprised) dx else 0f
        when (mood) {
            CloudMood.Idle -> {
                val h = (eh * (1f - blink)).coerceAtLeast(26f)
                r(ex, top + eh - h, ex + ew, top + eh, eye)
            }
            // joy: a "^" of three blocks
            CloudMood.Happy -> {
                r(ex - 34f, top + 120f, ex + 4f, top + 158f, eye)
                r(ex + 4f, top + 82f, ex + ew - 4f, top + 120f, eye)
                r(ex + ew - 4f, top + 120f, ex + ew + 34f, top + 158f, eye)
            }
            // anger: the eye is lower, with a slanted stepped brow toward the nose
            CloudMood.Angry -> {
                r(ex, top + 80f, ex + ew, top + eh, eye)
                val inner = if (i == 0) ex + ew - 34f else ex
                r(inner, top + 40f, inner + 34f, top + 80f, eye)
                r(if (i == 0) ex else ex + 34f, top + 80f, if (i == 0) ex + 34f else ex + ew, top + 110f, WHITE)
            }
            CloudMood.Sleep -> r(ex - 10f, top + eh - 34f, ex + ew + 10f, top + eh, eye)
            // being petted: "^" and lilac cheeks under the eyes
            CloudMood.Love -> {
                r(ex - 34f, top + 120f, ex + 4f, top + 158f, eye)
                r(ex + 4f, top + 82f, ex + ew - 4f, top + 120f, eye)
                r(ex + ew - 4f, top + 120f, ex + ew + 34f, top + 158f, eye)
                val cx = if (i == 0) ex - 70f else ex + ew + 10f
                r(cx, top + eh - 6f, cx + 60f, top + eh + 30f, Color(LILAC))
            }
            // wink: left "^", right open
            CloudMood.Wink -> if (i == 0) {
                r(ex - 34f, top + 120f, ex + 4f, top + 158f, eye)
                r(ex + 4f, top + 82f, ex + ew - 4f, top + 120f, eye)
                r(ex + ew - 4f, top + 120f, ex + ew + 34f, top + 158f, eye)
            } else r(ex, top, ex + ew, top + eh, eye)
            // woke up with a jolt: eyes wider and higher
            CloudMood.Surprised -> r(ex - 14f, top - 34f, ex + ew + 14f, top + eh, eye)
            // dizzy: an "x" of five blocks
            CloudMood.Dizzy -> {
                val b = 36f; val gx = ex + ew / 2 - b * 1.5f; val gy = top + 50f
                listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2).forEach { (a, bb) -> r(gx + a * b, gy + bb * b, gx + (a + 1) * b, gy + (bb + 1) * b, eye) }
            }
            // drowsy: eyelids half down
            CloudMood.Drowsy -> r(ex + dx, top + eh * 0.55f, ex + dx + ew, top + eh, eye)
        }
    }
}

/**
 * Step counter: jerky motion, redrawn only a few times a second. Read it while drawing (not in composition),
 * so a tick redraws the canvas and recomposes nothing. Stands still with motion off or the app in the background.
 */
@Composable
private fun rememberSteps(periodMs: Long): androidx.compose.runtime.State<Int> {
    val n = remember { mutableIntStateOf(0) }
    if (app.ocmobile.ui.theme.rememberMotionEnabled()) app.ocmobile.ui.theme.WhileStarted(periodMs) { while (true) { delay(periodMs); n.intValue++ } }
    return n
}

/** The mascot's endless idle motion: blinking, looking around, floating. Null with motion off: a still frame. */
private class IdleMotion(val blink: androidx.compose.runtime.State<Float>, val look: androidx.compose.runtime.State<Float>, val bob: androidx.compose.runtime.State<Float>)

@Composable
private fun rememberIdleMotion(): IdleMotion {
    val t = rememberInfiniteTransition(label = "cloud")
    val blinkT = t.animateFloat(0f, 1f, infiniteRepeatable(keyframes { durationMillis = 4400; 0f at 0; 0f at 4100; 1f at 4180; 1f at 4260; 0f at 4340 }), label = "blink")
    // looks around rarely and calmly: every 12 seconds to the left, then right, and back
    val lookT = t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 12000
        0f at 0; 0f at 6000; -1f at 6400 using androidx.compose.animation.core.FastOutSlowInEasing; -1f at 7600
        1f at 8200 using androidx.compose.animation.core.FastOutSlowInEasing; 1f at 9400; 0f at 9800 using androidx.compose.animation.core.FastOutSlowInEasing
    }), label = "look")
    val bob = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    return remember { IdleMotion(blinkT, lookT, bob) }
}

/**
 * The cl0udly cloud mascot. Floats, blinks, now and then looks around; [watching] means it looks down
 * at the input while someone types. While thinking, pixel rain falls under it.
 *
 * You can play with it:
 * - tap: it cheers and bounces; two in a row: it winks; five: it gets angry and shakes;
 * - hold: it falls asleep ("z" float up); tap a sleeping one: it wakes with wide eyes;
 * - pet it (move a finger back and forth): it squints, blushes, hearts float up;
 * - pull and release: it stretches after the finger and springs back, its eyes follow the finger;
 * - flick up: it jumps;
 * - shake the phone: it gets dizzy ("x" eyes); tilt it: it looks toward the tilt;
 * - leave it alone for long: first it nods off, then falls asleep by itself; at night it's drowsy.
 */
@Composable
fun PixelCloud(modifier: Modifier, thinking: Boolean = false, interactive: Boolean = true, watching: Boolean = false, onReact: ((String) -> Unit)? = null, cue: Any? = null) {
    val c = Oc.c
    val vibrate = rememberHaptic()
    val haptic = { if (Graph.prefs.mascotHaptics) vibrate() }
    val scope = rememberCoroutineScope()
    var mood by remember { mutableStateOf(CloudMood.Idle) }
    val taps = remember { ArrayDeque<Long>() }
    var calmJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val squash = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val pullX = remember { Animatable(0f) }
    val pullY = remember { Animatable(0f) }
    val jump = remember { Animatable(0f) }
    var touchLook by remember { mutableStateOf<Offset?>(null) }   // where to look while a finger is on the cloud (-1..1)
    var tilt by remember { mutableStateOf(0f) }
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    val hearts = remember { androidx.compose.runtime.mutableStateListOf<Bit>() }   // hearts, notes, stars, drops, "?"
    val flip = remember { Animatable(1f) }       // spinning around
    val dartX = remember { Animatable(0f) }      // eyes darting / looking up
    val dartY = remember { Animatable(0f) }
    var lastTapKind by remember { mutableIntStateOf(-1) }
    var upside by remember { mutableStateOf(false) }
    var lowBattery by remember { mutableStateOf(false) }
    val holiday = Holiday.chosen()
    val prefs = Graph.prefs
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var clock by remember { mutableStateOf(0L) }
    fun calm(ms: Long) {
        // an interrupted reaction must not leave the eyes off to the side or the cloud turned around
        if (calmJob?.isActive == true) scope.launch { dartX.snapTo(0f); dartY.snapTo(0f); flip.snapTo(1f) }
        calmJob?.cancel(); calmJob = scope.launch { delay(ms); mood = CloudMood.Idle }
    }
    fun set(m: CloudMood, ms: Long) { mood = m; calm(ms) }
    fun boing(depth: Float = 0.84f) = scope.launch { squash.snapTo(depth); squash.animateTo(1f, spring(0.4f, 600f)) }
    fun wobble(amp: Float) = scope.launch {
        shake.snapTo(0f)
        listOf(amp, -amp, amp * 0.7f, -amp * 0.7f, amp * 0.3f, 0f).forEach { shake.animateTo(it, tween(45)) }
    }
    fun heart(x: Float = 0.2f + Math.random().toFloat() * 0.6f) { hearts.add(Bit('h', x, System.currentTimeMillis())) }
    fun bit(kind: Char, x: Float, delayMs: Long = 0) { hearts.add(Bit(kind, x, System.currentTimeMillis() + delayMs)) }
    // one tap: a different reaction each time, never the same one twice in a row
    fun tapReaction() {
        onReact?.invoke("tap")
        var kind: Int
        do kind = (0..9).random() while (kind == lastTapKind)
        lastTapKind = kind
        calmJob?.cancel()
        calmJob = scope.launch {
            when (kind) {
                // cheers and bounces
                0 -> { mood = CloudMood.Happy; boing(); delay(1000) }
                // hops
                1 -> { mood = CloudMood.Happy; jump.animateTo(-0.28f, tween(110)); jump.animateTo(0f, tween(110)); boing(0.82f); delay(700) }
                // spins around
                2 -> { mood = CloudMood.Surprised; flip.animateTo(-1f, tween(140)); flip.animateTo(1f, tween(140)); boing(0.9f); mood = CloudMood.Happy; delay(600) }
                // dances to notes
                3 -> { mood = CloudMood.Happy; bit('n', 0.75f); bit('n', 0.25f, 260)
                    repeat(2) { shake.animateTo(0.035f, tween(90)); shake.animateTo(-0.035f, tween(90)) }; shake.animateTo(0f, tween(60)); delay(500) }
                // hums
                4 -> { mood = CloudMood.Happy; bit('n', 0.7f); bit('n', 0.45f, 220); bit('n', 0.8f, 440); delay(1200) }
                // sparkles around, eyes wider
                5 -> { mood = CloudMood.Surprised; listOf(0.05f, 0.95f, 0.5f, 0.2f, 0.8f).forEachIndexed { i, x -> bit('s', x, i * 70L) }; boing(0.9f); delay(800) }
                // eyes dart back and forth
                6 -> { mood = CloudMood.Idle; listOf(-1f, 1f, -1f, 0f).forEach { dartX.animateTo(it, tween(90)); delay(60) } }
                // peekaboo: closes and pops open its eyes
                7 -> { mood = CloudMood.Sleep; delay(320); mood = CloudMood.Surprised; boing(0.8f); delay(600) }
                // sneezes a little rain
                8 -> { mood = CloudMood.Sleep; delay(180); mood = CloudMood.Angry; boing(0.75f)
                    shake.animateTo(0.04f, tween(50)); shake.animateTo(0f, tween(80))
                    listOf(0.25f, 0.45f, 0.65f, 0.35f, 0.55f).forEachIndexed { i, x -> bit('d', x, i * 40L) }; delay(500) }
                // wonders: "?" and a look up
                else -> { mood = CloudMood.Idle; bit('q', 0.82f); dartY.animateTo(-1f, tween(120)); dartX.animateTo(0.6f, tween(120)); delay(900); dartX.animateTo(0f, tween(120)); dartY.animateTo(0f, tween(120)) }
            }
            mood = CloudMood.Idle
        }
    }

    // a cue from outside (a new tour page): answers with a random reaction, like a tap
    LaunchedEffect(cue) { if (cue != null) { delay(250); tapReaction() } }
    // the agent finished: a happy hop; an error: eyes down and a small settle
    val outcome = MascotSignals.outcome
    val hop = app.ocmobile.ui.theme.rememberMotionEnabled()
    LaunchedEffect(outcome) {
        if (outcome == null || !interactive || System.currentTimeMillis() - outcome.at > 3000) return@LaunchedEffect
        calmJob?.cancel()
        if (outcome.ok) {
            mood = CloudMood.Happy
            if (hop) { jump.animateTo(-0.22f, tween(110)); jump.animateTo(0f, tween(110)); boing(0.85f) }
            delay(900)
        } else {
            mood = CloudMood.Drowsy
            if (hop) boing(0.93f)
            delay(1200)
        }
        mood = CloudMood.Idle
    }
    // hearts float up for ~0.9 s; the clock ticks only while they exist
    LaunchedEffect(hearts.isNotEmpty()) {
        while (hearts.isNotEmpty()) {
            androidx.compose.runtime.withFrameMillis { }
            clock = System.currentTimeMillis()
            hearts.removeAll { clock - it.born > it.life }
        }
    }
    // someone started typing: it wakes up (if it dozed off) and looks down at the input
    val typing by androidx.compose.runtime.rememberUpdatedState(watching)
    LaunchedEffect(watching) {
        if (!watching) return@LaunchedEffect
        lastTouch = System.currentTimeMillis()
        if (mood == CloudMood.Sleep || mood == CloudMood.Drowsy) { onReact?.invoke("wake"); set(CloudMood.Surprised, 600) }
    }
    // bored: after 40 s without touches it nods off, after 80 s it falls asleep by itself (never while you type)
    app.ocmobile.ui.theme.WhileStarted(interactive) {
        if (!interactive) return@WhileStarted
        while (true) {
            delay(2000)
            if (typing) { lastTouch = System.currentTimeMillis(); continue }
            val idle = System.currentTimeMillis() - lastTouch
            if (!prefs.mascotSleepy) continue
            if (mood == CloudMood.Idle && idle > 40_000) mood = CloudMood.Drowsy
            if (mood == CloudMood.Drowsy && idle > 80_000) { calmJob?.cancel(); mood = CloudMood.Sleep }
        }
    }
    // phone turned over: it stares and shakes until turned back
    LaunchedEffect(upside) {
        if (!upside) { if (mood == CloudMood.Surprised) mood = CloudMood.Idle; return@LaunchedEffect }
        calmJob?.cancel(); mood = CloudMood.Surprised; haptic()
        while (true) { shake.animateTo(0.02f, tween(40)); shake.animateTo(-0.02f, tween(40)) }
    }
    // charging: plugged in, it cheers with lightning overhead; battery under 15 %, it's drowsy
    androidx.compose.runtime.DisposableEffect(interactive) {
        if (!interactive || !prefs.mascotBattery) return@DisposableEffect onDispose { }
        var plugged: Boolean? = null
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(cx: android.content.Context, i: android.content.Intent) {
                val now = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
                val lvl = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                lowBattery = !now && lvl in 0 until scale * 15 / 100
                if (plugged == false && now) {
                    lastTouch = System.currentTimeMillis(); haptic(); set(CloudMood.Happy, 1500); boing(0.8f)
                    bit('z', 0.78f); bit('z', 0.78f, 450); bit('z', 0.78f, 900)
                }
                plugged = now
            }
        }
        ctx.registerReceiver(r, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    // morning: the first launch of the day after 6 am, it stretches and hops
    LaunchedEffect(interactive) {
        if (!interactive) return@LaunchedEffect
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (!prefs.mascotMorning || CloudTalk.currentHour() < 6 || Graph.prefs.lastMorning == day) return@LaunchedEffect
        Graph.prefs.lastMorning = day
        delay(500)
        calmJob?.cancel(); mood = CloudMood.Drowsy
        squash.animateTo(1.22f, tween(260)); delay(350)
        mood = CloudMood.Happy
        squash.animateTo(1f, tween(90))
        jump.animateTo(-0.35f, tween(130)); jump.animateTo(0f, tween(130)); boing(0.8f)
        delay(700); mood = CloudMood.Idle
    }
    // holiday: its own things fly around (snow, confetti, leaves, bats); in autumn it "sneezes" by itself; on April 1 it suddenly turns around
    app.ocmobile.ui.theme.WhileStarted(holiday, interactive) {
        if (!interactive || holiday == null || !prefs.mascotSeasonal) return@WhileStarted
        var tick = 0
        while (true) {
            delay(if (holiday.ambient != null) holiday.every else 4000)
            tick++
            holiday.ambient?.let { a -> bit(a, -0.3f + Math.random().toFloat() * 1.6f) }
            if (mood != CloudMood.Idle) continue
            if (holiday.sneezes && tick % 23 == 0) {
                mood = CloudMood.Sleep; delay(160); mood = CloudMood.Angry; boing(0.78f)
                listOf(0.3f, 0.5f, 0.7f).forEachIndexed { i, x -> bit('d', x, i * 40L) }
                delay(400); mood = CloudMood.Idle
            }
            if (holiday == Holiday.AprilFools && tick % 5 == 0) { flip.animateTo(-1f, tween(140)); delay(1200); flip.animateTo(1f, tween(140)) }
        }
    }
    // dictation: while you speak, volume bars hop above its head
    val listening = MascotSignals.listening && interactive
    // phone shake and tilt (not with motion off: battery saver or animations turned off)
    val motion = app.ocmobile.ui.theme.rememberMotionEnabled()
    androidx.compose.runtime.DisposableEffect(interactive, motion) {
        val sm = ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val acc = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        if (!interactive || !motion || !prefs.mascotSensors || sm == null || acc == null) return@DisposableEffect onDispose { }
        var lastShake = 0L; var jolts = 0; var gx = 0f
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val (ax, ay, az) = e.values
                gx = gx * 0.85f + ax * 0.15f
                val nt = (-gx / 6f).coerceIn(-1f, 1f)
                if (kotlin.math.abs(nt - tilt) > 0.08f) tilt = nt
                // upside down: gravity pulls toward the top of the screen
                val up = if (upside) ay < -4f else ay < -7f
                if (up != upside) upside = up
                val g = kotlin.math.sqrt(ax * ax + ay * ay + az * az)
                if (g > 19f) {
                    val now = System.currentTimeMillis()
                    jolts = if (now - lastShake < 450) jolts + 1 else 1
                    lastShake = now
                    if (jolts >= 3 && mood != CloudMood.Dizzy && !upside) { lastTouch = now; onReact?.invoke("dizzy"); set(CloudMood.Dizzy, 2200); wobble(0.06f); haptic() }
                }
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        sm.registerListener(l, acc, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }

    val frameS = rememberSteps(if (thinking) 130 else 240)
    val idleMotion = if (motion) rememberIdleMotion() else null
    val down by androidx.compose.animation.core.animateFloatAsState(if (watching) 1f else 0f, tween(200), label = "down")
    val tiltA by androidx.compose.animation.core.animateFloatAsState(tilt, tween(150), label = "tilt")
    val night = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) in 0..4 }

    Canvas(
        modifier
            .graphicsLayer {
                scaleX = (2f - squash.value) * flip.value; scaleY = squash.value
                translationX = shake.value * size.width + pullX.value * size.width
                translationY = pullY.value * size.height + jump.value * size.height
                transformOrigin = TransformOrigin(0.5f, 0.75f)
            }
            .then(if (!interactive) Modifier else Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    MascotGrab.held = true
                    try {
                    val wasAsleep = mood == CloudMood.Sleep || mood == CloudMood.Drowsy
                    lastTouch = System.currentTimeMillis()
                    fun look(p: Offset) = Offset((p.x / size.width * 2 - 1).coerceIn(-1f, 1f), (p.y / size.height * 2 - 1).coerceIn(-1f, 1f))
                    if (!wasAsleep) touchLook = look(first.position)
                    val slop = viewConfiguration.touchSlop
                    var moved = false
                    var lastX = first.position.x; var dir = 0; var run = 0f; var turns = 0; var travel = 0f
                    val vt = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    vt.addPosition(first.uptimeMillis, first.position)
                    var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    // wait: released (tap), moved (gesture) or held (sleep)
                    val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: continue
                            if (!ch.pressed) { up = ch; return@withTimeoutOrNull true }
                            if ((ch.position - first.position).getDistance() > slop) { moved = true; ch.consume(); return@withTimeoutOrNull true }
                            ch.consume()
                        }
                        @Suppress("UNREACHABLE_CODE") true
                    }
                    if (decided == null) {
                        // held: it falls asleep
                        haptic(); calmJob?.cancel(); touchLook = null; mood = CloudMood.Sleep; onReact?.invoke("sleep")
                        boing(0.92f)
                        do { val ev = awaitPointerEvent() } while (ev.changes.any { it.pressed })
                        return@awaitEachGesture
                    }
                    if (!moved) {
                        touchLook = null
                        val now = System.currentTimeMillis()
                        taps.addLast(now); while (taps.isNotEmpty() && now - taps.first() > 1800) taps.removeFirst()
                        haptic()
                        when {
                            wasAsleep -> { taps.clear(); onReact?.invoke("wake"); set(CloudMood.Surprised, 900); scope.launch { jump.animateTo(-0.22f, tween(90)); jump.animateTo(0f, tween(110)); boing(0.8f) } }
                            taps.size >= 5 || mood == CloudMood.Angry -> { if (mood != CloudMood.Angry) onReact?.invoke("angry"); set(CloudMood.Angry, 2600); wobble(0.05f); boing() }
                            taps.size >= 2 && now - taps[taps.size - 2] < 320 -> { set(CloudMood.Wink, 1100); boing(0.9f) }
                            else -> tapReaction()
                        }
                        return@awaitEachGesture
                    }
                    // the finger moved: it stretches after it; moved back and forth: it's being petted
                    if (wasAsleep) set(CloudMood.Surprised, 600)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        vt.addPosition(ch.uptimeMillis, ch.position)
                        if (!ch.pressed) break
                        ch.consume()
                        val d = ch.position - first.position
                        touchLook = look(ch.position)
                        scope.launch { pullX.snapTo((d.x / size.width) * 0.22f); pullY.snapTo((d.y / size.height) * 0.22f) }
                        val step = ch.position.x - lastX; lastX = ch.position.x
                        travel += kotlin.math.abs(step)
                        val nd = if (step > 0) 1 else if (step < 0) -1 else dir
                        if (nd != dir && run > size.width * 0.12f) {
                            turns++; run = 0f
                            if (turns >= 2) {
                                if (mood != CloudMood.Love) { haptic(); onReact?.invoke("pet"); Graph.prefs.petCount = ((Graph.prefs.petCount.toIntOrNull() ?: 0) + 1).toString() }
                                set(CloudMood.Love, 1600); heart()
                            }
                        }
                        if (nd != dir) run = 0f
                        dir = nd; run += kotlin.math.abs(step)
                    }
                    touchLook = null
                    val v = vt.calculateVelocity()
                    when {
                        turns >= 2 -> { heart(); heart() }
                        // flicked up: a jump
                        v.y < -1400f && kotlin.math.abs(v.y) > kotlin.math.abs(v.x) -> scope.launch {
                            set(CloudMood.Happy, 900)
                            pullY.animateTo(0f, tween(60))
                            jump.animateTo(-0.55f, tween(150, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                            jump.animateTo(0f, tween(140, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                            boing(0.78f)
                        }
                        else -> if (mood == CloudMood.Idle || mood == CloudMood.Surprised) set(CloudMood.Happy, 700)
                    }
                    // released: it springs back
                    scope.launch { pullX.animateTo(0f, spring(0.35f, 700f)) }
                    scope.launch { pullY.animateTo(0f, spring(0.35f, 700f)) }
                    } finally { MascotGrab.held = false }
                }
            }),
    ) {
        // the cloud is centered; rain, "z" and hearts are drawn beyond the canvas edges
        val frame = frameS.value
        val blinkT = idleMotion?.blink?.value ?: 0f
        val lookT = idleMotion?.look?.value ?: 0f
        val bob = idleMotion?.bob?.value ?: 0f
        val w = minOf(size.width * 0.9f, size.height * 0.85f * CW / CH)
        val k = w / CW
        val step = floor(26f * k).coerceAtLeast(1f)          // the cloud's "pixel"
        val lift = if (mood == CloudMood.Sleep) 0f else bob * step * 1.5f
        val x = (size.width - w) / 2
        val y = (size.height - CH * k) / 2 + step * 0.75f - lift
        // at night or on low battery it's drowsy, but not while you type to it
        val shown = if (mood == CloudMood.Idle && (night || lowBattery) && !watching) CloudMood.Drowsy else mood
        val tl = touchLook
        val eyeDx = when {
            tl != null -> tl.x * 0.035f
            listening -> 0.035f
            thinking || watching -> 0f
            else -> (lookT * 0.03f + tiltA * 0.03f + dartX.value * 0.035f).coerceIn(-0.045f, 0.045f)
        }
        drawIconCloud(
            x, y, w, shown, eyeDx, if (blinkT > 0.5f && tl == null) 1f else 0f, if (c.isDark) c.bg else INK,
            eyeDy = if (tl != null) tl.y * 0.05f else if (listening) 0.07f else down * 0.07f + dartY.value * 0.04f, outline = if (c.isDark) null else INK,
        )

        val bottom = y + CH * k
        if (thinking) for (i in 0..3) {
            val s = (frame + i * 3) % 6
            if (s < 5) drawRect(GREY.copy(alpha = 1f - s * 0.18f), Offset(x + (110f + i * 170f) * k, bottom + (s * 1.4f + 0.6f) * step), Size(step, step * 1.6f))
        }
        // "z" only during real sleep, not when it squints for a moment (peekaboo, sneeze)
        if (mood == CloudMood.Sleep && calmJob?.isActive != true) for (n in 0..2) {
            val s = (frame + n * 4) % 12
            val glyph = SLEEP_Z[s / 4]
            val zx = x + w * 0.8f + (s / 2) * step
            val zy = y - glyph.size * step - s * step
            val col = GREY.copy(alpha = 1f - (s / 12f) * (s / 12f))
            glyph.forEachIndexed { r, row -> row.forEachIndexed { cI, ch -> if (ch == 'x') drawRect(col, Offset(zx + cI * step, zy + r * step), Size(step, step)) } }
        }
        // woke up: "!" above its head
        if (mood == CloudMood.Surprised) {
            val ex = x + w * 0.86f; val ey = y - step * 6
            drawRect(Color(LILAC), Offset(ex, ey), Size(step, step * 3)); drawRect(Color(LILAC), Offset(ex, ey + step * 4), Size(step, step))
        }
        // dizzy: a dot runs in a circle above its head
        if (mood == CloudMood.Dizzy) {
            val a = frame * 1.3f
            for (n in 0..2) {
                val ang = a + n * 2.1f
                drawRect(GREY, Offset(x + w * 0.5f + kotlin.math.cos(ang) * w * 0.28f - step / 2, y - step * 2.5f + kotlin.math.sin(ang) * step * 1.2f), Size(step, step))
            }
        }
        holiday?.let { drawOutfit(it, x, y, k, step) }
        if (listening) {
            val lv = MascotSignals.level.coerceIn(0f, 1f)
            listOf(0.6f, 1f, 0.75f).forEachIndexed { i, f ->
                val hgt = floor(step * (1 + (lv * f * 4 + ((frame + i) % 3) * 0.5f)))
                drawRect(Color(LILAC), Offset(x + w + step * (0.6f + i * 1.6f), y - hgt + step * 2), Size(step, hgt))
            }
        }
        // particles: hearts and notes float up, sparkles flash around, drops fall, "?" hangs above its head
        val hs = step * 0.8f
        hearts.forEach { b ->
            val age = ((clock - b.born).toFloat() / b.life).coerceIn(0f, 1f)
            if (clock < b.born) return@forEach
            val fall = CH * k * 1.4f
            val conf = listOf(Color(LILAC), Color(0xFFF2C94C), Color(0xFFF08DB0), Color(0xFF7FD1C7))
            val (px, py, col) = when (b.kind) {
                'f' -> Triple(floor(x + b.x * w + kotlin.math.sin(age * 6f + b.x * 9f) * step), floor(y - CH * k * 0.5f + age * fall), (if (c.isDark) Color.White else Color(0xFF8FA8C8)).copy(alpha = 0.75f * (1f - age)))
                'c' -> Triple(floor(x + b.x * w + kotlin.math.sin(age * 10f + b.x * 31f) * step * 1.5f), floor(y - CH * k * 0.5f + age * fall), conf[((b.x * 997).toInt() and 3)].copy(alpha = 1f - age * age))
                'l' -> Triple(floor(x + b.x * w + kotlin.math.sin(age * 7f + b.x * 13f) * step * 2f), floor(y - CH * k * 0.4f + age * fall), Color(0xFFE09A4F).copy(alpha = 0.9f * (1f - age)))
                'b' -> Triple(floor(x - w * 0.4f + age * w * 1.8f), floor(y - step * 4 + kotlin.math.sin(age * 12f) * step * 1.5f), GREY.copy(alpha = if (age < 0.85f) 0.9f else (1f - age) * 6f))
                'z' -> Triple(floor(x + b.x * w), floor(y - step * 5 - age * step * 2), Color(0xFFF2C94C).copy(alpha = 1f - age * age))
                's' -> Triple(floor(x + b.x * w - step), floor(y + (if (b.x in 0.3f..0.7f) -step * 3 else CH * k * 0.35f) - age * step * 2), Color(LILAC).copy(alpha = if (age < 0.5f) 1f else 2f - age * 2))
                'd' -> Triple(floor(x + b.x * w), floor(y + CH * k + age * CH * k * 0.9f), GREY.copy(alpha = 1f - age))
                'q' -> Triple(floor(x + b.x * w), floor(y - step * 6), Color(LILAC).copy(alpha = if (age < 0.8f) 1f else 5f - age * 5))
                else -> Triple(floor(x + b.x * w + kotlin.math.sin(age * 9f + b.x * 20f) * step), floor(y - step - age * CH * k * 0.9f), (if (b.kind == 'n') GREY else Color(LILAC)).copy(alpha = 1f - age * age))
            }
            val pic = when (b.kind) {
                'n' -> NOTE; 's' -> STAR; 'd' -> DROP; 'q' -> QUESTION; 'f' -> FLAKE; 'c' -> if ((clock / 150) % 2 == 0L) CONF_A else CONF_B
                'l' -> LEAF; 'b' -> if ((clock / 120) % 2 == 0L) BAT_A else BAT_B; 'z' -> BOLT; else -> HEART
            }
            pic.forEachIndexed { r, row -> row.forEachIndexed { cI, ch -> if (ch == 'x') drawRect(col, Offset(px + cI * hs, py + r * hs), Size(hs, hs)) } }
        }
    }
}

/** A particle by the mascot: [kind] h heart, n note, s sparkle, d drop, q "?"; [x] is a share of the cloud's width. */
class Bit(val kind: Char, val x: Float, val born: Long, val life: Long = when (kind) { 'd' -> 600; 'z' -> 800; 'f', 'c', 'b', 'l' -> 2200; else -> 900 })

/** A finger is on the mascot: the drawer doesn't open by swipe meanwhile. */
object MascotGrab { var held by mutableStateOf(false) }

private val HEART = listOf(".x.x.", "xxxxx", "xxxxx", ".xxx.", "..x..")
private val NOTE = listOf("..xx", "..x.", "..x.", "xxx.", "xx..")
private val STAR = listOf(".x.", "xxx", ".x.")
private val DROP = listOf("x", "x")
private val QUESTION = listOf("xxx", "..x", ".xx", "...", ".x.")
private val FLAKE = listOf("x")
private val CONF_A = listOf("x", "x")
private val CONF_B = listOf("xx")
private val LEAF = listOf(".x", "xx", "x.")
private val BAT_A = listOf("x...x", "xx.xx", ".xxx.")
private val BAT_B = listOf(".xxx.", "xx.xx", "x...x")
private val BOLT = listOf(".x", "xx", "x.")
// sleeping "Z" in three sizes: a full top and bottom with a clean diagonal, so it doesn't read as "I" or "H"
private val SLEEP_Z = listOf(
    listOf("xxxxx", "...x.", "..x..", ".x...", "xxxxx"),
    listOf("xxxxxx", "....x.", "...x..", "..x...", ".x....", "xxxxxx"),
    listOf("xxxxxxx", ".....x.", "....x..", "...x...", "..x....", ".x.....", "xxxxxxx"),
)

// ---------- sky above the top bar ----------

// ridge colors: white, two blues, a shadow below (the author's palette)
private val SKY_TONES = longArrayOf(0xFFFFFFFF, 0xFFCBDBFC, 0xFFAFC4ED, 0xFFADC0E7, 0xFF4C618B)
// lilac for rare details: folds of some puffs and a couple of stripes in the tail
private const val LILAC = 0xFF9F9DF2.toInt()
// the same, darkened for the dark theme: the blue tint kept, low brightness
private val DARK_TONES = longArrayOf(0xFF4A5873, 0xFF3A465E, 0xFF2E384D, 0xFF283145, 0xFF1C2333)

private fun bankTones(dark: Boolean, sky: Int, boost: Float = 1f) =
    if (dark) IntArray(5) { mix(sky, DARK_TONES[it].toInt(), boost.coerceAtMost(1f)).let { c -> if (boost > 1f) mix(c, SKY_TONES[it].toInt(), boost - 1f) else c } } + mix(sky, LILAC, 0.62f * boost)
    else IntArray(5) { mix(sky, SKY_TONES[it].toInt(), 0.6f) } + mix(sky, LILAC, 0.7f)

private fun mix(a: Int, b: Int, k: Float): Int {
    fun ch(s: Int) = (((a shr s) and 255) * (1 - k) + ((b shr s) and 255) * k).roundToInt()
    return (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/**
 * A pixel ridge of clouds [gw]×[gh] on an opaque sky. Big full-height puffs on the left,
 * smaller puffs to the right, the ridge thins and breaks into horizontal stripes and wisps.
 * [tones]: highlight, light, middle, bottom, shadow.
 */
private fun paintBank(gw: Int, gh: Int, sky: Int, tones: IntArray, frame: Int?, reveal: Int = gw): IntArray {
    val px = IntArray(gw * gh) { sky }
    val rnd = java.util.Random(11)
    val h = gh - (if (frame != null) 3 else 1)
    fun put(x: Int, y: Int, tone: Int) { if (x in 0 until minOf(gw, reveal) && y in 0 until h) px[y * gw + x] = tones[tone] }

    // puffs: left to right, the radius shrinks, the centers sit on a shared bottom
    class Billow(val x: Float, val y: Float, val r: Float, val lilac: Boolean)
    val billows = ArrayList<Billow>()
    var bx = -2f
    while (true) {
        val f = bx / gw
        if (f > 0.66f) break
        val base = h * (0.86f - 0.22f * f)
        val r = (h * 0.42f * (1f - f / 0.75f).coerceAtLeast(0.14f) * (0.8f + rnd.nextFloat() * 0.4f)).coerceAtMost((base - 1) / 1.5f)
        // a puff is now higher, now lower: the ridge top is uneven, like a cumulus cloud
        billows += Billow(bx, base - r * (0.45f + rnd.nextFloat() * 0.5f), r, billows.size % 2 == 1)
        bx += r * (0.7f + rnd.nextFloat() * 0.45f)
    }
    fun bottom(x: Int): Float { val f = x.toFloat() / gw; return h * (0.86f - 0.22f * f) + (if ((x / 5) % 2 == 0) 0f else 1f) * (1f - f) }

    for (x in 0 until gw) {
        val cx = x + 0.5f
        val bot = bottom(x).toInt().coerceAtMost(h - 1)
        // column top and folds: where one puff's arc passes inside another
        var top = Int.MAX_VALUE
        var topLilac = false
        val creases = HashMap<Int, Boolean>()      // fold row → whether it's lilac
        for (b in billows) {
            val dx = cx - b.x
            if (dx * dx > b.r * b.r) continue
            val arc = (b.y - kotlin.math.sqrt(b.r * b.r - dx * dx)).toInt()
            if (arc < top) { if (top != Int.MAX_VALUE) creases[top] = topLilac; top = arc; topLilac = b.lilac } else creases[arc] = b.lilac
        }
        if (top == Int.MAX_VALUE || top > bot) continue
        var depth = 0
        for (y in top.coerceAtLeast(0)..bot) {
            if (y in creases && y > top + 1) depth = if (creases[y] == true) -2 else -1
            val tone = when {
                y == bot -> 4
                y == bot - 1 -> 3
                depth == -2 -> 5                         // lilac fold
                depth == -1 -> 2                         // fold
                depth == 0 -> 0                          // highlight along the top of a puff
                depth <= 2 -> 1
                depth == 3 -> if ((x + y) % 2 == 0) 1 else 2
                else -> 2
            }
            put(x, y, tone)
            depth = if (depth < 0) 1 else depth + 1
        }
    }
    // tail: horizontal stripes and wisps, rarer and shorter further right
    var x = (gw * 0.5f).toInt()
    while (x < gw) {
        val f = x.toFloat() / gw
        val y = (h * (0.55f + rnd.nextFloat() * 0.3f - 0.25f * f)).toInt()
        val len = (3 + rnd.nextInt(9) * (1.15f - f)).toInt().coerceAtLeast(2)
        val lilac = rnd.nextInt(2) == 0
        for (i in 0 until len) {
            put(x + i, y, if (lilac) 5 else if (i == 0 || i == len - 1) 2 else 1)
            put(x + i, y + 1, if (i == 0 || i == len - 1) 4 else 3)
        }
        x += len + 2 + (rnd.nextInt(5) + 12 * f).toInt()
    }
    return px
}

private fun IntArray.toImage(w: Int, h: Int): ImageBitmap = Bitmap.createBitmap(this, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()

private fun DrawScope.pixels(img: ImageBitmap, x: Float, y: Float, px: Float) = drawImage(
    img, IntOffset.Zero, IntSize(img.width, img.height),
    IntOffset(x.roundToInt(), y.roundToInt()), IntSize((img.width * px).roundToInt(), (img.height * px).roundToInt()),
    filterQuality = FilterQuality.None,
)

/**
 * The cl0udly background: a few still, dim clouds on the background color, each screen with its own
 * layout. Images are cached by size, theme and screen.
 */
object CloudBackdrop {
    private class Cumulus(val cx: Float, val base: Int, val width: Float)

    private val cache = object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > 10
    }

    /** One cloud: puffs in a bell shape (bigger in the middle), a flat bottom on row [base]. */
    private fun paintCumulus(out: IntArray, gw: Int, gh: Int, billows: List<Triple<Float, Float, Float>>, base: Int, tones: IntArray, ground: Boolean) {
        val minX = billows.minOf { it.first - it.third }.toInt().coerceAtLeast(0)
        val maxX = billows.maxOf { it.first + it.third }.toInt().coerceAtMost(gw - 1)
        for (x in minX..maxX) {
            val cx = x + 0.5f
            var top = Int.MAX_VALUE; var topLilac = false
            val creases = HashMap<Int, Boolean>()
            billows.forEachIndexed { i, (bx, by, r) ->
                val dx = cx - bx
                if (dx * dx > r * r) return@forEachIndexed
                val arc = (by - kotlin.math.sqrt(r * r - dx * dx)).toInt()
                val lilac = i % 3 == 1
                if (arc < top) { if (top != Int.MAX_VALUE) creases[top] = topLilac; top = arc; topLilac = lilac } else creases[arc] = lilac
            }
            val bot = base.coerceAtMost(gh - 1)
            if (top == Int.MAX_VALUE || top > bot) continue
            var depth = 0
            for (y in top.coerceAtLeast(0)..bot) {
                if (y in creases && y > top + 1) depth = if (creases[y] == true) -2 else -1
                val tone = when {
                    !ground && y == base -> 4
                    !ground && y == base - 1 -> 3
                    depth == -2 -> 5
                    depth == -1 -> 2
                    depth == 0 -> 0
                    depth <= 2 -> 1
                    depth == 3 -> if ((x + y) % 2 == 0) 1 else 2
                    else -> 2
                }
                out[y * gw + x] = tones[tone]
                depth = if (depth < 0) 1 else depth + 1
            }
        }
    }

    private fun cumulusBillows(rnd: java.util.Random, cx: Float, base: Int, width: Float): List<Triple<Float, Float, Float>> {
        val n = (width / 4.5f).toInt().coerceAtLeast(3)
        return List(n) { i ->
            val f = i / (n - 1f)
            val bell = sin(Math.PI.toFloat() * f)
            val r = (1.8f + bell * width * 0.16f) * (0.8f + rnd.nextFloat() * 0.4f)
            Triple(cx - width / 2 + f * width, base - r * (0.35f + rnd.nextFloat() * 0.45f), r)
        }
    }

    /** [safe] rows at the top hold the bar's buttons (menu, (i), the avatar): no cloud reaches into them. */
    private fun build(gw: Int, gh: Int, bg: Int, dark: Boolean, variant: String, topOnly: Boolean, safe: Int): ImageBitmap {
        val out = IntArray(gw * gh) { bg }
        val rnd = java.util.Random(variant.hashCode().toLong())
        fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
        val tones = if (dark) IntArray(5) { mix(bg, DARK_TONES[it].toInt(), 0.45f) } + mix(bg, LILAC, 0.23f)
            else IntArray(5) { mix(bg, SKY_TONES[it].toInt(), 0.25f) } + mix(bg, LILAC, 0.2f)
        // clouds alternate at the left and right edges, top to bottom; the first one's side depends on the screen
        val n = if (topOnly) 2 else 3 + rnd.nextInt(2)
        val leftFirst = rnd.nextBoolean()
        val spots = List(n) { i ->
            val left = (i % 2 == 0) == leftFirst
            val width = gw * r(0.26f, 0.44f)
            // a cloud rises about a third of its width above its flat bottom
            val clear = safe + width * 0.36f + 2f
            val cy = if (topOnly) clear + gh * r(0f, 0.06f) + i * gh * 0.05f
                else maxOf(clear, gh * ((i + 0.3f + r(0f, 0.5f)) / n))
            Cumulus(if (left) r(-0.02f, 0.2f) else r(0.75f, 1.02f), cy.toInt(), width)
        }
        spots.forEach { c -> paintCumulus(out, gw, gh, cumulusBillows(rnd, c.cx * gw, c.base, c.width), c.base, tones, false) }
        return out.toImage(gw, gh)
    }

    @Composable
    fun Modifier.backdrop(bg: Color, dark: Boolean, variant: String, topOnly: Boolean): Modifier {
        val bgArgb = bg.toArgb()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val safeTop = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density) + with(density) { 72.dp.roundToPx() }
        return this.drawWithCache {
            val px = 4.dp.toPx().roundToInt().toFloat()
            val gw = ceil(size.width / px).toInt()
            val gh = ceil(size.height / px).toInt()
            val safe = ceil(safeTop / px).toInt()
            val key = "$gw:$gh:$bgArgb:$variant:$safe"
            val img = synchronized(cache) { cache.getOrPut(key) { build(gw, gh, bgArgb, dark, variant, topOnly, safe) } }
            onDrawBehind { pixels(img, 0f, 0f, px) }
        }
    }
}

// ---------- splash ----------

// 5×9 pixel letters for "cl0udly"
private val GLYPHS = mapOf(
    'c' to listOf(".....", ".....", ".###.", "#...#", "#....", "#....", "#...#", ".###.", "....."),
    'l' to listOf("##...", ".#...", ".#...", ".#...", ".#...", ".#...", ".#...", "###..", "....."),
    '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "#.#.#", "##..#", "#...#", ".###.", "....."),
    'u' to listOf(".....", ".....", "#...#", "#...#", "#...#", "#...#", "#..##", ".##.#", "....."),
    'd' to listOf("....#", "....#", ".####", "#...#", "#...#", "#...#", "#...#", ".####", "....."),
    'y' to listOf(".....", ".....", "#...#", "#...#", "#...#", "#...#", ".####", "....#", ".###."),
)
private val GLYPH_W = mapOf('l' to 3)

private fun wordWidth(s: String) = s.sumOf { (GLYPH_W[it] ?: 5) + 1 } - 1

/** A word in pixel letters; [shown] is how many letters are visible, the last one slides in from below. */
private fun DrawScope.drawPixelWord(s: String, left: Float, top: Float, px: Float, shown: Float, ink: Color, accent: Color) {
    var gx = 0
    s.forEachIndexed { i, ch ->
        val k = shown - i
        if (k <= 0f) return
        val dy = if (k < 0.5f) 2 else if (k < 1f) 1 else 0
        val g = GLYPHS[ch] ?: return@forEachIndexed
        val col = if (ch == '0') accent else ink
        for (y in g.indices) for (x in g[y].indices) if (g[y][x] == '#')
            drawRect(col, Offset(left + (gx + x) * px, top + (y + dy) * px), Size(px, px))
        gx += (GLYPH_W[ch] ?: 5) + 1
    }
}

/**
 * The cl0udly splash: the cloud ridge appears from left to right, the little cloud steps down
 * and lands, blinks and looks around, the letters "cl0udly" pop up one by one, the cloud cheers.
 */
@Composable
internal fun CloudIntro(t: Float, exit: Float = 0f) {
    val c = Oc.c
    val sky = c.bg.toArgb()
    val banks = remember { HashMap<Int, ImageBitmap>() }     // ridge reveal frames, by the number of open columns
    Canvas(Modifier.fillMaxSize()) {
        // exit: the background dissolves, the home screen is already underneath
        val e = exit.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        drawRect(c.bg.copy(alpha = 1f - e))
        val px = floor(size.width / 100f).coerceAtLeast(2f)
        val gw = 100; val gh = 22
        val bankTop = size.height * 0.4f
        // ridge: columns open from left to right during the first 40%
        val reveal = ((t / 0.4f).coerceIn(0f, 1f) * gw).toInt()
        val img = banks.getOrPut(reveal) { paintBank(gw, gh, sky, bankTones(c.isDark, sky, 1.12f), null, reveal).toImage(gw, gh) }
        if (e == 0f) pixels(img, (size.width - gw * px) / 2, bankTop, px)
        else {
            // the ridge parts: the left half goes left, the right half right
            val half = gw / 2
            val x0 = (size.width - gw * px) / 2
            val shift = e * size.width * 0.7f
            val a = 1f - e
            drawImage(img, IntOffset(0, 0), IntSize(half, gh), IntOffset((x0 - shift).roundToInt(), bankTop.roundToInt()), IntSize((half * px).roundToInt(), (gh * px).roundToInt()), alpha = a, filterQuality = FilterQuality.None)
            drawImage(img, IntOffset(half, 0), IntSize(gw - half, gh), IntOffset((x0 + half * px + shift).roundToInt(), bankTop.roundToInt()), IntSize(((gw - half) * px).roundToInt(), (gh * px).roundToInt()), alpha = a, filterQuality = FilterQuality.None)
        }

        // the little cloud steps down and lands on the ridge
        val w = size.width * 0.34f
        val k = w / CW
        val step = floor(26f * k)
        // the little cloud starts where the system launch screen's cloud was (centered) and rises onto the ridge
        val fall = (t / 0.4f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        val bounce = if (t in 0.43f..0.48f) 1 else 0
        val mood = if (t > 0.82f) CloudMood.Happy else CloudMood.Idle
        val blink = if (t in 0.56f..0.6f) 1f else 0f
        val eyeDx = if (t in 0.62f..0.69f) -0.04f else if (t in 0.69f..0.76f) 0.04f else 0f
        // the little cloud: by the end of the exit it's where the home screen cloud is, and melts on top of it
        val wHome = size.width * 0.295f
        val wNow = w + (wHome - w) * e
        val yLand = bankTop - CH * k * 0.55f
        val ySplash = size.height * 0.5f - CH * k / 2
        val yStart = ySplash + floor((yLand - ySplash) * fall / step) * step + bounce * step
        val yHome = size.height * 0.375f - CH * (wHome / CW) / 2
        val yNow = yStart + (yHome - yStart) * e
        val mAlpha = if (e > 0.8f) 1f - (e - 0.8f) / 0.2f else 1f
        if (mAlpha > 0f) {
            val layer = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
            drawContext.canvas.saveLayer(layer, androidx.compose.ui.graphics.Paint().apply { alpha = mAlpha })
            drawIconCloud((size.width - wNow) / 2, yNow, wNow, if (e > 0f) CloudMood.Idle else mood, eyeDx, blink, c.bg)
            drawContext.canvas.restore()
        }

        val word = "cl0udly"
        val shown = ((t - 0.5f) / 0.3f).coerceIn(0f, 1f) * word.length
        val wa = (1f - e * 2f).coerceIn(0f, 1f)
        if (wa > 0f) drawPixelWord(word, ((size.width - wordWidth(word) * px) / 2).roundToInt().toFloat(), bankTop + (gh + 6) * px + e * 60f, px, shown, c.text.copy(alpha = wa), c.accent.copy(alpha = wa))
    }
}

// ---------- agent work: the little icon cloud is busy with something ----------

// the icon cloud on a 12 × 7 grid: W white, G grey, E eye
private val MINI = listOf(
    "..WWWWWWWG..",
    "..WWWWWWWG..",
    "WWWWEWWEWWGW",
    "GWWWEWWEWWWW",
    "GWWWEWWEWWWW",
    "GGWWWWWWWWWW",
    "GGWWWWWWWWWW",
)

/** Mini cloud colors: white on the background in the dark theme, dark with white eyes in the light one. */
private class MiniInk(val body: Color, val grey: Color, val eye: Color, val accent: Color, val lilac: Color, val dim: Color)

@Composable
private fun miniInk(): MiniInk {
    val c = Oc.c
    return if (c.isDark) MiniInk(Color(0xFFF1F3F7), GREY, c.bg, Color(0xFF8FA6CC), Color(LILAC), c.textDim)
    else MiniInk(INK, Color(0xFF6B6B70), Color.White, Color(0xFF4C618B), Color(0xFF7270C8), c.textDim)
}

/**
 * Draws the mini cloud in grid cells: [gx], [gy] is the top left corner, [px] the cell size.
 * Eyes: [eyeDx] sideways shift, [eyes] how many eye cells are visible from below (3 open, 1 squint),
 * [eyesUp] eyes raised (looking up).
 */
private fun DrawScope.drawMini(ink: MiniInk, gx: Int, gy: Int, px: Float, eyeDx: Int = 0, eyes: Int = 3, eyesUp: Boolean = false) {
    for (y in MINI.indices) for (x in MINI[y].indices) {
        val ch = MINI[y][x]
        if (ch == '.') continue
        val col = when (ch) { 'G' -> ink.grey; else -> ink.body }
        drawRect(col, Offset((gx + x) * px, (gy + y) * px), Size(px, px))
    }
    // the eyes separately: they can shift and squint
    for (ex in intArrayOf(4, 7)) for (k in 0 until eyes) {
        val y = if (eyesUp) 2 + k else 4 - k
        drawRect(ink.eye, Offset((gx + ex + eyeDx) * px, (gy + y) * px), Size(px, px))
    }
}

private fun DrawScope.cell(x: Int, y: Int, px: Float, col: Color, w: Int = 1, h: Int = 1) =
    drawRect(col, Offset(x * px, y * px), Size(w * px, h * px))

/** A 16 × 12 cell pixel work icon, frames advance in steps. */
@Composable
fun CloudWorkGlyph(work: Work, size: androidx.compose.ui.unit.Dp) {
    val ink = miniInk()
    val fS = rememberSteps(if (work == Work.Wait) 260 else 150)
    // "thinking" is a little different every time, like in the other looks
    val variant = remember { kotlin.random.Random.nextInt(2) }
    Canvas(Modifier.size(width = size * 16f / 12f, height = size)) {
        val f = fS.value
        val px = floor(this.size.height / 12f).coerceAtLeast(1f)
        when (work) {
            // thinking: looks up, thought dots float up above it one by one
            Work.Think, Work.Other -> {
                drawMini(ink, 0, 4, px, eyeDx = if (variant == 0) 0 else (f / 8) % 2, eyes = 2, eyesUp = true)
                val s = f % 8
                if (s >= 1) cell(12, 3, px, ink.accent)
                if (s >= 3) cell(13, 1, px, ink.accent, 2, 2)
                if (s >= 5 && variant == 1) cell(14, 0, px, ink.lilac)
            }
            // writing: the cloud holds a pen, a line appears under it
            Work.Write -> {
                drawMini(ink, 0, 2, px, eyeDx = 1, eyes = 2)
                val n = f % 14
                for (i in 0 until minOf(n, 11)) if (i % 4 != 3) cell(1 + i, 11, px, ink.dim)
                val x = 1 + minOf(n, 11)
                cell(x + 1, 8, px, ink.lilac); cell(x, 9, px, ink.accent); cell(x - 1, 10, px, ink.body)
            }
            // reading: an open book under the cloud, the eyes run along a line, the page turns
            Work.Read -> {
                drawMini(ink, 2, 0, px, eyeDx = if ((f / 3) % 3 == 0) -1 else if ((f / 3) % 3 == 1) 0 else 1, eyes = 2)
                cell(1, 9, px, ink.accent, 7, 3); cell(8, 9, px, ink.accent, 7, 3)
                cell(7, 9, px, ink.grey, 1, 3); cell(8, 9, px, ink.grey, 1, 3)
                for (r in 0..1) { cell(2, 10 + r, px, ink.body.copy(alpha = 0.5f), 4); cell(10, 10 + r, px, ink.body.copy(alpha = 0.5f), 4) }
                val flip = f % 10
                if (flip < 6) cell(8 + flip, 8 + (if (flip in 2..3) 0 else 1), px, ink.lilac, 1, 2)
            }
            // editing: lines on the right, one slides out and settles in a new place
            Work.Edit -> {
                drawMini(ink, 0, 2, px, eyeDx = 1)
                val s = f % 12
                cell(1, 10, px, ink.dim, 5)
                cell(7, 10, px, ink.dim, 3)
                val dy = if (s < 6) s / 2 else 3 - (s - 6) / 2
                cell(12, 3 + dy, px, ink.lilac, 3)
                cell(12, 7 - dy, px, ink.accent, 4)
            }
            // running: rain comes from the cloud, drops step down
            Work.Run -> {
                drawMini(ink, 2, 0, px, eyes = 2)
                for (i in 0..3) {
                    val y = (f + i * 2) % 5
                    cell(3 + i * 3, 8 + y, px, if (i % 2 == 0) ink.accent else ink.lilac, 1, 2)
                }
            }
            // searching: a magnifier circles around the cloud, the cloud follows it
            Work.Search -> {
                val pos = intArrayOf(0, 1, 2, 1)[(f / 2) % 4]
                drawMini(ink, 0, 3, px, eyeDx = pos - 1)
                val lx = 10 + pos; val ly = 2 + (if ((f / 2) % 4 == 2) 1 else 0)
                cell(lx + 1, ly, px, ink.accent, 2); cell(lx, ly + 1, px, ink.accent, 1, 2); cell(lx + 3, ly + 1, px, ink.accent, 1, 2)
                cell(lx + 1, ly + 3, px, ink.accent, 2); cell(lx + 4, ly + 4, px, ink.lilac); cell(lx + 5, ly + 5, px, ink.lilac)
            }
            // on the web: the cloud floats over a pixel globe, a meridian runs on the globe
            Work.Web -> {
                val gx = 4
                listOf(0 to 3, 1 to 1, 2 to 0, 3 to 0, 4 to 0, 5 to 1, 6 to 3).forEach { (x, top) ->
                    cell(gx + x, 5 + top, px, ink.accent, 1, 7 - 2 * top)
                }
                val m = f % 7
                val mTop = intArrayOf(3, 1, 0, 0, 0, 1, 3)[m]
                cell(gx + m, 5 + mTop, px, ink.lilac, 1, 7 - 2 * mTop)
                cell(gx, 8, px, ink.grey, 7)
                val cx = (f / 2) % 8 - 2
                drawMini(ink, cx, 0, px, eyes = 2)
            }
            // planning: a list beside it, checkmarks appear one by one
            Work.Plan -> {
                drawMini(ink, 0, 3, px, eyeDx = 1)
                val done = (f / 4) % 4
                for (i in 0..2) {
                    val y = 1 + i * 4
                    cell(13, y, px, if (i < done) ink.lilac else ink.dim, 2, 2)
                    if (i < done) cell(13, y, px, ink.body)
                }
            }
            // waiting: the cloud dozes, "z" rise above it
            Work.Wait -> {
                drawMini(ink, 0, 4, px, eyes = 1)
                val s = f % 8
                val zx = 12 + s / 3; val zy = 4 - s / 2
                if (s < 7) listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 2 to 1, 1 to 2, 0 to 3, 1 to 3, 2 to 3, 3 to 3).forEach { (a, b) -> cell(zx + a, zy + b - 2, px, ink.dim) }
            }
        }
    }
}

/** The cl0udly work indicator: the cloud at work and a label with running dots. */
@Composable
internal fun CloudThinking(work: Work, modifier: Modifier) {
    val c = Oc.c
    val dots = rememberSteps(420).value % 4
    androidx.compose.foundation.layout.Row(modifier.padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        CloudWorkGlyph(work, 32.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        Txt(work.label + ".".repeat(dots), size = 15.sp, color = c.textDim)
    }
}

/** A still mini cloud: the icon instead of the asterisk next to "Thinking". */
@Composable
fun MiniCloudIcon(size: androidx.compose.ui.unit.Dp) {
    val ink = miniInk()
    Canvas(Modifier.size(width = size, height = size * 7f / 12f)) {
        drawMini(ink, 0, 0, this.size.width / 12f)
    }
}

// ---------- anonymous chat ----------

/**
 * A ghost cloud for anonymous chat: a transparent body, only a dashed outline and white eyes.
 * [active] means anonymous mode is on: the outline is brighter. [blinks] blinks (not in the header button).
 * In the light theme the eyes are dark, otherwise they're invisible.
 */
@Composable
fun GhostCloud(size: androidx.compose.ui.unit.Dp, active: Boolean = true, eyesLook: Boolean = false, blinks: Boolean = true) {
    val c = Oc.c
    val line = if (active) c.text else c.textDim
    val eye = if (c.isDark) Color.White else c.text
    val t = rememberInfiniteTransition(label = "ghost")
    val blinkT by t.animateFloat(0f, 1f, infiniteRepeatable(keyframes { durationMillis = 5200; 0f at 0; 0f at 4900; 1f at 4980; 1f at 5080; 0f at 5160 }), label = "gblink")
    Canvas(Modifier.size(width = size, height = size * CH / CW)) {
        val k = this.size.width / CW
        val sw = (this.size.width / 22f).coerceAtLeast(1.5f)
        val inset = sw / 2
        fun X(v: Float) = v * k
        // the icon cloud's outline as one path, pixel by pixel, stroked
        val p = androidx.compose.ui.graphics.Path()
        p.moveTo(inset, X(113f))
        p.lineTo(X(152f), X(113f))
        p.lineTo(X(152f), inset)
        p.lineTo(X(635f), inset)
        p.lineTo(X(635f), X(113f))
        p.lineTo(this.size.width - inset, X(113f))
        p.lineTo(this.size.width - inset, this.size.height - inset)
        p.lineTo(inset, this.size.height - inset)
        p.close()
        val dash = sw * 1.6f
        drawPath(p, line, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, join = androidx.compose.ui.graphics.StrokeJoin.Miter,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f))))
        val ew = X(68f); val eh = X(198f) * (if (blinks && blinkT > 0.5f) 0.15f else 1f)
        val dx = if (eyesLook) X(20f) else 0f
        for (ex in floatArrayOf(245f, 430f)) drawRect(eye, Offset(X(ex) + dx, X(110f) + X(198f) - eh), Size(ew, eh))
    }
}

/** Round header button with the ghost cloud: turns anonymous chat on and off. */
@Composable
fun GhostCloudButton(active: Boolean, onClick: () -> Unit) {
    val c = Oc.c
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (active) c.text.copy(alpha = 0.12f) else c.surfaceHigh.copy(alpha = 0.7f))
            .border(1.dp, if (active) c.textDim else c.border, androidx.compose.foundation.shape.CircleShape).tap(onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { GhostCloud(26.dp, active, blinks = false) }
}
