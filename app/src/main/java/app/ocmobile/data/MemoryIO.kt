package app.ocmobile.data

import app.ocmobile.ui.components.MemGraph
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Memory in and out: a memory export (Claude's zip or a folder of .md notes) comes in as shelves,
 * the shelves go out as a zip; and the map of memory the agent gets when memory is too big to send
 * whole: each shelf with what it holds and which shelves it is linked to (the same links the graph
 * draws), so it opens only what it needs. The phone has the same file.
 */
object MemoryIO {
    /** Shelves that always go whole: who the user is and how they like things. */
    val core = setOf("about-me", "profile", "preferences", "me")

    /** A shelf's one-line description: from its front matter, or its first plain line. */
    fun describe(f: File): String {
        val lines = runCatching { f.readLines() }.getOrDefault(emptyList())
        lines.firstOrNull { it.startsWith("description:") }?.let { return it.substringAfter(':').trim().trim('"') }
        var inFront = lines.firstOrNull()?.trim() == "---"
        lines.drop(if (inFront) 1 else 0).forEach { l ->
            if (inFront) { if (l.trim() == "---") inFront = false; return@forEach }
            val t = l.trim().removePrefix("#").trim()
            if (t.isNotEmpty() && !t.startsWith("- ")) return t.take(120)
        }
        return ""
    }

    /** Every .md under [src] lands in [dest] as a shelf (folders flattened); facts merge into shelves that exist. */
    fun import(src: File, dest: File): Int {
        dest.mkdirs()
        var n = 0
        val added = ArrayList<File>()
        src.walkTopDown().filter { it.isFile && it.extension == "md" && it.name != "MEMORY.md" }.forEach { f ->
            val to = File(dest, f.name.lowercase().replace(Regex("[^a-z0-9а-яё._-]+"), "-"))
            val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
            if (!to.exists()) { to.writeText(text); added += to }
            else {
                val have = to.readLines().map { it.trim() }.toSet()
                val add = text.lines().filter { it.trim().startsWith("- ") && it.trim() !in have }
                if (add.isNotEmpty()) to.appendText((if (to.readText().endsWith("\n")) "" else "\n") + add.joinToString("\n") + "\n")
            }
            n++
        }
        addToIndex(dest, added)
        return n
    }

    /** New shelves get a line in MEMORY.md; the lines already there (maybe written by hand) stay as they are. */
    fun addToIndex(dir: File, shelves: List<File>) {
        val idx = File(dir, "MEMORY.md")
        if (!idx.exists()) return rebuildIndex(dir)
        val t = idx.readText()
        val lines = shelves.filter { !t.contains("(${it.name})") }.map { f ->
            "- [${f.nameWithoutExtension.replace('-', ' ').replaceFirstChar { it.uppercase() }}](${f.name}) — ${describe(f)}"
        }
        if (lines.isNotEmpty()) idx.writeText(t.replace("Nothing saved yet.\n", "").trimEnd() + "\n" + lines.joinToString("\n") + "\n")
    }

    fun rebuildIndex(dir: File) {
        val shelves = dir.listFiles { f -> f.isFile && f.extension == "md" && f.name != "MEMORY.md" }?.sortedBy { it.name }.orEmpty()
        File(dir, "MEMORY.md").writeText("# Memory index\n\n" + shelves.joinToString("\n") { f ->
            "- [${f.nameWithoutExtension.replace('-', ' ').replaceFirstChar { it.uppercase() }}](${f.name}) — ${describe(f)}"
        } + "\n")
    }

    fun unzip(input: InputStream, to: File) {
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val f = File(to, e.name)
                if (!e.isDirectory && f.canonicalPath.startsWith(to.canonicalPath)) { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                e = z.nextEntry
            }
        }
    }

    fun zip(dir: File, out: OutputStream) {
        ZipOutputStream(out).use { z ->
            dir.walkTopDown().filter { it.isFile && it.extension == "md" }.forEach { f ->
                z.putNextEntry(ZipEntry("memory/" + f.relativeTo(dir).path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }

    /**
     * The map: the core shelves whole, every other shelf as one line with its description, how many
     * facts it has and the shelves it's linked to. [path] is where the agent opens a shelf.
     */
    fun map(dir: File, path: String): String {
        val g = MemGraph.read(dir, null, mutableSetOf(), true)
        val shelfOf = HashMap<Int, Int>()
        var cur = -1
        g.nodes.forEachIndexed { i, n -> if (n.kind == 1) cur = i else if (n.kind == 2) shelfOf[i] = cur }
        val related = HashMap<Int, MutableSet<Int>>()
        g.links.forEach { (a, b) ->
            val sa = if (g.nodes[a].kind == 1) a else shelfOf[a]; val sb = if (g.nodes[b].kind == 1) b else shelfOf[b]
            if (sa != null && sb != null && sa != sb && g.nodes[a].kind != 0 && g.nodes[b].kind != 0) {
                related.getOrPut(sa) { mutableSetOf() } += sb; related.getOrPut(sb) { mutableSetOf() } += sa
            }
        }
        return buildString {
            append("## Map of your memory\nYour memory is large, so here is its map (the same graph the user sees): core shelves in full, the rest as one line each. ")
            append("When a topic comes up, open that shelf ($path/<file>) and follow its linked shelves to find connected facts; don't open shelves you don't need.\n")
            g.nodes.forEachIndexed { i, n ->
                if (n.kind != 1) return@forEachIndexed
                val f = n.file ?: return@forEachIndexed
                if (f.nameWithoutExtension in core) { append("\n### ${f.name}\n").append(runCatching { f.readText().trim() }.getOrDefault("")).append("\n"); return@forEachIndexed }
                val facts = g.neighbours(i).count { g.nodes[it].kind == 2 && shelfOf[it] == i }
                val links = related[i].orEmpty().mapNotNull { g.nodes[it].file?.nameWithoutExtension }.sorted().take(6)
                append("\n- ${f.name}: ${describe(f).ifBlank { "notes" }} ($facts facts)")
                if (links.isNotEmpty()) append(" → ").append(links.joinToString(", "))
            }
        }.trim()
    }
}
