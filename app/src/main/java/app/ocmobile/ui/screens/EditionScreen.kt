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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import app.ocmobile.ui.theme.Look
import app.ocmobile.ui.theme.Looks
import app.ocmobile.ui.theme.SerifDisplay
import app.ocmobile.ui.theme.SerifFamily
import app.ocmobile.ui.theme.UnboundedFamily

@Composable
fun EditionScreen(nav: Nav) {
    val c = Oc.c
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Interface", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // два столбца: сразу видно несколько видов рядом
            Looks.all.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { l ->
                        val on = Graph.prefs.edition == l.id
                        Column(
                            Modifier.weight(1f).clip(Oc.round(20.dp)).background(c.surface)
                                .border(if (on) 2.dp else 1.dp, if (on) c.link else c.border, Oc.round(20.dp))
                                .clickable { Graph.prefs.edition = l.id }.padding(10.dp),
                        ) {
                            LookPreview(l)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(l.title, Modifier.weight(1f), size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                                Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (on) c.link else c.border, CircleShape).padding(4.dp).then(if (on) Modifier.background(c.link, CircleShape) else Modifier))
                            }
                            Txt(l.sub, Modifier.padding(top = 3.dp), size = 12.sp, color = c.textDim, lineHeight = 16.sp, maxLines = 3)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Txt("Colors, fonts and corners switch right away. Light and dark mode work in every interface.", Modifier.padding(horizontal = 6.dp, vertical = 6.dp), size = 13.sp, color = c.textDim, lineHeight = 19.sp)
            // анимация входа и иконка под выбранный вид
            val ctx = androidx.compose.ui.platform.LocalContext.current
            var intro by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Graph.prefs.introEnabled) }
            val match = app.ocmobile.ui.theme.AppIcons.all.firstOrNull { it.id == "look_" + Graph.prefs.edition }
            app.ocmobile.ui.components.SettingsGroup(Modifier.padding(top = 4.dp)) {
                app.ocmobile.ui.components.SettingsRow("Launch animation", subtitle = "Plays when the app starts; tap to skip", icon = Ic.Play, trailing = {
                    app.ocmobile.ui.components.OcSwitch(intro) { intro = it; Graph.prefs.introEnabled = it }
                })
                app.ocmobile.ui.components.SettingsRow("Play it now", icon = Ic.Sparkle, onClick = { app.ocmobile.ui.components.Intro.replay.intValue++ })
                if (match != null) app.ocmobile.ui.components.SettingsRow(
                    if (Graph.prefs.appIcon == match.id) "Icon matches this interface" else "Use the ${match.label} icon",
                    subtitle = "Any icon can still be picked in Settings → App icon", icon = Ic.Image,
                    onClick = { Graph.prefs.appIcon = match.id; app.ocmobile.ui.theme.AppIcons.apply(ctx, match.id) },
                )
            }
        }
    }
}

/** Маленький макет экрана в палитре, шрифтах и стиле самого вида — не зависит от текущей темы. */
@Composable
internal fun LookPreview(l: Look) {
    val p: OcColors = l.dark
    val font: FontFamily = when (l.id) { "terminal" -> FontFamily.Monospace; "notebook" -> SerifFamily; "messenger" -> FontFamily.Default; "claude" -> InterFamily; else -> InterFamily }
    val head: FontFamily = when (l.id) { "claude", "notebook" -> SerifDisplay; "hairline" -> UnboundedFamily; else -> font }
    val r: (Dp) -> Shape = { d -> when (l.id) { "terminal" -> RoundedCornerShape(0.dp); "desktop" -> RoundedCornerShape(6.dp); else -> RoundedCornerShape((d * l.radius).let { x -> l.radiusMax?.let { x.coerceAtMost(it.dp) } ?: x }) } }
    val send = l.sendColor?.invoke(p) ?: p.text
    Box(Modifier.fillMaxWidth().height(132.dp).clip(r(14.dp)).background(p.bg).padding(9.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Txt(if (l.id == "terminal") "opencode" else "OpenCode", size = 14.sp, color = p.text, family = head, weight = if (l.id == "hairline" || l.id == "terminal") FontWeight.Bold else null)
                Spacer(Modifier.height(6.dp))
                val msg = "make it blue"
                when (l.user) {
                    "plain" -> Txt("You — $msg", size = 10.sp, color = p.textDim, family = font)
                    "line" -> Box(Modifier.align(Alignment.End).border(1.dp, p.border, r(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { Txt(msg, size = 10.sp, color = p.text, family = font) }
                    "card" -> Box(Modifier.align(Alignment.End).clip(r(10.dp)).background(p.surface).border(1.dp, p.border, r(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { Txt(msg, size = 10.sp, color = p.text, family = font) }
                    else -> if (l.id == "terminal") Row(Modifier.fillMaxWidth().height(22.dp).background(p.surface)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(p.link)); Txt(msg, Modifier.padding(start = 6.dp).align(Alignment.CenterVertically), size = 10.sp, color = p.text, family = font)
                    } else Box(Modifier.align(Alignment.End).clip(r(12.dp)).background(p.userBubble).padding(horizontal = 8.dp, vertical = 4.dp)) { Txt(msg, size = 10.sp, color = p.text, family = font) }
                }
                Spacer(Modifier.height(5.dp))
                val ans = "Done — the button is blue."
                when (l.reply) {
                    "bubble" -> Box(Modifier.clip(r(12.dp)).background(p.surface).padding(horizontal = 8.dp, vertical = 4.dp)) { Txt(ans, size = 10.sp, color = p.text, family = font) }
                    "card" -> Box(Modifier.fillMaxWidth().clip(r(10.dp)).background(p.surface).border(1.dp, p.border, r(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { Txt(ans, size = 10.sp, color = p.text, family = font) }
                    else -> Txt(ans, size = 10.sp, color = p.text, family = if (l.id == "claude" || l.id == "notebook") SerifFamily else font)
                }
            }
            Row(
                Modifier.fillMaxWidth().height(28.dp).clip(if (l.compact || l.id == "claude") RoundedCornerShape(50) else r(12.dp)).background(p.surface).border(1.dp, p.border, if (l.compact || l.id == "claude") RoundedCornerShape(50) else r(12.dp)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(if (l.id == "terminal") "Build · pickle" else "Ask anything", size = 10.sp, color = p.textDim, family = font, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(18.dp).clip(if (l.pill != null || l.frame != "claude") r(5.dp) else CircleShape).background(send))
            }
        }
    }
}
