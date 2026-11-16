package app.ocmobile.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ocmobile.Graph
import app.ocmobile.api.str
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.rememberMotionEnabled
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** A file to show in the app: on this phone's environment, inside a message, or on the connected computer. */
sealed interface ViewSrc {
    val name: String

    data class Local(val file: File) : ViewSrc { override val name get() = file.name }
    data class Data(val url: String, override val name: String) : ViewSrc
    /** On the computer the phone drives (Remote Control): read through the server, only inside the project. */
    data class Remote(val path: String, val directory: String?) : ViewSrc { override val name get() = path.trimEnd('/').substringAfterLast('/') }
}

/**
 * Everything you and the agent send opens right in the app, like in Claude: pictures zoom, PDFs scroll page
 * by page, video and audio play, Markdown and web pages show rendered (with their source a tap away),
 * tables as tables, any text or code with line numbers. Only what the phone can't show goes to another app.
 */
object FileView {
    var src by mutableStateOf<ViewSrc?>(null)
    fun open(s: ViewSrc) { src = s }
}

private enum class Kind { Image, Pdf, Video, Audio, Markdown, Html, Csv, Text, Other }

private class Loaded(val file: File, val mime: String, val kind: Kind)

private const val TEXT_LIMIT = 2_000_000

private fun kindOf(f: File, mime: String): Kind {
    val ext = f.extension.lowercase()
    return when {
        mime.startsWith("image/") && ext != "svg" -> Kind.Image
        mime == "application/pdf" || ext == "pdf" -> Kind.Pdf
        mime.startsWith("video/") -> Kind.Video
        mime.startsWith("audio/") -> Kind.Audio
        ext in setOf("md", "markdown") -> Kind.Markdown
        ext in setOf("html", "htm", "svg") -> Kind.Html
        ext in setOf("csv", "tsv") -> Kind.Csv
        app.ocmobile.data.Attachments.isTextLike(mime, f.name) || looksLikeText(f) -> Kind.Text
        else -> Kind.Other
    }
}

/** No zero bytes in the first 4 KB and almost all of it valid text: show it as text. */
private fun looksLikeText(f: File): Boolean = runCatching {
    val b = f.inputStream().use { head(it, 4096) }
    if (b.isEmpty()) return true
    if (b.any { it == 0.toByte() }) return false
    val s = String(b, Charsets.UTF_8)
    s.count { it == '�' } < s.length / 50 + 1
}.getOrDefault(false)

/** Up to [n] bytes from the start (InputStream.readNBytes needs Android 13). */
private fun head(i: java.io.InputStream, n: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < n) { val r = i.read(buf, 0, minOf(buf.size, n - out.size())); if (r <= 0) break; out.write(buf, 0, r) }
    return out.toByteArray()
}

private suspend fun load(ctx: Context, s: ViewSrc): Loaded = withContext(Dispatchers.IO) {
    fun cached(name: String, bytes: ByteArray): File {
        val dir = File(ctx.cacheDir, "view/" + Integer.toHexString(s.hashCode())).apply { mkdirs() }
        return File(dir, name.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "file" }).apply { writeBytes(bytes) }
    }
    when (s) {
        is ViewSrc.Local -> { val m = OutFiles.mime(s.file); Loaded(s.file, m, kindOf(s.file, m)) }
        is ViewSrc.Data -> {
            val head = s.url.substringAfter("data:").substringBefore(',')
            val body = s.url.substringAfter(',')
            val bytes = if (head.endsWith(";base64")) Base64.decode(body, Base64.DEFAULT) else Uri.decode(body).toByteArray()
            val mime = head.substringBefore(';').ifBlank { "application/octet-stream" }
            val f = cached(s.name, bytes)
            Loaded(f, mime, kindOf(f, mime))
        }
        is ViewSrc.Remote -> {
            val o = RemoteFiles.get(s.path, s.directory) ?: throw java.io.IOException("The computer sent nothing for ${s.name}")
            val content = o.str("content").orEmpty()
            val bytes = if (o.str("type") == "binary" || o.str("encoding") == "base64") Base64.decode(content, Base64.DEFAULT) else content.toByteArray()
            val f = cached(s.name, bytes)
            val mime = o.str("mimeType") ?: OutFiles.mime(f)
            Loaded(f, mime, kindOf(f, mime))
        }
    }
}

