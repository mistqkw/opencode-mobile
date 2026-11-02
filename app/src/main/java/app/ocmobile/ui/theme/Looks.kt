package app.ocmobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.ocmobile.R

/**
 * Interface look ("edition"). Claude, Terminal and Desktop are the original three; the rest are built
 * on the Claude frame and differ in palette, fonts, corner radius and how messages look.
 */
class Look(
    val id: String,
    val title: String,
    val sub: String,
    val dark: OcColors,
    val light: OcColors,
    /** Screen frame: claude | terminal | desktop. */
    val frame: String = "claude",
    /** One-line input, model in the top bar (ChatGPT, Kimi). */
    val compact: Boolean = false,
    /** User message: bubble | tail (like a messenger) | plain (label and text) | card | line (1px border). */
    val user: String = "bubble",
    /** Reply: plain | bubble | card. */
    val reply: String = "plain",
    /** Corner radius multiplier and cap (null means no cap). */
    val radius: Float = 1f,
    val radiusMax: Float? = null,
    /** Pill radius; null means a circle. */
    val pill: Float? = null,
    /** Color of the round send/voice button; null means the text color. */
    val sendColor: ((OcColors) -> Color)? = null,
)

private fun v(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Unbounded: a wide grotesque for Hairline headings (OFL). */
val UnboundedFamily = FontFamily(v(R.font.unbounded, 400), v(R.font.unbounded, 500), v(R.font.unbounded, 600), v(R.font.unbounded, 700))
/** JetBrains Mono: Hairline's mono labels (OFL). */
val JetBrainsMono = FontFamily(v(R.font.jetbrains_mono, 400), v(R.font.jetbrains_mono, 500), v(R.font.jetbrains_mono, 600), v(R.font.jetbrains_mono, 700))

private fun pal(
    dark: Boolean, bg: Long, surface: Long, high: Long, text: Long, dim: Long, border: Long, accent: Long, link: Long,
    user: Long, code: Long, track: Long, danger: Long = if (dark) 0xFFE5645C else 0xFFC8433B,
) = OcColors(
    isDark = dark, bg = Color(bg), surface = Color(surface), surfaceHigh = Color(high), text = Color(text), textDim = Color(dim),
    border = Color(border), accent = Color(accent), link = Color(link), toggleOn = Color(link), progressTrack = Color(track), progressFill = Color(link),
    danger = Color(danger), ok = Color(if (dark) 0xFF5CB885 else 0xFF2E8B57), warn = Color(if (dark) 0xFFE0A030 else 0xFFB87A10),
    userBubble = Color(user), codeBg = Color(code), scrim = Color(if (dark) 0x99000000 else 0x66000000),
)

object Looks {
    val claude = Look("claude", "Warm", "Soft and warm, with serif replies", DarkColors, LightColors)
    val terminal = Look("terminal", "OpenCode · Terminal", "Monospace, square corners, like the TUI", TerminalDark, TerminalLight, frame = "terminal")
    val desktop = Look("desktop", "OpenCode · Desktop", "Flat panels and Inter, like the desktop app", DesktopDark, DesktopLight, frame = "desktop")

    // TexFi v4: black, 1px lines, Unbounded in headings, a single #4a7dfb accent
    val hairline = Look(
        "hairline", "Hairline", "Black, 1px lines, wide headings, one blue accent",
        pal(true, 0xFF000000, 0xFF0A0A0A, 0xFF151515, 0xFFF2F2F2, 0xFF8C8C8C, 0xFF262626, 0xFF4A7DFB, 0xFF4A7DFB, 0xFF000000, 0xFF0A0A0A, 0xFF0E1A33),
        pal(false, 0xFFFFFFFF, 0xFFF6F6F6, 0xFFECECEC, 0xFF0A0A0A, 0xFF7A7A7A, 0xFFE2E2E2, 0xFF4A7DFB, 0xFF4A7DFB, 0xFFFFFFFF, 0xFFF6F6F6, 0xFFE3EBFF),
        user = "line", radius = 0.35f, radiusMax = 10f, pill = 8f, sendColor = { it.accent },
    )

    // Notebook: paper, serifs, no bubbles
    val notebook = Look(
        "notebook", "Notebook", "Paper, serif text, no bubbles — reads like a journal",
        pal(true, 0xFF1C1A16, 0xFF25221D, 0xFF2F2B25, 0xFFEDE6D6, 0xFF9C927F, 0xFF3A352C, 0xFFD0794A, 0xFF7FA7D9, 0xFF25221D, 0xFF211E19, 0xFF2A2620),
        pal(false, 0xFFF7F3EA, 0xFFEFE9DC, 0xFFE5DDCC, 0xFF2B2721, 0xFF857C6C, 0xFFD9D0BD, 0xFFB5562F, 0xFF2F6FB0, 0xFFEFE9DC, 0xFFEFE9DC, 0xFFE8DFCB),
        user = "plain", radius = 0.3f, radiusMax = 8f, pill = 6f, sendColor = { it.accent },
    )

    // Messenger: bubbles with tails on both sides, time inside the bubble
    val messenger = Look(
        "messenger", "Messenger", "Bubbles on both sides with tails, like Telegram",
        pal(true, 0xFF0E1621, 0xFF182533, 0xFF1F2B38, 0xFFF5F5F5, 0xFF7F91A4, 0xFF22303F, 0xFF5288C1, 0xFF6AB3F3, 0xFF2B5278, 0xFF0B121A, 0xFF13283F),
        pal(false, 0xFFE6EBEE, 0xFFFFFFFF, 0xFFF1F4F6, 0xFF000000, 0xFF707579, 0xFFD6DCE0, 0xFF3390EC, 0xFF3390EC, 0xFFEEFFDE, 0xFFF4F6F8, 0xFFDCEBFA),
        user = "tail", reply = "bubble", sendColor = { it.accent },
    )

    // Cards: every reply is its own card
    val cards = Look(
        "cards", "Cards", "Every message is a card — tidy for long tasks",
        pal(true, 0xFF0F1012, 0xFF18191C, 0xFF222327, 0xFFECEDEE, 0xFF8B8D93, 0xFF2A2C31, 0xFF8B7CF6, 0xFF9D90FF, 0xFF18191C, 0xFF121316, 0xFF211C3D),
        pal(false, 0xFFF4F4F6, 0xFFFFFFFF, 0xFFEEEEF2, 0xFF141518, 0xFF6E7079, 0xFFDEDFE4, 0xFF6E5CF0, 0xFF5B4BE0, 0xFFFFFFFF, 0xFFF2F2F5, 0xFFE7E3FF),
        user = "card", reply = "card", radius = 0.75f, sendColor = { it.accent },
    )

    // ChatGPT (taken from the phone): pure black, #303030 buttons, blue voice button, one-line input
    val chatgpt = Look(
        "chatgpt", "ChatGPT", "Pure black, one-line input, blue voice button",
        pal(true, 0xFF000000, 0xFF1F1F1F, 0xFF303030, 0xFFECECEC, 0xFFAFAFAF, 0xFF3A3A3A, 0xFF3B6FF5, 0xFF5E9BFF, 0xFF303030, 0xFF171717, 0xFF132447),
        pal(false, 0xFFFFFFFF, 0xFFF4F4F4, 0xFFE9E9E9, 0xFF0D0D0D, 0xFF6E6E6E, 0xFFE5E5E5, 0xFF3B6FF5, 0xFF2F64E8, 0xFFF4F4F4, 0xFFF4F4F4, 0xFFDDE7FE),
        compact = true, sendColor = { it.accent },
    )

    // Kimi (taken from the phone): graphite #111, round #292929 buttons, light blue mascot
    val kimi = Look(
        "kimi", "Kimi", "Graphite, round buttons, a little blue mascot",
        pal(true, 0xFF111111, 0xFF1C1C1C, 0xFF292929, 0xFFEDEDED, 0xFF9A9A9A, 0xFF2A2A2A, 0xFF3D8BFF, 0xFF5AA0FF, 0xFF292929, 0xFF181818, 0xFF102540),
        pal(false, 0xFFFFFFFF, 0xFFF5F5F5, 0xFFEBEBEB, 0xFF141414, 0xFF808080, 0xFFE6E6E6, 0xFF1677FF, 0xFF1677FF, 0xFFF2F3F5, 0xFFF5F5F5, 0xFFDDEBFF),
        compact = true,
    )

    // cl0udly: the Kimi frame, a pixel cloud instead of the ball, a strip of pixel clouds on top.
    // Only black, white and a very dim blue.
    val cloud = Look(
        "cloud", "cl0udly", "Graphite, pixel clouds, a little cloud mascot",
        pal(true, 0xFF0B0C0E, 0xFF141619, 0xFF1D2024, 0xFFE4E7EB, 0xFF8B9199, 0xFF25292E, 0xFF7C93AF, 0xFF97AAC2, 0xFF1D2024, 0xFF111316, 0xFF1A222C),
        pal(false, 0xFFF7F8FA, 0xFFEEF0F3, 0xFFE4E7EB, 0xFF15171A, 0xFF7B828B, 0xFFDDE1E6, 0xFF5A7393, 0xFF5A7393, 0xFFE9ECF0, 0xFFEEF0F3, 0xFFDCE3EC),
        compact = true,
    )

    val all = listOf(cloud, claude, terminal, desktop, hairline, notebook, messenger, cards, chatgpt, kimi)
    fun of(id: String): Look = all.firstOrNull { it.id == id } ?: cloud
}

/** Looks on the Kimi frame: round buttons, model pill on top, mascot on the home screen. */
val String.kimiLike: Boolean get() = this == "kimi" || this == "cloud"

/**
 * Background of a regular screen (settings, menu, lists): dim clouds for cl0udly, the background color for the rest.
 * [variant] is the screen name: each has its own cloud layout.
 */
@androidx.compose.runtime.Composable
fun androidx.compose.ui.Modifier.screenBackdrop(variant: String): androidx.compose.ui.Modifier {
    val c = Oc.c
    if (Oc.look.id == "cloud") return with(app.ocmobile.ui.components.CloudBackdrop) { backdrop(c.bg, c.isDark, variant, topOnly = false) }
    return this.background(c.bg)
}

/**
 * Background for the look. Notebook is a notebook page: pale rules and a margin as an accent line on the left.
 * The rest get just the background color.
 */
@androidx.compose.runtime.Composable
fun androidx.compose.ui.Modifier.lookBackdrop(home: Boolean = false): androidx.compose.ui.Modifier {
    val c = Oc.c
    // cl0udly: on home and in chat the clouds are only at the top, the input area stays clean
    if (Oc.look.id == "cloud") return with(app.ocmobile.ui.components.CloudBackdrop) { backdrop(c.bg, c.isDark, if (home) "home" else "chat", topOnly = true) }
    if (Oc.look.id != "notebook") return this.background(c.bg)
    return this.background(c.bg).drawBehind {
        val step = 34.dp.toPx()
        var y = step * 3
        while (y < size.height) { drawRect(c.border.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1f)); y += step }
        drawRect(c.accent.copy(alpha = 0.3f), androidx.compose.ui.geometry.Offset(12.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height))
    }
}
