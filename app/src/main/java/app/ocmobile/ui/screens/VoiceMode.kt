package app.ocmobile.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import app.ocmobile.Graph
import app.ocmobile.data.CHAT_DIR
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class Talk { Idle, Listening, Thinking, Speaking }

/**
 * Голосовой режим — как в Claude: экран разговора. Слушаем (SpeechRecognizer), отправляем в чат,
 * ждём ответ агента и читаем его вслух (TextToSpeech), затем снова слушаем.
 * [sessionId] = null — новый чат создаётся с первой фразой; [onClose] получает id чата, если он появился.
 */
@Composable
fun VoiceMode(sessionId: String?, onClose: (String?) -> Unit) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(sessionId) }
    var talk by remember { mutableStateOf(Talk.Idle) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(true) }
    var caption by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (!it) caption = "Microphone access is needed to talk" }

    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx) else null }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var listenNext by remember { mutableStateOf<() -> Unit>({}) }

    fun speak(text: String) {
        val t = tts
        if (!speaker || t == null || text.isBlank()) { talk = Talk.Idle; listenNext(); return }
        val clean = text.replace(Regex("```[\\s\\S]*?```"), " code omitted. ").replace(Regex("[`*#>_|\\[\\]]"), "").replace(Regex("\\((/|https?://)[^)]*\\)"), "").take(2500)
        t.language = app.ocmobile.data.Speech.ttsLocale(clean)
        talk = Talk.Speaking
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "voice")
    }

    /** Отправить фразу и дождаться ответа агента. */
    fun ask(text: String) {
        talk = Talk.Thinking; caption = text
        scope.launch {
            try {
                val id = session ?: Graph.chats.createSession(CHAT_DIR.also { if (!Graph.devices.isRemote) Graph.paths.host(it).mkdirs() }).id.also { session = it }
                val chat = Graph.chats.chat(id)
                if (chat.directory == null) chat.directory = Graph.chats.session(id)?.directory
                runCatching { chat.load() }
                val before = chat.messages.size
                Graph.chats.send(ctx, id, text, emptyList())
                // ждём, пока агент начнёт и закончит (статус сессии исчезает, когда она простаивает)
                withTimeoutOrNull(15_000) { Graph.chats.statuses.first { it[id] != null } }
                withTimeoutOrNull(10 * 60_000) { Graph.chats.statuses.first { it[id] == null } }
                delay(300)
                val reply = chat.messages.drop(before).lastOrNull { !it.info.isUser }
                val answer = reply?.let { assistantText(it) }.orEmpty().ifBlank { reply?.info?.errorMessage.orEmpty() }
                caption = answer
                speak(answer)
            } catch (e: Exception) {
                caption = e.message ?: "Something went wrong"
                talk = Talk.Idle
            }
        }
    }

    fun listen() {
        val r = recognizer ?: run { caption = "Speech recognition is not available on this device"; return }
        if (!granted) { perm.launch(Manifest.permission.RECORD_AUDIO); return }
        if (muted) { talk = Talk.Idle; return }
        talk = Talk.Listening; caption = ""
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { level = 0f }
            override fun onError(error: Int) {
                level = 0f
                // тишина или не расслышали — просто слушаем дальше
                if (talk == Talk.Listening && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) scope.launch { delay(250); listen() }
                else if (talk == Talk.Listening) talk = Talk.Idle
            }
            override fun onResults(results: Bundle?) {
                level = 0f
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isBlank()) scope.launch { delay(200); listen() } else ask(t)
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) caption = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(app.ocmobile.data.Speech.recognizerIntent())
    }
    listenNext = { listen() }

    DisposableEffect(Unit) {
        tts = TextToSpeech(ctx.applicationContext) { st ->
            if (st == TextToSpeech.SUCCESS) tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { scope.launch { if (talk == Talk.Speaking) { talk = Talk.Idle; listen() } } }
                @Deprecated("api") override fun onError(id: String?) { scope.launch { talk = Talk.Idle; listen() } }
            })
        }
        onDispose {
            recognizer?.destroy()
            tts?.stop(); tts?.shutdown()
        }
    }
    LaunchedEffect(granted) { if (granted) listen() else perm.launch(Manifest.permission.RECORD_AUDIO) }

    fun close() { recognizer?.cancel(); tts?.stop(); onClose(session) }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                VoiceRing(talk, level, Modifier.size(220.dp).tap(scale = 0.97f) {
                    when (talk) {
                        Talk.Speaking -> { tts?.stop(); talk = Talk.Idle; listen() } // перебить — как в Claude
                        Talk.Idle -> listen()
                        Talk.Listening -> recognizer?.stopListening()
                        Talk.Thinking -> {}
                    }
                })
                Spacer(Modifier.height(28.dp))
                AnimatedContent(talk, label = "talk", transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(80)) }) { t ->
                    Txt(
                        when (t) {
                            Talk.Idle -> if (session == null) "Let's talk" + Graph.prefs.userName.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty() else "Tap to talk"
                            Talk.Listening -> "Listening…"
                            Talk.Thinking -> "Thinking…"
                            Talk.Speaking -> "Tap to interrupt"
                        },
                        size = 24.sp, family = Oc.heading,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.height(96.dp).padding(horizontal = 32.dp), contentAlignment = Alignment.TopCenter) {
                    Txt(caption.takeLast(240), size = 16.sp, color = c.textDim, lineHeight = 22.sp, maxLines = 4, align = TextAlign.Center)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(if (speaker) Ic.Volume else Ic.VolumeOff, false) { speaker = !speaker; if (!speaker) tts?.stop() }
                    Spacer(Modifier.weight(1f))
                    // микрофон: выключить/включить
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(if (muted) c.danger.copy(alpha = .18f) else c.surfaceHigh).tap {
                            muted = !muted
                            if (muted) { recognizer?.cancel(); if (talk == Talk.Listening) talk = Talk.Idle } else if (talk == Talk.Idle) listen()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Ico(if (muted) Ic.MicOff else Ic.Mic, tint = if (muted) c.danger else c.text, size = 26.dp) }
                    Spacer(Modifier.weight(1f))
                    RoundBtn(Ic.Close, true) { close() }
                }
            }
        }
    }
}

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: () -> Unit) {
    val c = Oc.c
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(if (filled) c.text else c.surfaceHigh).border(1.dp, if (filled) c.text else c.border, CircleShape).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = if (filled) c.bg else c.text, size = 22.dp) }
}

