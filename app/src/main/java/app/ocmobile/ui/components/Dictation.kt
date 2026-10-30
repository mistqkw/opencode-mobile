package app.ocmobile.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.ocmobile.Graph
import app.ocmobile.data.Speech
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay

/**
 * Диктовка прямо в поле ввода — как в Claude: вместо системного окна полоса записи с живой волной,
 * текст появляется по ходу речи. Распознаватель сам останавливается после паузы — перезапускаем его,
 * пока пользователь не нажмёт «готово» или «отправить».
 */
class Dictation(private val ctx: Context) {
    var active by mutableStateOf(false)
        private set
    /** Уже распознанное и текущий кусок. */
    var done by mutableStateOf("")
        private set
    var partial by mutableStateOf("")
        private set
    val levels = mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } }
    var startedAt by mutableLongStateOf(0L)
        private set
    private var rec: SpeechRecognizer? = null
    private var lastPush = 0L

    val text get() = listOf(done, partial).filter { it.isNotBlank() }.joinToString(" ")

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { Graph.chats.toasts.tryEmit("No speech recognizer on this device"); return }
        done = ""; partial = ""; active = true; startedAt = System.currentTimeMillis()
        for (i in levels.indices) levels[i] = 0f
        rec = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener) }
        listen()
    }

    private fun listen() { if (active) runCatching { rec?.startListening(Speech.recognizerIntent()) } }

    /** Остановить и вернуть всё распознанное. */
    fun finish(): String { val t = text; stop(); return t }

    fun stop() {
        active = false
        rec?.let { runCatching { it.cancel(); it.destroy() } }
        rec = null
    }

    private fun push(v: Float) {
        val now = System.currentTimeMillis()
        if (now - lastPush < 55) { levels[levels.lastIndex] = maxOf(levels.last(), v); return }
        lastPush = now
        levels.removeAt(0); levels.add(v)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { push(((rmsdB + 2f) / 12f).coerceIn(0.04f, 1f)) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            if (!active) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> listen()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { Graph.chats.toasts.tryEmit("Microphone access is needed"); stop() }
                else -> { Graph.chats.toasts.tryEmit("Speech recognition stopped (error $error)"); stop() }
            }
        }
        override fun onResults(results: Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (t.isNotBlank()) done = listOf(done, t).filter { it.isNotBlank() }.joinToString(" ")
            partial = ""
            listen()
        }
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object { const val BARS = 36 }
}

@Composable
fun rememberDictation(): Pair<Dictation, () -> Unit> {
    val ctx = LocalContext.current
    val d = remember { Dictation(ctx) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) d.start() else Graph.chats.toasts.tryEmit("Microphone access is needed to dictate")
    }
    DisposableEffect(Unit) { onDispose { d.stop() } }
    val start = {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) d.start()
        else perm.launch(Manifest.permission.RECORD_AUDIO)
    }
    return d to start
}

/** Полоса записи: отмена, волна, время, «вставить» и «отправить». */
@Composable
fun DictationBar(d: Dictation, onCancel: () -> Unit, onDone: () -> Unit, onSend: () -> Unit) {
    val c = Oc.c
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(d.active) { while (d.active) { now = System.currentTimeMillis(); delay(250) } }
    val secs = ((now - d.startedAt) / 1000).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ic.Close, onCancel, size = 40.dp, bg = c.surfaceHigh, tint = c.textDim)
        Spacer(Modifier.width(12.dp))
        val bar = c.text
        Canvas(Modifier.weight(1f).height(32.dp)) {
            val n = d.levels.size
            val step = size.width / n
            val w = (step * 0.5f).coerceAtMost(4.dp.toPx())
            d.levels.forEachIndexed { i, v ->
                val h = (size.height * v).coerceAtLeast(w)
                drawRoundRect(bar.copy(alpha = 0.35f + 0.65f * (i.toFloat() / n)), Offset(i * step + (step - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
            }
        }
        Spacer(Modifier.width(10.dp))
        Txt("%d:%02d".format(secs / 60, secs % 60), size = 14.sp, color = c.textDim, family = Oc.mono)
        Spacer(Modifier.width(10.dp))
        IconBtn(Ic.Check, onDone, size = 40.dp, bg = c.surfaceHigh)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp).clip(Oc.pill).background(c.text).tap(onClick = onSend), contentAlignment = Alignment.Center) {
            Ico(Ic.ArrowUp, tint = c.bg, size = 20.dp)
        }
    }
}
