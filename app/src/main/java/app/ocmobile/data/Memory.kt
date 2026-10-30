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

- Its current contents are already loaded below under "What you remember". Do NOT run ls, cat or read on $MEMORY_DIR just to recall something — answer from what is below right away. Open a shelf file only when the section says it was too long to include.
- Use what you remember quietly; do not announce that you looked it up.
- IMPORTANT: remembering means actually writing to a file with your file tools (write/edit/bash). Never just say "I will remember" — save it in the same turn, before you reply. If the shelf file does not exist, create it, then add its line to MEMORY.md.
- When the user tells you something lasting — preferences, people, projects, goals, routines, decisions, facts about their life or work — or asks you to remember something, save it. Do not save one-off details, and do not save sensitive things unless asked.
- Keep memory sorted into shelves: one file per topic, for example about-me.md, work.md, projects.md, people.md, routines.md, health.md, ideas.md. Put each fact on the shelf that fits best, create a new shelf when none fits, merge duplicates, correct outdated facts, remove wrong ones.
- Every shelf starts with a one-line description of what belongs on it. MEMORY.md stays a short index, one line per shelf: "- [Title](file.md) — what is inside".
- If inbox.md has notes, sort them onto the right shelves and clear it.
- When asked to forget something, delete it. After changing memory, say in one short line what you saved or removed.
""".trim()

    /** Сколько текста памяти вкладывать в инструкции целиком; длиннее — только индекс и начала полок. */
    private const val INLINE_LIMIT = 24_000
    private const val SHELF_LIMIT = 4_000

    /**
     * Содержимое памяти прямо в инструкциях: без этого агент в начале каждого чата тратит
     * два лишних круга (ls/cat индекса, потом cat полок), а это ~4 с на бесплатной модели.
     */
    fun snapshot(): String {
        val index = runCatching { File(dir(), "MEMORY.md").readText().trim() }.getOrDefault("")
        val files = shelves().map { it.name to runCatching { it.readText().trim() }.getOrDefault("") }.filter { it.second.isNotEmpty() }
        val whole = files.sumOf { it.second.length } <= INLINE_LIMIT
        return buildString {
            append("## What you remember\n\n")
            append("### MEMORY.md\n").append(index.ifEmpty { "(empty)" }).append("\n")
            if (files.isEmpty()) append("\nNo shelves yet.\n")
            files.forEach { (name, text) ->
                append("\n### ").append(name).append("\n")
                if (whole || text.length <= SHELF_LIMIT) append(text).append("\n")
                else append(text.take(SHELF_LIMIT)).append("\n… (too long to include — open $MEMORY_DIR/").append(name).append(" for the rest)\n")
            }
        }.trim()
    }

    /** Всегда в AGENTS.md: как отдать пользователю файл и где лежат его вложения. */
    val filesPrompt = """
# Files and the user

