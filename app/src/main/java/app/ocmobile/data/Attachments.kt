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

/** Turns picked files into prompt parts: images are scaled down and sent as data: URLs. */
object Attachments {
    private const val MAX_EDGE = 2048
    private const val RECOMPRESS_ABOVE = 1_500_000

    // safeName is called for every session id used in upload paths; these must not be rebuilt each time.
    private val UNSAFE_NAME = Regex("[/\\\\:*?\"<>|]")
    private val TEXT_MIMES = setOf("application/json", "application/xml", "application/javascript", "application/x-sh", "application/yaml", "application/toml")
    private val TEXT_EXTS = setOf("txt", "md", "json", "kt", "java", "py", "js", "ts", "tsx", "c", "h", "cpp", "rs", "go", "sh", "yml", "yaml", "toml", "xml", "html", "css", "csv", "log", "ini", "conf", "gradle", "kts", "sql")

    fun describe(ctx: Context, uri: Uri): Attachment {
        var name = uri.lastPathSegment ?: "file"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        return Attachment(uri, mime, name)
    }

    /**
     * A prompt part for an attachment. Models only take what they can (images, sometimes PDF);
     * the rest (audio, video, archives, any files) goes onto the environment's disk and the agent is told where.
     */
    fun toPart(ctx: Context, a: Attachment, sessionId: String): JsonObject {
        val m = app.ocmobile.Graph.catalog.model(app.ocmobile.Graph.prefs.providerId, app.ocmobile.Graph.prefs.modelId)
        val inline = (a.isImage && m?.supportsImages != false) || (a.mime == "application/pdf" && m?.capabilities?.input?.pdf == true)
        if (inline) return toFilePart(ctx, a)
        val bytes = ctx.contentResolver.openInputStream(a.uri)!!.use { it.readBytes() }
        if (app.ocmobile.Graph.devices.isRemote) {
            // a file can't be put on someone else's computer: send the text right in the message
            if (!isTextLike(a.mime, a.name) || bytes.size > 256_000) throw java.io.IOException("Only images and text files can be sent to a remote device")
            return synthetic("<attached-file name=\"${a.name}\" mime=\"${a.mime}\" size=\"${bytes.size}\"/>\nContents of ${a.name}:\n```\n${String(bytes)}\n```")
        }
        // Files live in this chat's own folder and stay there. Each session gets permission rules that
        // allow only its own folder, so no copying/exposing is needed (and a running agent never loses
        // its files when another chat is opened).
        val dir = uploadsHost(sessionId).apply { mkdirs() }
        var f = java.io.File(dir, safeName(a.name))
        var i = 2
        while (f.exists()) { f = java.io.File(dir, safeName(a.name).let { n -> n.substringBeforeLast('.', n) + "-$i" + (if ('.' in n) "." + n.substringAfterLast('.') else "") }); i++ }
        f.writeBytes(bytes)
        val guest = "${uploadsDir(sessionId)}/${f.name}"
        return synthetic(
            "<attached-file path=\"$guest\" name=\"${a.name}\" mime=\"${a.mime}\" size=\"${bytes.size}\"/>\n" +
                "The user attached the file ${a.name} (${a.mime}, ${human(bytes.size.toLong())}). It is saved at $guest — open it with your tools when needed.",
        )
    }

    /**
     * Per-chat file root, outside the session project directory (CHAT_DIR). Because it is external to
     * the project, OpenCode's `external_directory` permission rules apply to it. Every session is denied
     * the whole root and then allowed only its own folder (last matching rule wins), so one chat can
     * never read another chat's files. Files are never moved or deleted when switching chats.
     */
    const val CHAT_FILES = "/root/chatfiles"

    /** This chat's isolated file root. */
    fun chatRoot(sessionId: String): String = "$CHAT_FILES/${safeName(sessionId)}"

    /** Guest folder for one chat's attachments. */
    fun uploadsDir(sessionId: String): String = "${chatRoot(sessionId)}/uploads"

    /** The same folder on the host (inside the rootfs). */
    fun uploadsHost(sessionId: String) = app.ocmobile.Graph.paths.host(uploadsDir(sessionId))

    /** Removes this chat's file root. Old locations from earlier builds are cleaned up too. */
    fun deleteUploads(sessionId: String) {
        runCatching { app.ocmobile.Graph.paths.host(chatRoot(sessionId)).deleteRecursively() }
        runCatching { app.ocmobile.Graph.paths.host("$CHAT_DIR/${safeName(sessionId)}").deleteRecursively() }
        runCatching { java.io.File(app.ocmobile.Graph.paths.rootfs.parentFile, "uploadvault/${safeName(sessionId)}").deleteRecursively() }
    }

    fun synthetic(text: String) = buildJsonObject { put("type", "text"); put("text", text); put("synthetic", true) }

    private fun safeName(n: String) = n.replace(UNSAFE_NAME, "_").ifBlank { "file" }

    fun isTextLike(mime: String, name: String) = mime.startsWith("text/") || mime in TEXT_MIMES ||
        name.substringAfterLast('.', "").lowercase() in TEXT_EXTS

    fun human(b: Long) = when { b >= 1_000_000 -> "%.1f MB".format(b / 1e6); b >= 1000 -> "%.0f KB".format(b / 1e3); else -> "$b B" }

    /** Synthetic part with an attachment → (name, path); null if it isn't an attachment. */
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
