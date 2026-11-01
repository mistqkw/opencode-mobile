package app.ocmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ocmobile.R
import androidx.compose.ui.unit.Density
import app.ocmobile.Graph

@Immutable
class OcColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val textDim: Color,
    val border: Color,
    val accent: Color,
    val link: Color,
    val toggleOn: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val danger: Color,
    val ok: Color,
    val warn: Color,
    val userBubble: Color,
    val codeBg: Color,
    val scrim: Color,
)

// The palette is taken from screenshots of mobile Claude: warm near-black, graphite cards,
// coral accent and blue indicators.
val DarkColors = OcColors(
    isDark = true,
    bg = Color(0xFF141414), surface = Color(0xFF202020), surfaceHigh = Color(0xFF2B2B2A),
    text = Color(0xFFF5F4EF), textDim = Color(0xFF9B9A94), border = Color(0xFF3C3B38),
    accent = Color(0xFFD97757), link = Color(0xFF5EA0F2), toggleOn = Color(0xFF2B7BDC),
    progressTrack = Color(0xFF0B2547), progressFill = Color(0xFF2F7DE1),
    danger = Color(0xFFE5645C), ok = Color(0xFF5CB885), warn = Color(0xFFE0A030),
    userBubble = Color(0xFF282827), codeBg = Color(0xFF1A1A19), scrim = Color(0x99000000),
)

val LightColors = OcColors(
    isDark = false,
    bg = Color(0xFFFAF9F5), surface = Color(0xFFF0EEE6), surfaceHigh = Color(0xFFE6E3D8),
    text = Color(0xFF1F1E1D), textDim = Color(0xFF73726C), border = Color(0xFFD6D3C7),
    accent = Color(0xFFC6613F), link = Color(0xFF1F6FD6), toggleOn = Color(0xFF2B7BDC),
    progressTrack = Color(0xFFD6E4F7), progressFill = Color(0xFF2F7DE1),
    danger = Color(0xFFC8433B), ok = Color(0xFF2E8B57), warn = Color(0xFFB87A10),
    userBubble = Color(0xFFEAE7DC), codeBg = Color(0xFFEFEDE4), scrim = Color(0x66000000),
)

// OpenCode desktop: tokens taken from the app's stylesheets (dark #151515, text #ededed, accent #9dbefe, Inter).
val DesktopDark = OcColors(
    isDark = true,
    bg = Color(0xFF151515), surface = Color(0xFF1C1C1C), surfaceHigh = Color(0xFF262626),
    text = Color(0xFFEDEDED), textDim = Color(0xFF9B9B9B), border = Color(0xFF2C2C2C),
    accent = Color(0xFF9DBEFE), link = Color(0xFF9DBEFE), toggleOn = Color(0xFF5B8DEF),
    progressTrack = Color(0xFF091F52), progressFill = Color(0xFF9DBEFE),
    danger = Color(0xFFFC533A), ok = Color(0xFF35C02D), warn = Color(0xFFF5B238),
    userBubble = Color(0xFF222222), codeBg = Color(0xFF101010), scrim = Color(0x99000000),
)

val DesktopLight = OcColors(
    isDark = false,
    bg = Color(0xFFFCFCFC), surface = Color(0xFFF4F4F4), surfaceHigh = Color(0xFFEAEAEA),
    text = Color(0xFF171717), textDim = Color(0xFF6F6F6F), border = Color(0xFFDADADA),
    accent = Color(0xFF3D6FE8), link = Color(0xFF3D6FE8), toggleOn = Color(0xFF3D6FE8),
    progressTrack = Color(0xFFECF3FF), progressFill = Color(0xFF3D6FE8),
    danger = Color(0xFFD93A22), ok = Color(0xFF2A9D24), warn = Color(0xFFB87A10),
    userBubble = Color(0xFFF0F0F0), codeBg = Color(0xFFF4F4F4), scrim = Color(0x66000000),
)

// OpenCode in the terminal: near-black, peach accent and a blue bar on the left, like the TUI.
val TerminalDark = OcColors(
    isDark = true,
    bg = Color(0xFF0A0A0A), surface = Color(0xFF141414), surfaceHigh = Color(0xFF1E1E1E),
    text = Color(0xFFEEEEEE), textDim = Color(0xFF808080), border = Color(0xFF3C3C3C),
    accent = Color(0xFFFAB283), link = Color(0xFF5C9CF5), toggleOn = Color(0xFF5C9CF5),
    progressTrack = Color(0xFF14243E), progressFill = Color(0xFF5C9CF5),
    danger = Color(0xFFE06C75), ok = Color(0xFF7FD88F), warn = Color(0xFFF5A742),
    userBubble = Color(0xFF141414), codeBg = Color(0xFF0F0F0F), scrim = Color(0x99000000),
)

val TerminalLight = OcColors(
    isDark = false,
    bg = Color(0xFFFFFFFF), surface = Color(0xFFF3F3F3), surfaceHigh = Color(0xFFE8E8E8),
    text = Color(0xFF1A1A1A), textDim = Color(0xFF8A8A8A), border = Color(0xFFC8C8C8),
    accent = Color(0xFFD68C27), link = Color(0xFF3B7DD8), toggleOn = Color(0xFF3B7DD8),
    progressTrack = Color(0xFFDCE8F8), progressFill = Color(0xFF3B7DD8),
    danger = Color(0xFFD1383D), ok = Color(0xFF3D9A57), warn = Color(0xFFB87A10),
    userBubble = Color(0xFFF3F3F3), codeBg = Color(0xFFF3F3F3), scrim = Color(0x66000000),
)

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_semibold, FontWeight.Bold),
)

