package app.ocmobile.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.BuildConfig
import app.ocmobile.Graph
import app.ocmobile.api.OcJson
import app.ocmobile.api.str
import app.ocmobile.runtime.RuntimeState
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.Chip
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.Segmented
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

// ---------- Connectors (MCP) ----------

@Composable
fun ConnectorsScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<JsonObject?>(null) }
    var add by remember { mutableStateOf(false) }
    suspend fun load() { status = runCatching { Graph.api.mcpStatus() }.getOrDefault(JsonObject(emptyMap())) }
    LaunchedEffect(Unit) { load() }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Connectors", left = { IconBtn(Ic.Back, { nav.pop() }) })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 120.dp)) {
                val s = status
                if (s == null) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Spinner(28.dp) }
                else if (s.isEmpty()) Txt("No MCP servers configured. Connectors give the agent extra tools — databases, browsers, docs.", Modifier.padding(24.dp), size = 16.sp, color = c.textDim, lineHeight = 23.sp)
                else SettingsGroup {
                    s.forEach { (name, v) ->
                        val st = (v as? JsonObject)?.str("status") ?: "unknown"
                        val on = st == "connected"
                        SettingsRow(name, subtitle = (v as? JsonObject)?.str("error") ?: st.replace('_', ' '), icon = Ic.Puzzle, trailing = {
                            OcSwitch(on) { want ->
                                scope.launch { runCatching { if (want) Graph.api.mcpConnect(name) else Graph.api.mcpDisconnect(name) }; load() }
                            }
                        })
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) { Pill("Add connector", { add = true }, icon = Ic.Plus) }
    }
    if (add) AddConnectorDialog({ add = false; scope.launch { load() } })
}

@Composable
private fun AddConnectorDialog(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("remote") }
    var target by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OcDialog("Add connector", onDone, "Add", {
        val cfg = if (kind == "remote") buildJsonObject { put("type", "remote"); put("url", target.trim()); put("enabled", true) }
        else buildJsonObject {
            put("type", "local"); put("enabled", true)
            putJsonArray("command") { target.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } }
        }
        scope.launch {
            runCatching { Graph.api.patchConfig(buildJsonObject { put("mcp", buildJsonObject { put(name.trim(), cfg) }) }); Graph.api.mcpAdd(name.trim(), cfg) }
                .onSuccess { onDone() }.onFailure { error = it.message }
        }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OcTextField(name, { name = it }, placeholder = "Name (e.g. github)")
            Segmented(listOf("remote" to "Remote URL", "local" to "Local command"), kind, { kind = it })
            OcTextField(target, { target = it }, placeholder = if (kind == "remote") "https://…/mcp" else "npx -y @modelcontextprotocol/server-everything", mono = true)
            error?.let { Txt(it, size = 14.sp, color = Oc.c.danger) }
        }
    }
}

// ---------- Local server ----------

@Composable
fun ServerScreen(nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rt by Graph.runtime.state.collectAsState()
    val log by Graph.runtime.log.collectAsState()
    val prefs = Graph.prefs
    var showLog by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf<List<String>?>(null) }
    var reset by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Local server", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(24.dp)).background(c.surface).padding(20.dp)) {
                val (label, color) = when (val s = rt) {
                    is RuntimeState.Running -> "Running · OpenCode ${s.version}" to c.ok
                    is RuntimeState.Starting -> "Starting…" to c.warn
                    is RuntimeState.Crashed -> s.message to c.danger
                    else -> "Stopped" to c.textDim
                }
                Txt(label, size = 19.sp, color = color)
                Txt("Ubuntu 24.04 (proot) · 127.0.0.1:${prefs.serverPort}", size = 15.sp, color = c.textDim, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (rt is RuntimeState.Running || rt is RuntimeState.Starting) SmallButton("Stop", { Graph.runtime.stopServer() }, danger = true)
                    else SmallButton("Start", { Graph.runtime.startServer() }, filled = true)
                    SmallButton("Restart", { Graph.runtime.stopServer(); scope.launch { kotlinx.coroutines.delay(1200); Graph.runtime.startServer() } })
                    SmallButton("Log", { showLog = true })
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Start with the app", icon = Ic.Zap, trailing = { OcSwitch(prefs.autoStartServer) { prefs.autoStartServer = it } })
                SettingsRow("Keep CPU awake while running", icon = Ic.Server, trailing = { OcSwitch(prefs.keepAwake) { prefs.keepAwake = it } })
                SettingsRow("Compatibility mode", subtitle = "PROOT_NO_SECCOMP — try if crashes happen on your phone", icon = Ic.Shield, trailing = { OcSwitch(prefs.noSeccomp) { prefs.noSeccomp = it } })
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Update OpenCode", subtitle = updating ?: "Installed: ${prefs.opencodeVersion.ifBlank { "?" }}", icon = Ic.Download, onClick = {
                    if (updating == null) { updating = "Checking…"; scope.launch {
                        updating = runCatching { "Updated to " + Graph.runtime.updateOpencode() }.getOrElse { it.message }
                    } }
                })
                SettingsRow("Install dev tools", subtitle = "git, ripgrep, curl, python3, build-essential", icon = Ic.Terminal, onClick = {
                    val lines = mutableStateListOf<String>(); tools = lines
                    scope.launch {
                        val code = Graph.runtime.installDevTools { l -> scope.launch(Dispatchers.Main) { lines.add(l) } }
                        lines.add(if (code == 0) "✓ Done" else "✗ Failed (exit $code)")
                    }
                })
                SettingsRow("Don't kill the server in background", subtitle = "Exempt the app from battery optimization", icon = Ic.Zap, onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                SettingsRow("Reset environment", subtitle = "Deletes Ubuntu and everything inside it", icon = Ic.Trash, titleColor = c.danger, onClick = { reset = true })
            }
            Txt(
                "Android 12+ may kill background child processes. If the server dies when the screen is off, run once from a computer:\n" +
                    "adb shell device_config put activity_manager max_phantom_processes 2147483647\n" +
                    "adb shell settings put global settings_enable_monitor_phantom_procs false",
                size = 13.sp, color = c.textDim, modifier = Modifier.padding(20.dp), lineHeight = 19.sp, family = Oc.mono,
            )
        }
    }

    if (showLog) LogSheet(log) { showLog = false }
    tools?.let { lines -> LogSheet(lines, "Installing tools") { tools = null } }
    if (reset) OcDialog("Reset environment?", { reset = false }, "Reset", {
        reset = false; Graph.runtime.resetRuntime()
    }, danger = true) { Txt("Ubuntu, OpenCode and all projects under /root will be deleted. Sessions history is lost too.", size = 16.sp, color = Oc.c.textDim) }
}

