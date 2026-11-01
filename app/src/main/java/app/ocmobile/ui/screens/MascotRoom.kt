package app.ocmobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.CH
import app.ocmobile.ui.components.CW
import app.ocmobile.ui.components.CloudMood
import app.ocmobile.ui.components.CloudTalk
import app.ocmobile.ui.components.INK
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.PixelCloud
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.drawIconCloud
import app.ocmobile.ui.components.rememberHaptic
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.screenBackdrop
import kotlinx.coroutines.delay

private val LILAC = Color(0xFF9F9DF2)

/**
 * The mascot's room: you can chat with it (it answers taps, pets, sleep), see
 * how many times it was petted, and play ping-pong while the agent works. If a task is running, a
 * "back to chat" chip sits on top.
 */
@Composable
fun MascotRoom(nav: Nav) {
    val c = Oc.c
    var playing by remember { mutableStateOf(false) }
    val statuses by Graph.chats.statuses.collectAsState()
    val busyId = statuses.entries.firstOrNull { it.value == "busy" || it.value == "retry" }?.key
    Column(Modifier.fillMaxSize().screenBackdrop("MascotRoom")) {
        TopBar(if (playing) "Ping-pong" else "cl0udly", left = { IconBtn(Ic.Back, { if (playing) playing = false else nav.pop() }) })
        if (busyId != null) Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(14.dp)).border(1.dp, c.border, Oc.round(14.dp))
                .tap(scale = 0.98f) { nav.openChat(busyId) }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            app.ocmobile.ui.components.Spinner(12.dp, color = c.accent, stroke = 1.5.dp)
            Spacer(Modifier.width(10.dp))
            Txt("Your task is running", size = 14.sp, color = c.textDim, modifier = Modifier.weight(1f))
            Txt("Back to chat", size = 14.sp, color = c.accent)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (playing) Pong() else Room { playing = true }
        }
    }
}

