package app.ocmobile.ui.screens

import app.ocmobile.ui.theme.screenBackdrop
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ocmobile.Graph
import app.ocmobile.runtime.RuntimeState
import app.ocmobile.ui.components.Heading
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.PixelWordmark
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.AppIcons
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Looks
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch

/*
 * First launch. While the person gets to know the app, the environment (Ubuntu + OpenCode) installs in the background,
 * so by the last step everything is usually ready. First the cl0udly mascot shows how things
 * work here (five screens), then: about you and memory → your settings → permissions → account. On Start
 * the cloud calls you "into its environment" and pulls you in, through its eye straight to the home screen.
 */

private val STEPS = listOf("Hi", "Chat", "Computer", "Team", "Play", "You", "Setup", "Access", "Account")
private const val TOUR = 5      // the first five steps are the mascot's tour

@Composable
fun Onboarding() {
    val c = Oc.c
    val ctx = LocalContext.current
    val prefs = Graph.prefs
    val rt by Graph.runtime.state.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var icon by rememberSaveable { mutableStateOf<String?>(null) }
    var account by rememberSaveable { mutableStateOf<String?>(null) }

    // the environment installs right away, in parallel with the tour
    LaunchedEffect(Unit) { if (rt is RuntimeState.NotInstalled) Graph.scope.launch { Graph.runtime.install() } }

    fun go(to: Int) { forward = to > step; step = to.coerceIn(0, STEPS.lastIndex) }
    var entering by remember { mutableStateOf<String?>(null) }
    // "Start": first the cloud calls you in, then the home screen
    fun finish(acc: String) { if (entering == null) entering = acc }
    fun finishNow(acc: String) {
        val chosen = icon ?: "look_cloud"
        if (chosen != null && chosen != prefs.appIcon) { prefs.appIcon = chosen; runCatching { AppIcons.apply(ctx, chosen) } }
        prefs.loginSeen = true
        prefs.onboarded = true
        when (acc) {
            "github" -> Graph.chats.openRequest.value = "route:connect:github-copilot"
            "google" -> Graph.chats.openRequest.value = "route:connect:google"
            "opencode" -> Graph.chats.openRequest.value = "route:console:"
        }
    }
    val ready = rt is RuntimeState.Running
    // an account was picked while the environment installed: open sign-in as soon as the server is up
    LaunchedEffect(ready, account) { val a = account; if (ready && a != null) finish(a) }

    BackHandler(step > 0) { go(step - 1) }

    Column(Modifier.fillMaxSize().screenBackdrop("Onboarding1").statusBarsPadding().navigationBarsPadding().imePadding()) {
        // top: back, step bars, "Skip"
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (step > 0) Box(Modifier.size(44.dp).clip(CircleShape).tap { go(step - 1) }, contentAlignment = Alignment.Center) { Ico(Ic.Back, size = 22.dp) }
            }
            Row(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                STEPS.indices.forEach { i ->
                    val k by animateFloatAsState(if (i <= step) 1f else 0f, tween(260), label = "seg")
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(c.border)) {
                        Box(Modifier.fillMaxWidth(k).height(3.dp).clip(CircleShape).background(c.text))
                    }
                }
            }
            Box(Modifier.size(width = 56.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                if (step < STEPS.lastIndex) Txt("Skip", Modifier.clip(CircleShape).tap { go(if (step < TOUR) TOUR else STEPS.lastIndex) }.padding(8.dp), size = 15.sp, color = c.textDim)
            }
        }

        AnimatedContent(
            step, Modifier.weight(1f).fillMaxWidth(), label = "onboarding",
            transitionSpec = {
                val d = if (forward) 1 else -1
                (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(280)) { it / 6 * d }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(240)) { -it / 6 * d })
            },
        ) { s ->
            when (s) {
                in 0 until TOUR -> TourStep(s)
                TOUR -> YouStep()
                TOUR + 1 -> SetupStep(icon ?: "look_cloud") { icon = it }
                TOUR + 2 -> AccessStep()
                else -> AccountStep(rt, account) { a -> if (ready) finish(a) else account = a }
            }
        }

        // bottom: the main button and install progress
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
            if (step < STEPS.lastIndex) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(Oc.pill).background(c.text).tap { go(step + 1) },
                    contentAlignment = Alignment.Center,
                ) { Txt(when { step == 0 -> "Show me around"; step < TOUR - 1 -> "Next"; step == TOUR - 1 -> "Let's set up"; else -> "Continue" }, size = 17.sp, color = c.bg, weight = FontWeight.Medium) }
            }
            Spacer(Modifier.height(12.dp))
            SetupLine(rt)
        }
    }
    entering?.let { acc -> EnterCloud { finishNow(acc) } }
}

