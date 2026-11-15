package app.ocmobile.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import app.ocmobile.ui.components.tap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.data.Team
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.PickHeader
import app.ocmobile.ui.components.PickRow
import app.ocmobile.ui.components.PickSearch
import app.ocmobile.ui.components.PickerFrame
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.Segmented
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.screenBackdrop
import kotlinx.coroutines.launch

/**
 * "Team": off, your own team (name, role and model for each) or automatic
 * (a set of models and a cap, the main agent decides the rest). Changes go straight into the OpenCode config.
 */
@Composable
fun TeamScreen(nav: Nav) {
    val c = Oc.c
    val prefs = Graph.prefs
    Column(Modifier.fillMaxSize().screenBackdrop("Team")) {
        TopBar("Sub-agents", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            TeamPanel(addNow = Team.addRequested.also { Team.addRequested = false })
            SectionLabel("Working style")
            SettingsGroup {
                var brief by remember { mutableStateOf(prefs.brainStyle) }
                SettingsRow("Brief, and checks every step", subtitle = "Shorter replies and fewer tokens; re-reads its edits and runs the checks before handing work back.", trailing = {
                    OcSwitch(brief) { brief = it; prefs.brainStyle = it; app.ocmobile.data.Brain.writeSettings() }
                })
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                var prune by remember { mutableStateOf(prefs.brainPrune) }
                Txt("Long tool output is cut to its beginning and end; the whole text goes to a file it reads in parts.", Modifier.padding(start = 4.dp, bottom = 8.dp), size = 13.sp, color = c.textDim, lineHeight = 18.sp)
                Segmented(listOf("0" to "Keep", "8000" to "8k", "12000" to "12k", "20000" to "20k"), prune, { prune = it; prefs.brainPrune = it; app.ocmobile.data.Brain.writeSettings() })
            }
        }
    }
}

/**
 * The sub-agents themselves: the mode, and each helper as a card with its initial, name, role and
 * model, switched on or off right there; "Add a sub-agent" at the end. Used in the model picker
 * (a page like More models) and on the Sub-agents screen. [onAll] adds a row to the full screen.
 */
