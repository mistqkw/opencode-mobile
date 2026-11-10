package app.ocmobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.ocmobile.ui.theme.Looks
import app.ocmobile.ui.theme.OcColors
import org.junit.Assert.assertTrue
import org.junit.Test

/** Secondary text in cl0udly stays readable (WCAG AA, 4.5:1) on every background it sits on, in both themes. */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, c: OcColors) {
        listOf("bg" to c.bg, "surface" to c.surface, "surfaceHigh" to c.surfaceHigh, "userBubble" to c.userBubble).forEach { (where, bg) ->
            val dim = ratio(c.textDim, bg)
            assertTrue("$name textDim on $where is %.2f:1".format(dim), dim >= 4.5)
            val main = ratio(c.text, bg)
            assertTrue("$name text on $where is %.2f:1".format(main), main >= 4.5)
            // secondary text never outshines the main text
            assertTrue("$name textDim is brighter than text on $where", dim <= main)
        }
    }

    @Test fun cloudDark() = check("cloud dark", Looks.cloud.dark)
    @Test fun cloudLight() = check("cloud light", Looks.cloud.light)
}
