package app.ocmobile.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import coil3.compose.AsyncImage
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Files the agent hands to the user. The agent writes an absolute path in the reply (usually as a link
 * `[name](/root/outbox/name.mp3)`), the app finds such paths inside the environment and shows cards:
 * audio plays right in the chat, video and images get a preview, the rest can be opened, saved or shared.
 */
object OutFiles {
    const val OUTBOX = "/root/outbox"
    private val linkRe = Regex("""\]\((/[^)\s]+)\)""")
    private val tickRe = Regex("""`(/[^`\s]+)`""")
    private val lineRe = Regex("""(?m)^\s*(?:📎\s*)?(/[^\s`*]+)\s*$""")

    /** Absolute paths from the text that point to existing files in the environment. */
    fun find(text: String): List<File> {
        if (Graph.devices.isRemote || '/' !in text) return emptyList()
        val paths = LinkedHashSet<String>()
        listOf(linkRe, tickRe, lineRe).forEach { re -> re.findAll(text).forEach { paths.add(it.groupValues[1].trimEnd('.', ',', ';', ':')) } }
        return paths.mapNotNull { host(it) }.distinctBy { it.path }.take(8)
    }

    /**
     * Remote Control: paths in the reply to files on the computer. Only ones with an extension (not folders);
     * whether they exist is checked when the card is shown, through the server.
     */
    fun findRemote(text: String): List<String> {
        if (!Graph.devices.isRemote || '/' !in text) return emptyList()
        val paths = LinkedHashSet<String>()
        listOf(linkRe, tickRe, lineRe).forEach { re -> re.findAll(text).forEach { paths.add(it.groupValues[1].trimEnd('.', ',', ';', ':')) } }
        return paths.filter { p -> p.substringAfterLast('/').let { '.' in it && !it.endsWith('.') } && !p.contains("..") }.take(8)
    }

    /** Kinds of files that are things for the user to look at, not source code the agent edits. */
    val handout = setOf(
        "pdf", "png", "jpg", "jpeg", "gif", "webp", "svg", "mp3", "wav", "ogg", "flac", "m4a", "mp4", "webm", "mov",
        "zip", "tar", "gz", "7z", "apk", "docx", "xlsx", "pptx", "odt", "ods", "csv", "epub", "html", "txt", "md",
    )

    /** Files the agent made with its write tool (handouts only), as `path` lines for [find]. */
    fun written(parts: List<kotlinx.serialization.json.JsonObject>): String = parts.mapNotNull { p ->
        if ((p["tool"] as? kotlinx.serialization.json.JsonPrimitive)?.content != "write") return@mapNotNull null
        val path = ((p["state"] as? kotlinx.serialization.json.JsonObject)?.get("input") as? kotlinx.serialization.json.JsonObject)
            ?.get("filePath")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: return@mapNotNull null
        path.takeIf { it.startsWith("/") && it.substringAfterLast('.').lowercase() in handout && !it.substringAfterLast('/').startsWith("README", true) }
    }.distinct().joinToString("\n") { "`$it`" }

    /** Guest path → host file, only if it's a regular file inside the environment. */
    fun host(guest: String): File? {
        if (!guest.startsWith("/") || guest.contains("..")) return null
        val f = Graph.paths.host(guest)
        return f.takeIf { it.isFile && it.canonicalPath.startsWith(Graph.paths.rootfs.canonicalPath) }
    }

    fun mime(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: when (f.extension.lowercase()) {
            "md" -> "text/markdown"; "kt", "py", "sh", "json", "log" -> "text/plain"; else -> "application/octet-stream"
        }

    private fun uri(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    fun open(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, f), mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(Intent.createChooser(i, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Graph.chats.toasts.tryEmit("No app can open ${f.name}") }
    }

    fun share(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_SEND).setType(mime(f)).putExtra(Intent.EXTRA_STREAM, uri(ctx, f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A copy in Downloads/OpenCode, where the file manager, player and gallery can see it. */
    suspend fun save(ctx: Context, f: File) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) { withContext(Dispatchers.Main) { share(ctx, f) }; return@withContext }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, mime(f))
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenCode")
        }
        val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (u == null) { Graph.chats.toasts.tryEmit("Could not save ${f.name}"); return@withContext }
        ctx.contentResolver.openOutputStream(u)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        Graph.chats.toasts.tryEmit("Saved to Download/OpenCode")
    }

