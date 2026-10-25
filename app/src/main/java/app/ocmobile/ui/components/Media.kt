package app.ocmobile.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Ic
import coil3.compose.AsyncImage
import java.io.File

/** Что отдать Coil: файлы гостевого Linux лежат внутри rootfs на хосте. */
fun imageModel(url: String): Any =
    if (url.startsWith("file://")) Graph.paths.host(Uri.parse(url).path ?: "") else url

fun imageBytes(url: String): ByteArray? = runCatching {
    when {
        url.startsWith("data:") -> Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT)
        url.startsWith("file://") -> Graph.paths.host(Uri.parse(url).path ?: "").readBytes()
        else -> null
    }
}.getOrNull()

@Composable
fun ImageThumb(url: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AsyncImage(
        model = imageModel(url), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(14.dp)).background(Color(0x22888888)).clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewer(urls: List<String>, start: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) { urls.size }
    var chrome by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val tState = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { chrome = !chrome },
                                onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f },
                            )
                        }
                        .transformable(tState, canPan = { scale > 1f })
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(imageModel(urls[page]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            if (chrome) {
                Row(Modifier.fillMaxSize().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.Top) {
                    IconBtn(Ic.Close, onDismiss, tint = Color.White, bg = Color(0x66000000))
                    Box(Modifier.weight(1f))
                    IconBtn(Ic.External, { shareImage(ctx, urls[pager.currentPage]) }, tint = Color.White, bg = Color(0x66000000))
                    Box(Modifier.padding(start = 8.dp))
                    if (Build.VERSION.SDK_INT >= 29) IconBtn(Ic.Download, { saveImage(ctx, urls[pager.currentPage]) }, tint = Color.White, bg = Color(0x66000000))
                }
                if (urls.size > 1) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp)) {
                        Txt("${pager.currentPage + 1} / ${urls.size}", color = Color.White, size = 14.sp)
                    }
                }
            }
        }
    }
}

private fun mimeOf(url: String) = if (url.startsWith("data:")) url.substringAfter("data:").substringBefore(';') else "image/png"

private fun saveImage(ctx: Context, url: String) {
    val bytes = imageBytes(url) ?: return
    val mime = mimeOf(url)
    val ext = mime.substringAfter('/').replace("jpeg", "jpg")
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "opencode-${System.currentTimeMillis()}.$ext")
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenCode")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri != null) {
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        Toast.makeText(ctx, "Saved to Pictures/OpenCode", Toast.LENGTH_SHORT).show()
    }
}

private fun shareImage(ctx: Context, url: String) {
    val bytes = imageBytes(url) ?: return
    val ext = mimeOf(url).substringAfter('/').replace("jpeg", "jpg")
    val f = File(ctx.cacheDir, "camera").apply { mkdirs() }.let { File(it, "share-${System.currentTimeMillis()}.$ext") }
    f.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    ctx.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType(mimeOf(url)).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            null,
        )
    )
}