/** Put once in the root, over everything: shows [FileView.src] full screen. */
@Composable
fun FileViewerHost() {
    val s = FileView.src ?: return
    val ctx = LocalContext.current
    val motion = rememberMotionEnabled()
    val shown = remember(s) { Animatable(if (motion) 0f else 1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(s) { shown.animateTo(1f, tween(150, easing = FastOutSlowInEasing)) }
    fun close() = scope.launch { if (motion) shown.animateTo(0f, tween(110)); FileView.src = null }
    val state by produceState<Result<Loaded>?>(null, s) { value = runCatching { load(ctx, s) } }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val c = Oc.c
        val dark = state?.getOrNull()?.kind.let { it == Kind.Image || it == Kind.Video }
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = shown.value; val k = 0.96f + 0.04f * shown.value; scaleX = k; scaleY = k }
                .background(if (dark) Color.Black else c.bg),
        ) {
            val loaded = state?.getOrNull()
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val tint = if (dark) Color.White else c.text
                IconBtn(Ic.Close, { close() }, tint = tint)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Txt(s.name, size = 16.sp, weight = FontWeight.Medium, color = tint, maxLines = 1)
                    loaded?.let { Txt(typeLabel(it) + "  " + OutFiles.human(it.file.length()), size = 12.sp, color = if (dark) Color.White.copy(alpha = 0.6f) else c.textDim, maxLines = 1) }
                }
                if (loaded != null) {
                    IconBtn(Ic.Share, { OutFiles.share(ctx, loaded.file) }, tint = tint, iconSize = 21.dp)
                    IconBtn(Ic.Download, { scope.launch { runCatching { OutFiles.save(ctx, loaded.file) } } }, tint = tint, iconSize = 21.dp)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state == null -> Txt("Opening…", Modifier.align(Alignment.Center), color = c.textDim)
                    state!!.isFailure -> Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt("Can't open ${s.name}", size = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Txt(state!!.exceptionOrNull()?.message ?: "", size = 14.sp, color = c.textDim, align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    else -> Body(loaded!!)
                }
            }
        }
    }
}

private fun typeLabel(l: Loaded) = when (l.kind) {
    Kind.Image -> "Image"; Kind.Pdf -> "PDF"; Kind.Video -> "Video"; Kind.Audio -> "Audio"; Kind.Markdown -> "Markdown"
    Kind.Html -> "Web page"; Kind.Csv -> "Table"; Kind.Text -> l.file.extension.uppercase().ifBlank { "Text" }; Kind.Other -> l.file.extension.uppercase().ifBlank { "File" }
}

@Composable
private fun Body(l: Loaded) {
    when (l.kind) {
        Kind.Image -> ZoomImage(l.file)
        Kind.Pdf -> PdfPages(l.file)
        Kind.Video -> VideoPlayer(l.file)
        Kind.Audio -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) { AudioCard(l.file) }
        Kind.Markdown -> Rendered(l.file) { text -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding()) { SelectionContainer { Markdown(text) } } }
        Kind.Html -> Rendered(l.file) { text -> HtmlPage(text) }
        Kind.Csv -> CsvTable(l.file)
        Kind.Text -> TextLines(readText(l.file))
        Kind.Other -> Other(l.file)
    }
}

private fun readText(f: File): String {
    val b = f.inputStream().use { head(it, TEXT_LIMIT) }
    val s = String(b, Charsets.UTF_8)
    return if (f.length() > TEXT_LIMIT) s + "\n\n… (${OutFiles.human(f.length())}: only the first ${OutFiles.human(TEXT_LIMIT.toLong())} are shown)" else s
}

