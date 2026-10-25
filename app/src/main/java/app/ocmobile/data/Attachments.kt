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