@Composable
private fun Room(onPlay: () -> Unit) {
    val c = Oc.c
    var line by remember { mutableStateOf(CloudTalk.hello.random()) }
    var said by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pets by remember { mutableIntStateOf(Graph.prefs.petCount.toIntOrNull() ?: 0) }
    fun say(s: String?) { if (s != null) { line = s; said = System.currentTimeMillis() } }
    // it chats to itself if we stay quiet for long
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            if (System.currentTimeMillis() - said > 14_000) say(CloudTalk.idle.random())
        }
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // a line of speech: changes quickly, a bubble above the mascot
        AnimatedContent(line, label = "say", transitionSpec = { (fadeIn(tween(90)) + scaleIn(tween(120), initialScale = 0.9f)) togetherWith fadeOut(tween(60)) }) { t ->
            Box(
                Modifier.padding(horizontal = 32.dp).widthIn(max = 300.dp).heightIn(min = 48.dp).clip(Oc.round(16.dp)).background(c.surface)
                    .border(1.dp, c.border, Oc.round(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(t, size = 16.sp, align = TextAlign.Center, lineHeight = 22.sp) }
        }
        Spacer(Modifier.height(36.dp))
        PixelCloud(Modifier.size(width = 200.dp, height = 136.dp), onReact = { e ->
            if (e == "pet") pets++
            say(CloudTalk.of(e))
        })
        Spacer(Modifier.height(40.dp))
        Txt("Petted $pets ${if (pets == 1) "time" else "times"} · best rally ${Graph.prefs.pongBest}", size = 13.sp, color = c.textDim)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.clip(CircleShape).background(c.text).tap(scale = 0.96f, onClick = onPlay).padding(horizontal = 22.dp, vertical = 12.dp)) {
            Txt("Play ping-pong", size = 16.sp, color = c.bg, weight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        Txt("Tap, hold, pet or shake the phone", size = 13.sp, color = c.textDim)
    }
}

/**
 * Ping-pong: the mascot is the top paddle, you are the bottom one (drag with a finger). The ball speeds up with every
 * hit; the mascot is quick but not almighty. Miss and it cheers, score on it and it gets angry.
 */
@Composable
private fun Pong() {
    val c = Oc.c
    val haptic = rememberHaptic()
    val d = LocalDensity.current
    var w by remember { mutableFloatStateOf(0f) }
    var h by remember { mutableFloatStateOf(0f) }
    var bx by remember { mutableFloatStateOf(0f) }; var by by remember { mutableFloatStateOf(0f) }
    var vx by remember { mutableFloatStateOf(0f) }; var vy by remember { mutableFloatStateOf(0f) }
    var me by remember { mutableFloatStateOf(0.5f) }     // paddle center, as a share of the width
    var cloud by remember { mutableFloatStateOf(0.5f) }
    var you by remember { mutableIntStateOf(0) }
    var him by remember { mutableIntStateOf(0) }
    var rally by remember { mutableIntStateOf(0) }
    var mood by remember { mutableStateOf(CloudMood.Idle) }
    var line by remember { mutableStateOf("Ready? Drag to move.") }
    LaunchedEffect(mood) { if (mood != CloudMood.Idle) { delay(900); mood = CloudMood.Idle } }
    var serveAt by remember { mutableLongStateOf(System.currentTimeMillis() + 900) }
    val ball = with(d) { 12.dp.toPx() }
    val padH = with(d) { 14.dp.toPx() }
    val bottomGap = with(d) { 72.dp.toPx() }
    val topGap = with(d) { 16.dp.toPx() }

    fun serve(down: Boolean) {
        bx = w / 2; by = h * 0.45f
        val speed = h * 0.55f
        vx = speed * (if (Math.random() < 0.5) -0.45f else 0.45f); vy = if (down) speed else -speed
    }

    LaunchedEffect(w, h) {
        if (w == 0f) return@LaunchedEffect
        serve(true); vx = 0f; vy = 0f
        var last = 0L
        while (true) {
            val t = withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((t - last) / 1e9f).coerceAtMost(0.033f)
            last = t
            val now = System.currentTimeMillis()
            if (vx == 0f && vy == 0f) { if (now >= serveAt) serve(true) else continue }
            // the mascot reaches for the ball, but at a limited speed, so it can be beaten
            val target = (bx / w).coerceIn(0.16f, 0.84f)
            val maxStep = (0.7f + rally * 0.03f).coerceAtMost(1.15f) * dt
            cloud += (target - cloud).coerceIn(-maxStep, maxStep)
            bx += vx * dt; by += vy * dt
            if (bx < 0) { bx = 0f; vx = -vx }
            if (bx > w - ball) { bx = w - ball; vx = -vx }
            val cw = w * 0.28f; val ch = cw * CH / CW
            val cloudBottom = topGap + ch
            val padTop = h - bottomGap
            val padW = w * 0.26f
            // off the mascot (the bottom of the cloud)
            if (vy < 0 && by <= cloudBottom && by > cloudBottom - ball * 2 && bx + ball > cloud * w - cw / 2 && bx < cloud * w + cw / 2) {
                by = cloudBottom; vy = -vy * 1.05f; vx += ((bx + ball / 2) / w - cloud) * w * 2.2f; rally++
                mood = CloudMood.Happy
            }
            // off the player's paddle
            if (vy > 0 && by + ball >= padTop && by + ball < padTop + padH * 2 && bx + ball > me * w - padW / 2 && bx < me * w + padW / 2) {
                by = padTop - ball; vy = -vy * 1.06f; vx += ((bx + ball / 2) / w - me) * w * 2.6f; rally++
                haptic(); mood = CloudMood.Surprised
            }
            vx = vx.coerceIn(-w * 1.4f, w * 1.4f)
            // point
            if (by > h || by < -ball * 2) {
                val cloudScored = by > h
                if (cloudScored) { him++; mood = CloudMood.Happy; line = CloudTalk.won.random() }
                else { you++; mood = CloudMood.Angry; line = CloudTalk.lost.random(); haptic() }
                if (rally > (Graph.prefs.pongBest.toIntOrNull() ?: 0)) Graph.prefs.pongBest = rally.toString()
                rally = 0
                vx = 0f; vy = 0f; bx = w / 2; by = h * 0.45f
                serveAt = now + 900
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { w = it.width.toFloat(); h = it.height.toFloat() }.pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    me = (first.position.x / size.width).coerceIn(0.13f, 0.87f)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        me = (ch.position.x / size.width).coerceIn(0.13f, 0.87f)
                    }
                }
            },
        ) {
            if (w == 0f) return@Canvas
            // middle of the field: a dashed line
            var x = 0f
            while (x < size.width) { drawRect(c.border, Offset(x, size.height * 0.5f), Size(ball, 2f)); x += ball * 2 }
            // the mascot paddle, eyes follow the ball
            val cw = size.width * 0.28f
            drawIconCloud(
                cloud * size.width - cw / 2, topGap, cw, mood, (((bx / size.width) - cloud) * 0.08f).coerceIn(-0.035f, 0.035f), 0f,
                if (c.isDark) c.bg else INK, eyeDy = 0.05f, outline = if (c.isDark) null else INK,
            )
            // player's paddle and the ball
            val padW = size.width * 0.26f
            drawRect(c.text, Offset(me * size.width - padW / 2, size.height - bottomGap), Size(padW, padH))
            drawRect(LILAC, Offset(bx, by), Size(ball, ball))
        }
        // score and speech line
        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp)) {
            Txt("$him", size = 28.sp, color = c.textDim, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(40.dp))
            Txt("$you", size = 28.sp, color = c.text, weight = FontWeight.SemiBold)
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp), horizontalAlignment = Alignment.End) {
            Txt("rally $rally", size = 13.sp, color = c.textDim)
        }
        AnimatedContent(line, label = "pong-say", modifier = Modifier.align(Alignment.Center).padding(top = 120.dp), transitionSpec = { fadeIn(tween(90)) togetherWith fadeOut(tween(60)) }) { t ->
            Txt(t, size = 15.sp, color = c.textDim)
        }
    }
}
