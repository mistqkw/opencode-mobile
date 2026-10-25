package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.DesktopDark
import app.ocmobile.ui.theme.DarkColors
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.InterFamily
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.OcColors
import app.ocmobile.ui.theme.TerminalDark

private class Edition(val id: String, val title: String, val note: String)

private val editions = listOf(
    Edition("claude", "Claude", "Soft and round: serif headings, warm dark, pill buttons."),
    Edition("terminal", "OpenCode · Terminal", "The OpenCode you know from the console: monospace, square corners, a blue bar on your messages."),
    Edition("desktop", "OpenCode · Desktop", "The look of the desktop app: Inter, flat panels, quiet greys and a pale blue accent."),
)

@Composable
fun EditionScreen(nav: Nav) {
    val c = Oc.c
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Interface", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            editions.forEach { e ->
                val on = Graph.prefs.edition == e.id
                Column(
                    Modifier.fillMaxWidth().clip(Oc.round(24.dp)).background(c.surface)
                        .border(if (on) 2.dp else 1.dp, if (on) c.link else c.border, Oc.round(24.dp))
                        .clickable { Graph.prefs.edition = e.id }.padding(16.dp),
                ) {
                    Preview(e.id)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt(e.title, size = 18.sp, weight = FontWeight.Medium)
                            Txt(e.note, Modifier.padding(top = 4.dp), size = 14.sp, color = c.textDim, lineHeight = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) c.link else c.border, CircleShape).padding(5.dp).then(if (on) Modifier.background(c.link, CircleShape) else Modifier))
                    }
                }
            }
            Txt("Colors, fonts and corners switch right away. Light and dark mode keep working in every edition.", Modifier.padding(horizontal = 6.dp, vertical = 6.dp), size = 13.sp, color = c.textDim, lineHeight = 19.sp)
        }
    }
}

/** Маленький макет экрана в палитре и шрифте самой редакции — не зависит от текущей темы. */
@Composable
private fun Preview(id: String) {
    val p: OcColors = when (id) { "terminal" -> TerminalDark; "desktop" -> DesktopDark; else -> DarkColors }
    val font: FontFamily = when (id) { "terminal" -> FontFamily.Monospace; "desktop" -> InterFamily; else -> FontFamily.Default }
    val head: FontFamily = if (id == "claude") FontFamily.Serif else font
    val r: (Dp) -> Shape = { d -> when (id) { "terminal" -> RoundedCornerShape(0.dp); "desktop" -> RoundedCornerShape(8.dp); else -> RoundedCornerShape(d) } }
    Box(Modifier.fillMaxWidth().height(150.dp).clip(r(18.dp)).background(p.bg).padding(12.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Txt(if (id == "terminal") "opencode" else "OpenCode", size = if (id == "terminal") 17.sp else 18.sp, color = p.text, family = head, weight = if (id == "terminal") FontWeight.Bold else null)
                Spacer(Modifier.height(8.dp))
                if (id == "terminal") {
                    Row(Modifier.fillMaxWidth().height(34.dp).background(p.surface)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(p.link))
                        Txt("make the button blue", Modifier.padding(start = 10.dp).align(Alignment.CenterVertically), size = 12.sp, color = p.text, family = font)
                    }
                } else {
                    Box(Modifier.align(Alignment.End).clip(r(14.dp)).background(p.userBubble).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Txt("make the button blue", size = 12.sp, color = p.text, family = font)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(38.dp).clip(r(16.dp)).background(p.surface).border(1.dp, p.border, r(16.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(if (id == "terminal") "Build · big-pickle" else "Ask anything…", size = 12.sp, color = p.textDim, family = font)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(22.dp).clip(if (id == "claude") CircleShape else r(6.dp)).background(p.text))
            }
        }
    }
}
