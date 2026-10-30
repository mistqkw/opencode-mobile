package app.ocmobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
            item { DrawerHeader { go(it) } }
            item {
                val navs = if (Oc.look.id == "kimi")
                    // Kimi: разделы с подписями как в их меню, плюс память
                    listOf(Triple("Scheduled tasks", Ic.Clock2, Route.Scheduled), Triple("Remote devices", Ic.Laptop, Route.Code), Triple("Projects", Ic.Folder, Route.Projects), Triple("Artifacts", Ic.Puzzle, Route.Artifacts), Triple("Memory", Ic.Sparkle, Route.Memory))
                else listOf(Triple("Projects", Ic.Folder, Route.Projects), Triple("Code", Ic.Code, Route.Code), Triple("Artifacts", Ic.Puzzle, Route.Artifacts), Triple("Scheduled", Ic.Clock2, Route.Scheduled))
                if (Oc.look.id == "cards") {
                    // Cards: разделы — плитками 2×2
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        navs.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (l, ic, r) ->
                                    Column(Modifier.weight(1f).clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp)).tap(scale = 0.97f) { go(r) }.padding(14.dp)) {
                                        Ico(ic, tint = c.accent, size = 20.dp); Spacer(Modifier.height(10.dp)); Txt(l, size = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                } else Column(Modifier.padding(top = 8.dp)) { navs.forEach { (l, ic, r) -> NavItem(l, ic) { go(r) } } }
                if (Oc.look.id != "cards") Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth().height(1.dp).background(c.border))
            }
            if (pinned.isNotEmpty()) {
                item { Txt("Pinned", Modifier.padding(start = 24.dp, top = 6.dp, bottom = 6.dp), size = 16.sp, color = c.textDim) }
                items(pinned, key = { "p" + it.id }) { SessionRow(it, statuses[it.id], nav, close) }
            }
            item { Txt(if (Oc.look.id == "kimi") "Chat history" else "Recents", Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp), size = 16.sp, color = c.textDim) }
            items(recent, key = { "r" + it.id }) { SessionRow(it, statuses[it.id], nav, close) }
            if (recent.isEmpty() && pinned.isEmpty()) item { Txt("No sessions yet", Modifier.padding(start = 24.dp, top = 8.dp), size = 16.sp, color = c.textDim) }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.bg.copy(alpha = 0f), c.bg, c.bg)))
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (Oc.look.id == "kimi") {
                // Kimi: внизу поиск по истории и круглая кнопка настроек
                Row(
                    Modifier.weight(1f).height(52.dp).clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.7f)).border(1.dp, c.border, CircleShape)
                        .tap(scale = 0.98f) { go(Route.Recents) }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Ico(Ic.Search, tint = c.textDim, size = 22.dp); Spacer(Modifier.width(12.dp)); Txt("Search", size = 17.sp, color = c.textDim) }
                Spacer(Modifier.width(10.dp))
                app.ocmobile.ui.components.KimiCircleBtn(Ic.Sliders, { go(Route.Settings) }, size = 52.dp)
            } else {
            Avatar(52.dp) { go(Route.Settings) }
            Spacer(Modifier.weight(1f))
            Pill("New chat", { go(Route.Home) }, icon = Ic.Plus)
            }
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