/** Install progress line, visible on every step. */
@Composable
private fun SetupLine(rt: RuntimeState) {
    val c = Oc.c
    val (text, dot, p) = when (rt) {
        is RuntimeState.Installing -> Triple("Setting up OpenCode · ${rt.step}" + (rt.progress?.let { " · ${(it * 100).toInt()}%" } ?: ""), c.accent, rt.progress)
        is RuntimeState.InstallFailed -> Triple("Setup stopped — you can retry on the last step", c.danger, null)
        is RuntimeState.Running -> Triple("OpenCode is ready", c.ok, 1f)
        is RuntimeState.NotInstalled -> Triple("Preparing…", c.textDim, null)
        else -> Triple("Starting OpenCode…", c.accent, null)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Txt(text, size = 13.sp, color = c.textDim, maxLines = 1)
    }
    if (p != null && p < 1f) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(c.border)) {
            Box(Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).height(2.dp).background(c.accent))
        }
    }
}

@Composable
private fun StepTitle(title: String, sub: String) {
    val c = Oc.c
    Heading(title, Modifier.padding(horizontal = 24.dp), size = 30.sp)
    Spacer(Modifier.height(8.dp))
    Txt(sub, Modifier.padding(horizontal = 24.dp), size = 16.sp, color = c.textDim, lineHeight = 23.sp)
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun Scroll(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 16.dp), content = content)

@Composable
private fun Appear(shown: Boolean, i: Int, content: @Composable () -> Unit) =
    AnimatedVisibility(shown, enter = fadeIn(tween(160, delayMillis = 50 * i)) + slideInVertically(tween(200, delayMillis = 50 * i)) { it / 4 }) { content() }

// ---------- 4. about you and memory ----------

@Composable
private fun YouStep() {
    val c = Oc.c
    val prefs = Graph.prefs
    Scroll {
        StepTitle("A bit about you", "So I can greet you and, if you want, remember what matters.")
        Txt("What should it call you?", Modifier.padding(horizontal = 24.dp), size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp)).padding(horizontal = 16.dp, vertical = 15.dp)) {
            if (prefs.userName.isEmpty()) Txt("Your name", size = 17.sp, color = c.textDim)
            BasicTextField(
                prefs.userName, { prefs.userName = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 17.sp, fontFamily = Oc.body), cursorBrush = SolidColor(c.text),
            )
        }
        Spacer(Modifier.height(26.dp))
        Txt("Memory", Modifier.padding(horizontal = 24.dp), size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice(prefs.memoryEnabled, Ic.Scroll, "Remember me", "Keeps short notes about you and your projects on this phone, so you don't repeat yourself. You can see and delete everything.") { prefs.memoryEnabled = true; prefs.memoryAuto = true }
            Choice(!prefs.memoryEnabled, Ic.Ghost, "Start fresh every chat", "Nothing is saved between chats. You can turn memory on later.") { prefs.memoryEnabled = false }
        }
    }
}

@Composable
private fun Choice(on: Boolean, icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(if (on) 2.dp else 1.dp, if (on) c.link else c.border, Oc.round(18.dp))
            .tap(scale = 0.98f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Ico(icon, tint = if (on) c.link else c.textDim, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 16.sp, weight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Txt(sub, size = 14.sp, color = c.textDim, lineHeight = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) c.link else c.border, CircleShape).padding(5.dp).then(if (on) Modifier.background(c.link, CircleShape) else Modifier))
    }
}

// ---------- 5. permissions ----------

