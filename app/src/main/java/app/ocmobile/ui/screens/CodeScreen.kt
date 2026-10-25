package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.Session
import app.ocmobile.data.Device
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch

/** Вкладка Code как в Claude: устройства (Remote control) и список сессий карточками. */
@Composable
fun CodeScreen(nav: Nav, onMenu: () -> Unit) {
    val c = Oc.c
    val sessions = Graph.chats.codeOnly(Graph.chats.sessions.collectAsState().value)
    val statuses by Graph.chats.statuses.collectAsState()
    val questions by Graph.chats.questions.collectAsState()
    val perms by Graph.chats.permissions.collectAsState()
    val devices by Graph.devices.list.collectAsState()
    val activeId by Graph.devices.activeId.collectAsState()
    val connected by Graph.events.connected.collectAsState()
    var filter by remember { mutableStateOf("all") }
    var filterMenu by remember { mutableStateOf(false) }
    var addDevice by remember { mutableStateOf(false) }
    var deviceMenu by remember { mutableStateOf<Device?>(null) }
    val pinned = Graph.prefs.pinnedSet()
    val remote = Graph.devices.active()

    LaunchedEffect(activeId) { Graph.chats.refreshSessions() }

    val needInput = (questions.map { it.sessionID } + perms.map { it.sessionID }).toSet()
    val list = sessions.filter {
        when (filter) {
            "pinned" -> it.id in pinned
            "working" -> statuses[it.id] != null
            "input" -> it.id in needInput
            else -> true
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Code", left = { IconBtn(Ic.Menu, onMenu) }, right = { IconBtn(Ic.Clock2, { nav.push(Route.Recents) }) })
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Txt("Devices", Modifier.padding(start = 8.dp, top = 8.dp), size = 16.sp, color = c.textDim) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeviceChip("This phone", connected && activeId.isEmpty(), activeId.isEmpty(), Ic.Phone, { Graph.devices.select("") }, {})
                        devices.forEach { d ->
                            DeviceChip(d.name, connected && activeId == d.id, activeId == d.id, Ic.Laptop, { Graph.devices.select(d.id) }, { deviceMenu = d })
                        }
                        Row(
                            Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).tap(onClick = { addDevice = true }).height(52.dp).padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Ico(Ic.Plus, size = 22.dp); Spacer(Modifier.width(10.dp)); Txt("Add device", size = 18.sp) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("Sessions", Modifier.weight(1f), size = 16.sp, color = c.textDim)
                        Box {
                            Row(Modifier.clip(CircleShape).clickable { filterMenu = true }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Txt(mapOf("all" to "All", "pinned" to "Pinned", "working" to "Working", "input" to "Needs input")[filter]!!, size = 16.sp, color = c.textDim)
                                Spacer(Modifier.width(4.dp)); Ico(Ic.ChevronDown, tint = c.textDim, size = 18.dp)
                            }
                            DropdownMenu(filterMenu, { filterMenu = false }, containerColor = c.surface) {
                                listOf("all" to "All", "pinned" to "Pinned", "working" to "Working", "input" to "Needs input").forEach { (k, l) ->
                                    DropdownMenuItem(text = { Txt(l, size = 17.sp, color = if (filter == k) c.link else c.text) }, onClick = { filter = k; filterMenu = false })
                                }
                            }
                        }
                    }
                }
                if (list.isEmpty()) item {
                    Txt(
                        if (!connected && remote != null) "Can't reach ${remote.name}. Check that it is on and that opencode serve is running." else "No code sessions yet. Chats are in the menu; pick a project to start coding.",
                        Modifier.padding(horizontal = 8.dp, vertical = 24.dp), size = 16.sp, color = c.textDim,
                    )
                }
                items(list, key = { it.id }) { s -> SessionCard(s, statuses[s.id], needInput, questions.firstOrNull { it.sessionID == s.id }?.let { q -> q.questions.firstOrNull()?.question }, remote, connected, nav) }
            }
        }
        Pill("New session", { nav.push(Route.NewCode) }, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp).androidNavPad(), icon = Ic.Plus)
    }
    if (addDevice) AddDeviceSheet { addDevice = false }
    deviceMenu?.let { d ->
        OcSheet({ deviceMenu = null }, d.name) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Txt(d.url, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), size = 15.sp, color = c.textDim)
                app.ocmobile.ui.components.ChoiceRow("Remove device", false, { Graph.devices.remove(d.id); deviceMenu = null }, icon = Ic.Trash)
            }
        }
    }
}

