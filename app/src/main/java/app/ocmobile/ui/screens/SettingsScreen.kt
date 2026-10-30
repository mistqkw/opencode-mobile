package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.ocmobile.Graph
import app.ocmobile.runtime.RuntimeState
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.ChoiceRow
import app.ocmobile.ui.components.Chip
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.ModelSheet
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.AppIcons
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

@Composable
fun ChoiceDialog(title: String, options: List<Triple<String, String, ImageVector?>>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Oc.c
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(Oc.round(32.dp)).background(c.bg).border(1.dp, c.border, Oc.round(32.dp)).heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Txt(title, Modifier.padding(start = 24.dp, top = 16.dp, bottom = 10.dp), size = 22.sp)
            options.forEach { (k, l, ic) -> ChoiceRow(l, k == selected, { onSelect(k); onDismiss() }, icon = ic) }
        }
    }
}

@Composable
fun SettingsScreen(nav: Nav, onMenu: () -> Unit) {
    val c = Oc.c
    val prefs = Graph.prefs
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val rt by Graph.runtime.state.collectAsState()
    val providers by Graph.catalog.providers.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }
    var modelSheet by remember { mutableStateOf(false) }
    var iconSheet by remember { mutableStateOf(false) }
    val hasGo = providers.any { it.id == "opencode-go" }
    val modelLabel = Graph.catalog.selectedLabel()
    val serverText = when (rt) {
        is RuntimeState.Running -> "Running · OpenCode ${(rt as RuntimeState.Running).version}"
        is RuntimeState.Starting -> "Starting…"
        is RuntimeState.Crashed -> "Crashed"
        else -> "Stopped"
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Settings", left = { IconBtn(Ic.Menu, onMenu) }, right = { IconBtn(Ic.Info, { nav.push(Route.About) }) }, serif = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            // Карточка аккаунта (в Messenger — профиль, как в Telegram)
            if (Oc.look.id == "messenger") Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(96.dp) { nav.push(Route.Profile) }
                Spacer(Modifier.height(12.dp))
                Txt(prefs.userName.ifBlank { "OpenCode" }, size = 22.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Txt(if (hasGo) "OpenCode Go" else "free models", size = 14.sp, color = c.link, modifier = Modifier.padding(top = 2.dp))
            } else Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(22.dp)).border(1.dp, c.border, Oc.round(22.dp))
                    .clickable { nav.push(if (prefs.signedIn.isBlank()) Route.Login else Route.Providers) }.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Txt(prefs.userName.ifBlank { "OpenCode" }, size = 19.sp)
                    Txt(
                        when (prefs.signedIn) { "github" -> "Signed in with GitHub"; "google" -> "Signed in with Google"; "email" -> "Signed in with email"; "key" -> "Using an API key"; else -> "Not signed in" },
                        size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(Modifier.clip(Oc.pill).background(c.text).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Txt(if (hasGo) "Go" else "Free", size = 14.sp, color = c.bg)
                }
                Spacer(Modifier.width(10.dp)); Ico(Ic.ChevronRight, tint = c.textDim, size = 22.dp)
            }
            if (prefs.signedIn.isBlank()) {
                Column(Modifier.padding(16.dp).fillMaxWidth().clip(Oc.round(22.dp)).border(1.dp, c.border, Oc.round(22.dp)).padding(24.dp)) {
                    Txt("Sign in for more", size = 21.sp)
                    Txt("Free models share a limit per internet connection. Sign in with GitHub or Google for a free allowance of your own.", size = 17.sp, color = c.textDim, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                    Pill("Sign in", { nav.push(Route.Login) })
                }
            } else if (!hasGo) {
                Column(Modifier.padding(16.dp).fillMaxWidth().clip(Oc.round(22.dp)).border(1.dp, c.border, Oc.round(22.dp)).padding(24.dp)) {
                    Txt("Want more models?", size = 21.sp)
                    Txt("Connect OpenCode Go, Zen or any other provider to unlock stronger models.", size = 17.sp, color = c.textDim, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                    Pill("Connect", { nav.push(Route.Providers) })
                }
            } else Spacer(Modifier.height(16.dp))

            // Как в Claude: четыре коротких группы, остальное — на вложенных экранах.
            SettingsGroup {
                SettingsRow("Profile", icon = Ic.User, onClick = { nav.push(Route.Profile) })
                SettingsRow("Billing", icon = Ic.Dollar, onClick = { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://opencode.ai/console")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) })
                SettingsRow("Usage", icon = Ic.Drop, onClick = { nav.push(Route.Usage) })
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Capabilities", subtitle = listOfNotNull("Memory".takeIf { prefs.memoryEnabled }, modelLabel.takeIf { it.isNotBlank() }).joinToString(" · "), icon = Ic.Sliders, onClick = { nav.push(Route.Capabilities) })
                SettingsRow("OpenCode", subtitle = serverText, icon = Ic.Terminal, onClick = { nav.push(Route.OcSettings) })
                SettingsRow("Connectors", subtitle = "MCP servers", icon = Ic.Puzzle, onClick = { nav.push(Route.Connectors) })
                SettingsRow("GitHub", subtitle = if (app.ocmobile.data.GitHub.signedIn) "@" + prefs.githubLogin else "Let OpenCode work with your repos", icon = Ic.GitHub, onClick = { nav.push(Route.GitHub) })
                SettingsRow("Phone tools", subtitle = if (prefs.phoneTools) "OpenCode can act on this phone" else "Off", icon = Ic.Phone, onClick = { nav.push(Route.PhoneTools) })
                SettingsRow("Permissions", subtitle = mapOf("ask" to "Ask", "edits" to "Auto-edit", "auto" to "Auto")[prefs.permissionMode], icon = Ic.Shield, onClick = { nav.push(Route.PermissionsCfg) })
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Interface", subtitle = app.ocmobile.ui.theme.Looks.of(prefs.edition).title, icon = Ic.Laptop, onClick = { nav.push(Route.Edition) })
                SettingsRow("Color mode", subtitle = prefs.colorMode.replaceFirstChar { it.uppercase() }, icon = Ic.Moon, onClick = { dialog = "color" })
                SettingsRow("Font style", subtitle = if (prefs.fontStyle == "default") "Default" else "System", icon = Ic.FontSize, onClick = { nav.push(Route.Appearance) })
                SettingsRow("Voice", subtitle = app.ocmobile.data.Speech.label(), icon = Ic.Waveform, onClick = { dialog = "voice" })
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Haptic feedback", icon = Ic.Vibrate, trailing = { OcSwitch(prefs.haptics) { prefs.haptics = it } })
                SettingsRow("Progress on lock screen", subtitle = "What the agent is doing, with Allow / Deny buttons", icon = Ic.ListCheck, trailing = { OcSwitch(prefs.liveNotify) { prefs.liveNotify = it } })
                SettingsRow("Cost before sending", subtitle = "A hint under the message field", icon = Ic.Dollar, trailing = { OcSwitch(prefs.costHint) { prefs.costHint = it } })
                SettingsRow("Notifications", icon = Ic.Bell, onClick = {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                SettingsRow("App icon", subtitle = AppIcons.all.firstOrNull { it.id == prefs.appIcon }?.label, icon = Ic.Image, onClick = { iconSheet = true })
            }
            if (prefs.signedIn.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                SettingsGroup {
                    SettingsRow("Log out", icon = Ic.External, titleColor = c.danger, onClick = {
                        scope.launch {
                            for (pid in listOf("opencode", "opencode-go", "github-copilot", "google", "groq", "openrouter")) runCatching { Graph.api.removeAuth(pid) }
                            runCatching { Graph.api.disposeAll() }
                            app.ocmobile.data.Vault.forgetAccount()
                            prefs.signedIn = ""; runCatching { Graph.catalog.refresh() }
                        }
                    })
                }
            }
        }
    }

    when (dialog) {
        "voice" -> ChoiceDialog("Speech language", app.ocmobile.data.Speech.languages.map { Triple(it.first, it.second, null) }, prefs.speechLang, { prefs.speechLang = it }) { dialog = null }
        "color" -> ChoiceDialog("Color mode", listOf(Triple("system", "System", Ic.Phone), Triple("light", "Light", Ic.Sun), Triple("dark", "Dark", Ic.Moon)), prefs.colorMode, { prefs.colorMode = it }) { dialog = null }
        "font" -> ChoiceDialog("Font style", listOf(Triple("default", "Default (serif headings)", null), Triple("system", "System", Ic.Phone)), prefs.fontStyle, { prefs.fontStyle = it }) { dialog = null }
        "size" -> ChoiceDialog("Text size", listOf(Triple("small", "Small", null), Triple("medium", "Medium", null), Triple("large", "Large", null)), prefs.textSize, { prefs.textSize = it }) { dialog = null }
        "scale" -> ChoiceDialog("Display size", listOf(Triple("90", "Compact", null), Triple("100", "Default", null), Triple("110", "Large", null), Triple("120", "Extra large", null)), prefs.uiScale, { prefs.uiScale = it }) { dialog = null }
        "code" -> ChoiceDialog("Code font", listOf(Triple("mono", "Monospace", null), Triple("system", "System", null)), prefs.codeFont, { prefs.codeFont = it }) { dialog = null }
    }
    if (modelSheet) ModelSheet { modelSheet = false }
    if (iconSheet) IconPickerSheet { iconSheet = false }
}

/** «Capabilities»: модель, память, агенты, навыки — то, что в Claude лежит в одноимённом разделе. */
@Composable
fun CapabilitiesScreen(nav: Nav) {
    val c = Oc.c
    val prefs = Graph.prefs
    var modelSheet by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Capabilities", left = { IconBtn(Ic.Back, { nav.pop() }) }, serif = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            SettingsGroup {
                SettingsRow("Model", subtitle = Graph.catalog.selectedLabel(), icon = Ic.Cpu, onClick = { modelSheet = true })
                SettingsRow("Models & small model", icon = Ic.Sparkle, onClick = { nav.push(Route.Models) })
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Memory", subtitle = if (prefs.memoryEnabled) "${app.ocmobile.data.Memory.shelves().size} shelves" else "Off", icon = Ic.Scroll, onClick = { nav.push(Route.Memory) })
                SettingsRow("Agents", subtitle = prefs.agent.replaceFirstChar { it.uppercase() }, icon = Ic.Bot, onClick = { nav.push(Route.Agents) })
                SettingsRow("Skills", subtitle = "${app.ocmobile.data.Skills.local().size} skills · type / in a chat", icon = Ic.Scroll, onClick = { nav.push(Route.Skills) })
            }
        }
    }
    if (modelSheet) ModelSheet { modelSheet = false }
}

