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
import app.ocmobile.ui.components.PixelWordmark
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CONSOLE_LOGIN = "https://opencode.ai/console/login"
private val KEY_RE = Regex("sk-[A-Za-z0-9_-]{20,}")

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

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) { IconBtn(Ic.Close, { leave() }) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PixelWordmark(Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(32.dp))
            Rise(shown, 1) { Txt("Connect a free model", size = 28.sp, weight = FontWeight.Medium, align = TextAlign.Center) }
            Spacer(Modifier.height(12.dp))
            Rise(shown, 2) {
                Txt(
                    "OpenCode's built-in free models share one limit per internet connection and can run out. Sign in below to get a free allowance of your own — no subscription.",
                    size = 16.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 23.sp,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            freeProviders.forEachIndexed { i, p ->
                Rise(shown, 3 + i) {
                    ProviderButton("Continue with ${p.title}", p.note, when (p.id) { "github-copilot" -> Brand.GitHub; "google" -> Brand.Google; else -> Brand.Key }) { nav.push(Route.Connect(p.id)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Box(Modifier.clip(CircleShape).clickable { nav.push(Route.ConsoleLogin("github")) }.padding(horizontal = 12.dp, vertical = 12.dp)) { Txt("OpenCode account", size = 15.sp, color = c.textDim) }
            Box(Modifier.clip(CircleShape).clickable { nav.push(Route.Providers) }.padding(horizontal = 12.dp, vertical = 12.dp)) { Txt("API key", size = 15.sp, color = c.textDim) }
            Box(Modifier.clip(CircleShape).clickable { leave() }.padding(horizontal = 12.dp, vertical = 12.dp)) { Txt("Not now", size = 15.sp, color = c.textDim) }
        }
    }
}

@Composable
private fun Rise(shown: Boolean, step: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        shown,
        enter = fadeIn(tween(450, delayMillis = 140 * step)) + slideInVertically(tween(450, delayMillis = 140 * step)) { it / 4 },
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

private const val INJECT = """
(function(){
  if (window.__ocm) return; window.__ocm = 1;
  var method = '%METHOD%';
  function found(k){ try { Android.onKey(k); } catch(e){} }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    var o = navigator.clipboard.writeText.bind(navigator.clipboard);
    navigator.clipboard.writeText = function(t){ try { if (/^sk-[A-Za-z0-9_-]{20,}/.test(t)) found(t.trim()); } catch(e){} return o(t); };
  }
  function clickProvider(){
    if (window.__clicked || location.pathname.indexOf('/login') < 0 && location.pathname.indexOf('/signup') < 0) return;
    var want = method === 'github' ? 'GitHub' : method === 'google' ? 'Google' : 'email';
    var els = Array.prototype.slice.call(document.querySelectorAll('button,a,[role=button]'));
    var re = new RegExp('continue with\\s*' + want, 'i');
    var b = els.filter(function(e){ return re.test((e.textContent||'').trim()); })[0];
    if (b) { window.__clicked = 1; b.click(); }
  }
  function sniff(){
    var t = (document.body ? document.body.innerText : '') + ' ' + Array.prototype.map.call(document.querySelectorAll('input,textarea,code'), function(e){ return (e.value||'') + ' ' + (e.textContent||''); }).join(' ');
    var m = t.match(/sk-[A-Za-z0-9_-]{20,}/);
    if (m) found(m[0]);
    try { Android.onUrl(location.href); } catch(e){}
  }
  setInterval(function(){ clickProvider(); sniff(); }, 800);
})();
"""

/**
 * Вход в консоль OpenCode внутри приложения. GitHub и почта — во встроенном окне: после входа ключ API
 * берётся со страницы сам. Google не пускает встроенные окна, поэтому там — обычный браузер и ключ из буфера обмена.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConsoleLoginScreen(method: String, nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("login") }          // login | saving | done | error
    var error by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf("") }
    val external = method == "google"
    var handled by remember { mutableStateOf(false) }

    fun accept(key: String) {
        if (handled) return
        handled = true; state = "saving"
        scope.launch {
            try { connectAccount(key, method); state = "done"; delay(1300); nav.root(Route.Home) }
            catch (e: Exception) { error = e.message ?: "Could not save the key"; state = "error"; handled = false }
        }
    }

    if (external) {
        // Google: открываем браузер и ждём ключ в буфере обмена при возвращении в приложение
        LaunchedEffect(Unit) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CONSOLE_LOGIN)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    KEY_RE.find(text)?.let { accept(it.value) }
                }
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ic.Close, { nav.pop() })
                Txt(when (method) { "github" -> "GitHub"; "google" -> "Google"; else -> "Email" }, Modifier.weight(1f), size = 20.sp, weight = FontWeight.Medium, align = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            if (external) {
                Column(Modifier.weight(1f).padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Spinner(34.dp)
                    Spacer(Modifier.height(28.dp))
                    Txt("Finish signing in with Google in your browser", size = 22.sp, weight = FontWeight.Medium, align = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Txt(
                        "Google does not allow sign-in inside apps. Open API Keys in the console, create a key if you have none, and tap its copy button. This screen picks the key up as soon as you return.",
                        size = 16.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 23.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).tap { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CONSOLE_LOGIN)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.padding(horizontal = 22.dp, vertical = 14.dp)) {
                        Txt("Open the browser again", size = 16.sp)
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { cx ->
                            WebView(cx).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webChromeClient = WebChromeClient()
                                addJavascriptInterface(object {
                                    @JavascriptInterface fun onKey(k: String) { post { accept(k) } }
                                    @JavascriptInterface fun onUrl(u: String) { post { url = u } }
                                }, "Android")
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(v: WebView, u: String) { v.evaluateJavascript(INJECT.replace("%METHOD%", method), null) }
                                }
                                loadUrl(CONSOLE_LOGIN)
                            }
                        },
                    )
                    // подсказка, когда вход уже выполнен, а ключа ещё нет
                    if (url.contains("/console/") && !url.contains("/login") && state == "login") {
                        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).clip(Oc.round(22.dp)).background(c.surfaceHigh).padding(16.dp)) {
                            Txt("Signed in. Open API Keys and create a key — the app saves it automatically.", size = 15.sp, lineHeight = 21.sp)
                        }
                    }
                }
            }
        }
        // сохранение и успех
        AnimatedVisibility(state != "login", enter = fadeIn(tween(250))) {
            Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state) {
                        "saving" -> { Spinner(40.dp); Spacer(Modifier.height(20.dp)); Txt("Connecting your account…", size = 18.sp, color = c.textDim) }
                        "done" -> {
                            Box(Modifier.size(72.dp).clip(CircleShape).background(c.ok), contentAlignment = Alignment.Center) { Ico(Ic.Check, tint = c.bg, size = 40.dp) }
                            Spacer(Modifier.height(20.dp)); Txt("You are signed in", size = 22.sp, weight = FontWeight.Medium)
                        }
                        else -> {
                            Txt(error ?: "Something went wrong", size = 17.sp, color = c.danger, align = TextAlign.Center, modifier = Modifier.padding(32.dp))
                            Box(Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).tap { state = "login" }.padding(horizontal = 22.dp, vertical = 14.dp)) { Txt("Back", size = 16.sp) }
                        }
                    }
                }
            }
        }
    }
}