@Composable
fun TeamPanel(addNow: Boolean = false, onAll: (() -> Unit)? = null, onCreated: (() -> Unit)? = null) {
    val c = Oc.c
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    // the agent may have changed its helpers in the team file since
    remember { Team.readFile(); 0 }
    var mode by remember { mutableStateOf(prefs.teamMode) }
    var members by remember { mutableStateOf(Team.members) }
    var pool by remember { mutableStateOf(Team.pool) }
    var max by remember { mutableStateOf(prefs.teamMax) }
    var editing by remember { mutableStateOf<Int?>(if (addNow) -1 else null) }      // helper index; -1 means new
    var poolPicker by remember { mutableStateOf(false) }
    fun save() = scope.launch { Team.sync(force = true) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Segmented(listOf("off" to "Off", "manual" to "My team", "auto" to "Automatic"), mode, {
            mode = it; prefs.teamMode = it; save()
        })
        Txt(
            when (mode) {
                "manual" -> "The main agent splits each task between your sub-agents, runs them in parallel, then checks and combines their work."
                "auto" -> "The main agent decides how many helpers a task needs and which model each one uses. Simple tasks get none."
                else -> "One agent does everything. Turn on a team to let it hand parts of the work to others."
            },
            Modifier.padding(top = 10.dp, start = 4.dp, bottom = 6.dp), size = 14.sp, color = c.textDim, lineHeight = 20.sp,
        )
        androidx.compose.animation.AnimatedVisibility(mode == "manual") {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                members.forEachIndexed { i, m ->
                    androidx.compose.runtime.key(m.name) {
                        val pop = remember { androidx.compose.animation.core.Animatable(if (Team.justAdded == m.name) 0f else 1f) }
                        LaunchedEffect(Unit) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 400f)) }
                        Box(Modifier.graphicsLayer { scaleX = 0.6f + 0.4f * pop.value; scaleY = 0.6f + 0.4f * pop.value; alpha = pop.value.coerceIn(0f, 1f) }) {
                            MemberCard(m, { editing = i }) { on -> members = members.map { if (it == m) it.copy(on = on) else it }; Team.members = members; save() }
                        }
                    }
                }
                AddCard("Add a sub-agent") { editing = -1 }
                if (members.isEmpty()) Txt(
                    "For example: Researcher (reads the code and finds what to change), Coder (makes the change), Reviewer (checks it and runs the tests).",
                    Modifier.padding(4.dp), size = 13.sp, color = c.textDim, lineHeight = 18.sp,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(mode == "auto") {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                Txt("At most", Modifier.padding(start = 4.dp), size = 13.sp, color = c.textDim)
                Segmented(listOf("1" to "1", "2" to "2", "3" to "3", "4" to "4", "5" to "5"), max, { max = it; prefs.teamMax = it })
                Txt("Models the helpers can use", Modifier.padding(start = 4.dp, top = 6.dp), size = 13.sp, color = c.textDim)
                pool.forEach { ref ->
                    val (p, m) = ref.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    var always by remember(ref) { mutableStateOf(ref in Team.always) }
                    Card {
                        Avatar(modelName(p, m))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Txt(modelName(p, m), size = 16.sp, maxLines = 1)
                            Txt(Team.cost(ref) + if (always) " · helps without asking" else " · asks first", size = 13.sp, color = c.textDim, maxLines = 2)
                        }
                        OcSwitch(always) { always = it; Team.setAlways(ref, it); Graph.chats.forgetRules() }
                        IconBtn(Ic.Close, { pool = pool - ref; Team.pool = pool; save() }, tint = c.textDim, size = 36.dp, iconSize = 17.dp)
                    }
                }
                AddCard("Add a model") { poolPicker = true }
            }
        }
        if (onAll != null) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.Box(Modifier.clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp))) {
                SettingsRow("All sub-agent settings", icon = Ic.Sliders, chevron = true, onClick = onAll)
            }
        }
    }

    editing?.let { i ->
        val old = members.getOrNull(i)
        MemberDialog(old, onDelete = if (old != null) ({ members = members - old; Team.members = members; save(); editing = null }) else null, onDismiss = { editing = null }) { m ->
            members = if (old == null) members + m else members.map { if (it == old) m else it }
            // adding someone means wanting a team: switch it on
            if (mode == "off" || mode == "auto") { mode = "manual"; prefs.teamMode = "manual" }
            Team.members = members; save(); editing = null
            // a new one: its card pops in here, and its cloud flies into the row above the input
            if (old == null) { Team.justAdded = m.name; onCreated?.invoke() }
        }
    }
    if (poolPicker) ModelPicker({ poolPicker = false }) { p, m ->
        val ref = "$p/$m"
        if (ref !in pool) { pool = pool + ref; Team.pool = pool; save() }
        poolPicker = false
    }
}

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp))
            .then(if (onClick != null) Modifier.tap(scale = 0.98f, onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, content = content,
    )
}

