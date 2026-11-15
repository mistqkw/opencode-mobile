package app.ocmobile.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A chat's work laid out in time: your messages, each reply with its model and cost, and inside it
 * every thought, command and helper with when it started and how long it took. Built from the
 * messages already loaded, nothing is asked from the server. The computer has the same file.
 */
object Timeline {
    enum class Kind { You, Reply, Thought, Tool, Helper }

    data class Step(val at: Long, val who: String, val what: String, val ms: Long?, val kind: Kind, val cost: Double = 0.0, val running: Boolean = false)

    private fun JsonObject.o(k: String) = this[k] as? JsonObject
    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content
    private fun JsonObject.l(k: String) = (this[k] as? JsonPrimitive)?.content?.toLongOrNull()

    fun of(messages: List<ChatMessage>, helperName: (String?) -> String): List<Step> {
        val out = ArrayList<Step>()
        messages.forEach { m ->
            val i = m.info
            if (i.isUser) {
                val text = m.parts.filter { it.type == "text" && it.raw.s("synthetic") != "true" }.joinToString(" ") { it.text }.trim()
                out += Step(i.time.created, "You", text.take(90).ifBlank { "Attached files" }, null, Kind.You)
                return@forEach
            }
            out += Step(i.time.created, i.modelName ?: "Reply", "Reply", i.time.completed?.let { it - i.time.created }, Kind.Reply, i.cost ?: 0.0, i.time.completed == null)
            m.parts.forEach { p ->
                val r = p.raw
                when (p.type) {
                    "reasoning" -> {
                        val t = r.o("time"); val start = t?.l("start") ?: return@forEach
                        out += Step(start, "", p.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("**")?.removeSuffix("**")?.take(80) ?: "Thinking", t.l("end")?.let { it - start }, Kind.Thought)
                    }
                    "tool" -> {
                        val st = r.o("state") ?: return@forEach
                        val t = st.o("time"); val start = t?.l("start") ?: return@forEach
                        val inp = st.o("input")
                        val tool = r.s("tool").orEmpty()
                        val running = st.s("status") == "running" || st.s("status") == "pending"
                        val dur = t.l("end")?.let { it - start }
                        if (tool == "task") out += Step(start, helperName(inp?.s("subagent_type")), inp?.s("description") ?: "A task", dur, Kind.Helper, running = running)
                        else out += Step(start, "", st.s("title")?.takeIf { it.isNotBlank() } ?: (tool + (inp?.s("filePath")?.substringAfterLast('/')?.let { " $it" } ?: "")), dur, Kind.Tool, running = running)
                    }
                }
            }
        }
        return out.sortedBy { it.at }
    }

    fun duration(ms: Long): String = when {
        ms < 1000 -> "${ms} ms"
        ms < 60_000 -> String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)
        else -> "${ms / 60_000} min ${(ms / 1000) % 60} s"
    }
}
