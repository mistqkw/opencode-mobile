package app.ocmobile.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.ocmobile.Graph
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.Heading
import app.ocmobile.ui.components.PixelWordmark
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/** Сохраняет ключ аккаунта OpenCode на сервере: он работает и для Zen, и для Go. */
suspend fun connectAccount(key: String, method: String) {
    Graph.api.setApiKey("opencode", key)
    runCatching { Graph.api.setApiKey("opencode-go", key) }
    runCatching { Graph.api.disposeAll() }
    Graph.prefs.signedIn = method
    Graph.prefs.loginSeen = true
    runCatching { Graph.catalog.refresh() }
}

/** Экран входа: бесплатные модели через аккаунт GitHub или Google (или Groq/OpenRouter) — без подписки. */
@Composable
fun LoginScreen(nav: Nav) {
    val c = Oc.c
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    fun leave() { Graph.prefs.loginSeen = true; if (!nav.pop()) nav.root(Route.Home) }

    // Как в Claude: один главный путь и пара входов, без технических подробностей.
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) { IconBtn(Ic.Close, { leave() }) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PixelWordmark(Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(32.dp))
            Rise(shown, 1) { Heading("Code and chat, right on your phone", size = 28.sp, align = TextAlign.Center) }
            Spacer(Modifier.height(12.dp))
            Rise(shown, 2) {
                Txt("Free models work without an account. Sign in to add more models — your keys and memory stay on this phone.", size = 16.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 23.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Rise(shown, 3) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(Oc.pill).background(c.text).tap(onClick = { leave() }),
                    contentAlignment = Alignment.Center,
                ) { Txt("Start without an account", size = 17.sp, color = c.bg, weight = FontWeight.Medium) }
            }
            freeProviders.filter { it.id == "github-copilot" || it.id == "google" }.forEachIndexed { i, p ->
                Rise(shown, 4 + i) {
                    ProviderButton("Continue with ${p.title}", p.note, when (p.id) { "github-copilot" -> Brand.GitHub; "google" -> Brand.Google; else -> Brand.Key }) { nav.push(Route.Connect(p.id)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.clip(CircleShape).clickable { nav.push(Route.ConsoleLogin("")) }.padding(horizontal = 14.dp, vertical = 12.dp)) { Txt("OpenCode account", size = 15.sp, color = c.textDim) }
            Box(Modifier.clip(CircleShape).clickable { nav.push(Route.Providers) }.padding(horizontal = 14.dp, vertical = 12.dp)) { Txt("Other providers", size = 15.sp, color = c.textDim) }
        }
    }
}

@Composable
private fun Rise(shown: Boolean, step: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        shown,
        enter = fadeIn(tween(220, delayMillis = 50 * step)) + slideInVertically(tween(260, delayMillis = 50 * step)) { it / 5 },
    ) { content() }
}

private enum class Brand { GitHub, Google, Email, Key }

@Composable
private fun ProviderButton(text: String, note: String, brand: Brand, onClick: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().clip(Oc.pill).border(1.dp, c.border, Oc.pill).background(c.surface)
            .tap(onClick = onClick).height(64.dp).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            when (brand) {
                Brand.GitHub -> Ico(Ic.GitHub, size = 28.dp)
                Brand.Google -> Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text(
                        "G", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(brush = Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853)))),
                    )
                }
                Brand.Email -> Ico(Ic.Mail, size = 26.dp)
                Brand.Key -> Ico(Ic.Key, size = 26.dp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Txt(text, size = 18.sp)
            Txt(note, size = 13.sp, color = c.textDim)
        }
    }
}
