package app.ocmobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.ocmobile.ui.theme.Oc

/** Пиксельная надпись «opencode»: «open» тусклее, «code» ярче; нижняя часть букв чуть темнее. */
private val glyphs = mapOf(
    'o' to listOf("....", "XXXX", "X..X", "X..X", "X..X", "XXXX", "....", "...."),
    'p' to listOf("....", "XXXX", "X..X", "X..X", "X..X", "XXXX", "X...", "X..."),
    'e' to listOf("....", "XXXX", "X..X", "XXXX", "X...", "XXXX", "....", "...."),
    'n' to listOf("....", "XXX.", "X..X", "X..X", "X..X", "X..X", "....", "...."),
    'c' to listOf("....", "XXXX", "X...", "X...", "X...", "XXXX", "....", "...."),
    'd' to listOf("...X", "...X", "XXXX", "X..X", "X..X", "XXXX", "....", "...."),
)

private const val WORD = "opencode"
private const val COLS = WORD.length * 4 + (WORD.length - 1)
private const val ROWS = 8

private val eggMs = intArrayOf(900, 700, 1100, 800, 900)

private fun hash(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

/**
 * Пасхалки: каждое нажатие на надпись запускает новый номер — волна, скан, разлёт, помехи, сборка;
 * на седьмое надпись на секунду становится «Big Pickle». Всё плоским цветом, без свечения.
 */
@Composable
fun PixelWordmark(modifier: Modifier = Modifier, animate: Boolean = true, eggs: Boolean = true) {
    val c = Oc.c
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptic = rememberHaptic()
    var taps by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var kind by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var pickle by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val burst = remember { Animatable(1f) }
    fun poke() {
        haptic()
        taps++
        kind = (taps - 1) % 5
        scope.launch {
            burst.snapTo(0f)
            if (taps % 7 == 0) { pickle = true; launch { delay(1700); pickle = false } }
            burst.animateTo(1f, tween(eggMs[kind], easing = LinearEasing))
        }
    }
    val enter = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) enter.animateTo(1f, tween(1100, easing = LinearEasing)) }
    val t = rememberInfiniteTransition(label = "sweep")
    val sweep by t.animateFloat(-0.25f, 1.25f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "x")

    val dim = c.textDim
    val bright = c.text
    val green = c.ok
    val accent = c.accent
    val link = c.link
    val tapMod = if (eggs) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { poke() } else Modifier
    Canvas(modifier.then(tapMod).fillMaxWidth().aspectRatio(COLS / ROWS.toFloat())) {
        val cell = size.width / COLS
        val e = enter.value
        WORD.forEachIndexed { li, ch ->
            val g = glyphs.getValue(ch)
            val x0 = li * 5
            val tone = if (li < 4) dim else bright
            for (r in 0 until ROWS) for (k in 0 until 4) {
                if (g[r].getOrNull(k) != 'X') continue
                val col = x0 + k
                // вход: клетки «собираются» волной слева направо с небольшим перелётом
                val start = (col / COLS.toFloat()) * 0.55f + (r / ROWS.toFloat()) * 0.2f
                val local = ((e - start) / 0.3f).coerceIn(0f, 1f)
                if (local <= 0f) continue
                val back = local * local * (2.7f * local - 1.7f)
                val s = if (local >= 1f) 1f else back.coerceIn(0f, 1.15f)
                // нижняя половина букв темнее; по надписи изредка скользит светлая полоса
                var colr: Color = if (r >= 4) lerp(tone, c.bg, 0.45f) else tone
                val d = kotlin.math.abs(col / COLS.toFloat() - sweep)
                if (d < 0.07f) colr = lerp(colr, c.text, (1f - d / 0.07f) * 0.55f)
                var side = (cell + 0.8f) * s
                var ox = 0f; var oy = 0f
                val p = burst.value
                if (kind >= 0 && p < 1f) when (kind) {
                    0 -> { // волна: буквы по очереди подпрыгивают
                        val ph = (p * 1.7f - li * 0.1f).coerceIn(0f, 1f)
                        oy = -kotlin.math.sin(ph * Math.PI.toFloat()) * cell * 2.4f
                    }
                    1 -> { // скан: полоса пробегает слева направо и меняет «open» и «code» местами
                        val x = p * 1.4f - 0.2f
                        val fx = col / COLS.toFloat()
                        val sw = if (fx < x) (if (li < 4) bright else dim) else tone
                        colr = if (r >= 4) lerp(sw, c.bg, 0.45f) else sw
                        if (kotlin.math.abs(fx - x) < 0.05f) colr = link
                    }
                    2 -> { // разлёт и сборка
                        val h = hash(col, r); val h2 = hash(r, col + 99)
                        val out = if (p < 0.35f) 1f - (1f - p / 0.35f).let { it * it } else { val q = (p - 0.35f) / 0.65f; 1f - q * q * (3f - 2f * q) }
                        val a = h * 6.2831855f
                        ox = kotlin.math.cos(a) * out * cell * (5f + 12f * h2); oy = kotlin.math.sin(a) * out * cell * (5f + 12f * h2)
                        side *= 1f - out * 0.55f
                    }
                    3 -> { // помехи: строки сдвигаются рывками
                        val step = (p * 14f).toInt()
                        val hv = hash(r, step)
                        if (p < 0.85f && hv > 0.5f) { ox = (hash(step, r + 7) - 0.5f) * cell * 10f; colr = accent }
                    }
                    4 -> { // сборка: слой за слоем снизу вверх
                        val need = (ROWS - 1 - r) / ROWS.toFloat() * 0.75f + hash(col, r) * 0.15f
                        val q = ((p - need) / 0.25f).coerceIn(0f, 1f)
                        if (q <= 0f) continue
                        oy = (1f - q) * cell * 3f
                        side *= 0.4f + 0.6f * q
                        if (q < 1f) colr = c.text
                    }
                }
                if (pickle) colr = if (r >= 4) lerp(green, c.bg, 0.4f) else green
                drawRect(colr, Offset(col * cell + (cell - side) / 2 + ox, r * cell + (cell - side) / 2 + oy), Size(side, side))
            }
        }
    }
}
