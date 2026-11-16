package app.ocmobile.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ocmobile.BuildConfig
import app.ocmobile.Graph
import app.ocmobile.data.Updater
import app.ocmobile.data.Updater.State
import app.ocmobile.ui.theme.Oc

/** Open the update window by hand (from "About"). */
object UpdateUi { val open = mutableStateOf(false) }

/**
 * The "Update available" window: checks GitHub by itself on launch and, if there's a new version,
 * shows it with an Install button, then a download with progress and the system installer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateHost() {
    val st by Updater.state.collectAsState()
    var dismissed by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Updater.autoCheck() }
    val r = when (val s = st) {
        is State.Available -> s.r
        is State.Downloading -> s.r
        is State.Installing -> s.r
        is State.Failed -> s.r
        else -> null
    }
    // the window pops up by itself only for a new version that wasn't skipped or dismissed
    val auto = r != null && r.tag != Graph.prefs.updateSkipped && r.tag != dismissed
    // closed with "Later": a small bar at the top stays, one tap opens the window again
    if (!UpdateUi.open.value && !auto) {
        if (r != null && st is State.Available && r.tag != Graph.prefs.updateSkipped) UpdateBar(r) { UpdateUi.open.value = true }
        else { WhatsNewHost(); OcNotice() }
        return
    }
    if (UpdateUi.open.value && st is State.Idle) LaunchedEffect(Unit) { Updater.check() }

    val c = Oc.c
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(Updater.canInstall(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) allowed = Updater.canInstall(ctx) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    fun close() { UpdateUi.open.value = false; if (r != null) dismissed = r.tag }

    MagnetSheet(onDismiss = ::close, half = false, color = c.surface, corner = 28.dp, handle = false) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Txt(
                when (st) {
                    is State.Checking, is State.Idle -> "Checking for updates…"
                    is State.UpToDate -> "You're up to date"
                    is State.Failed -> "Update didn't go through"
                    else -> "Update available"
                },
                size = 22.sp, weight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Txt(
                if (r != null) "v${Updater.current}  →  ${r.name.removePrefix("v").let { "v$it" }}" else "v${Updater.current} is the newest version on GitHub.",
                size = 15.sp, color = c.textDim,
            )
            if (r != null && r.notes.isNotBlank() && st !is State.Failed) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Txt(r.notes.replace("**", "").replace(Regex("(?m)^- "), "•  ").trim(), size = 14.sp, lineHeight = 20.sp)
                }
            }
            (st as? State.Failed)?.let { Spacer(Modifier.height(12.dp)); Txt(it.message, size = 14.sp, color = c.danger, lineHeight = 20.sp) }
            if (BuildConfig.DEBUG && r != null) {
                Spacer(Modifier.height(10.dp))
                Txt("This is a test build: the release app installs next to it.", size = 13.sp, color = c.textDim)
            }
            Spacer(Modifier.height(20.dp))
            when (val s = st) {
                is State.Downloading -> {
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.surfaceHigh)) {
                        Box(Modifier.fillMaxWidth(s.progress.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(c.accent))
                    }
                    Spacer(Modifier.height(10.dp))
                    Txt("Downloading… ${(s.progress * 100).toInt()}%" + if (s.r.size > 0) "  ·  ${s.r.size / 1_048_576} MB" else "", size = 14.sp, color = c.textDim)
                }
                is State.Installing -> Txt("Opening the installer…", size = 14.sp, color = c.textDim)
                is State.Available, is State.Failed -> if (r == null) Pill("Try again", { Updater.check() }, Modifier.fillMaxWidth())
                    else if (!allowed) {
                        Txt("Android asks once to let OpenCode install updates.", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
                        Spacer(Modifier.height(12.dp))
                        Pill("Allow installs", { Updater.openInstallPermission(ctx) }, Modifier.fillMaxWidth())
                    } else Pill(if (s is State.Failed) "Try again" else "Install", { Updater.install(ctx, r) }, Modifier.fillMaxWidth())
                else -> Pill("Close", ::close, Modifier.fillMaxWidth(), filled = false)
            }
            if (r != null && (st is State.Available || st is State.Failed)) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt("Skip this version", Modifier.clip(CircleShape).tap(scale = 1f) { Graph.prefs.updateSkipped = r.tag; close() }.padding(vertical = 12.dp), size = 15.sp, color = c.textDim)
                    Txt("Later", Modifier.clip(CircleShape).tap(scale = 1f, onClick = ::close).padding(vertical = 12.dp), size = 15.sp, color = c.text)
                }
            }
        }
    }
}

/** "v… is out — Update": a quiet bar under the status bar while an update waits. */
@Composable
private fun UpdateBar(r: app.ocmobile.data.Updater.Release, onOpen: () -> Unit) {
    val c = Oc.c
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 52.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.clip(CircleShape).background(c.surfaceHigh).border(1.dp, c.border, CircleShape).tap(scale = 0.96f, onClick = onOpen).padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent))
            Spacer(Modifier.width(10.dp))
            Txt("${r.name.removePrefix("v").let { "v$it" }} is out", size = 14.sp)
            Spacer(Modifier.width(10.dp))
            Txt("Update", size = 14.sp, color = c.accent, weight = FontWeight.Medium)
        }
    }
}