@Composable
private fun AccessStep() {
    val c = Oc.c
    val ctx = LocalContext.current
    val prefs = Graph.prefs
    var rev by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) rev++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { rev++ }
    fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    @Suppress("UNUSED_EXPRESSION") rev
    val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    Scroll {
        StepTitle("Give it a hand", "All optional. Each one makes a feature work — you can change them later in Android settings.")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Access(Ic.Bell, "Notifications", "Answers, progress on the lock screen and Allow / Deny buttons", notif) {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Access(Ic.Zap, "Keep working in the background", "So long tasks don't stop when the screen is off", battery) {
                @Suppress("BatteryLife")
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Access(Ic.Mic, "Microphone", "Dictation and voice mode", has(Manifest.permission.RECORD_AUDIO)) { ask.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
            Access(Ic.Pin, "Location", "Only when you ask OpenCode where you are", has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                ask.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
            Row(
                Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.Phone, tint = c.accent, size = 22.dp); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt("Let OpenCode use this phone", size = 16.sp, weight = FontWeight.Medium)
                    Txt("Reminders, alarms, clipboard, camera, Downloads", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
                }
                OcSwitch(prefs.phoneTools) { prefs.phoneTools = it }
            }
        }
    }
}

@Composable
private fun Access(icon: ImageVector, title: String, sub: String, granted: Boolean, onAsk: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = c.accent, size = 22.dp); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 16.sp, weight = FontWeight.Medium)
            Txt(sub, size = 14.sp, color = c.textDim, lineHeight = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        if (granted) Row(verticalAlignment = Alignment.CenterVertically) { Ico(Ic.Check, tint = c.ok, size = 17.dp); Spacer(Modifier.width(4.dp)); Txt("On", size = 14.sp, color = c.ok) }
        else Box(Modifier.clip(Oc.pill).background(c.text).tap(onClick = onAsk).padding(horizontal = 16.dp, vertical = 8.dp)) { Txt("Allow", size = 14.sp, color = c.bg, weight = FontWeight.Medium) }
    }
}

// ---------- 6. account ----------

@Composable
private fun AccountStep(rt: RuntimeState, chosen: String?, onPick: (String) -> Unit) {
    val c = Oc.c
    val ready = rt is RuntimeState.Running
    Scroll {
        StepTitle("Connect an account", "Free models work without one. Sign in to unlock more — keys stay on this phone.")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountOption("free", chosen, ready, Ic.Sparkle, "Start free", "No account — free models right away", primary = true, onPick = onPick)
            AccountOption("github", chosen, ready, Ic.GitHub, "Continue with GitHub", "Copilot's free models", onPick = onPick)
            AccountOption("google", chosen, ready, Ic.Globe, "Continue with Google", "Gemini's free tier", onPick = onPick)
            AccountOption("opencode", chosen, ready, Ic.Key, "OpenCode account", "Zen and Go plans", onPick = onPick)
        }
        if (rt is RuntimeState.InstallFailed) {
            Txt(rt.message, Modifier.padding(horizontal = 24.dp, vertical = 16.dp), size = 14.sp, color = c.danger, lineHeight = 20.sp)
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(50.dp).clip(Oc.pill).border(1.dp, c.border, Oc.pill).tap { Graph.scope.launch { Graph.runtime.install() } }, contentAlignment = Alignment.Center) {
                Txt("Retry setup", size = 16.sp)
            }
        } else if (chosen != null && !ready) {
            Txt("Almost there — it opens as soon as setup finishes.", Modifier.padding(24.dp), size = 14.sp, color = c.textDim, align = TextAlign.Center)
        }
    }
}

@Composable
private fun AccountOption(id: String, chosen: String?, ready: Boolean, icon: ImageVector, title: String, sub: String, primary: Boolean = false, onPick: (String) -> Unit) {
    val c = Oc.c
    val waiting = chosen == id && !ready
    val fg = if (primary) c.bg else c.text
    Row(
        Modifier.fillMaxWidth().height(68.dp).clip(Oc.pill)
            .then(if (primary) Modifier.background(c.text) else Modifier.background(c.surface).border(if (chosen == id) 2.dp else 1.dp, if (chosen == id) c.link else c.border, Oc.pill))
            .tap(enabled = chosen == null || chosen == id) { onPick(id) }.padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = fg, size = 24.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 17.sp, color = fg, weight = FontWeight.Medium)
            Txt(if (waiting) "Waiting for setup…" else sub, size = 13.sp, color = if (primary) c.bg.copy(alpha = 0.7f) else c.textDim)
        }
        if (waiting) app.ocmobile.ui.components.Spinner(18.dp, color = fg, stroke = 2.dp)
    }
}