    fun human(b: Long) = app.ocmobile.data.Attachments.human(b)
}

/** One player for the whole app: a new track stops the previous one. */
private object Player {
    var mp: MediaPlayer? = null
    var path by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)

    fun toggle(f: File) {
        if (path == f.path && mp != null) {
            mp?.let { if (it.isPlaying) { it.pause(); playing = false } else { it.start(); playing = true } }
            return
        }
        stop()
        runCatching {
            mp = MediaPlayer().apply {
                setDataSource(f.path)
                setOnCompletionListener { playing = false; seekTo(0) }
                prepare(); start()
            }
            path = f.path; playing = true
        }.onFailure { Graph.chats.toasts.tryEmit("Can't play ${f.name}") }
    }

    fun stop() { mp?.release(); mp = null; path = null; playing = false }
}

/**
 * What the agent sends: pictures, videos and audio as small square tiles in a row (like Claude's
 * app shows a picture), a tap opens it; other files as cards. Long press: save or share.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FileCards(files: List<File>) {
    val (media, rest) = files.partition { OutFiles.mime(it).let { m -> m.startsWith("image/") || m.startsWith("video/") || m.startsWith("audio/") } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (media.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            media.forEach { MediaTile(it) }
        }
        rest.forEach { FileCard(it) }
    }
}

/** Side of a media tile: the size Claude's app shows a picture at. */
val TILE = 108.dp

@Composable
fun MediaTile(f: File) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val m = OutFiles.mime(f)
    var menu by remember { mutableStateOf(false) }
    val shape = Oc.round(18.dp)
    val audio = m.startsWith("audio/")
    val playing = audio && Player.path == f.path && Player.playing
    if (audio) DisposableEffect(f.path) { onDispose { if (Player.path == f.path) Player.stop() } }
    Box(
        Modifier.size(TILE).clip(shape).background(c.surface).border(1.dp, c.border, shape)
            .tap(scale = 0.96f, onLongClick = { menu = true }) { if (audio) Player.toggle(f) else FileView.open(ViewSrc.Local(f)) },
        contentAlignment = Alignment.Center,
    ) {
        when {
            m.startsWith("image/") -> AsyncImage(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            m.startsWith("video/") -> {
                val frame by produceState<Bitmap?>(null, f.path) {
                    value = withContext(Dispatchers.IO) { retriever(f.path) { r -> if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320) else r.getFrameAtTime(500_000) } }
                }
                Box(Modifier.fillMaxSize().background(Color.Black))
                frame?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xAA000000)), contentAlignment = Alignment.Center) { Ico(Ic.Play, tint = Color.White, size = 18.dp) }
            }
            else -> {
                // audio: a few bars like a waveform, the play button over them, the name under
                var progress by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(playing) { while (playing) { Player.mp?.let { if (it.duration > 0) progress = it.currentPosition.toFloat() / it.duration }; delay(120) } }
                val bars = remember(f.name) { List(14) { i -> 0.25f + ((f.name.hashCode() shr (i % 16)) and 7) / 10f } }
                Canvas(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 30.dp)) {
                    val w = size.width / (bars.size * 2 - 1)
                    bars.forEachIndexed { i, h ->
                        val bh = size.height * h.coerceAtMost(1f)
                        val done = playing && i.toFloat() / bars.size < progress
                        drawRoundRect(if (done) c.text else c.textDim.copy(alpha = 0.5f), Offset(i * 2 * w, (size.height - bh) / 2), Size(w, bh), CornerRadius(w / 2))
                    }
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(c.text), contentAlignment = Alignment.Center) { Ico(if (playing) Ic.Pause else Ic.Play, tint = c.bg, size = 18.dp) }
                Txt(f.nameWithoutExtension, size = 11.sp, color = c.textDim, maxLines = 1, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 7.dp))
            }
        }
        if (menu) FloatMenu({ menu = false }, listOf(
            FloatItem("Save to Downloads", app.ocmobile.ui.theme.Ic.Download) { scope.launch { runCatching { OutFiles.save(ctx, f) } } },
            FloatItem("Share", app.ocmobile.ui.theme.Ic.Share) { OutFiles.share(ctx, f) },
        ))
    }
}

