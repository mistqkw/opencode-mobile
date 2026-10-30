package app.ocmobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.NamedItem
import app.ocmobile.data.CHAT_DIR
import app.ocmobile.data.Skills
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch

/** Карточки и рамки — по образу: в Hairline и Terminal — линии на фоне, в остальных — плашки. */
@Composable
private fun cardShape(): Shape = when (Oc.look.id) {
    "terminal" -> androidx.compose.ui.graphics.RectangleShape
    "hairline" -> androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
    "kimi" -> androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    else -> Oc.round(20.dp)
}

@Composable
private fun Modifier.skillCard(): Modifier {
    val c = Oc.c
    val shape = cardShape()
    val lined = Oc.look.id == "hairline" || Oc.look.id == "terminal"
    return clip(shape).background(if (lined) c.bg else c.surface).then(if (lined || Oc.look.id == "notebook") Modifier.border(1.dp, c.border, shape) else Modifier)
}

/** Новый чат, в котором агент помогает собрать навык. */
suspend fun startSkillChat(ctx: android.content.Context, about: String = ""): String {
    Graph.paths.host(CHAT_DIR).mkdirs()
    val s = Graph.chats.createSession(CHAT_DIR)
    val ask = if (about.isBlank()) "I want to create a new skill. Ask me what it should do, then write it."
    else "Create a skill: $about"
    Graph.chats.send(ctx, s.id, ask + "\n\n(Save it as ${Skills.DIR}/<name>/SKILL.md.)", emptyList())
    return s.id
}

@Composable
fun SkillsScreen(nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val mine = remember(reload) { Skills.local() }
    var server by remember { mutableStateOf<List<NamedItem>>(emptyList()) }
    val commands by Graph.catalog.commands.collectAsState()
    var query by remember { mutableStateOf("") }
    var paste by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        if (reload == 0 && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Skills.repairAll() }) kotlinx.coroutines.delay(1500)
        server = runCatching { Graph.api.skills() }.getOrDefault(emptyList())
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { Skills.import(ctx, uri) }
                .onSuccess { names -> reload++; Graph.chats.toasts.tryEmit("Added " + names.joinToString { "/$it" }) }
                .onFailure { Graph.chats.toasts.tryEmit(it.message ?: "Could not import") }
        }
    }
    fun chat() {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { startSkillChat(ctx) }.onSuccess { nav.openChat(it) }.onFailure { Graph.chats.toasts.tryEmit(it.message ?: "Could not start the chat") }
            busy = false
        }
    }

    val q = query.trim().lowercase()
    fun hit(n: String, d: String?) = q.isEmpty() || n.lowercase().contains(q) || d.orEmpty().lowercase().contains(q)
    val mineNames = mine.map { it.name }.toSet()
    val builtIn = server.filter { it.name !in mineNames && it.location?.startsWith(Skills.dir().path) != true }
    val cmds = commands.filter { it.source == "command" }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Skills", left = { IconBtn(Ic.Back, { nav.pop() }) }, right = { IconBtn(Ic.Plus, { nav.push(Route.SkillEdit("")) }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Txt(
                "Skills teach OpenCode how to do a job your way. It loads one when the task fits, or when you type /name in the chat.",
                Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp,
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AddTile(Ic.Chat, "Create in chat", "Describe it, OpenCode writes it", Modifier.weight(1f)) { chat() }
                AddTile(Ic.Edit, "Write it", "Name, when to use, steps", Modifier.weight(1f)) { nav.push(Route.SkillEdit("")) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AddTile(Ic.FileUp, "Import file", "Claude or ChatGPT .zip, .skill, SKILL.md", Modifier.weight(1f)) { importer.launch(arrayOf("*/*")) }
                AddTile(Ic.Copy, "Paste instructions", "From a custom GPT or a Claude project", Modifier.weight(1f)) { paste = true }
            }

            if (mine.size + builtIn.size > 4) SearchPill(query, { query = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp))

            SectionLabel("Your skills")
            val shown = mine.filter { hit(it.name, it.description) }
            if (mine.isEmpty()) Txt("No skills yet. Make one above, or import the ones you use in Claude or ChatGPT.", Modifier.padding(horizontal = 20.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.forEach { s -> SkillCard(s.name, s.description, if (s.files > 1) "${s.files} files" else null) { nav.push(Route.SkillEdit(s.name)) } }
            }
            val bi = builtIn.filter { hit(it.name, it.description) }
            if (bi.isNotEmpty()) {
                SectionLabel("Built in")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bi.forEach { s -> SkillCard(s.name, s.description.orEmpty(), "read only") { nav.push(Route.SkillEdit("builtin:" + s.name)) } }
                }
            }
            val cm = cmds.filter { hit(it.name, it.description) }
            if (cm.isNotEmpty()) {
                SectionLabel("Commands")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cm.forEach { s -> SkillCard(s.name, s.description.orEmpty(), "command", icon = Ic.Terminal) {} }
                }
            }
        }
    }
    if (paste) PasteSheet({ paste = false }) { name, desc, text ->
        runCatching { Skills.fromInstructions(name, desc, text) }
            .onSuccess { paste = false; reload++; Graph.chats.toasts.tryEmit("Saved /$it") }
            .onFailure { Graph.chats.toasts.tryEmit(it.message ?: "Could not save") }
    }
}