/** Markdown and web pages: shown rendered, with a switch to their source text. */
@Composable
private fun Rendered(f: File, view: @Composable (String) -> Unit) {
    val c = Oc.c
    val text = remember(f) { readText(f) }
    var source by remember(f) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            listOf(false to "View", true to "Source").forEach { (v, label) ->
                Txt(label, Modifier.clip(Oc.pill).background(if (source == v) c.surfaceHigh else Color.Transparent).tap { source = v }.padding(horizontal = 14.dp, vertical = 7.dp),
                    size = 14.sp, color = if (source == v) c.text else c.textDim)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { if (source) TextLines(text) else view(text) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlPage(html: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { cx ->
            WebView(cx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                // links leave the file: they open in the app's browser, the page stays put
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, req: android.webkit.WebResourceRequest): Boolean {
                        if (req.url.scheme == "http" || req.url.scheme == "https") Graph.chats.openRequest.value = "route:preview:" + req.url
                        return true
                    }
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

/** Any text or code: numbered lines, wrapped, selectable; a button copies all of it. */
@Composable
private fun TextLines(text: String) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    val lines = remember(text) { text.split('\n') }
    val gutter = remember(lines.size) { lines.size.toString().length }
    Box(Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp)) {
                itemsIndexed(lines) { i, line ->
                    Row(Modifier.fillMaxWidth().padding(end = 14.dp)) {
                        Txt((i + 1).toString().padStart(gutter), Modifier.padding(start = 12.dp, end = 12.dp), size = 12.sp, color = c.textDim.copy(alpha = 0.6f), family = Oc.mono, lineHeight = 19.sp)
                        Txt(line.ifEmpty { " " }, Modifier.weight(1f), size = 13.sp, family = Oc.mono, lineHeight = 19.sp)
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).clip(Oc.pill).background(c.surfaceHigh).border(1.dp, c.border, Oc.pill)
                .tap { clip.setText(AnnotatedString(text)); app.ocmobile.ui.components.copiedNote() }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Ico(Ic.Copy, tint = c.text, size = 18.dp); Spacer(Modifier.width(8.dp)); Txt("Copy all", size = 14.sp) }
    }
}

/** CSV/TSV as a table: the first row is the header; columns get the width of their longest cell (up to a limit). */
@Composable
private fun CsvTable(f: File) {
    val c = Oc.c
    val rows = remember(f) { parseCsv(readText(f), if (f.extension.lowercase() == "tsv") '\t' else ',').take(2000) }
    if (rows.isEmpty()) return TextLines("")
    val cols = rows.maxOf { it.size }
    val widths = remember(rows) { (0 until cols).map { i -> (rows.take(200).maxOf { it.getOrNull(i)?.length ?: 0 }.coerceIn(3, 32) * 8 + 24).dp } }
    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        LazyColumn(Modifier.width(widths.fold(0.dp) { a, w -> a + w }).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            itemsIndexed(rows) { r, row ->
                Row(Modifier.background(if (r == 0) c.surfaceHigh else if (r % 2 == 0) c.surface else Color.Transparent)) {
                    (0 until cols).forEach { i ->
                        Txt(row.getOrNull(i).orEmpty(), Modifier.width(widths[i]).padding(horizontal = 12.dp, vertical = 8.dp), size = 13.sp,
                            weight = if (r == 0) FontWeight.SemiBold else null, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** A plain CSV reader: quoted fields, doubled quotes, line breaks inside quotes. */
internal fun parseCsv(s: String, sep: Char): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < s.length) {
        val ch = s[i]
        when {
            quoted && ch == '"' && s.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            !quoted && ch == sep -> { row.add(cell.toString()); cell.clear() }
            !quoted && (ch == '\n' || ch == '\r') -> {
                if (ch == '\r' && s.getOrNull(i + 1) == '\n') i++
                row.add(cell.toString()); cell.clear(); rows.add(row); row = ArrayList()
            }
            else -> cell.append(ch)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
    return rows.filterNot { it.size == 1 && it[0].isEmpty() }
}

@Composable
private fun ZoomImage(f: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val t = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
            .transformable(t, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        contentAlignment = Alignment.Center,
    ) { AsyncImage(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
}

/**
 * PDF pages one under another, drawn by Android itself. One finger scrolls; two fingers zoom and move
 * (the list keeps scrolling while zoomed); a double tap zooms in and back.
 */
@Composable
private fun PdfPages(f: File) {
    val c = Oc.c
    val doc = remember(f) { runCatching { PdfDoc(f) } }
    DisposableEffect(f) { onDispose { doc.getOrNull()?.close() } }
    val d = doc.getOrNull() ?: return Txt("This PDF can't be opened: ${doc.exceptionOrNull()?.message}", Modifier.padding(24.dp), color = c.textDim)
    var scale by remember { mutableFloatStateOf(1f) }
    var dx by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        LazyColumn(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; dx = 0f } else scale = 2f }) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val e = awaitPointerEvent()
                            if (e.changes.count { it.pressed } >= 2) {
                                scale = (scale * e.calculateZoom()).coerceIn(1f, 5f)
                                val lim = widthPx * (scale - 1f) / 2f
                                dx = (dx + e.calculatePan().x).coerceIn(-lim, lim)
                                e.changes.forEach { it.consume() }
                            }
                        } while (e.changes.any { it.pressed })
                    }
                }
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = dx; transformOrigin = TransformOrigin(0.5f, 0f) },
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(d.count) { i ->
                val ratio = remember(i) { d.ratio(i) }
                val bmp by produceState<Bitmap?>(null, i) { value = d.render(i, (widthPx * 1.6f).toInt().coerceAtMost(2200)) }
                Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White)) {
                    bmp?.let { Image(it.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                }
            }
            item { Txt("${d.count} pages", Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), size = 12.sp, color = c.textDim, align = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

/** PdfRenderer draws one page at a time: every use goes through a lock, off the main thread. */
private class PdfDoc(f: File) {
    private val fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    private val r = PdfRenderer(fd)
    private val lock = Mutex()
    private val sizes = (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } }
    val count get() = sizes.size
    fun ratio(i: Int) = sizes[i].let { (w, h) -> w.toFloat() / h.coerceAtLeast(1) }
    suspend fun render(i: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val (w, h) = sizes[i]
                val bmp = Bitmap.createBitmap(width, (width.toFloat() * h / w).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                r.openPage(i).use { it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                bmp
            }.getOrNull()
        }
    }
    fun close() { runCatching { r.close(); fd.close() } }
}

@Composable
private fun VideoPlayer(f: File) {
    AndroidView(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        factory = { cx ->
            val box = android.widget.FrameLayout(cx)
            val v = VideoView(cx)
            box.addView(v, android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
            val mc = MediaController(cx)
            mc.setAnchorView(v)
            v.setMediaController(mc)
            v.setVideoPath(f.path)
            v.setOnPreparedListener { it.isLooping = false; v.start(); mc.show(2500) }
            v.setOnErrorListener { _, _, _ -> Graph.chats.toasts.tryEmit("This video can't play here"); true }
            box
        },
        onRelease = { box -> (box.getChildAt(0) as? VideoView)?.stopPlayback() },
    )
}

@Composable
private fun Other(f: File) {
    val c = Oc.c
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(Oc.round(22.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Ico(Ic.File, tint = c.textDim, size = 38.dp) }
        Spacer(Modifier.height(16.dp))
        Txt(f.name, size = 18.sp, weight = FontWeight.Medium, align = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Txt("The app can't show this kind of file.", size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(22.dp))
        Pill("Open in another app", { OutFiles.open(ctx, f) }, icon = Ic.External)
    }
}
