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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ocmobile.Graph
import app.ocmobile.R
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.Heading
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CONSOLE_LOGIN = "https://opencode.ai/console/login"
private const val CONSOLE = "https://opencode.ai/console"
private val KEY_RE = Regex("sk-[A-Za-z0-9_-]{20,}")

/*
 * Подключение аккаунта OpenCode: один ключ API работает и для Zen (оплата по факту), и для Go (подписка).
 * Вместо голой страницы консоли — выбор способа, встроенная консоль, по которой приложение ведёт само
 * (после входа открывает API Keys, создаёт ключ «OpenCode mobile» и забирает его), и экран «Готово».
 */

private const val INJECT = """
(function(){
  if (window.__ocm) return; window.__ocm = 1;
  var method = '%METHOD%';
  function found(k){ try { Android.onKey(k); } catch(e){} }
  function say(s){ try { Android.onStage(s); } catch(e){} }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    var o = navigator.clipboard.writeText.bind(navigator.clipboard);
    navigator.clipboard.writeText = function(t){ try { if (/^sk-[A-Za-z0-9_-]{20,}/.test(t)) found(t.trim()); } catch(e){} return o(t); };
  }
  function els(){ return Array.prototype.slice.call(document.querySelectorAll('button,a,[role=button],[role=menuitem]')); }
  function byText(re){ return els().filter(function(e){ var t=(e.textContent||'').trim(); return re.test(t) && e.offsetParent !== null; })[0]; }
  var tries = { nav: 0, create: 0, confirm: 0 };
  function step(){
    var p = location.pathname;
    if (p.indexOf('/login') >= 0 || p.indexOf('/signup') >= 0 || p.indexOf('/auth') === 0) {
      say('login');
      if (!window.__clicked && method) {
        var want = method === 'github' ? 'GitHub' : method === 'google' ? 'Google' : 'email';
        var b = byText(new RegExp('continue with\\s*' + want, 'i'));
        if (b) { window.__clicked = 1; b.click(); }
      }
      return;
    }
    if (p.indexOf('/console') !== 0) return;
    // ключ уже на странице — забираем
    var t = (document.body ? document.body.innerText : '') + ' ' + Array.prototype.map.call(document.querySelectorAll('input,textarea,code'), function(e){ return (e.value||'') + ' ' + (e.textContent||''); }).join(' ');
    var m = t.match(/sk-[A-Za-z0-9_-]{20,}/);
    if (m) { found(m[0]); return; }
    var onKeys = /key/i.test(p) || !!byText(/^create (an? )?(api )?key/i);
    if (!onKeys) {
      say('console');
      if (tries.nav < 6) { var l = byText(/^api keys?$/i) || byText(/^keys$/i); if (l) { tries.nav++; l.click(); } }
      return;
    }
    say('keys');
    // диалог создания открыт: имя и подтверждение
    var input = Array.prototype.slice.call(document.querySelectorAll('input[type=text],input:not([type])')).filter(function(e){ return e.offsetParent !== null && !e.value; })[0];
    if (input && tries.confirm < 3) {
      var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      setter.call(input, 'OpenCode mobile'); input.dispatchEvent(new Event('input', { bubbles: true }));
      var ok = byText(/^(create|save|confirm|add)( key)?$/i);
      if (ok) { tries.confirm++; setTimeout(function(){ ok.click(); }, 300); }
      return;
    }
    if (tries.create < 2) { var c = byText(/^create (an? )?(api )?key/i) || byText(/^(\+\s*)?new key$/i); if (c) { tries.create++; c.click(); } }
  }
  setInterval(step, 900);
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConsoleLoginScreen(method: String, nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // choose | web | browser | paste | saving | done | error
    var stage by remember { mutableStateOf(if (method.isBlank()) "choose" else if (method == "google") "browser" else "web") }
    var via by remember { mutableStateOf(method.ifBlank { "github" }) }
    var webStage by remember { mutableStateOf("login") }   // login | console | keys
    var error by remember { mutableStateOf<String?>(null) }
    var handled by remember { mutableStateOf(false) }

    fun accept(key: String) {
        if (handled) return
        handled = true; stage = "saving"
        scope.launch {
            try { connectAccount(key, via); delay(400); stage = "done" }
            catch (e: Exception) { error = e.message ?: "Could not save the key"; stage = "error"; handled = false }
        }
    }
    fun openBrowser() = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CONSOLE_LOGIN)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    // Google не пускает встроенные окна: браузер, а ключ ловим в буфере обмена, когда человек вернётся
    if (stage == "browser") {
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    KEY_RE.find(cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty())?.let { accept(it.value) }
                }
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
    }
    BackHandler(stage in setOf("web", "browser", "paste", "error") && method.isBlank()) { stage = "choose"; handled = false }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(if (stage == "choose" || stage == "done" || method.isNotBlank()) Ic.Close else Ic.Back, {
                if (stage == "choose" || stage == "done" || method.isNotBlank()) { if (!nav.pop()) nav.root(Route.Home) } else { stage = "choose"; handled = false }
            })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (stage == "web" || stage == "browser") Steps(if (webStage == "keys") 1 else if (webStage == "console") 1 else 0) }
            Spacer(Modifier.width(48.dp))
        }
        AnimatedContent(stage, Modifier.weight(1f), label = "acct", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { s ->
            when (s) {
                "choose" -> Choose(
                    onMethod = { m -> via = m; stage = if (m == "google") "browser".also { openBrowser() } else "web" },
                    onPaste = { stage = "paste" },
                )
                "web" -> Box(Modifier.fillMaxSize()) {
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
                                    @JavascriptInterface fun onStage(st: String) { post { webStage = st } }
                                }, "Android")
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(v: WebView, u: String) { v.evaluateJavascript(INJECT.replace("%METHOD%", via), null) }
                                }
                                loadUrl(CONSOLE_LOGIN)
                            }
                        },
                    )
                    // подсказка снизу: что сейчас происходит
                    Row(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp).fillMaxWidth().clip(Oc.round(22.dp)).background(c.surfaceHigh)
                            .border(1.dp, c.border, Oc.round(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (webStage != "login") { Spinner(18.dp, stroke = 2.dp); Spacer(Modifier.width(12.dp)) }
                        Txt(
                            when (webStage) {
                                "console" -> "Signed in. Opening your API keys…"
                                "keys" -> "Creating a key for this phone… If nothing happens, tap “Create API Key”."
                                else -> "Sign in to OpenCode. The app takes it from there."
                            },
                            Modifier.weight(1f), size = 14.sp, lineHeight = 20.sp,
                        )
                    }
                }
                "browser" -> Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
                    Heading("Finish in your browser", size = 28.sp)
                    Spacer(Modifier.height(10.dp))
                    Txt("Google doesn't allow signing in inside apps, so this one takes a short detour.", size = 16.sp, color = c.textDim, lineHeight = 23.sp)
                    Spacer(Modifier.height(26.dp))
                    listOf("Sign in with Google", "Open API Keys and create a key", "Tap the key's copy button and come back").forEachIndexed { i, t ->
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Txt("${i + 1}", size = 14.sp, weight = FontWeight.Medium) }
                            Spacer(Modifier.width(14.dp)); Txt(t, size = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spinner(16.dp, stroke = 2.dp); Spacer(Modifier.width(10.dp))
                        Txt("Waiting for the key in your clipboard…", size = 14.sp, color = c.textDim)
                    }
                    Spacer(Modifier.height(20.dp))
                    BigButton("Open the browser again", filled = false) { openBrowser() }
                }
                "paste" -> PasteKey { accept(it) }
                "saving" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Spinner(40.dp); Spacer(Modifier.height(20.dp)); Txt("Connecting Zen and Go…", size = 18.sp, color = c.textDim)
                }
                "done" -> Done { nav.root(Route.Home) }
                else -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(error ?: "Something went wrong", size = 17.sp, color = c.danger, align = TextAlign.Center, lineHeight = 24.sp)
                    Spacer(Modifier.height(20.dp))
                    BigButton("Try again", filled = false) { stage = if (method.isBlank()) "choose" else "web" }
                }
            }
        }
    }
}

/** Шаги наверху встроенной консоли: вход → ключ → готово. */
@Composable
private fun Steps(at: Int) {
    val c = Oc.c
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf("Sign in", "API key", "Done").forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.padding(horizontal = 8.dp).width(14.dp).height(1.dp).background(c.border))
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (i <= at) c.text else c.border))
            Spacer(Modifier.width(6.dp))
            Txt(t, size = 13.sp, color = if (i <= at) c.text else c.textDim)
        }
    }
}

@Composable
private fun Mark(size: androidx.compose.ui.unit.Dp) = Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF1E1E1E)), contentAlignment = Alignment.Center) {
    Image(painterResource(R.drawable.ic_fg_look_desktop), null, Modifier.requiredSize(size * 1.5f))
}

@Composable
private fun Choose(onMethod: (String) -> Unit, onPaste: () -> Unit) {
    val c = Oc.c
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Mark(64.dp)
        Spacer(Modifier.height(20.dp))
        Heading("Connect OpenCode", size = 30.sp)
        Spacer(Modifier.height(8.dp))
        Txt("One account, one key — it unlocks both plans. You pay OpenCode directly; the key stays on this phone.", size = 16.sp, color = c.textDim, lineHeight = 23.sp)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Plan(Modifier.weight(1f), "Zen", "Pay as you go", "Hand-picked top models, checked to work well with the agent.", Ic.Sparkle)
            Plan(Modifier.weight(1f), "Go", "\$10 / month", "Generous limits on strong open models for everyday coding.", Ic.Zap)
        }
        Spacer(Modifier.height(26.dp))
        Txt("Sign in with", size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Method(Ic.GitHub, "GitHub", "Fastest — done inside the app") { onMethod("github") }
            Method(Ic.Mail, "Email", "A code arrives in your inbox") { onMethod("email") }
            Method(Ic.Globe, "Google", "Opens your browser for a moment") { onMethod("google") }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().clip(Oc.pill).tap(scale = 0.98f, onClick = onPaste).padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Key, tint = c.textDim, size = 18.dp); Spacer(Modifier.width(8.dp))
            Txt("I already have an API key", size = 15.sp, color = c.textDim)
        }
    }
}

@Composable
private fun Plan(modifier: Modifier, name: String, price: String, text: String, icon: ImageVector) {
    val c = Oc.c
    Column(modifier.clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(icon, tint = c.accent, size = 18.dp); Spacer(Modifier.width(8.dp))
            Txt(name, size = 18.sp, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Txt(price, size = 14.sp, color = c.accent)
        Spacer(Modifier.height(8.dp))
        Txt(text, size = 13.sp, color = c.textDim, lineHeight = 18.sp)
    }
}

@Composable
private fun Method(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(Oc.pill).background(c.surface).border(1.dp, c.border, Oc.pill).tap(scale = 0.98f, onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, size = 24.dp); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Txt(title, size = 17.sp, weight = FontWeight.Medium); Txt(sub, size = 13.sp, color = c.textDim) }
        Ico(Ic.ChevronRight, tint = c.textDim, size = 18.dp)
    }
}

@Composable
private fun BigButton(text: String, filled: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Oc.c
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(Oc.pill).then(if (filled) Modifier.background(if (enabled) c.text else c.surfaceHigh) else Modifier.border(1.dp, c.border, Oc.pill))
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(text, size = 16.sp, color = if (filled) (if (enabled) c.bg else c.textDim) else c.text, weight = FontWeight.Medium) }
}

@Composable
private fun PasteKey(onKey: (String) -> Unit) {
    val c = Oc.c
    val ctx = LocalContext.current
    var key by remember { mutableStateOf("") }
    val valid = KEY_RE.matches(key.trim())
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Heading("Paste your key", size = 28.sp)
        Spacer(Modifier.height(8.dp))
        Txt("Find it in the OpenCode console under API Keys. It starts with “sk-”.", size = 16.sp, color = c.textDim, lineHeight = 23.sp)
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().clip(Oc.round(16.dp)).background(c.surface).border(1.dp, if (key.isNotBlank() && !valid) c.danger else c.border, Oc.round(16.dp)).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 16.dp)) {
                if (key.isEmpty()) Txt("sk-…", size = 16.sp, color = c.textDim, family = Oc.mono)
                BasicTextField(key, { key = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = Oc.mono), cursorBrush = SolidColor(c.text))
            }
            Box(Modifier.clip(Oc.pill).background(c.surfaceHigh).tap {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                KEY_RE.find(cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty())?.let { key = it.value }
            }.padding(horizontal = 14.dp, vertical = 9.dp)) { Txt("Paste", size = 14.sp) }
        }
        if (key.isNotBlank() && !valid) Txt("That doesn't look like an OpenCode key.", Modifier.padding(top = 8.dp), size = 13.sp, color = c.danger)
        Spacer(Modifier.weight(1f))
        BigButton("Connect", enabled = valid) { onKey(key.trim()) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(Oc.pill).tap(scale = 0.98f) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CONSOLE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) { Txt("Open the console", size = 15.sp, color = c.textDim) }
        Spacer(Modifier.height(8.dp))
    }
}

/** Готово: галочка «вырастает», ниже — что теперь доступно. */
@Composable
private fun Done(onStart: () -> Unit) {
    val c = Oc.c
    val ctx = LocalContext.current
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    val providers = Graph.catalog.providers.value
    val zen = providers.firstOrNull { it.id == "opencode" }?.models?.size ?: 0
    val go = providers.firstOrNull { it.id == "opencode-go" }?.models?.size ?: 0
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Spacer(Modifier.weight(0.6f))
        Box(Modifier.size(84.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(c.ok), contentAlignment = Alignment.Center) {
            Ico(Ic.Check, tint = c.bg, size = 44.dp)
        }
        Spacer(Modifier.height(24.dp))
        Heading("You're connected", size = 30.sp)
        Spacer(Modifier.height(8.dp))
        Txt("Your OpenCode key is saved on this phone.", size = 16.sp, color = c.textDim)
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.Sparkle, tint = c.accent, size = 20.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Txt("Zen", size = 16.sp, weight = FontWeight.Medium); Txt(if (zen > 0) "$zen models · pay as you go" else "Pay as you go", size = 13.sp, color = c.textDim) }
                Ico(Ic.Check, tint = c.ok, size = 18.dp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.Zap, tint = c.accent, size = 20.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Txt("Go", size = 16.sp, weight = FontWeight.Medium); Txt(if (go > 0) "$go models · with the Go plan" else "Works with the Go plan", size = 13.sp, color = c.textDim) }
                Txt("Plan", Modifier.clip(Oc.pill).tap { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://opencode.ai/go")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.padding(horizontal = 10.dp, vertical = 6.dp), size = 14.sp, color = c.accent)
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt("Usage limits for Go show up in the circle at the top of a chat.", size = 13.sp, color = c.textDim, lineHeight = 19.sp)
        Spacer(Modifier.weight(1f))
        BigButton("Start chatting", onClick = onStart)
        Spacer(Modifier.height(12.dp))
    }
}
