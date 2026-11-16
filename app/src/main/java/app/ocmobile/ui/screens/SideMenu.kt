package app.ocmobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.screenBackdrop

/** The side menu's sections: which are shown and in what order lives in prefs.drawerSections. */
object DrawerSections {
    class Section(val key: String, val label: String, val icon: ImageVector, val route: Route)

    val all get() = listOf(
        Section("scheduled", "Scheduled tasks", Ic.Clock2, Route.Scheduled),
        Section("devices", "Remote devices", Ic.Laptop, Route.Code),
        Section("projects", "Projects", Ic.Folder, Route.Projects),
        Section("artifacts", "Artifacts", Ic.Puzzle, Route.Artifacts),
        Section("memory", "Memory", Ic.Sparkle, Route.Memory),
        Section("mascot", "cl0udly", Ic.Cloud, Route.Mascot),
        Section("skills", "Skills", Ic.Scroll, Route.Skills),
        Section("team", "Sub-agents", Ic.Users, Route.Team),
        Section("connectors", "Connectors", Ic.Puzzle, Route.Connectors),
        Section("usage", "Usage", Ic.Chart, Route.Usage),
        Section("settings", "Settings", Ic.Sliders, Route.Settings),
    )

    private fun order() = Graph.prefs.drawerSections.split(',').filter { it.isNotBlank() }

    fun shown(): List<Section> { val byKey = all.associateBy { it.key }; return order().mapNotNull { byKey[it] } }

    fun toggle(key: String) {
        val o = order()
        Graph.prefs.drawerSections = (if (key in o) o - key else o + key).joinToString(",")
    }

    fun move(key: String, by: Int) {
        val o = order().toMutableList()
        val i = o.indexOf(key); val j = i + by
        if (i < 0 || j !in o.indices) return
        o.removeAt(i); o.add(j, key)
        Graph.prefs.drawerSections = o.joinToString(",")
    }
}

/** Settings → Side menu: pick and order its sections, and choose what the chat list in it shows. */
@Composable
fun SideMenuScreen(nav: Nav) {
    val c = Oc.c
    val prefs = Graph.prefs
    var recentPick by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().screenBackdrop("SideMenu")) {
        TopBar("Side menu", left = { IconBtn(Ic.Back, { nav.pop() }) }, serif = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Txt("Sections", Modifier.padding(start = 24.dp, bottom = 8.dp), size = 14.sp, color = c.textDim)
            val shown = DrawerSections.shown()
            SettingsGroup {
                // the shown ones first, in their order, with arrows to move them; then the hidden ones
                shown.forEachIndexed { i, s ->
                    SettingsRow(s.label, icon = s.icon, trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (i > 0) IconBtn(Ic.ArrowUp, { DrawerSections.move(s.key, -1) }, size = 40.dp, iconSize = 18.dp, tint = c.textDim)
                            if (i < shown.lastIndex) IconBtn(Ic.ChevronDown, { DrawerSections.move(s.key, 1) }, size = 40.dp, iconSize = 18.dp, tint = c.textDim)
                            OcSwitch(true) { DrawerSections.toggle(s.key) }
                        }
                    })
                }
                DrawerSections.all.filter { a -> shown.none { it.key == a.key } }.forEach { s ->
                    SettingsRow(s.label, icon = s.icon, titleColor = c.textDim, trailing = { OcSwitch(false) { DrawerSections.toggle(s.key) } })
                }
            }
            Spacer(Modifier.height(20.dp))
            Txt("Chats", Modifier.padding(start = 24.dp, bottom = 8.dp), size = 14.sp, color = c.textDim)
            SettingsGroup {
                SettingsRow("Recent chats", subtitle = if (prefs.drawerRecent == "all") "All of them" else "The last ${prefs.drawerRecent}", icon = Ic.Chat, onClick = { recentPick = true })
                SettingsRow("Pinned on top", icon = Ic.Pin, trailing = { OcSwitch(prefs.drawerPinned) { prefs.drawerPinned = it } })
                if (Oc.look.id == "cloud") SettingsRow("Group by day", subtitle = "Today, Yesterday, Earlier", icon = Ic.Clock, trailing = { OcSwitch(prefs.drawerByDay) { prefs.drawerByDay = it } })
                if (Oc.look.id == "cloud") SettingsRow("Icon by chat kind", subtitle = "Code, task, research, writing", icon = Ic.Code, trailing = { OcSwitch(prefs.drawerChatIcons) { prefs.drawerChatIcons = it } })
                SettingsRow("Sub-agent count", icon = Ic.Users, trailing = { OcSwitch(prefs.drawerHelpers) { prefs.drawerHelpers = it } })
                SettingsRow("Working and done marks", icon = Ic.Bell, trailing = { OcSwitch(prefs.drawerStatus) { prefs.drawerStatus = it } })
            }
            Spacer(Modifier.height(20.dp))
            Txt("Look", Modifier.padding(start = 24.dp, bottom = 8.dp), size = 14.sp, color = c.textDim)
            SettingsGroup {
                SettingsRow("Your name on top", icon = Ic.User, trailing = { OcSwitch(prefs.drawerHeader) { prefs.drawerHeader = it } })
                if (Oc.look.id == "kimi" || Oc.look.id == "cloud") SettingsRow("Search at the bottom", icon = Ic.Search, trailing = { OcSwitch(prefs.drawerSearch) { prefs.drawerSearch = it } })
                SettingsRow("Compact rows", subtitle = "Fit more on the screen", icon = Ic.ListCheck, trailing = { OcSwitch(prefs.drawerCompact) { prefs.drawerCompact = it } })
            }
            Spacer(Modifier.height(16.dp))
            Txt("Settings stay reachable from the cloud button on top even with the section hidden.", Modifier.padding(horizontal = 24.dp), size = 13.sp, color = c.textDim)
        }
    }
    if (recentPick) ChoiceDialog(
        "Recent chats",
        listOf("8", "12", "20", "40", "all").map { Triple(it, if (it == "all") "All of them" else "The last $it", null) },
        prefs.drawerRecent, { prefs.drawerRecent = it },
    ) { recentPick = false }
}