// ---------- tour: the mascot explains ----------

private class TourPage(val say: String, val note: String)

private val TOUR_PAGES = listOf(
    TourPage("Hi! I'm cl0udly.", "A little cloud that lives on your phone. I write code, run it and chat with you — all right here, no computer needed."),
    TourPage("Just ask.", "While I work, tap the summary line to see every step: the commands I run, the files I edit, the live terminal."),
    TourPage("Got a computer?", "Run one command on it and scan the QR code in Remote devices — then I work there too, right from this phone."),
    TourPage("I don't work alone.", "For big tasks I can call helpers and run them in parallel. And I can remember what matters to you — only if you want."),
    TourPage("Let's be friends.", "Tap me, hold me, pet me. Waiting for a task? Visit my room — I'm undefeated at ping-pong. Mostly."),
)

@Composable
private fun TourStep(i: Int) {
    val c = Oc.c
    val page = TOUR_PAGES[i]
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // a speech bubble above the mascot
        Appear(shown, 0) {
            Box(Modifier.clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Txt(page.say, size = 20.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(26.dp))
        app.ocmobile.ui.components.PixelCloud(Modifier.size(width = 190.dp, height = 130.dp), cue = i)
        Spacer(Modifier.height(30.dp))
        Appear(shown, 2) { Txt(page.note, size = 16.sp, color = c.textDim, lineHeight = 23.sp, align = TextAlign.Center) }
        Spacer(Modifier.height(22.dp))
        Appear(shown, 3) { TourPicture(i) }
    }
}

/** A small picture for the page: what it looks like in the app. */
@Composable
private fun TourPicture(i: Int) {
    val c = Oc.c
    val mono = Oc.mono
    when (i) {
        1 -> Column(Modifier.fillMaxWidth().clip(Oc.round(14.dp)).border(1.dp, c.border, Oc.round(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Ran 2 commands, edited ", size = 14.sp, color = c.textDim); Txt("app.py", size = 14.sp)
                Txt(" +12", size = 14.sp, color = c.ok); Txt(" −3", size = 14.sp, color = c.danger)
                Spacer(Modifier.width(4.dp)); Ico(Ic.ChevronRight, tint = c.textDim, size = 14.dp)
            }
            Txt("$ python app.py", Modifier.fillMaxWidth().clip(Oc.round(8.dp)).background(c.codeBg).padding(horizontal = 10.dp, vertical = 8.dp), size = 13.sp, color = c.link, family = mono)
        }
        2 -> Row(verticalAlignment = Alignment.CenterVertically) {
            // a pixel "QR": just a pattern
            androidx.compose.foundation.Canvas(Modifier.size(56.dp)) {
                val n = 9; val px = size.width / n
                val bits = "111010111100101001101111011000101100011101101001001110111010100101011110110010111"
                for (y in 0 until n) for (x in 0 until n) if (bits[(y * n + x) % bits.length] == '1') drawRect(c.text, androidx.compose.ui.geometry.Offset(x * px, y * px), androidx.compose.ui.geometry.Size(px, px))
            }
            Spacer(Modifier.width(14.dp))
            Txt("Remote devices → Add device\n→ Scan QR code", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
        }
        3 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TourChip(Ic.Users, "Team · 2 working")
            TourChip(Ic.Scroll, "Memory")
        }
        4 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TourChip(Ic.Sparkle, "Pet me")
            TourChip(Ic.Play, "Ping-pong")
        }
        else -> Unit
    }
}

@Composable
private fun TourChip(icon: ImageVector, text: String) {
    val c = Oc.c
    Row(Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, tint = c.accent, size = 16.dp); Spacer(Modifier.width(8.dp)); Txt(text, size = 14.sp)
    }
}

