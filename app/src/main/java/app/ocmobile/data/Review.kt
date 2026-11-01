package app.ocmobile.data

import app.ocmobile.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reviewing the agent's edits per file: "Keep" just marks the file as reviewed,
 * "Undo" returns the file to how it was before the edits. If the server gave the old content (before), write it,
 * otherwise revert the patch: each @@ block is found in the file and changed back. Only for the server on the phone.
 */
object Review {
    /** A file in Ubuntu by its path from the diff: absolute or relative to the session folder. */
    private fun target(directory: String?, file: String): File =
        Graph.paths.host(if (file.startsWith("/")) file else (directory ?: "/root").trimEnd('/') + "/" + file)

    suspend fun undo(directory: String?, file: String, before: String?, patch: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val f = target(directory, file)
            when {
                before != null -> if (before.isEmpty() && patchCreates(patch)) f.delete() else f.writeText(before)
                patch != null -> {
                    if (patchCreates(patch)) { f.delete(); return@runCatching }
                    f.writeText(reverse(f.readText(), patch))
                }
                else -> error("No change data for $file")
            }
        }
    }

    private fun patchCreates(patch: String?) = patch != null && patch.lineSequence().any { it.startsWith("--- /dev/null") }

    /** Reverts a unified diff: finds the blocks (+ and context) in the "after" text and puts "before" (- and context) in their place. */
    fun reverse(after: String, patch: String): String {
        var lines = after.split("\n").toMutableList()
        val hunks = ArrayList<Pair<List<String>, List<String>>>()      // (as it is now, as it was)
        var cur: Pair<MutableList<String>, MutableList<String>>? = null
        for (l in patch.lines()) {
            when {
                l.startsWith("@@") -> { cur?.let { hunks += it }; cur = mutableListOf<String>() to mutableListOf() }
                cur == null || l.startsWith("+++") || l.startsWith("---") || l.startsWith("\\") -> {}
                l.startsWith("+") -> cur!!.first += l.substring(1)
                l.startsWith("-") -> cur!!.second += l.substring(1)
                else -> { val t = if (l.startsWith(" ")) l.substring(1) else l; cur!!.first += t; cur!!.second += t }
            }
        }
        cur?.let { hunks += it }
        // from the end, so line numbers above don't shift
        for ((now, was) in hunks.reversed()) {
            val at = find(lines, now) ?: error("The file changed since, can't undo safely")
            lines = (lines.subList(0, at) + was + lines.subList(at + now.size, lines.size)).toMutableList()
        }
        return lines.joinToString("\n")
    }

    private fun find(lines: List<String>, block: List<String>): Int? {
        if (block.isEmpty()) return null
        for (i in lines.size - block.size downTo 0) if (lines.subList(i, i + block.size) == block) return i
        return null
    }
}
