package app.ocmobile.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.ocmobile.Graph
import app.ocmobile.api.obj
import app.ocmobile.api.str
import app.ocmobile.data.PhoneBridge
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Живое превью того, что собирает агент: dev-сервер из окружения (localhost общий с приложением)
 * или .html-файл проекта через мост. Когда агент правит файлы — страница перезагружается сама.
 */
@OptIn(FlowPreview::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(url: String, sessionId: String?, nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var web by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf(label(url)) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf<String?>(null) }
    var reloads by remember { mutableStateOf(0) }

    // агент поправил файл — перезагрузить (с паузой, чтобы серия правок дала одну перезагрузку)
    LaunchedEffect(Unit) {
        Graph.events.events.filter { e ->
            e.type == "file.edited" || (e.type == "message.part.updated" && e.props.obj("part")?.let { p ->
                p.str("tool") in setOf("edit", "write", "apply_patch", "patch", "multiedit") && p.obj("state")?.str("status") == "completed"
            } == true)
        }.debounce(700).collect { web?.reload(); reloads++ }
    }
    BackHandler(web?.canGoBack() == true) { web?.goBack() }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(title, left = { IconBtn(Ic.Back, { nav.pop() }) }, right = {
            IconBtn(Ic.Refresh, { failed = null; web?.reload() })
            IconBtn(Ic.External, { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web?.url ?: url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        })
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (loading) c.accent.copy(alpha = 0.6f) else c.border))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { cx ->
                    WebView(cx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.allowFileAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                                val h = req.url.host ?: return false
                                if (h == "127.0.0.1" || h == "localhost") return false
                                // чужие ссылки — во внешний браузер, превью остаётся на проекте
                                runCatching { cx.startActivity(Intent(Intent.ACTION_VIEW, req.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                return true
                            }
                            override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) { loading = true }
                            override fun onPageFinished(view: WebView, u: String?) { loading = false; title = view.title?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: label(u ?: url) }
                            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: android.webkit.WebResourceError) {
                                if (req.isForMainFrame) failed = err.description?.toString() ?: "Could not open the page"
                            }
                        }
                        loadUrl(url)
                        web = this
                    }
                },
            )
            failed?.let { msg ->
                Column(Modifier.fillMaxSize().background(c.bg).padding(28.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt("The page didn't open", size = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Txt(if (url.contains(":${PhoneBridge.PORT}/")) msg else "$msg\n\nIs the dev server still running? Ask OpenCode to start it again.", size = 15.sp, color = c.textDim, lineHeight = 21.sp, align = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Pill("Try again", { failed = null; web?.reload() })
                }
            }
        }
        if (sessionId != null) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (reloads > 0) c.ok else c.textDim))
            Spacer(Modifier.size(8.dp))
            Txt(if (reloads > 0) "Live · reloaded $reloads×" else "Live · reloads when OpenCode edits files", Modifier.weight(1f), size = 13.sp, color = c.textDim, maxLines = 1)
            // одна кнопка: попросить агента выложить сайт и прислать ссылку
            Pill("Publish", {
                scope.launch { runCatching { Graph.chats.send(ctx, sessionId, "Publish this site to GitHub Pages (create the repo if needed) and send me the link.", emptyList()) } }
                nav.pop()
            })
        }
    }
}

private fun label(u: String): String {
    val uri = Uri.parse(u)
    return if (uri.port == PhoneBridge.PORT) uri.path.orEmpty().substringAfterLast('/').ifBlank { "Preview" }
    else "${uri.host}:${uri.port}${uri.path.orEmpty().takeIf { it != "/" }.orEmpty()}"
}

/** Что из чата можно открыть в превью: адреса dev-серверов из вывода и записанные .html-файлы. */
fun previewTargets(chat: app.ocmobile.data.Chat): List<Pair<String, String>> {
    val out = LinkedHashMap<String, String>()
    val re = Regex("""https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0):(\d{2,5})(/[^\s"'`)\]<>]*)?""")
    chat.messages.takeLast(14).asReversed().forEach { m ->
        m.parts.asReversed().forEach { p ->
            val st = p.raw.obj("state")
            val text = when (p.type) { "text" -> p.text; "tool" -> (st?.str("output") ?: "") + " " + (st?.obj("input")?.str("command") ?: ""); else -> "" }
            re.findAll(text).forEach { mt ->
                val port = mt.groupValues[1]
                if (port != "4096" && port != "${PhoneBridge.PORT}") out.putIfAbsent("localhost:$port", "http://127.0.0.1:$port" + mt.groupValues[2])
            }
            if (p.type == "tool" && p.raw.str("tool") in setOf("write", "edit", "apply_patch")) {
                st?.obj("input")?.str("filePath")?.takeIf { it.endsWith(".html") || it.endsWith(".htm") }?.let { f ->
                    out.putIfAbsent(f.substringAfterLast('/'), PhoneBridge.previewUrl(f))
                }
            }
        }
    }
    return out.entries.take(2).map { it.key to it.value }
}