// ---------- your settings (instead of picking an interface) ----------

@Composable
private fun SetupStep(icon: String, onIcon: (String) -> Unit) {
    val c = Oc.c
    val p = Graph.prefs
    var mode by remember { mutableStateOf(p.colorMode) }
    var haptics by remember { mutableStateOf(p.haptics) }
    var mascot by remember { mutableStateOf(p.mascotOn) }
    var persona by remember { mutableStateOf(p.mascotPersona) }
    var outfits by remember { mutableStateOf(p.mascotOutfit != "off") }
    Scroll {
        StepTitle("Make it yours", "All of this lives in Settings too — change it whenever you like.")
        Txt("Theme", Modifier.padding(horizontal = 24.dp), size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            app.ocmobile.ui.components.Segmented(listOf("system" to "System", "dark" to "Dark", "light" to "Light"), mode, { mode = it; p.colorMode = it })
        }
        Spacer(Modifier.height(22.dp))
        Txt("Icon on your home screen", Modifier.padding(horizontal = 24.dp), size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcons.all.filter { it.id == "look_cloud" || it.id == "cloud_light" }.forEach { ic ->
                val sel = icon == ic.id
                Column(Modifier.clip(Oc.round(16.dp)).tap(scale = 0.95f) { onIcon(ic.id) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(ic.bg).border(if (sel) 3.dp else 1.dp, if (sel) c.link else c.border, CircleShape), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(painterResource(ic.fgRes), null, Modifier.requiredSize(96.dp))
                    }
                    Txt(if (ic.id == "cloud_light") "Light" else "Dark", Modifier.padding(top = 6.dp), size = 13.sp, color = if (sel) c.link else c.text)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupSwitch(Ic.Vibrate, "Vibration", "A light tap when you press things", haptics) { haptics = it; p.haptics = it }
            SetupSwitch(Ic.Cloud, "cl0udly on the home screen", "The cloud that greets you", mascot) { mascot = it; p.mascotOn = it }
            SetupSwitch(Ic.Chat, "The assistant is cl0udly", "It knows its name and where it lives; names its real model when asked", persona) { persona = it; p.mascotPersona = it }
            SetupSwitch(Ic.Sparkle, "Holiday outfits", "Hats, snow and confetti on the right days", outfits) { outfits = it; p.mascotOutfit = if (it) "auto" else "off" }
        }
    }
}

@Composable
private fun SetupSwitch(icon: ImageVector, title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).tap(scale = 0.98f) { onChange(!on) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = if (on) c.link else c.textDim, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 16.sp, weight = FontWeight.Medium)
            Txt(sub, size = 13.sp, color = c.textDim, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        OcSwitch(on, onChange)
    }
}

// ---------- entering: "welcome to my environment" ----------

/**
 * After "Start": the cloud hops and calls you in, then rushes closer; the screen
 * "dives" into its eye and melts away, and the home screen opens right away.
 */
@Composable
private fun EnterCloud(onDone: () -> Unit) {
    val c = Oc.c
    val zoom = remember { androidx.compose.animation.core.Animatable(1f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        kotlinx.coroutines.delay(1300)
        zoom.animateTo(14f, tween(380, easing = androidx.compose.animation.core.FastOutLinearInEasing))
        onDone()
    }
    BackHandler { }
    Box(Modifier.fillMaxSize().background(c.bg).tap { }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Appear(shown, 0) {
                Box(Modifier.graphicsLayer { alpha = (2f - zoom.value).coerceIn(0f, 1f) }.clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Txt("Come on in —\nwelcome to my dev environment!", size = 19.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, lineHeight = 26.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.graphicsLayer {
                scaleX = zoom.value; scaleY = zoom.value
                // the white body fades before it fills the screen: only the eye and the background remain
                alpha = (1f - (zoom.value - 1f) / 5f).coerceIn(0f, 1f)
                // dive into the left eye
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.39f, 0.52f)
            }) {
                app.ocmobile.ui.components.PixelCloud(Modifier.size(width = 220.dp, height = 150.dp), interactive = false, cue = "enter")
            }
        }
    }
}