/** Serif for replies and headings in the Claude edition: Source Serif 4 (OFL), the closest to Claude's font. */
val SerifFamily = FontFamily(
    Font(R.font.source_serif_regular, FontWeight.Normal),
    Font(R.font.source_serif_semibold, FontWeight.Medium),
    Font(R.font.source_serif_semibold, FontWeight.SemiBold),
    Font(R.font.source_serif_semibold, FontWeight.Bold),
)
val SerifDisplay = FontFamily(Font(R.font.source_serif_display, FontWeight.Normal), Font(R.font.source_serif_semibold, FontWeight.SemiBold))

val LocalOc = staticCompositionLocalOf { DarkColors }

object Oc {
    val c: OcColors @Composable @ReadOnlyComposable get() = LocalOc.current

    /** Headings are serif, like Claude; in "System" the system font. */
    val edition: String @Composable @ReadOnlyComposable get() = Graph.prefs.edition
    val terminal: Boolean @Composable @ReadOnlyComposable get() = Graph.prefs.edition == "terminal"
    val desktop: Boolean @Composable @ReadOnlyComposable get() = Graph.prefs.edition == "desktop"

    /** The current look (palettes, fonts, message style). */
    val look: Look @Composable @ReadOnlyComposable get() = Looks.of(Graph.prefs.edition)
    /** The Claude frame: every look except Terminal and Desktop. */
    val claudeLike: Boolean @Composable @ReadOnlyComposable get() = Looks.of(Graph.prefs.edition).frame == "claude"

    val heading: FontFamily
        @Composable @ReadOnlyComposable get() = when (Graph.prefs.edition) {
            "terminal" -> FontFamily.Monospace
            "desktop", "chatgpt", "kimi", "cloud", "cards", "messenger" -> InterFamily
            "hairline" -> UnboundedFamily
            "notebook" -> SerifDisplay
            else -> if (Graph.prefs.fontStyle == "default") SerifDisplay else FontFamily.Default
        }
    val body: FontFamily
        @Composable @ReadOnlyComposable get() = when (Graph.prefs.edition) {
            "terminal" -> FontFamily.Monospace
            "notebook" -> SerifFamily
            "messenger" -> FontFamily.Default
            else -> if (Graph.prefs.fontStyle == "default" || Graph.prefs.edition != "claude") InterFamily else FontFamily.Default
        }
    /** Reply font: serif in Claude and Notebook. */
    val reply: FontFamily
        @Composable @ReadOnlyComposable get() = when (Graph.prefs.edition) {
            "claude" -> if (Graph.prefs.fontStyle == "default") SerifFamily else FontFamily.Default
            "notebook" -> SerifFamily
            else -> body
        }
    /** Small labels: mono in Hairline. */
    val label: FontFamily
        @Composable @ReadOnlyComposable get() = if (Graph.prefs.edition == "hairline") JetBrainsMono else body
    val mono: FontFamily
        @Composable @ReadOnlyComposable get() = if (Graph.prefs.edition == "terminal" || Graph.prefs.codeFont == "mono") (if (Graph.prefs.edition == "hairline") JetBrainsMono else FontFamily.Monospace) else Oc.body

    /** Corner radius: soft for Claude, restrained for desktop, square for terminal; new looks use their own multiplier. */
    @Composable @ReadOnlyComposable
    fun round(r: Dp): Shape = when (Graph.prefs.edition) {
        "terminal" -> RoundedCornerShape(0.dp)
        "desktop" -> RoundedCornerShape((r * 0.4f).coerceIn(6.dp, 14.dp))
        else -> Looks.of(Graph.prefs.edition).let { l -> RoundedCornerShape((r * l.radius).let { x -> l.radiusMax?.let { x.coerceAtMost(it.dp) } ?: x }) }
    }

    /** Pills and round buttons: rectangles in terminal, rounded rectangles in desktop and hairline. */
    val pill: Shape
        @Composable @ReadOnlyComposable get() = when (Graph.prefs.edition) {
            "terminal" -> RoundedCornerShape(0.dp)
            "desktop" -> RoundedCornerShape(10.dp)
            else -> Looks.of(Graph.prefs.edition).pill?.let { RoundedCornerShape(it.dp) } ?: CircleShape
        }
}

@Composable
fun OcTheme(content: @Composable () -> Unit) {
    val mode = Graph.prefs.colorMode
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val look = Looks.of(Graph.prefs.edition)
    val colors = if (dark) look.dark else look.light
    // Claude's fonts are noticeably smaller than my originals: a base factor of 0.88 taken from phone screenshots.
    val scale = when (Graph.prefs.textSize) { "small" -> 0.8f; "large" -> 1f; else -> 0.88f }
    val ui = (Graph.prefs.uiScale.toFloatOrNull() ?: 100f) / 100f
    val d = LocalDensity.current
    val scheme = if (dark) darkColorScheme(
        background = colors.bg, surface = colors.surface, onSurface = colors.text, onBackground = colors.text,
        primary = colors.link, surfaceContainer = colors.bg, surfaceContainerHigh = colors.surface,
    ) else lightColorScheme(
        background = colors.bg, surface = colors.surface, onSurface = colors.text, onBackground = colors.text,
        primary = colors.link, surfaceContainer = colors.bg, surfaceContainerHigh = colors.surface,
    )
    CompositionLocalProvider(
        LocalOc provides colors,
        LocalDensity provides Density(d.density * ui, d.fontScale * scale),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
