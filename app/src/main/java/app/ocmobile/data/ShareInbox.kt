package app.ocmobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.ocmobile.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * «Поделиться → Ask OpenCode» из любого приложения. Текст и файлы попадают в поле ввода нового чата.
 * Файлы сразу копируем к себе: разрешение на чужой content:// живёт недолго.
 */
object ShareInbox {
    class Shared(val text: String, val uris: List<Uri>)

    val pending = MutableStateFlow<Shared?>(null)

    fun take(ctx: Context, i: Intent) {
        val text = listOfNotNull(i.getStringExtra(Intent.EXTRA_SUBJECT), i.getStringExtra(Intent.EXTRA_TEXT))
            .filter { it.isNotBlank() }.distinct().joinToString("\n")
        @Suppress("DEPRECATION")
        val streams: List<Uri> = when (i.action) {
            Intent.ACTION_SEND_MULTIPLE -> (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
            else -> listOfNotNull(if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM))
        }
        val app = ctx.applicationContext
        Graph.scope.launch(Dispatchers.IO) {
            val dir = File(app.cacheDir, "camera/shared").apply { mkdirs() }
            val local = streams.take(10).mapNotNull { u ->
                runCatching {
                    val name = Attachments.describe(app, u).name.replace('/', '_')
                    val f = File(dir, "${System.currentTimeMillis()}-$name")
                    ctx.contentResolver.openInputStream(u)!!.use { inp -> f.outputStream().use { inp.copyTo(it) } }
                    FileProvider.getUriForFile(app, app.packageName + ".files", f)
                }.getOrNull()
            }
            if (text.isNotBlank() || local.isNotEmpty()) pending.value = Shared(text, local)
        }
    }
}
