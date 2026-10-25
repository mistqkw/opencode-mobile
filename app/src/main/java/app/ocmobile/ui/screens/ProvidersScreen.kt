package app.ocmobile.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.AllProvider
import app.ocmobile.api.AuthMethod
import app.ocmobile.api.str
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.Chip
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch

private val popular = listOf("opencode-go", "opencode", "anthropic", "openai", "github-copilot", "google", "openrouter", "xai", "deepseek", "groq", "mistral", "zai", "moonshotai")

@Composable
fun ProvidersScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val connected by Graph.catalog.providers.collectAsState()
    var all by remember { mutableStateOf<List<AllProvider>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var connect by remember { mutableStateOf<AllProvider?>(null) }
    var disconnect by remember { mutableStateOf<String?>(null) }

    suspend fun load() { all = runCatching { Graph.api.allProviders().all }.getOrDefault(emptyList()); loading = false }
    LaunchedEffect(Unit) { load() }

    val connectedIds = connected.map { it.id }.toSet()
    val available = all.filter { it.id !in connectedIds && (query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true)) }
        .sortedWith(compareBy({ popular.indexOf(it.id).let { i -> if (i < 0) 999 else i } }, { it.name.lowercase() }))

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Providers", left = { IconBtn(Ic.Back, { nav.pop() }) })
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item { SectionLabel("Connected") }
            item {
                if (connected.isEmpty()) Txt("Nothing connected yet", Modifier.padding(horizontal = 20.dp), size = 16.sp, color = c.textDim)
                else SettingsGroup {
                    connected.forEach { p ->
                        SettingsRow(p.name.ifBlank { p.id }, subtitle = "${p.models.size} models", icon = Ic.Key, trailing = {
                            if (p.id == "opencode-go") Chip("Go", color = c.bg, bg = c.text)
                            SmallButton("Disconnect", { disconnect = p.id }, danger = true)
                        })
                    }
                }
            }
            item {
                SectionLabel("Add provider")
                Box(Modifier.padding(horizontal = 16.dp)) { OcTextField(query, { query = it }, placeholder = "Search providers") }
                Spacer(Modifier.height(12.dp))
            }
            if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp)) { Spinner(26.dp) } }
            item {
                SettingsGroup {
                    available.take(60).forEach { p ->
                        SettingsRow(p.name.ifBlank { p.id }, subtitle = p.id, icon = Ic.Plus, chevron = true, onClick = { connect = p })
                    }
                }
            }
        }
    }

    connect?.let { p ->
        ConnectSheet(p, onDone = { connect = null; scope.launch { load() } })
    }
    disconnect?.let { id ->
        OcDialog("Disconnect $id?", { disconnect = null }, "Disconnect", {
            disconnect = null
            scope.launch {
                runCatching { Graph.api.removeAuth(id); Graph.api.disposeAll() }
                Graph.catalog.refresh(); load()
            }
        }, danger = true) { Txt("Stored credentials for this provider are removed from the phone.", size = 16.sp, color = Oc.c.textDim) }
    }
}

@Composable
private fun ConnectSheet(p: AllProvider, onDone: () -> Unit) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var methods by remember { mutableStateOf<List<AuthMethod>?>(null) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var oauthIndex by remember { mutableStateOf<Int?>(null) }
    var oauthHint by remember { mutableStateOf<String?>(null) }
    var oauthMode by remember { mutableStateOf("code") }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(p.id) {
        methods = runCatching { Graph.api.providerAuthMethods()[p.id] }.getOrNull().orEmpty().ifEmpty { listOf(AuthMethod("api", "API key")) }
    }

    fun finish() { scope.launch { runCatching { Graph.api.disposeAll() }; Graph.catalog.refresh(); Graph.chats.toasts.tryEmit("${p.name} connected"); onDone() } }

    OcSheet(onDone, p.name.ifBlank { p.id }, skipHalf = false) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (methods == null) Box(Modifier.fillMaxWidth().padding(24.dp)) { Spinner(26.dp) }
            methods?.forEachIndexed { i, m ->
                if (m.type == "api") {
                    Txt(m.label.ifBlank { "API key" }, size = 16.sp, color = c.textDim)
                    OcTextField(key, { key = it }, placeholder = "Paste key", password = true, mono = true)
                    if (p.id == "opencode-go" || p.id == "opencode") {
                        Txt("Get a key at opencode.ai/console (Go and Zen use the same console).", size = 14.sp, color = c.textDim)
                        SmallButton("Open console", { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://opencode.ai/console")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                    }
                    Pill(if (busy) "Connecting…" else "Connect", {
                        busy = true; error = null
                        scope.launch {
                            runCatching { Graph.api.setApiKey(p.id, key.trim()) }.onSuccess { finish() }.onFailure { error = it.message }
                            busy = false
                        }
                    }, enabled = key.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth())
                } else {
                    Pill(m.label.ifBlank { "Sign in" }, {
                        busy = true; error = null
                        scope.launch {
                            runCatching { Graph.api.oauthAuthorize(p.id, i) }.onSuccess { r ->
                                oauthIndex = i
                                oauthMode = r.str("method") ?: "code"
                                oauthHint = r.str("instructions")
                                r.str("url")?.let { u -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                if (oauthMode == "auto") {
                                    runCatching { Graph.api.oauthCallback(p.id, i, null) }.onSuccess { finish() }.onFailure { error = it.message }
                                }
                            }.onFailure { error = it.message }
                            busy = false
                        }
                    }, filled = false, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                }
            }
            oauthHint?.let { Txt(it, size = 15.sp, color = c.textDim) }
            if (oauthIndex != null && oauthMode == "code") {
                OcTextField(code, { code = it }, placeholder = "Paste the code from the browser", mono = true)
                Pill("Complete sign-in", {
                    busy = true
                    scope.launch {
                        runCatching { Graph.api.oauthCallback(p.id, oauthIndex!!, code.trim()) }.onSuccess { finish() }.onFailure { error = it.message }
                        busy = false
                    }
                }, enabled = code.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth())
            }
            error?.let { Txt(it, size = 15.sp, color = c.danger) }
        }
    }
}