/**
 * After an update: "Updated to v…" with what's new in it, once per version. The lines come from
 * [WhatsNew]; a version without lines shows nothing.
 */
@Composable
private fun WhatsNewHost() {
    val p = Graph.prefs
    val now = Updater.current
    if (!p.onboarded || p.seenVersion == now) return
    val lines = WhatsNew.notes[now]
    if (lines.isNullOrEmpty()) { LaunchedEffect(now) { p.seenVersion = now }; return }
    val c = Oc.c
    MagnetSheet(onDismiss = { p.seenVersion = now }, half = false, color = c.surface, corner = 28.dp, handle = false) {
        Column(Modifier.padding(24.dp)) {
            Txt("Updated to v$now", size = 22.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Txt("What's new", size = 15.sp, color = c.textDim)
            Spacer(Modifier.height(16.dp))
            lines.forEach { l ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c.accent))
                    Spacer(Modifier.width(12.dp))
                    Txt(l, size = 15.sp, lineHeight = 21.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            val close = LocalSheetClose.current
            Pill("Nice", { close { p.seenVersion = now } }, Modifier.fillMaxWidth())
        }
    }
}

/** What each version brought, said simply. The newest goes on top; add a line per feature when it lands. */
object WhatsNew {
    val notes: Map<String, List<String>> = mapOf(
        "0.0.2 beta-2" to listOf(
            "New look: a night sky with pixel clouds, and a new launch screen",
            "Menus and windows open right where you tap, as floating cards",
            "The input has a thin frame and one button, grows smoothly and keeps a draft in every chat",
            "Code in replies: line numbers, highlighting, copy, save and preview",
            "Search finds words inside messages, whatever the letter case",
            "A deleted chat can be brought back, and errors say what to do",
            "Fixed: the \"OK\" error and the used-up free limit with GitHub Copilot, broken replies from GPT-OSS",
            "The chat stays where you left it when you scroll up a little",
        ),
        "0.0.2 beta-1" to listOf(
            "Chats and memory move between the phone and the computer by themselves when you're home",
            "Remember this? cards: the agent offers what it heard about you, you decide",
            "Sub-agents work as a team: add them in the model picker and in +, they know the whole conversation",
            "Memory as a graph, plus import and export (a memory zip from Claude works too)",
            "Photos, videos and audio from the agent show as small tiles; code and headings look like in Claude",
            "OpenCode updates itself, and you can go back to the previous version in Server settings",
            "A dot on the menu breathes while the agent works and turns blue when the task is done",
            "Russian and English, voice talk with the cloud, a task timeline and limit warnings",
            "Settings and every section are in every look now",
        ),
        "0.0.1 beta-5" to listOf(
            "The app speaks Russian and English: pick it in Settings → Language",
            "Sub-agents right in the model picker and in +: name them, give each a role and a model",
            "Memory as a graph, plus import and export (a memory zip from Claude works too)",
            "New games with the cloud: Catch the rain, Cups and Echo",
            "A button to jump down in a chat, and a bar when an update is out",
        ),
    )
}


/** "OpenCode updated to …" for a few seconds after the server switched to a new build. */
@Composable
private fun OcNotice() {
    val p = Graph.prefs
    if (p.ocNotice.isBlank()) return
    val c = Oc.c
    LaunchedEffect(p.ocNotice) { kotlinx.coroutines.delay(8000); p.ocNotice = "" }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 52.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.clip(CircleShape).background(c.surfaceHigh).border(1.dp, c.border, CircleShape).tap(scale = 0.96f) { p.ocNotice = "" }.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.ok))
            Spacer(Modifier.width(10.dp))
            Txt("OpenCode updated to ${p.ocNotice}", size = 14.sp)
        }
    }
}