private fun Modifier.androidNavPad(): Modifier = this.then(Modifier.padding(bottom = 0.dp))

@Composable
private fun DeviceChip(name: String, online: Boolean, active: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, onLong: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.clip(CircleShape).background(if (active) c.surfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (active) c.text.copy(alpha = .5f) else c.border, CircleShape)
            .tap(onLongClick = onLong, onClick = onClick).height(52.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = if (active) c.text else c.textDim, size = 22.dp); Spacer(Modifier.width(10.dp))
        Txt(name, size = 18.sp, maxLines = 1)
        if (active) { Spacer(Modifier.width(10.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) c.ok else c.warn)) }
    }
}

@Composable
private fun SessionCard(s: Session, status: String?, needInput: Set<String>, question: String?, remote: Device?, connected: Boolean, nav: Nav) {
    val c = Oc.c
    var actions by remember { mutableStateOf(false) }
    if (actions) SessionActionsSheet(s) { actions = false }
    val dir = s.directory.trimEnd('/').substringAfterLast('/').ifBlank { "/" }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .22f)).border(1.dp, c.border.copy(alpha = .6f), RoundedCornerShape(30.dp))
            .tap(scale = 0.985f, onLongClick = { actions = true }) { nav.openChat(s.id) }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.surface), contentAlignment = Alignment.Center) {
                if (status != null) Spinner(22.dp) else Ico(Ic.Code, tint = c.textDim, size = 24.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(s.displayTitle, Modifier.weight(1f), size = 19.sp, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Txt(shortAgo(s.time.updated), size = 15.sp, color = c.textDim)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (remote != null) {
                        Ico(Ic.Laptop, tint = if (connected) c.ok else c.textDim, size = 20.dp); Spacer(Modifier.width(8.dp))
                        Txt(if (connected) "Connected" else "Disconnected", size = 16.sp, color = if (connected) c.ok else c.textDim)
                        Txt("  •  Remote control", size = 16.sp, color = c.textDim)
                    } else {
                        Ico(Ic.Folder, tint = c.textDim, size = 20.dp); Spacer(Modifier.width(8.dp))
                        Txt(dir, size = 16.sp, color = c.textDim, maxLines = 1)
                    }
                }
            }
        }
        if (s.id in needInput) {
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(16.dp)) {
                Txt(question ?: "Waiting for your permission", size = 16.sp, maxLines = 3, lineHeight = 22.sp)
            }
        }
    }
}

fun shortAgo(ms: Long): String {
    if (ms <= 0) return ""
    val d = (System.currentTimeMillis() - ms) / 1000
    return when {
        d < 60 -> "now"
        d < 3600 -> "${d / 60}m"
        d < 86400 -> "${d / 3600}h"
        d < 30 * 86400 -> "${d / 86400}d"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date(ms))
    }
}

@Composable
private fun AddDeviceSheet(onDismiss: () -> Unit) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    OcSheet(onDismiss, "Add device", skipHalf = false) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("Control OpenCode that runs on your computer. On it, start the server:", size = 16.sp, color = c.textDim, lineHeight = 22.sp)
            Txt("OPENCODE_SERVER_PASSWORD=secret opencode serve --hostname 0.0.0.0", Modifier.clip(RoundedCornerShape(14.dp)).background(c.codeBg).padding(12.dp), size = 13.sp, family = Oc.mono)
            OcTextField(name, { name = it }, placeholder = "Name (e.g. Laptop)")
            OcTextField(url, { url = it }, placeholder = "Address, e.g. 192.168.0.5:4096")
            OcTextField(pass, { pass = it }, placeholder = "Server password", password = true)
            error?.let { Txt(it, size = 15.sp, color = c.danger) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                if (busy) Spinner(28.dp) else SmallButton("Connect", {
                    if (url.isBlank()) { error = "Enter the address"; return@SmallButton }
                    busy = true; error = null
                    val full = app.ocmobile.data.Devices.normalize(url)
                    scope.launch {
                        try {
                            Graph.api.probe(full, pass)
                            val d = Graph.devices.add(name, full, pass)
                            Graph.devices.select(d.id)
                            onDismiss()
                        } catch (e: Exception) {
                            error = e.message?.takeIf { it.isNotBlank() } ?: "Can't reach the server"
                        } finally { busy = false }
                    }
                }, filled = true)
            }
        }
    }
}