/** «OpenCode»: сервер на телефоне, провайдеры, конфиг — как раздел Claude Code в Claude. */
@Composable
fun OcSettingsScreen(nav: Nav) {
    val c = Oc.c
    val rt by Graph.runtime.state.collectAsState()
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("OpenCode", left = { IconBtn(Ic.Back, { nav.pop() }) }, serif = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            SettingsGroup {
                SettingsRow("Providers", subtitle = "Sign-ins and API keys", icon = Ic.Key, onClick = { nav.push(Route.Providers) })
                SettingsRow("Local server", subtitle = when (rt) {
                    is RuntimeState.Running -> "Running · OpenCode ${(rt as RuntimeState.Running).version}"
                    is RuntimeState.Starting -> "Starting…"; is RuntimeState.Crashed -> "Restarting…"; else -> "Stopped"
                }, icon = Ic.Server, onClick = { nav.push(Route.Server) })
                SettingsRow("Config (opencode.json)", icon = Ic.Code, onClick = { nav.push(Route.ConfigEditor) })
                SettingsRow("Backup & move", subtitle = "Everything in one file", icon = Ic.Archive, onClick = { nav.push(Route.Backup) })
            }
        }
    }
}

/** «Font style»: шрифт, размер текста и интерфейса, код — одним экраном вместо пяти строк. */
@Composable
fun AppearanceScreen(nav: Nav) {
    val c = Oc.c
    val prefs = Graph.prefs
    var dialog by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Font style", left = { IconBtn(Ic.Back, { nav.pop() }) }, serif = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            SettingsGroup {
                SettingsRow("Font", subtitle = if (prefs.fontStyle == "default") "Default (serif replies)" else "System", icon = Ic.FontSize, onClick = { dialog = "font" })
                SettingsRow("Text size", subtitle = prefs.textSize.replaceFirstChar { it.uppercase() }, icon = Ic.File, onClick = { dialog = "size" })
                SettingsRow("Display size", subtitle = when (prefs.uiScale) { "90" -> "Compact"; "110" -> "Large"; "120" -> "Extra large"; else -> "Default" }, icon = Ic.Expand, onClick = { dialog = "scale" })
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Code font", subtitle = if (prefs.codeFont == "mono") "Monospace" else "System", icon = Ic.Terminal, onClick = { dialog = "code" })
                SettingsRow("Wrap long lines", icon = Ic.Diff, trailing = { OcSwitch(prefs.wrapLines) { prefs.wrapLines = it } })
            }
        }
    }
    when (dialog) {
        "font" -> ChoiceDialog("Font", listOf(Triple("default", "Default (serif replies)", null), Triple("system", "System", Ic.Phone)), prefs.fontStyle, { prefs.fontStyle = it }) { dialog = null }
        "size" -> ChoiceDialog("Text size", listOf(Triple("small", "Small", null), Triple("medium", "Medium", null), Triple("large", "Large", null)), prefs.textSize, { prefs.textSize = it }) { dialog = null }
        "scale" -> ChoiceDialog("Display size", listOf(Triple("90", "Compact", null), Triple("100", "Default", null), Triple("110", "Large", null), Triple("120", "Extra large", null)), prefs.uiScale, { prefs.uiScale = it }) { dialog = null }
        "code" -> ChoiceDialog("Code font", listOf(Triple("mono", "Monospace", null), Triple("system", "System", null)), prefs.codeFont, { prefs.codeFont = it }) { dialog = null }
    }
}
