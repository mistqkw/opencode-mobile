package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.ocmobile.api.NamedItem
import app.ocmobile.api.str
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.ChoiceRow
import app.ocmobile.ui.components.Chip
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.ModelSheet
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.Segmented
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun ModelsScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val providers by Graph.catalog.providers.collectAsState()
    var cfg by remember { mutableStateOf(JsonObject(emptyMap())) }
    var pickMain by remember { mutableStateOf(false) }
    var pickSmall by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cfg = runCatching { Graph.api.config() }.getOrDefault(cfg) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Models", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            SettingsGroup {
                SettingsRow("Model for new messages", subtitle = Graph.catalog.selectedLabel(), icon = Ic.Cpu, chevron = true, onClick = { pickMain = true })
                SettingsRow("Small model", subtitle = cfg.str("small_model") ?: "Auto (titles & summaries)", icon = Ic.Sparkle, chevron = true, onClick = { pickSmall = true })
            }
            SectionLabel("Available")
            SettingsGroup {
                providers.forEach { p -> SettingsRow(p.name.ifBlank { p.id }, subtitle = "${p.models.size} models", icon = Ic.Server) }
            }
        }
    }
    if (pickMain) ModelSheet { pickMain = false }
    if (pickSmall) OcSheet({ pickSmall = false }, "Small model", skipHalf = false) {
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            item { ChoiceRow("Auto", cfg.str("small_model") == null, { pickSmall = false }, subtitle = "Let OpenCode choose") }
            providers.forEach { p ->
                items(p.models.values.toList(), key = { p.id + it.id }) { m ->
                    val ref = p.id + "/" + m.id
                    ChoiceRow(m.name.ifBlank { m.id }, cfg.str("small_model") == ref, {
                        pickSmall = false
                        scope.launch { cfg = runCatching { Graph.api.patchConfig(buildJsonObject { put("small_model", ref) }) }.getOrDefault(cfg) }
                    }, subtitle = p.name)
                }
            }
        }
    }
}

@Composable
fun PermissionsScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(JsonObject(emptyMap())) }
    LaunchedEffect(Unit) { cfg = runCatching { Graph.api.config() }.getOrDefault(cfg) }
    val perm = cfg["permission"]
    fun valueOf(k: String): String = when (perm) {
        is JsonPrimitive -> perm.content
        is JsonObject -> (perm[k] as? JsonPrimitive)?.content ?: "default"
        else -> "default"
    }
    fun set(k: String, v: String) = scope.launch {
        cfg = runCatching { Graph.api.patchConfig(buildJsonObject { put("permission", buildJsonObject { put(k, v) }) }) }.getOrDefault(cfg)
    }
    val opts = listOf("allow" to "Allow", "ask" to "Ask", "deny" to "Deny")
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Permissions", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            SectionLabel("New sessions")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Segmented(listOf("ask" to "Ask", "edits" to "Auto-edit", "auto" to "Auto"), Graph.prefs.permissionMode, { Graph.prefs.permissionMode = it })
                Txt(
                    "Applied to every session you start. Auto-edit lets the agent change files but asks before shell commands and web access.",
                    size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 10.dp), lineHeight = 20.sp,
                )
            }
            SectionLabel("Global rules (opencode.json)")
            listOf("edit" to "File edits", "bash" to "Shell commands", "webfetch" to "Web fetch", "external_directory" to "Outside the project").forEach { (k, label) ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Txt(label, size = 17.sp, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                    Segmented(opts, valueOf(k), { set(k, it) })
                }
            }
        }
    }
}

@Composable
fun AgentsScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val agents by Graph.catalog.agents.collectAsState()
    LaunchedEffect(Unit) { Graph.catalog.refresh() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Agents", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            SettingsGroup {
                agents.forEach { a ->
                    SettingsRow(
                        a.name.replaceFirstChar { it.uppercase() }, subtitle = a.description, icon = Ic.Bot,
                        trailing = { if (Graph.prefs.agent == a.name) Chip("Active", color = c.link) },
                        onClick = {
                            Graph.prefs.agent = a.name
                            scope.launch { runCatching { Graph.api.patchConfig(buildJsonObject { put("default_agent", a.name) }) } }
                        },
                    )
                }
            }
            Txt("Custom agents can be added in the config (agent section) or as markdown files in ~/.config/opencode/agent inside the container.", size = 14.sp, color = c.textDim, modifier = Modifier.padding(20.dp), lineHeight = 20.sp)
        }
    }
}

@Composable
fun SkillsScreen(nav: Nav) {
    val c = Oc.c
    var skills by remember { mutableStateOf<List<NamedItem>>(emptyList()) }
    var cmds by remember { mutableStateOf<List<NamedItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        skills = runCatching { Graph.api.skills() }.getOrDefault(emptyList())
        cmds = runCatching { Graph.api.commands() }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Skills & commands", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            SectionLabel("Skills")
            if (skills.isEmpty()) Txt("No skills installed", Modifier.padding(horizontal = 20.dp), size = 16.sp, color = c.textDim)
            else SettingsGroup { skills.forEach { SettingsRow(it.name, subtitle = it.description, icon = Ic.Scroll) } }
            SectionLabel("Slash commands")
            if (cmds.isEmpty()) Txt("No commands", Modifier.padding(horizontal = 20.dp), size = 16.sp, color = c.textDim)
            else SettingsGroup { cmds.forEach { SettingsRow("/" + it.name, subtitle = it.description, icon = Ic.Terminal) } }
        }
    }
}