/** The helper's first letter in a circle, tinted by its name, so each one is easy to tell apart. */
@Composable
private fun Avatar(name: String, dim: Boolean = false) {
    val hue = (name.hashCode() and 0xffff) % 360
    val tint = androidx.compose.ui.graphics.Color.hsv(hue.toFloat(), 0.45f, 0.85f)
    androidx.compose.foundation.layout.Box(
        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = if (dim) 0.12f else 0.22f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Txt(name.trim().take(1).uppercase().ifBlank { "?" }, size = 17.sp, color = if (dim) Oc.c.textDim else tint, weight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
}

@Composable
private fun MemberCard(m: Team.Member, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = Oc.c
    Card(onEdit) {
        // its own little cloud, the same one that waits above the input
        androidx.compose.foundation.layout.Box(Modifier.size(40.dp).graphicsLayer { alpha = if (m.on) 1f else 0.4f }, contentAlignment = androidx.compose.ui.Alignment.Center) {
            app.ocmobile.ui.components.HelperGlyph(if (m.on) "working" else "idle", 34.dp, Team.slug(m.name))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Txt(m.name, size = 16.sp, maxLines = 1, color = if (m.on) c.text else c.textDim)
            Txt(m.role, size = 13.sp, color = c.textDim, maxLines = 2, lineHeight = 17.sp)
            Txt(modelName(m.provider, m.model), size = 12.sp, color = c.accent.copy(alpha = if (m.on) 1f else 0.5f), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
        }
        OcSwitch(m.on, onToggle)
    }
}

@Composable
private fun AddCard(label: String, onClick: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().clip(Oc.round(20.dp)).border(1.dp, c.border, Oc.round(20.dp)).tap(scale = 0.98f, onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        app.ocmobile.ui.components.Ico(Ic.Plus, tint = c.accent, size = 20.dp)
        Spacer(Modifier.size(10.dp))
        Txt(label, size = 16.sp, color = c.accent)
    }
}

private fun modelName(p: String, m: String): String =
    if (m.isBlank()) "chat model" else Graph.catalog.model(p, m)?.name?.ifBlank { null } ?: m

@Composable
internal fun MemberDialog(old: Team.Member?, onDelete: (() -> Unit)?, onDismiss: () -> Unit, onSave: (Team.Member) -> Unit) {
    val c = Oc.c
    var name by remember { mutableStateOf(old?.name ?: "") }
    var role by remember { mutableStateOf(old?.role ?: "") }
    var provider by remember { mutableStateOf(old?.provider ?: "") }
    var model by remember { mutableStateOf(old?.model ?: "") }
    var picking by remember { mutableStateOf(false) }
    OcDialog(if (old == null) "New sub-agent" else old.name, onDismiss, confirmText = if (old == null) "Create" else "Save", onConfirm = {
        if (name.isNotBlank() && role.isNotBlank()) onSave(Team.Member(name.trim(), role.trim(), provider, model, old?.on ?: true))
    }) {
        Column {
            OcTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Name, e.g. Reviewer")
            Spacer(Modifier.height(10.dp))
            OcTextField(role, { role = it }, Modifier.fillMaxWidth(), placeholder = "What it does", singleLine = false, minLines = 2)
            Spacer(Modifier.height(6.dp))
            SettingsRow("Model", subtitle = modelName(provider, model), icon = Ic.Cpu, chevron = true, onClick = { picking = true })
            if (onDelete != null) SettingsRow("Remove", icon = Ic.Trash, titleColor = c.danger, onClick = onDelete)
        }
    }
    if (picking) ModelPicker({ picking = false }, allowChatModel = true) { p, m -> provider = p; model = m; picking = false }
}

/** Pick a model from the connected providers; [allowChatModel] adds a "same as in chat" item. */
@Composable
private fun ModelPicker(onDismiss: () -> Unit, allowChatModel: Boolean = false, onPick: (String, String) -> Unit) {
    val providers by Graph.catalog.providers.collectAsState()
    var query by remember { mutableStateOf("") }
    PickerFrame("Choose a model", onDismiss, skipHalf = false) {
        PickSearch(query, { query = it }, "Search models")
        LazyColumn(Modifier.heightIn(max = 520.dp).padding(bottom = 8.dp)) {
            if (allowChatModel) item { PickRow("Same as the chat", { onPick("", "") }) }
            providers.forEach { p ->
                val ms = p.models.values.filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) }.sortedBy { it.name }
                if (ms.isNotEmpty()) {
                    item(key = "h-${p.id}") { PickHeader(p.name.ifBlank { p.id }) }
                    items(ms, key = { p.id + "/" + it.id }) { m -> PickRow(m.name.ifBlank { m.id }, { onPick(p.id, m.id) }) }
                }
            }
        }
    }
}
