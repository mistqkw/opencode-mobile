package app.ocmobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.data.GitHub
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** GitHub в окружении телефона: войти один раз — дальше агент работает с репозиториями от твоего имени. */
@Composable
fun GitHubScreen(nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = Graph.prefs
    val st by GitHub.state.collectAsState()
    var identity by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(st, prefs.githubLogin) { if (GitHub.signedIn) identity = GitHub.gitIdentity() }
    val look = Oc.look.id
    val mono = if (look == "hairline" || look == "terminal") Oc.mono else null

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("GitHub", left = { IconBtn(Ic.Back, { if (st is GitHub.State.Code) GitHub.cancel(); nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            if (Graph.devices.isRemote) {
                Txt("GitHub sign-in is for the server on this phone. On a computer, sign in there with gh auth login.", Modifier.padding(20.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp)
                return@Column
            }
            AnimatedContent(
                when { st is GitHub.State.Code -> 1; st is GitHub.State.Working -> 2; GitHub.signedIn -> 3; else -> 0 }, label = "gh",
            ) { k ->
                when (k) {
                    1 -> CodeStep(ctx, st as? GitHub.State.Code ?: return@AnimatedContent, mono)
                    2 -> WorkStep(st as? GitHub.State.Working ?: return@AnimatedContent)
                    3 -> Column {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(prefs.githubAvatar, null, Modifier.size(64.dp).clip(CircleShape).background(c.surface))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Txt(prefs.githubName, size = 20.sp, weight = FontWeight.Medium)
                                Txt("@" + prefs.githubLogin, size = 15.sp, color = c.textDim, family = mono)
                            }
                        }
                        SettingsGroup {
                            SettingsRow("Commits as", subtitle = identity?.let { "${it.first} <${it.second}>" } ?: "…", icon = Ic.Git)
                            SettingsRow("No AI co-authors", subtitle = "Commits and pull requests are yours alone", icon = Ic.User)
                            SettingsRow("What OpenCode can do", subtitle = "Clone, commit, push, open pull requests, issues and releases with gh", icon = Ic.GitHub)
                        }
                        Spacer(Modifier.height(18.dp))
                        Txt(
                            "Just ask in a chat: \"clone my repo …\", \"fix the bug and push\", \"open a PR\".",
                            Modifier.padding(horizontal = 20.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp,
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.padding(horizontal = 16.dp)) { SmallButton("Sign out", { scope.launch { GitHub.signOut() } }, danger = true) }
                    }
                    else -> Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(28.dp))
                        Box(Modifier.size(84.dp).clip(if (look == "terminal") RectangleShape else CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
                            Ico(Ic.GitHub, size = 42.dp)
                        }
                        Spacer(Modifier.height(20.dp))
                        Txt("Connect GitHub", size = 24.sp, weight = FontWeight.Medium, family = mono)
                        Spacer(Modifier.height(8.dp))
                        Txt(
                            "Sign in once. Then OpenCode can clone your repositories, edit them, commit, push and open pull requests — as you, with no co-authors.",
                            size = 15.sp, color = c.textDim, lineHeight = 21.sp, align = TextAlign.Center,
                        )
                        (st as? GitHub.State.Failed)?.let {
                            Spacer(Modifier.height(14.dp))
                            Txt(it.message, size = 14.sp, color = c.danger, lineHeight = 19.sp, align = TextAlign.Center)
                        }
                        Spacer(Modifier.height(26.dp))
                        Pill("Sign in with GitHub", { scope.launch { GitHub.signIn() } }, Modifier.fillMaxWidth(), icon = Ic.GitHub)
                        Spacer(Modifier.height(10.dp))
                        Txt("Installs git and the GitHub CLI in Ubuntu, about 30 MB.", size = 13.sp, color = c.textDim)
                    }
                }
            }
        }
    }
}

private fun copy(ctx: Context, s: String) =
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("GitHub code", s))

@Composable
private fun CodeStep(ctx: Context, st: GitHub.State.Code, mono: androidx.compose.ui.text.font.FontFamily?) {
    val c = Oc.c
    val shape = when (Oc.look.id) { "terminal" -> RectangleShape; "hairline" -> RoundedCornerShape(2.dp); else -> Oc.round(24.dp) }
    Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Txt("Enter this code on GitHub", size = 20.sp, weight = FontWeight.Medium)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(vertical = 26.dp),
            contentAlignment = Alignment.Center,
        ) { Txt(st.userCode, size = 40.sp, weight = FontWeight.Medium, family = Oc.mono) }
        Spacer(Modifier.height(18.dp))
        Pill("Copy and open GitHub", {
            copy(ctx, st.userCode)
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(st.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, Modifier.fillMaxWidth(), icon = Ic.External)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Spinner(16.dp, stroke = 2.dp); Spacer(Modifier.width(10.dp))
            Txt("Waiting for you to approve…", size = 14.sp, color = c.textDim, family = mono)
        }
        Spacer(Modifier.height(16.dp))
        SmallButton("Cancel", { GitHub.cancel() })
    }
}

@Composable
private fun WorkStep(st: GitHub.State.Working) {
    val c = Oc.c
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spinner(30.dp)
        Spacer(Modifier.height(18.dp))
        Txt(st.step + "…", size = 18.sp, weight = FontWeight.Medium)
        if (st.log.isNotBlank()) { Spacer(Modifier.height(8.dp)); Txt(st.log, size = 12.sp, color = c.textDim, family = Oc.mono, maxLines = 1) }
    }
}
