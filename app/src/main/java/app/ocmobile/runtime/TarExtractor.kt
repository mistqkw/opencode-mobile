package app.ocmobile.runtime

import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Распаковка .tar.gz без внешних утилит: понимает ustar, GNU long names и pax,
 * симлинки и жёсткие ссылки. Права доступа сохраняются, но у владельца (приложения)
 * всегда остаётся доступ — иначе потом не удалить и не обновить файлы.
 */
object TarExtractor {
    class Result(val files: Int, val skipped: Int)

    fun extract(archive: File, dest: File, onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }): Result {
        dest.mkdirs()
        val destCanon = dest.canonicalFile
        val total = archive.length()
        val counting = CountingStream(FileInputStream(archive).buffered(1 shl 16))
        val dirModes = ArrayList<Pair<File, Int>>()
        var files = 0
        var skipped = 0
        var lastReport = 0L

        GZIPInputStream(counting, 1 shl 16).use { gz ->
            val hdr = ByteArray(512)
            var longName: String? = null
            var longLink: String? = null
            var paxPath: String? = null
            var paxLink: String? = null

            while (true) {
                if (!readFully(gz, hdr)) break
                if (hdr.all { it == 0.toByte() }) break

                var name = cstr(hdr, 0, 100)
                val mode = octal(hdr, 100, 8).toInt()
                val size = octal(hdr, 124, 12)
                val type = hdr[156].toInt().toChar()
                var link = cstr(hdr, 157, 100)
                if (cstr(hdr, 257, 5) == "ustar") {
                    val prefix = cstr(hdr, 345, 155)
                    if (prefix.isNotEmpty()) name = "$prefix/$name"
                }

                when (type) {
                    'L' -> { longName = readString(gz, size); continue }
                    'K' -> { longLink = readString(gz, size); continue }
                    'x' -> {
                        for ((k, v) in parsePax(readString(gz, size))) {
                            if (k == "path") paxPath = v
                            if (k == "linkpath") paxLink = v
                        }
                        continue
                    }
                    'g' -> { skip(gz, size + pad(size)); continue }
                }
                (paxPath ?: longName)?.let { name = it }
                (paxLink ?: longLink)?.let { link = it }
                longName = null; longLink = null; paxPath = null; paxLink = null

                val rel = name.removePrefix("./").trimStart('/').trimEnd('/')
                if (rel.isEmpty() || rel.split('/').any { it == ".." }) {
                    skip(gz, size + pad(size)); skipped++; continue
                }
                val target = File(dest, rel)
                val parent = target.parentFile
                parent?.mkdirs()
                if (parent != null && !parent.canonicalFile.path.startsWith(destCanon.path)) {
                    skip(gz, size + pad(size)); skipped++; continue
                }

                when (type) {
                    '5' -> {
                        target.mkdirs()
                        dirModes += target to mode
                    }
                    '2' -> {
                        target.delete()
                        Os.symlink(link, target.path)
                        files++
                    }
                    '1' -> {
                        val src = File(dest, link.trimStart('/').removePrefix("./"))
                        target.delete()
                        if (src.exists()) src.copyTo(target, overwrite = true)
                        Os.chmod(target.path, (mode and 0xFFF and 0x1FF) or 0x180)
                        files++
                    }
                    '0', '\u0000', '7' -> {
                        target.delete()
                        target.outputStream().buffered(1 shl 16).use { out ->
                            var left = size
                            val buf = ByteArray(1 shl 16)
                            while (left > 0) {
                                val n = gz.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (n < 0) error("tar: unexpected end of archive in $rel")
                                out.write(buf, 0, n)
                                left -= n
                            }
                        }
                        skip(gz, pad(size))
                        Os.chmod(target.path, (mode and 0x1FF) or 0x180)
                        files++
                    }
                    else -> { skip(gz, size + pad(size)); skipped++ } // устройства, fifo
                }

                val now = counting.count
                if (now - lastReport > 256 * 1024) { lastReport = now; onProgress(now, total) }
            }
        }
        // Права на каталоги — в конце, иначе закрытый каталог не даст положить в него файлы.
        for ((dir, mode) in dirModes.asReversed()) {
            runCatching { Os.chmod(dir.path, (mode and 0x1FF) or 0x1C0) }
        }
        onProgress(total, total)
        return Result(files, skipped)
    }

    private fun pad(size: Long) = (512 - size % 512) % 512

    private fun skip(i: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(1 shl 14)
        while (left > 0) {
            val r = i.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) return
            left -= r
        }
    }

    private fun readString(i: InputStream, size: Long): String {
        val bytes = ByteArray(size.toInt())
        if (!readFully(i, bytes)) error("tar: truncated record")
        skip(i, pad(size))
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000', '\n')
    }

    private fun parsePax(s: String): List<Pair<String, String>> =
        s.split('\n').mapNotNull { rec ->
            val sp = rec.indexOf(' ')
            val kv = if (sp >= 0) rec.substring(sp + 1) else return@mapNotNull null
            val eq = kv.indexOf('=')
            if (eq < 0) null else kv.substring(0, eq) to kv.substring(eq + 1)
        }

    private fun readFully(i: InputStream, b: ByteArray): Boolean {
        var off = 0
        while (off < b.size) {
            val r = i.read(b, off, b.size - off)
            if (r < 0) return off != 0 && error("tar: truncated header")
            off += r
        }
        return true
    }

    private fun cstr(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        if (b[off].toInt() and 0x80 != 0) { // base-256 для больших значений
            var v = (b[off].toInt() and 0x7F).toLong()
            for (k in 1 until len) v = (v shl 8) or (b[off + k].toLong() and 0xFF)
            return v
        }
        var v = 0L
        var started = false
        for (k in off until off + len) {
            val c = b[k].toInt().toChar()
            if (c in '0'..'7') { v = v * 8 + (c - '0'); started = true }
            else if (started || (c != ' ' && c != '\u0000')) break
        }
        return v
    }

    private class CountingStream(i: InputStream) : FilterInputStream(i) {
        var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }
}