/** Шапка меню: у каждого вида своя. */
@Composable
private fun DrawerHeader(go: (Route) -> Unit) {
    val c = Oc.c
    when (Oc.look.id) {
        // Telegram: цветная плашка с аватаром и именем
        "messenger" -> Column(Modifier.fillMaxWidth().background(c.surface).padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Avatar(64.dp) { go(Route.Settings) }
                Spacer(Modifier.weight(1f))
                IconBtn(Ic.Search, { go(Route.Recents) })
            }
            Spacer(Modifier.height(14.dp))
            Txt(Graph.prefs.userName.ifBlank { "OpenCode" }, size = 17.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Txt("OpenCode on this phone", size = 14.sp, color = c.textDim)
        }
        // Hairline: моно-подпись, широкий заголовок и линия
        "hairline" -> Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("TEXFI / OPENCODE", Modifier.weight(1f), size = 11.sp, color = c.textDim, family = Oc.label)
                IconBtn(Ic.Search, { go(Route.Recents) }, iconSize = 20.dp)
            }
            Heading("Open", size = 30.sp)
            Box(Modifier.padding(top = 2.dp)) { Heading("Code.", size = 30.sp, color = c.accent) }
        }
        // ChatGPT: сверху строка поиска
        "chatgpt" -> Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp).fillMaxWidth().height(44.dp).clip(CircleShape).background(c.surfaceHigh)
                .tap(scale = 0.99f) { go(Route.Recents) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Ico(Ic.Search, tint = c.textDim, size = 20.dp); Spacer(Modifier.width(10.dp)); Txt("Search", size = 16.sp, color = c.textDim) }
        // Kimi: строгий заголовок без маскота (маскот только на пустом экране и пока думает)
        // Kimi: аватар, имя и круглая кнопка нового чата
        "kimi" -> Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(44.dp) { go(Route.Settings) }
            Spacer(Modifier.width(14.dp))
            Txt(Graph.prefs.userName.ifBlank { "OpenCode" }, Modifier.weight(1f), size = 19.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
            app.ocmobile.ui.components.KimiCircleBtn(Ic.Edit, { go(Route.Home) }, size = 48.dp)
        }
        // Notebook: заголовок и дата курсивом
        "notebook" -> Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Heading("Notebook", Modifier.weight(1f), size = 30.sp)
                IconBtn(Ic.Search, { go(Route.Recents) })
            }
            androidx.compose.material3.Text(
                java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.ENGLISH).format(java.util.Date()),
                fontFamily = Oc.reply, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 15.sp, color = c.textDim,
            )
        }
        else -> Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Heading("OpenCode", Modifier.weight(1f), size = 30.sp)
            IconBtn(Ic.Search, { go(Route.Recents) })
        }
    }
}

@Composable
private fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = Oc.c
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = if (Oc.look.id == "messenger") 13.dp else 15.dp), verticalAlignment = Alignment.CenterVertically) {
        when (Oc.look.id) {
            "hairline" -> { Ico(icon, tint = c.textDim, size = 18.dp); Spacer(Modifier.width(18.dp)); Txt(label.uppercase(), Modifier.weight(1f), size = 13.sp, family = Oc.label); Txt("→", size = 13.sp, color = c.textDim, family = Oc.label) }
            "messenger" -> { Ico(icon, tint = c.textDim, size = 24.dp); Spacer(Modifier.width(28.dp)); Txt(label, size = 16.sp) }
            "notebook" -> { Ico(icon, tint = c.accent, size = 22.dp); Spacer(Modifier.width(18.dp)); Txt(label, size = 18.sp, family = Oc.reply) }
            "kimi" -> { Ico(icon, tint = c.text, size = 23.dp); Spacer(Modifier.width(20.dp)); Txt(label, size = 18.sp) }
            else -> { Ico(icon, tint = c.textDim, size = 26.dp); Spacer(Modifier.width(20.dp)); Txt(label, size = 18.sp, color = c.textDim) }
        }
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
        if (Oc.look.id == "messenger") {
            // как список чатов: кружок с первой буквой
            val hue = listOf(0xFFE17076, 0xFFFAA774, 0xFF7BC862, 0xFF6EC9CB, 0xFF65AADD, 0xFFA695E7, 0xFFEE7AAE)[(s.id.hashCode() and 0x7fffffff) % 7]
            Box(Modifier.size(44.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(hue)), contentAlignment = Alignment.Center) {
                Txt(s.displayTitle.take(1).uppercase(), size = 18.sp, color = androidx.compose.ui.graphics.Color.White, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
            Spacer(Modifier.width(14.dp))
        } else if (Oc.look.id != "kimi") {
            Ico(if (s.directory == app.ocmobile.data.CHAT_DIR) Ic.Chat else if (s.directory.isBlank()) Ic.Terminal else Ic.Code, tint = c.textDim, size = if (Oc.look.id == "hairline") 18.dp else 22.dp)
            Spacer(Modifier.width(18.dp))
        }
        Txt(s.displayTitle, Modifier.weight(1f), size = if (Oc.look.id == "hairline") 15.sp else if (Oc.look.id == "messenger") 16.sp else 18.sp, maxLines = 1, family = if (Oc.look.id == "notebook") Oc.reply else null)
        if (needs || status != null) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(11.dp).clip(CircleShape).background(if (needs) c.link else c.textDim))
        }
    }
}