@Composable
private fun LogSheet(lines: List<String>, title: String = "Server log", onDismiss: () -> Unit) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    OcSheet(onDismiss, title, skipHalf = false) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("Copy", { clip.setText(AnnotatedString(lines.joinToString("\n"))) })
        }
        Column(Modifier.fillMaxWidth().height(480.dp).verticalScroll(rememberScrollState(), reverseScrolling = true).padding(16.dp)) {
            Txt(lines.joinToString("\n").ifBlank { "(empty)" }, size = 11.sp, family = Oc.mono, color = c.textDim, lineHeight = 15.sp)
        }
    }
}

// ---------- Config editor ----------

@Composable
fun ConfigEditorScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val pretty = remember { Json { prettyPrint = true } }
    suspend fun load() {
        text = runCatching { pretty.encodeToString(JsonObject.serializer(), Graph.api.config()) }.getOrElse { "{}" }; loaded = true
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Config", left = { IconBtn(Ic.Back, { nav.pop() }) }, right = { IconBtn(Ic.Refresh, { scope.launch { load() } }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Txt("Everything OpenCode supports in opencode.json: models, agents, MCP, permissions, formatters, instructions, keybinds. Changes are merged and applied at once.", size = 14.sp, color = c.textDim, modifier = Modifier.padding(bottom = 12.dp), lineHeight = 20.sp)
            if (!loaded) Spinner(26.dp) else OcTextField(text, { text = it; msg = null }, singleLine = false, minLines = 16, mono = true)
            Spacer(Modifier.height(14.dp))
            Pill("Save", {
                val o = runCatching { OcJson.parseToJsonElement(text) as JsonObject }.getOrNull()
                if (o == null) msg = "Not valid JSON object"
                else scope.launch { msg = runCatching { Graph.api.patchConfig(o); "Saved" }.getOrElse { it.message } }
            }, modifier = Modifier.fillMaxWidth())
            msg?.let { Txt(it, size = 15.sp, color = if (it == "Saved") c.ok else c.danger, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

// ---------- Profile ----------

@Composable
fun ProfileScreen(nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val prefs = Graph.prefs
    var name by remember { mutableStateOf(prefs.userName) }
    var rules by remember { mutableStateOf(app.ocmobile.data.Memory.userRules()) }
    var saved by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val side = minOf(bmp.width, bmp.height)
            val sq = android.graphics.Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val out = android.graphics.Bitmap.createScaledBitmap(sq, 512.coerceAtMost(side), 512.coerceAtMost(side), true)
            val f = File(ctx.filesDir, "avatar-${System.currentTimeMillis()}.jpg")
            f.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            prefs.avatarPath = f.path
        }
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Profile", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(112.dp) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            Spacer(Modifier.height(14.dp))
            SmallButton("Edit photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt("What should we call you?", size = 17.sp, modifier = Modifier.padding(start = 4.dp))
                OcTextField(name, { name = it })
                Pill("Update profile", { prefs.userName = name.trim() }, enabled = name.trim() != prefs.userName, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Txt("What personal preferences should OpenCode consider in responses?", size = 19.sp, modifier = Modifier.padding(start = 4.dp))
                Txt("Saved as global AGENTS.md — applies to every project.", size = 14.sp, color = c.textDim, modifier = Modifier.padding(start = 4.dp))
                OcTextField(rules, { rules = it; saved = false }, singleLine = false, minLines = 6, placeholder = "Reply in Russian. Prefer small diffs. Ask before big refactors.")
                Pill(if (saved) "Saved" else "Save preferences", {
                    runCatching { app.ocmobile.data.Memory.saveUserRules(rules) }.onSuccess { saved = true }
                }, enabled = !saved, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------- About ----------

@Composable
fun AboutScreen(nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    fun open(u: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("About", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            SettingsGroup {
                SettingsRow("OpenCode mobile", subtitle = "v${BuildConfig.VERSION_NAME}", icon = Ic.Info)
                SettingsRow("OpenCode (in container)", subtitle = Graph.prefs.opencodeVersion.ifBlank { "not installed" }, icon = Ic.Terminal)
                SettingsRow("OpenCode documentation", icon = Ic.External, onClick = { open("https://opencode.ai/docs") })
                SettingsRow("OpenCode Go console", icon = Ic.External, onClick = { open("https://opencode.ai/console") })
            }
            Txt(
                "Unofficial client. Bundles proot and libtalloc from the Termux packages (GPL-2.0 / LGPL-3.0) and downloads Ubuntu Base 24.04 and OpenCode (MIT) on first run.",
                size = 13.sp, color = c.textDim, modifier = Modifier.padding(20.dp), lineHeight = 19.sp,
            )
        }
    }
}
