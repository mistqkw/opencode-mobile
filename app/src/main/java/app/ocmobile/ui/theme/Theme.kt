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
import androidx.compose.ui.text.font.FontFamily
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

// Палитра снята со скриншотов мобильного Claude: тёплый почти-чёрный, графитовые карточки,
// коралловый акцент и синие индикаторы.
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

val LocalOc = staticCompositionLocalOf { DarkColors }

object Oc {
    val c: OcColors @Composable @ReadOnlyComposable get() = LocalOc.current

    /** Заголовки — с засечками, как в Claude; в «System» — системный шрифт. */
    val heading: FontFamily
        @Composable @ReadOnlyComposable get() = if (Graph.prefs.fontStyle == "default") FontFamily.Serif else FontFamily.Default
    val body: FontFamily @Composable @ReadOnlyComposable get() = FontFamily.Default
    val mono: FontFamily
        @Composable @ReadOnlyComposable get() = if (Graph.prefs.codeFont == "mono") FontFamily.Monospace else FontFamily.Default
}

@Composable
fun OcTheme(content: @Composable () -> Unit) {
    val mode = Graph.prefs.colorMode
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) DarkColors else LightColors
    // Шрифты Claude заметно мельче моих исходных: базовый множитель 0.88 снят по скриншотам с телефона.
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
