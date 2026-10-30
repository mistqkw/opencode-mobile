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
 * Файлы, которые агент отдаёт пользователю. Агент пишет в ответе абсолютный путь (обычно ссылкой
 * `[name](/root/outbox/name.mp3)`), приложение находит такие пути внутри окружения и показывает карточки:
 * звук играет прямо в чате, видео и картинки — с превью, остальное открывается, сохраняется или отправляется.
 */
object OutFiles {
    const val OUTBOX = "/root/outbox"
    private val linkRe = Regex("""\]\((/[^)\s]+)\)""")
    private val tickRe = Regex("""`(/[^`\s]+)`""")
    private val lineRe = Regex("""(?m)^\s*(?:📎\s*)?(/[^\s`*]+)\s*$""")

    /** Абсолютные пути из текста, которые указывают на существующие файлы в окружении. */
    fun find(text: String): List<File> {
        if (Graph.devices.isRemote || '/' !in text) return emptyList()
        val paths = LinkedHashSet<String>()
        listOf(linkRe, tickRe, lineRe).forEach { re -> re.findAll(text).forEach { paths.add(it.groupValues[1].trimEnd('.', ',', ';', ':')) } }
        return paths.mapNotNull { host(it) }.distinctBy { it.path }.take(8)
    }

    /** Гостевой путь → файл на хосте, только если это обычный файл внутри окружения. */
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

    /** Копия в Загрузки/OpenCode — там её видят файловый менеджер, плеер и галерея. */
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

/** Один проигрыватель на всё приложение: новый трек останавливает прежний. */
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

@Composable
fun FileCards(files: List<File>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { files.forEach { FileCard(it) } }
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
        Row(Modifier.tap(onClick = { OutFiles.open(ctx, f) }).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun AudioCard(f: File) {
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
                // дорожка: тап — перемотка
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
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black).tap(onClick = { OutFiles.open(ctx, f) }), contentAlignment = Alignment.Center) {
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
            AsyncImage(f, null, Modifier.fillMaxWidth().heightInImage().tap(onClick = { OutFiles.open(ctx, f) }), contentScale = ContentScale.Crop)
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