@Composable
private fun AddTile(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Oc.c
    Column(modifier.height(118.dp).skillCard().tap(scale = 0.97f, onClick = onClick).padding(14.dp)) {
        Ico(icon, tint = if (Oc.look.id == "kimi") c.text else c.accent, size = 22.dp)
        Spacer(Modifier.weight(1f))
        Txt(title, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Txt(sub, size = 12.sp, color = c.textDim, maxLines = 2, lineHeight = 16.sp)
    }
}

@Composable
private fun SearchPill(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val c = Oc.c
    Row(modifier.fillMaxWidth().clip(Oc.pill).background(c.surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(Ic.Search, tint = c.textDim, size = 18.dp); Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Txt("Search skills", size = 16.sp, color = c.textDim)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = Oc.body), cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SkillCard(name: String, description: String, badge: String?, icon: ImageVector = Ic.Scroll, onClick: () -> Unit) {
    val c = Oc.c
    val mono = Oc.look.id == "hairline" || Oc.look.id == "terminal"
    Row(Modifier.fillMaxWidth().skillCard().tap(scale = 0.98f, onClick = onClick).padding(16.dp)) {
        Box(Modifier.size(36.dp).clip(cardShape()).background(c.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Ico(icon, tint = c.accent, size = 18.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("/$name", Modifier.weight(1f, fill = false), size = 16.sp, weight = FontWeight.Medium, family = if (mono) Oc.mono else null, maxLines = 1)
                if (badge != null) { Spacer(Modifier.width(8.dp)); Txt(badge, size = 12.sp, color = c.textDim, family = if (mono) Oc.mono else null) }
            }
            if (description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Txt(description, size = 14.sp, color = c.textDim, maxLines = 2, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun PasteSheet(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val c = Oc.c
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    OcSheet(onDismiss, title = "Paste instructions") {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(
                "ChatGPT: open your GPT → Edit → Configure and copy Instructions. Claude: copy a project's instructions. They become a skill you can call with /name.",
                size = 14.sp, color = c.textDim, lineHeight = 20.sp,
            )
            OcTextField(name, { name = it }, placeholder = "Name, e.g. code-reviewer")
            OcTextField(desc, { desc = it }, placeholder = "When to use it (optional)", singleLine = false)
            OcTextField(text, { text = it }, placeholder = "Paste the instructions", singleLine = false, minLines = 6)
            Pill("Save skill", { onSave(name.ifBlank { "imported" }, desc, text) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
        }
    }
}

/** Просмотр и правка одного навыка. «builtin:имя» — встроенный, только чтение. */
@Composable
fun SkillEditScreen(arg: String, nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val builtIn = arg.startsWith("builtin:")
    val isNew = arg.isEmpty()
    val existing = remember(arg) { if (builtIn || isNew) null else Skills.local().firstOrNull { it.name == arg } }
    var name by remember { mutableStateOf(existing?.name ?: arg.removePrefix("builtin:")) }
    var desc by remember { mutableStateOf(existing?.description.orEmpty()) }
    var body by remember { mutableStateOf(existing?.body.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (builtIn) LaunchedEffect(arg) {
        runCatching { Graph.api.skills() }.getOrNull()?.firstOrNull { it.name == name }?.let { desc = it.description.orEmpty(); body = Skills.parse(it.content.orEmpty()).third }
    }
    val dirty = name != (existing?.name ?: "") || desc != existing?.description.orEmpty() || body != existing?.body.orEmpty()
    fun save(then: (String) -> Unit = {}) {
        runCatching { Skills.save(name, desc, body, existing?.name) }
            .onSuccess { then(it) }
            .onFailure { Graph.chats.toasts.tryEmit(it.message ?: "Could not save") }
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        TopBar(
            if (isNew) "New skill" else "/$name",
            left = { IconBtn(Ic.Back, { nav.pop() }) },
            right = {
                if (!builtIn && !isNew) IconBtn(Ic.Trash, { confirmDelete = true })
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (builtIn) {
                Txt(desc, size = 15.sp, color = c.textDim, lineHeight = 21.sp)
                Box(Modifier.fillMaxWidth().skillCard().padding(16.dp)) { Txt(body, size = 13.sp, family = Oc.mono, lineHeight = 19.sp) }
                return@Column
            }
            Label("Name")
            OcTextField(name, { name = it.replace(' ', '-').lowercase() }, placeholder = "my-skill", mono = true)
            Label("When to use it")
            OcTextField(desc, { desc = it }, placeholder = "E.g. Use when I ask for a cover letter or a CV", singleLine = false)
            Label("Instructions")
            OcTextField(body, { body = it }, placeholder = "Step by step: what to do, what to avoid, the format of the result…", singleLine = false, minLines = 10, mono = true)
            if (existing != null && existing.files > 1)
                Txt("Plus ${existing.files - 1} more files in ${Skills.DIR}/${existing.name}", size = 13.sp, color = c.textDim)
        }
        if (!builtIn) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isNew) SmallButton("Try in chat", {
                val go = { n: String ->
                    scope.launch {
                        runCatching {
                            Graph.paths.host(CHAT_DIR).mkdirs()
                            Graph.chats.createSession(CHAT_DIR).id
                        }.onSuccess { id -> Graph.chats.draft(id, "/$n "); nav.openChat(id) }
                    }
                    Unit
                }
                if (dirty) save { go(it) } else go(name)
            })
            Spacer(Modifier.weight(1f))
            Pill(if (isNew) "Create" else "Save", { save { Graph.chats.toasts.tryEmit("Saved /$it"); nav.pop() } }, enabled = name.isNotBlank() && body.isNotBlank() && (dirty || isNew))
        }
    }
    if (confirmDelete) app.ocmobile.ui.components.OcDialog(
        "Delete /$name?", { confirmDelete = false }, confirmText = "Delete", danger = true,
        onConfirm = { Skills.delete(existing?.name ?: name); confirmDelete = false; nav.pop() },
    ) { Txt("The skill folder and its files will be removed.", size = 15.sp, color = c.textDim) }
}

@Composable
private fun Label(t: String) = Txt(t, Modifier.padding(start = 4.dp, top = 6.dp), size = 13.sp, color = Oc.c.textDim, weight = FontWeight.Medium)
