package app.ocmobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@Composable
fun PixelWordmark(modifier: Modifier = Modifier, animate: Boolean = true) {
    val c = Oc.c
    val enter = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) enter.animateTo(1f, tween(1100, easing = LinearEasing)) }
    val t = rememberInfiniteTransition(label = "sweep")
    val sweep by t.animateFloat(-0.25f, 1.25f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "x")

    val dim = c.textDim
    val bright = c.text
    Canvas(modifier.fillMaxWidth().aspectRatio(COLS / ROWS.toFloat())) {
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
                val side = (cell + 0.8f) * s
                drawRect(colr, Offset(col * cell + (cell - side) / 2, r * cell + (cell - side) / 2), Size(side, side))
            }
        }
    }
}
