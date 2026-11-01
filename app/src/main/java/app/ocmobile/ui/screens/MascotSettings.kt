package app.ocmobile.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.Holiday
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.PickRow
import app.ocmobile.ui.components.PickerFrame
import app.ocmobile.ui.components.PixelCloud
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.screenBackdrop

/**
 * The "cl0udly" tab in settings (only in the cl0udly look): a live preview of the mascot, what it
 * can do and reacts to, its outfit (by date, none, or any by hand), stats and the room.
 */
@Composable
fun MascotSettings(nav: Nav) {
    val c = Oc.c
    val p = Graph.prefs
    // switches change settings at once; the counter redraws the screen and the preview
    var rev by remember { mutableIntStateOf(0) }
    var outfitSheet by remember { mutableStateOf(false) }
    fun toggle(set: (Boolean) -> Unit): (Boolean) -> Unit = { set(it); rev++ }

    Column(Modifier.fillMaxSize().screenBackdrop("MascotSettings")) {
        TopBar("cl0udly", left = { IconBtn(Ic.Back, { nav.pop() }) })
        key(rev) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    PixelCloud(Modifier.size(width = 150.dp, height = 102.dp))
                }
                Txt("Tap, hold, pet or shake — try it here.", size = 13.sp, color = c.textDim, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))

                SettingsGroup {
                    SettingsRow("Show on the home screen", icon = Ic.Cloud, trailing = { OcSwitch(p.mascotOn, toggle { p.mascotOn = it }) })
                    SettingsRow("Room", subtitle = "Chat with cl0udly and play ping-pong", icon = Ic.Play, chevron = true, onClick = { nav.push(Route.Mascot) })
                }

                SectionLabel("In chats")
                SettingsGroup {
                    SettingsRow("The assistant is cl0udly", subtitle = "Knows its name, that it's a cloud living in this app, and names its real model when asked", icon = Ic.Chat,
                        trailing = { OcSwitch(p.mascotPersona, toggle { p.mascotPersona = it }) })
                }

                SectionLabel("Reacts to")
                SettingsGroup {
                    SettingsRow("Vibration", subtitle = "Taps, pets and ping-pong", icon = Ic.Vibrate, trailing = { OcSwitch(p.mascotHaptics, toggle { p.mascotHaptics = it }) })
                    SettingsRow("Shake, tilt and upside down", subtitle = "Uses the motion sensor while visible", icon = Ic.Phone, trailing = { OcSwitch(p.mascotSensors, toggle { p.mascotSensors = it }) })
                    SettingsRow("Charging and low battery", icon = Ic.Zap, trailing = { OcSwitch(p.mascotBattery, toggle { p.mascotBattery = it }) })
                    SettingsRow("Dozes off when left alone", icon = Ic.Moon, trailing = { OcSwitch(p.mascotSleepy, toggle { p.mascotSleepy = it }) })
                    SettingsRow("Morning stretch", subtitle = "First launch of the day", icon = Ic.Sun, trailing = { OcSwitch(p.mascotMorning, toggle { p.mascotMorning = it }) })
                }

                SectionLabel("Outfits")
                SettingsGroup {
                    SettingsRow("Outfit", subtitle = outfitLabel(p.mascotOutfit), icon = Ic.Sparkle, chevron = true, onClick = { outfitSheet = true })
                    SettingsRow("Snow, leaves and confetti", subtitle = "And the odd autumn sneeze", icon = Ic.Drop, trailing = { OcSwitch(p.mascotSeasonal, toggle { p.mascotSeasonal = it }) })
                }

                SectionLabel("Together")
                SettingsGroup {
                    val pets = p.petCount
                    SettingsRow("Petted", subtitle = "$pets ${if (pets == "1") "time" else "times"}", icon = Ic.Sparkle)
                    SettingsRow("Best ping-pong rally", subtitle = p.pongBest, icon = Ic.ListCheck)
                    SettingsRow("Reset", icon = Ic.Refresh, titleColor = c.danger, onClick = { p.petCount = "0"; p.pongBest = "0"; rev++ })
                }
            }
        }
    }

    if (outfitSheet) PickerFrame("Outfit", { outfitSheet = false }) {
        fun pick(v: String) { p.mascotOutfit = v; outfitSheet = false; rev++ }
        PickRow("By date", { pick("auto") }, selected = p.mascotOutfit == "auto")
        PickRow("None", { pick("off") }, selected = p.mascotOutfit == "off")
        Holiday.wardrobe.forEach { (label, h) -> PickRow(label, { pick(h.name) }, selected = p.mascotOutfit == h.name) }
    }
}

private fun outfitLabel(v: String): String = when (v) {
    "auto" -> "By date" + (Holiday.now()?.let { h -> Holiday.wardrobe.firstOrNull { it.second == h }?.first?.let { " · $it today" } } ?: "")
    "off" -> "None"
    else -> Holiday.wardrobe.firstOrNull { it.second.name == v }?.first ?: v
}
