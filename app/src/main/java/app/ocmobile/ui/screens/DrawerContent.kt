package app.ocmobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.components.tap
import app.ocmobile.api.Session
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.Heading
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun DrawerContent(nav: Nav, close: () -> Unit) {
    val c = Oc.c
    val sessions = Graph.chats.chatsOnly(Graph.chats.sessions.collectAsState().value)
    val statuses by Graph.chats.statuses.collectAsState()
    val pinnedIds = Graph.prefs.pinnedSet()
    val pinned = sessions.filter { it.id in pinnedIds }
    val recent = sessions.filter { it.id !in pinnedIds }.take(12)
    fun go(r: Route) { close(); nav.root(r) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Heading("OpenCode", Modifier.weight(1f), size = 30.sp)
                    IconBtn(Ic.Search, { go(Route.Recents) })
                }
            }
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    NavItem("Projects", Ic.Folder) { go(Route.Projects) }
                    NavItem("Code", Ic.Code) { go(Route.Code) }
                    NavItem("Artifacts", Ic.Puzzle) { go(Route.Artifacts) }
                    NavItem("Scheduled", Ic.Clock2) { go(Route.Scheduled) }
                }
                Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth().height(1.dp).background(c.border))
            }
            if (pinned.isNotEmpty()) {
                item { Txt("Pinned", Modifier.padding(start = 24.dp, top = 6.dp, bottom = 6.dp), size = 16.sp, color = c.textDim) }
                items(pinned, key = { "p" + it.id }) { SessionRow(it, statuses[it.id], nav, close) }
            }
            item { Txt("Recents", Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp), size = 16.sp, color = c.textDim) }
            items(recent, key = { "r" + it.id }) { SessionRow(it, statuses[it.id], nav, close) }
            if (recent.isEmpty() && pinned.isEmpty()) item { Txt("No sessions yet", Modifier.padding(start = 24.dp, top = 8.dp), size = 16.sp, color = c.textDim) }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.bg.copy(alpha = 0f), c.bg, c.bg)))
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(52.dp) { go(Route.Settings) }
            Spacer(Modifier.weight(1f))
            Pill("New chat", { go(Route.Home) }, icon = Ic.Plus)
        }
    }
}

@Composable
fun Avatar(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val c = Oc.c
    val path = Graph.prefs.avatarPath
    Box(Modifier.size(size).clip(CircleShape).background(c.surfaceHigh).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (path.isNotBlank() && File(path).exists()) AsyncImage(File(path), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Txt(Graph.prefs.userName.take(1).uppercase().ifBlank { "?" }, size = (size.value * 0.42f).sp, color = c.text)
    }
}

@Composable
private fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, tint = Oc.c.textDim, size = 26.dp); Spacer(Modifier.width(20.dp)); Txt(label, size = 18.sp, color = Oc.c.textDim)
    }
}

@Composable
private fun SessionRow(s: Session, status: String?, nav: Nav, close: () -> Unit) {
    var actions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (actions) SessionActionsSheet(s) { actions = false }
    val c = Oc.c
    val current = (nav.current as? Route.Chat)?.id == s.id
    val needs = Graph.chats.questions.collectAsState().value.any { it.sessionID == s.id } || Graph.chats.permissions.collectAsState().value.any { it.sessionID == s.id }
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().clip(Oc.round(18.dp)).background(if (current) c.surfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
            .tap(scale = 1f, onLongClick = { actions = true }) { close(); nav.openChat(s.id) }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(if (s.directory == app.ocmobile.data.CHAT_DIR) Ic.Chat else if (s.directory.isBlank()) Ic.Terminal else Ic.Code, tint = c.textDim, size = 22.dp)
        Spacer(Modifier.width(18.dp))
        Txt(s.displayTitle, Modifier.weight(1f), size = 18.sp, maxLines = 1)
        if (needs || status != null) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(11.dp).clip(CircleShape).background(if (needs) c.link else c.textDim))
        }
    }
}