@Composable
fun FileCard(f: File) {
    val m = OutFiles.mime(f)
    when {
        m.startsWith("audio/") -> AudioCard(f)
        m.startsWith("video/") -> VideoCard(f)
        m.startsWith("image/") -> ImageCard(f)
        else -> PlainCard(f, m)
    }
}

@Composable
private fun CardFrame(content: @Composable () -> Unit) {
    val c = Oc.c
    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp))) { content() }
}

@Composable
private fun Actions(f: File) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val c = Oc.c
    Row {
        IconBtn(Ic.Download, { scope.launch { runCatching { OutFiles.save(ctx, f) } } }, size = 38.dp, iconSize = 19.dp, tint = c.textDim)
        IconBtn(Ic.Share, { OutFiles.share(ctx, f) }, size = 38.dp, iconSize = 19.dp, tint = c.textDim)
    }
}

@Composable
private fun TitleBlock(f: File, sub: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Txt(f.name, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
        Txt(sub, size = 13.sp, color = Oc.c.textDim, maxLines = 1)
    }
}

@Composable
private fun PlainCard(f: File, mime: String) {
    val c = Oc.c
    val ctx = LocalContext.current
    CardFrame {
        Row(Modifier.tap(onClick = { FileView.open(ViewSrc.Local(f)) }).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(Oc.round(10.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                Ico(if (mime.contains("zip") || mime.contains("tar") || mime.contains("compressed")) Ic.Archive else Ic.File, tint = c.textDim, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            TitleBlock(f, f.extension.uppercase().ifBlank { "FILE" } + " · " + OutFiles.human(f.length()), Modifier.weight(1f))
            Actions(f)
        }
    }
}

@Composable
internal fun AudioCard(f: File) {
    val c = Oc.c
    val active = Player.path == f.path
    val playing = active && Player.playing
    var progress by remember { mutableFloatStateOf(0f) }
    var dur by remember(f.path) { mutableStateOf(0) }
    LaunchedEffect(f.path) {
        dur = withContext(Dispatchers.IO) {
            retriever(f.path) { r -> r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() } ?: 0
        }
    }
    LaunchedEffect(playing) {
        while (playing) { Player.mp?.let { if (it.duration > 0) progress = it.currentPosition.toFloat() / it.duration }; delay(120) }
    }
    DisposableEffect(f.path) { onDispose { if (Player.path == f.path) Player.stop() } }
    CardFrame {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.text).tap(onClick = { Player.toggle(f) }), contentAlignment = Alignment.Center) {
                Ico(if (playing) Ic.Pause else Ic.Play, tint = c.bg, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(f.name, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                // track: tap to seek
                Canvas(Modifier.fillMaxWidth().height(18.dp).pointerInput(f.path) {
                    detectTapGestures { o: Offset ->
                        val k = (o.x / size.width).coerceIn(0f, 1f)
                        if (Player.path != f.path) Player.toggle(f)
                        Player.mp?.let { it.seekTo((it.duration * k).toInt()); progress = k }
                    }
                }) {
                    val y = size.height / 2
                    drawRoundRect(c.border, Offset(0f, y - 2.dp.toPx()), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    drawRoundRect(c.text, Offset(0f, y - 2.dp.toPx()), Size(size.width * (if (active) progress else 0f), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
                Txt(
                    (if (active && dur > 0) time((progress * dur).toInt()) + " / " else "") + (if (dur > 0) time(dur) else f.extension.uppercase()) + " · " + OutFiles.human(f.length()),
                    size = 12.sp, color = c.textDim,
                )
            }
            Actions(f)
        }
    }
}

private fun time(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun VideoCard(f: File) {
    val c = Oc.c
    val ctx = LocalContext.current
    val frame by produceState<Bitmap?>(null, f.path) {
        value = withContext(Dispatchers.IO) { retriever(f.path) { r -> if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720) else r.getFrameAtTime(500_000) } }
    }
    CardFrame {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black).tap(onClick = { FileView.open(ViewSrc.Local(f)) }), contentAlignment = Alignment.Center) {
                frame?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop) }
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xCC000000)), contentAlignment = Alignment.Center) { Ico(Ic.Play, tint = Color.White, size = 24.dp) }
            }
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TitleBlock(f, "Video · " + OutFiles.human(f.length()), Modifier.weight(1f))
                Actions(f)
            }
        }
    }
}

