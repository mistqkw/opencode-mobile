package app.ocmobile.data

import app.ocmobile.Graph
import java.io.File

/** Каталоги «обычных» чатов и памяти внутри окружения (proot). Всё, что вне CHAT_DIR, — вкладка Code. */
const val CHAT_DIR = "/root/chats"
const val MEMORY_DIR = "/root/memory"

/**
 * Память: обычные markdown-файлы в /root/memory — «полки» по темам и индекс MEMORY.md.
 * Агенту про неё рассказывает блок в глобальном AGENTS.md, который приложение ведёт само.
 */
object Memory {
    private const val START = "<!-- ocm-memory:start -->"
    private const val END = "<!-- ocm-memory:end -->"

    val defaultPrompt = """
# Memory

You have a persistent memory: plain markdown files in $MEMORY_DIR. It survives between conversations.

- At the start of a conversation read $MEMORY_DIR/MEMORY.md (the index) and the shelf files that fit the request. Use what you learn quietly; do not announce that you read it.
- When the user tells you something lasting — preferences, people, projects, goals, routines, decisions, facts about their life or work — or asks you to remember something, save it. Do not save one-off details, and do not save sensitive things unless asked.
- Keep memory sorted into shelves: one file per topic, for example about-me.md, work.md, projects.md, people.md, routines.md, health.md, ideas.md. Put each fact on the shelf that fits best, create a new shelf when none fits, merge duplicates, correct outdated facts, remove wrong ones.
- Every shelf starts with a one-line description of what belongs on it. MEMORY.md stays a short index, one line per shelf: "- [Title](file.md) — what is inside".
- If inbox.md has notes, sort them onto the right shelves and clear it.
- When asked to forget something, delete it. After changing memory, say in one short line what you saved or removed.
""".trim()

    fun dir(): File = Graph.paths.host(MEMORY_DIR)

    fun shelves(): List<File> =
        dir().listFiles { f -> f.isFile && f.extension == "md" && f.name != "MEMORY.md" }?.sortedBy { it.name }.orEmpty()

    fun prompt(): String {
        val custom = Graph.prefs.memoryCustom.trim()
        return if (custom.isEmpty()) defaultPrompt else defaultPrompt + "\n\n## The user's own instructions for memory\n" + custom
    }

    /** Создаёт каталоги и приводит блок памяти в AGENTS.md в соответствие с настройками. */
    fun sync() {
        runCatching {
            Graph.paths.host(CHAT_DIR).mkdirs()
            dir().mkdirs()
            val index = File(dir(), "MEMORY.md")
            if (!index.exists()) index.writeText("# Memory index\n\nNothing saved yet.\n")
            val f = Graph.paths.globalAgentsMd
            f.parentFile?.mkdirs()
            val old = if (f.exists()) f.readText() else ""
            val a = old.indexOf(START)
            val b = old.indexOf(END)
            val without = if (a >= 0 && b > a) (old.substring(0, a) + old.substring(b + END.length)).trim() else old.trim()
            val block = if (Graph.prefs.memoryEnabled) "$START\n${prompt()}\n$END" else ""
            val text = listOf(without, block).filter { it.isNotEmpty() }.joinToString("\n\n")
            if (text != old.trim()) f.writeText(text + "\n")
        }
    }

    /** Правила пользователя — AGENTS.md без служебного блока памяти (его ведёт приложение). */
    fun userRules(): String {
        val f = Graph.paths.globalAgentsMd
        val old = runCatching { if (f.exists()) f.readText() else "" }.getOrDefault("")
        val a = old.indexOf(START); val b = old.indexOf(END)
        return (if (a >= 0 && b > a) old.substring(0, a) + old.substring(b + END.length) else old).trim()
    }

    fun saveUserRules(text: String) {
        val f = Graph.paths.globalAgentsMd
        f.parentFile?.mkdirs()
        f.writeText(text.trim() + "\n")
        sync()
    }

    fun addNote(text: String) {
        runCatching {
            dir().mkdirs()
            val f = File(dir(), "inbox.md")
            val head = if (f.exists()) "" else "Notes added by hand, waiting to be sorted onto shelves.\n\n"
            f.appendText(head + "- " + text.trim().replace("\n", " ") + "\n")
        }
    }
}
