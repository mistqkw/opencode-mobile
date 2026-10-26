package app.ocmobile.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.ocmobile.Graph
import app.ocmobile.api.str
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Провайдеры с бесплатным тарифом: вход аккаунтом на их сайте, затем ключ попадает в приложение сам. */
class FreeProvider(val id: String, val title: String, val note: String, val url: String, val keyRegex: Regex?, val where: String)

val freeProviders = listOf(
    FreeProvider("github-copilot", "GitHub", "Copilot · free plan", "", null, "GitHub"),
    FreeProvider("google", "Google", "Gemini · free tier", "https://aistudio.google.com/apikey", Regex("AIza[0-9A-Za-z_\\-]{30,}"), "Google AI Studio"),
    FreeProvider("groq", "Groq", "Fast open models · free tier", "https://console.groq.com/keys", Regex("gsk_[A-Za-z0-9]{30,}"), "Groq Console"),
    FreeProvider("openrouter", "OpenRouter", "Models marked :free", "https://openrouter.ai/settings/keys", Regex("sk-or-[A-Za-z0-9_\\-]{20,}"), "OpenRouter"),
)

/**
 * Подключение провайдера без ручного копирования: GitHub — вход по коду устройства (сервер сам ждёт подтверждения),
 * остальные — вход аккаунтом на сайте, кнопка «Copy» у ключа и возврат в приложение: ключ подхватывается из буфера.
 */
@Composable
fun ConnectScreen(providerId: String, nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fp = freeProviders.firstOrNull { it.id == providerId } ?: freeProviders.first()
    var state by remember { mutableStateOf("wait") }            // wait | saving | done | error
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<String?>(null) }       // код устройства GitHub
    var manual by remember { mutableStateOf("") }
    var handled by remember { mutableStateOf(false) }
    val device = fp.keyRegex == null

    fun open(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun done(method: String) {
        Graph.prefs.signedIn = method
        Graph.prefs.loginSeen = true
        state = "done"
        scope.launch {
            runCatching { Graph.api.disposeAll() }
            runCatching { Graph.catalog.refresh() }
            Graph.catalog.selectBestFor(providerId)
            delay(1400); nav.root(Route.Home)
        }
    }

    fun acceptKey(key: String) {
        if (handled) return
        handled = true; state = "saving"
        scope.launch {
            try { Graph.api.setApiKey(providerId, key.trim()); done("key") }
            catch (e: Exception) { error = e.message ?: "Could not save the key"; state = "error"; handled = false }
        }
    }

    if (device) {
        LaunchedEffect(Unit) {
            try {
                val r = Graph.api.oauthAuthorize(providerId, 0, mapOf("deploymentType" to "github.com"))
                val instr = r.str("instructions").orEmpty()
                code = Regex("[A-Z0-9]{4}-[A-Z0-9]{4}").find(instr)?.value ?: instr
                val url = r.str("url") ?: "https://github.com/login/device"
                open(url)
                Graph.api.oauthCallback(providerId, 0, null)           // ждёт подтверждения в браузере
                runCatching { app.ocmobile.data.FreeModels.enableCopilot() }
                done("github")
            } catch (e: Exception) { error = e.message ?: "GitHub did not confirm the sign-in"; state = "error" }
        }
    } else {
        LaunchedEffect(Unit) { open(fp.url) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    fp.keyRegex?.find(text)?.let { acceptKey(it.value) }
                }
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ic.Close, { nav.pop() })
                Txt(fp.title, Modifier.weight(1f), size = 20.sp, weight = FontWeight.Medium, align = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (device) {
                    Txt("Enter this code on GitHub", size = 24.sp, weight = FontWeight.Medium, align = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    if (code == null) Spinner(34.dp) else {
                        val clip = LocalClipboardManager.current
                        Box(Modifier.clip(Oc.round(24.dp)).background(c.surface).border(1.dp, c.border, Oc.round(24.dp)).tap { clip.setText(AnnotatedString(code!!)); Graph.chats.toasts.tryEmit("Code copied") }.padding(horizontal = 32.dp, vertical = 22.dp)) {
                            Txt(code!!, size = 36.sp, family = Oc.mono, weight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(18.dp))
                        Txt("GitHub opened in your browser. Paste or type the code there and approve. This screen continues by itself.", size = 16.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 23.sp)
                        Spacer(Modifier.height(20.dp)); Spinner(28.dp)
                        Spacer(Modifier.height(20.dp))
                        Txt("Free access depends on your Copilot plan; GitHub has a free one.", size = 14.sp, color = c.textDim, align = TextAlign.Center)
                    }
                } else {
                    Spinner(34.dp)
                    Spacer(Modifier.height(26.dp))
                    Txt("Sign in with ${fp.title} in your browser", size = 24.sp, weight = FontWeight.Medium, align = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Txt(
                        "1. Sign in on ${fp.where}.\n2. Create an API key if you have none.\n3. Tap the key's copy button and come back — the app picks it up on its own.",
                        size = 16.sp, color = c.textDim, lineHeight = 25.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).tap { open(fp.url) }.padding(horizontal = 22.dp, vertical = 14.dp)) { Txt("Open ${fp.where} again", size = 16.sp) }
                    Spacer(Modifier.height(28.dp))
                    Txt("or paste the key here", size = 14.sp, color = c.textDim)
                    Spacer(Modifier.height(8.dp))
                    OcTextField(manual, { manual = it }, placeholder = "API key", password = true, mono = true)
                    Spacer(Modifier.height(10.dp))
                    Pill("Connect", { acceptKey(manual) }, enabled = manual.length > 12, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        AnimatedVisibility(state != "wait", enter = fadeIn(tween(250))) {
            Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state) {
                        "saving" -> { Spinner(40.dp); Spacer(Modifier.height(20.dp)); Txt("Connecting…", size = 18.sp, color = c.textDim) }
                        "done" -> {
                            Box(Modifier.size(72.dp).clip(CircleShape).background(c.ok), contentAlignment = Alignment.Center) { Ico(Ic.Check, tint = c.bg, size = 40.dp) }
                            Spacer(Modifier.height(20.dp)); Txt("${fp.title} connected", size = 22.sp, weight = FontWeight.Medium)
                            Txt(Graph.catalog.selectedLabel(), Modifier.padding(top = 6.dp), size = 15.sp, color = c.textDim)
                        }
                        else -> {
                            Txt(error ?: "Something went wrong", size = 17.sp, color = c.danger, align = TextAlign.Center, modifier = Modifier.padding(32.dp))
                            Box(Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).tap { nav.pop() }.padding(horizontal = 22.dp, vertical = 14.dp)) { Txt("Back", size = 16.sp) }
                        }
                    }
                }
            }
        }
    }
}