/**
 * Кольцо из лучей — без свечений, только линии. Слушаем: лучи вытягиваются от громкости голоса;
 * думаем: по кругу бежит светлая волна; говорим: кольцо мягко «дышит» волнами.
 */
@Composable
private fun VoiceRing(talk: Talk, level: Float, modifier: Modifier) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "ring")
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "ph")
    val lv by animateFloatAsState(level, tween(90), label = "lv")
    val accent = if (Oc.edition == "claude" || Oc.edition == "notebook") c.accent else c.link
    Canvas(modifier) {
        val n = 56
        val cx = size.width / 2; val cy = size.height / 2
        val r0 = size.minDimension * 0.26f
        val maxLen = size.minDimension * 0.22f
        for (i in 0 until n) {
            val a = i * 2 * PI / n
            val wave = sin(a * 3 + ph * 2 * PI).toFloat()
            val k = when (talk) {
                Talk.Idle -> 0.18f + 0.04f * wave
                Talk.Listening -> 0.16f + lv * (0.55f + 0.45f * ((sin(a * 5 - ph * 4 * PI).toFloat() + 1f) / 2f))
                Talk.Thinking -> 0.14f + 0.3f * ((cos(a - ph * 2 * PI).toFloat() + 1f) / 2f).let { it * it * it }
                Talk.Speaking -> 0.22f + 0.3f * ((sin(a * 4 + ph * 6 * PI).toFloat() + 1f) / 2f) * ((sin(ph * 10 * PI).toFloat() + 1.4f) / 2.4f)
            }
            val len = maxLen * k
            val from = Offset(cx + cos(a).toFloat() * r0, cy + sin(a).toFloat() * r0)
            val to = Offset(cx + cos(a).toFloat() * (r0 + len), cy + sin(a).toFloat() * (r0 + len))
            val col = when (talk) {
                Talk.Thinking -> accent.copy(alpha = 0.35f + 0.65f * ((cos(a - ph * 2 * PI).toFloat() + 1f) / 2f))
                Talk.Idle -> c.textDim
                else -> accent
            }
            drawLine(col, from, to, size.minDimension * 0.014f, StrokeCap.Round)
        }
    }
}
