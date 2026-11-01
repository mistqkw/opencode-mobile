package app.ocmobile.ui.screens

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
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(prefs.teamMode) }
    var members by remember { mutableStateOf(Team.members) }
    var pool by remember { mutableStateOf(Team.pool) }
    var max by remember { mutableStateOf(prefs.teamMax) }
    var editing by remember { mutableStateOf<Int?>(null) }      // helper index; -1 means new
    var poolPicker by remember { mutableStateOf(false) }
    fun save() = scope.launch { Team.sync(force = true) }

    Column(Modifier.fillMaxSize().screenBackdrop("Team")) {
        TopBar("Team", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Segmented(listOf("off" to "Off", "manual" to "My team", "auto" to "Automatic"), mode, {
                    mode = it; prefs.teamMode = it; save()
                })
                Txt(
                    when (mode) {
                        "manual" -> "The main agent splits each task between your helpers and runs them in parallel, then checks and combines what they did."
                        "auto" -> "The main agent decides how many helpers a task needs and which model each one uses. Simple tasks get none."
                        else -> "One agent does everything, as before."
                    },
                    Modifier.padding(top = 10.dp, start = 4.dp), size = 14.sp, color = c.textDim, lineHeight = 20.sp,
                )
            }
            when (mode) {
                "manual" -> {
                    SectionLabel("Helpers")
                    SettingsGroup {
                        members.forEachIndexed { i, m ->
                            SettingsRow(m.name, subtitle = m.role + " · " + modelName(m.provider, m.model), icon = Ic.Bot, chevron = true, onClick = { editing = i })
                        }
                        SettingsRow("Add a helper", icon = Ic.Plus, onClick = { editing = -1 })
                    }
                    if (members.isEmpty()) Txt(
                        "For example: Researcher (reads the code and finds what to change), Coder (makes the change), Reviewer (checks it and runs the tests).",
                        Modifier.padding(20.dp), size = 14.sp, color = c.textDim, lineHeight = 20.sp,
                    )
                }
                "auto" -> {
                    SectionLabel("At most")
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Segmented(listOf("2" to "2", "3" to "3", "4" to "4", "5" to "5"), max, { max = it; prefs.teamMax = it })
                    }
                    SectionLabel("Models the helpers can use")
                    SettingsGroup {
                        pool.forEach { ref ->
                            val (p, m) = ref.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                            SettingsRow(modelName(p, m), icon = Ic.Cpu, trailing = {
                                IconBtn(Ic.Close, { pool = pool - ref; Team.pool = pool; save() }, tint = c.textDim, size = 38.dp, iconSize = 18.dp)
                            })
                        }
                        SettingsRow("Add a model", icon = Ic.Plus, onClick = { poolPicker = true })
                    }
                    Txt(
                        if (pool.isEmpty()) "No models picked: helpers use the model you chat with." else "Mix a strong model with a fast one: the main agent gives hard parts to the first and simple ones to the second.",
                        Modifier.padding(20.dp), size = 14.sp, color = c.textDim, lineHeight = 20.sp,
                    )
                }
            }
            if (mode != "off") Txt(
                "Helpers show up in the chat under Team while they work. Each one counts against your model limits.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp), size = 13.sp, color = c.textDim, lineHeight = 18.sp,
            )
        }
    }

    editing?.let { i ->
        val old = members.getOrNull(i)
        MemberDialog(old, onDelete = if (old != null) ({ members = members - old; Team.members = members; save(); editing = null }) else null, onDismiss = { editing = null }) { m ->
            members = if (old == null) members + m else members.map { if (it == old) m else it }
            Team.members = members; save(); editing = null
        }
    }
    if (poolPicker) ModelPicker({ poolPicker = false }) { p, m ->
        val ref = "$p/$m"
        if (ref !in pool) { pool = pool + ref; Team.pool = pool; save() }
        poolPicker = false
    }
}

private fun modelName(p: String, m: String): String =
    if (m.isBlank()) "chat model" else Graph.catalog.model(p, m)?.name?.ifBlank { null } ?: m

@Composable
private fun MemberDialog(old: Team.Member?, onDelete: (() -> Unit)?, onDismiss: () -> Unit, onSave: (Team.Member) -> Unit) {
    val c = Oc.c
    var name by remember { mutableStateOf(old?.name ?: "") }
    var role by remember { mutableStateOf(old?.role ?: "") }
    var provider by remember { mutableStateOf(old?.provider ?: "") }
    var model by remember { mutableStateOf(old?.model ?: "") }
    var picking by remember { mutableStateOf(false) }
    OcDialog(if (old == null) "New helper" else old.name, onDismiss, confirmText = "Save", onConfirm = {
        if (name.isNotBlank() && role.isNotBlank()) onSave(Team.Member(name.trim(), role.trim(), provider, model))
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
