package app.ocmobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

/** Превращает выбранные файлы в части промпта: картинки уменьшаем и кладём как data: URL. */
object Attachments {
    private const val MAX_EDGE = 2048
    private const val RECOMPRESS_ABOVE = 1_500_000

    fun describe(ctx: Context, uri: Uri): Attachment {
        var name = uri.lastPathSegment ?: "file"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        return Attachment(uri, mime, name)
    }

    /**
     * Часть промпта для вложения. Модели принимают только то, что умеют (картинки, иногда PDF);
     * остальное — звук, видео, архивы, любые файлы — кладём на диск окружения и говорим агенту, где оно лежит.
     */
    fun toPart(ctx: Context, a: Attachment): JsonObject {
        val m = app.ocmobile.Graph.catalog.model(app.ocmobile.Graph.prefs.providerId, app.ocmobile.Graph.prefs.modelId)
        val inline = (a.isImage && m?.supportsImages != false) || (a.mime == "application/pdf" && m?.capabilities?.input?.pdf == true)
        if (inline) return toFilePart(ctx, a)
        val bytes = ctx.contentResolver.openInputStream(a.uri)!!.use { it.readBytes() }
        if (app.ocmobile.Graph.devices.isRemote) {
            // на чужой компьютер файл не положить: текст отправляем прямо в сообщении
            if (!isTextLike(a.mime, a.name) || bytes.size > 256_000) throw java.io.IOException("Only images and text files can be sent to a remote device")
            return synthetic("<attached-file name=\"${a.name}\" mime=\"${a.mime}\" size=\"${bytes.size}\"/>\nContents of ${a.name}:\n```\n${String(bytes)}\n```")
        }
        val dir = app.ocmobile.Graph.paths.host(UPLOADS).apply { mkdirs() }
        var f = java.io.File(dir, safeName(a.name))
        var i = 2
        while (f.exists()) { f = java.io.File(dir, safeName(a.name).let { n -> n.substringBeforeLast('.', n) + "-$i" + (if ('.' in n) "." + n.substringAfterLast('.') else "") }); i++ }
        f.writeBytes(bytes)
        val guest = "$UPLOADS/${f.name}"
        return synthetic(
            "<attached-file path=\"$guest\" name=\"${a.name}\" mime=\"${a.mime}\" size=\"${bytes.size}\"/>\n" +
                "The user attached the file ${a.name} (${a.mime}, ${human(bytes.size.toLong())}). It is saved at $guest — open it with your tools when needed.",
        )
    }

    const val UPLOADS = "/root/uploads"

    private fun synthetic(text: String) = buildJsonObject { put("type", "text"); put("text", text); put("synthetic", true) }

    private fun safeName(n: String) = n.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "file" }

    fun isTextLike(mime: String, name: String) = mime.startsWith("text/") || mime in setOf("application/json", "application/xml", "application/javascript", "application/x-sh", "application/yaml", "application/toml") ||
        name.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "json", "kt", "java", "py", "js", "ts", "tsx", "c", "h", "cpp", "rs", "go", "sh", "yml", "yaml", "toml", "xml", "html", "css", "csv", "log", "ini", "conf", "gradle", "kts", "sql")

    fun human(b: Long) = when { b >= 1_000_000 -> "%.1f MB".format(b / 1e6); b >= 1000 -> "%.0f KB".format(b / 1e3); else -> "$b B" }

    /** Синтетическая часть с вложением → (имя, путь); null, если это не вложение. */
    fun parseAttached(text: String): Pair<String, String?>? {
        if (!text.startsWith("<attached-file ")) return null
        val head = text.substringBefore("/>")
        fun attr(k: String) = Regex("$k=\"([^\"]*)\"").find(head)?.groupValues?.get(1)
        return (attr("name") ?: "file") to attr("path")
    }

    fun toFilePart(ctx: Context, a: Attachment): JsonObject {
        var bytes = ctx.contentResolver.openInputStream(a.uri)!!.use { it.readBytes() }
        var mime = a.mime
        var name = a.name
        if (a.isImage && mime != "image/gif" && (bytes.size > RECOMPRESS_ABOVE || largeEdge(bytes) > MAX_EDGE)) {
            bytes = recompress(bytes)
            mime = "image/jpeg"
            name = name.substringBeforeLast('.') + ".jpg"
        }
        val url = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        return buildJsonObject {
            put("type", "file")
            put("mime", mime)
            put("filename", name)
            put("url", url)
        }
    }

    private fun largeEdge(b: ByteArray): Int {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        return maxOf(o.outWidth, o.outHeight)
    }

    private fun recompress(b: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return b
        val scale = MAX_EDGE.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        val rotation = runCatching {
            when (ExifInterface(b.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation != 0f) m.postRotate(rotation)
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 86, out)
        return out.toByteArray()
    }
}