@Composable
private fun ImageCard(f: File) {
    val ctx = LocalContext.current
    CardFrame {
        Column {
            AsyncImage(f, null, Modifier.fillMaxWidth().heightInImage().tap(onClick = { FileView.open(ViewSrc.Local(f)) }), contentScale = ContentScale.Crop)
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TitleBlock(f, "Image · " + OutFiles.human(f.length()), Modifier.weight(1f))
                Actions(f)
            }
        }
    }
}

private fun Modifier.heightInImage() = this.then(Modifier.heightIn(max = 320.dp))

private inline fun <T> retriever(path: String, block: (MediaMetadataRetriever) -> T): T? {
    val r = MediaMetadataRetriever()
    return try { r.setDataSource(path); block(r) } catch (_: Exception) { null } finally { runCatching { r.release() } }
}

/** Files on the computer the phone drives, fetched once and kept for the viewer (a few MB at most). */
object RemoteFiles {
    private val cache = object : LinkedHashMap<String, kotlinx.serialization.json.JsonObject>(16, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, kotlinx.serialization.json.JsonObject>) = size > 12
    }
    private val missing = HashSet<String>()

    suspend fun get(path: String, directory: String?): kotlinx.serialization.json.JsonObject? {
        val k = "$directory|$path"
        synchronized(cache) { cache[k]?.let { return it }; if (k in missing) return null }
        val o = runCatching { Graph.api.fileContent(path, directory) }.getOrNull()
        synchronized(cache) { if (o != null && (o["content"]?.toString()?.length ?: 0) < 8_000_000) cache[k] = o else if (o == null) missing.add(k) }
        return o
    }
}

/** Cards for files on the computer (Remote Control): a name and size; a tap opens it in the viewer. */
@Composable
fun RemoteFileCards(paths: List<String>, directory: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paths.forEach { p ->
            val info by produceState<kotlinx.serialization.json.JsonObject?>(null, p, directory) { value = RemoteFiles.get(p, directory) }
            info?.let { o -> RemoteCard(p, directory, o) }
        }
    }
}

@Composable
private fun RemoteCard(path: String, directory: String?, o: kotlinx.serialization.json.JsonObject) {
    val c = Oc.c
    val name = path.substringAfterLast('/')
    val content = (o["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    val binary = (o["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "binary"
    val size = if (binary) content.length * 3L / 4 else content.toByteArray().size.toLong()
    CardFrame {
        Row(Modifier.tap(onClick = { FileView.open(ViewSrc.Remote(path, directory)) }).padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(Oc.round(10.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Ico(Ic.Laptop, tint = c.textDim, size = 20.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(name, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                Txt(name.substringAfterLast('.').uppercase() + " · " + OutFiles.human(size) + " · on the computer", size = 13.sp, color = c.textDim, maxLines = 1)
            }
        }
    }
}