The user talks to you from a phone app. It cannot see your terminal or your disk, only your replies.
- When you make or find a file the user should get — audio (mp3, wav), video (mp4), images, documents, archives, any format — save it (for example in /root/outbox) and put its absolute path in your reply as a markdown link on its own line: [song.mp3](/root/outbox/song.mp3). The app turns that link into a card the user can play, open, save or share. Never paste binary data or base64 into the chat.
- Files the user attaches are saved in /root/uploads; the message tells you the exact path. Open them with your tools.
""".trim()

    fun dir(): File = Graph.paths.host(MEMORY_DIR)

    /** Подробные заметки по проектам: в инструкции не вкладываются, агент открывает их по ссылке с полки Projects. */
    fun projectNotes(): List<File> =
        File(dir(), "projects").listFiles { f -> f.isFile && f.extension == "md" }?.sortedBy { it.name }.orEmpty()

    fun shelves(): List<File> =
        dir().listFiles { f -> f.isFile && f.extension == "md" && f.name != "MEMORY.md" }?.sortedBy { it.name }.orEmpty()

    /** Темы полок, которые можно выбрать в настройках: файл, название, что на ней. */
    val topics = listOf(
        Triple("about-me", "About me", "name, city, language, how to talk to me"),
        Triple("work", "Work", "job, schedule, tools, colleagues"),
        Triple("projects", "Projects", "what I build and where it stands"),
        Triple("people", "People", "family, friends, who is who"),
        Triple("routines", "Routines", "habits, daily plans, reminders"),
        Triple("health", "Health", "sleep, sport, food, wellbeing"),
        Triple("ideas", "Ideas", "plans, wishes, things to try"),
        Triple("music", "Music", "taste, tracks, gear"),
    )

    fun prompt(): String {
        val p = Graph.prefs
        val extra = buildList {
            if (!p.memoryAuto) add("- Save to memory ONLY when the user explicitly asks you to remember something. Otherwise only read it.")
            val t = p.memoryTopics.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (t.isNotEmpty()) add("- Keep only these shelves: " + t.joinToString(", ") { "$it.md" } + ". Do not create other shelves; put anything else in the closest one or skip it.")
        }
        val base = if (extra.isEmpty()) defaultPrompt else defaultPrompt + "\n\n## Settings chosen by the user\n" + extra.joinToString("\n")
        val custom = p.memoryCustom.trim()
        return if (custom.isEmpty()) base else base + "\n\n## The user's own instructions for memory\n" + custom
    }

    /** Факты на полке — строки списка «- …». */
    fun facts(f: File): List<String> = runCatching { f.readLines() }.getOrDefault(emptyList())
        .filter { it.trimStart().startsWith("- ") }.map { it.trimStart().removePrefix("- ").trim() }

    fun removeFact(f: File, fact: String) {
        runCatching {
            val lines = f.readLines().toMutableList()
            val i = lines.indexOfFirst { it.trimStart().removePrefix("- ").trim() == fact && it.trimStart().startsWith("- ") }
            if (i >= 0) { lines.removeAt(i); f.writeText(lines.joinToString("\n") + "\n") }
            sync()
        }
    }

    fun addFact(f: File, fact: String) {
        runCatching {
            val head = if (f.exists()) "" else "# ${f.nameWithoutExtension.replace('-', ' ').replaceFirstChar { it.uppercase() }}\n\n"
            val t = if (f.exists()) f.readText() else ""
            f.writeText(head + t.trimEnd() + (if (t.isBlank()) "" else "\n") + "- " + fact.trim().replace("\n", " ") + "\n")
            if (f.name != "MEMORY.md") {
                val idx = File(dir(), "MEMORY.md")
                val it = if (idx.exists()) idx.readText() else "# Memory index\n\n"
                if (!it.contains("(${f.name})")) idx.writeText(it.replace("Nothing saved yet.\n", "").trimEnd() + "\n- [${f.nameWithoutExtension.replace('-', ' ').replaceFirstChar { c -> c.uppercase() }}](${f.name})\n")
            }
            sync()
        }
    }

    /** Весь текст памяти одним файлом — чтобы поделиться или сохранить. */
    fun exportText(): String = buildString {
        append(runCatching { File(dir(), "MEMORY.md").readText().trim() }.getOrDefault("")).append("\n\n")
        (shelves() + projectNotes()).forEach { append("---\n\n").append(runCatching { it.readText().trim() }.getOrDefault("")).append("\n\n") }
    }.trim()

    /** Создаёт каталоги и приводит блок памяти в AGENTS.md в соответствие с настройками и файлами памяти. Пишет, только если что-то изменилось. */
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
            Graph.paths.host("/root/outbox").mkdirs()
            val body = listOfNotNull(filesPrompt, Skills.prompt, GitHub.prompt, prompt().takeIf { Graph.prefs.memoryEnabled }, snapshot().takeIf { Graph.prefs.memoryEnabled }).joinToString("\n\n")
            val block = "$START\n$body\n$END"
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
