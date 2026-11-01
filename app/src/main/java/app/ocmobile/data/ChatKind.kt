package app.ocmobile.data

import app.ocmobile.api.Session

/**
 * What kind of chat it is, from the title and folder without loading messages: code, task, search, text or just talk.
 * The drawer icon comes from here.
 */
enum class ChatKind { Code, Task, Research, Writing, Talk;
    companion object {
        private val code = Regex("""(?i)\b(code|bug|fix|script|build|app|repo|git|kotlin|python|java|rust|react|refactor|error|crash|function|api|deploy|test|bash|command|server|docker|sql|css|html)\w*|баг|код|скрипт|сборк|ошибк|функци|сервер|репозитор|прилож""")
        // a file name in the title: count.sh, main.py, App.kt
        private val file = Regex("""\b[\w-]+\.(sh|py|kt|kts|js|ts|tsx|jsx|java|go|rs|c|cpp|h|rb|php|swift|json|ya?ml|toml|md|sql|css|html|gradle)\b""")
        private val task = Regex("""(?i)\b(plan|schedul|remind|todo|list|organi[sz]|task|checklist|steps|routine|alarm)\w*|задач|план|напомн|список|распис|будильн""")
        private val research = Regex("""(?i)\b(search|find|compare|research|news|explain|what is|how to|why|review|summar|check)\w*|найди|сравн|что такое|почему|объясн|поиск|новост""")
        private val writing = Regex("""(?i)\b(write|essay|post|letter|email|story|poem|translat|lyrics|caption|article|reply)\w*|напиши|текст|пост|письм|перев|стих|статья""")

        fun of(s: Session): ChatKind {
            if (s.directory.isNotBlank() && s.directory != CHAT_DIR) return Code
            // the agent changed files: that's code work, whatever the chat is called
            val sm = s.summary
            if (sm != null && ((sm["files"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0) +
                ((sm["additions"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0) > 0) return Code
            val t = s.title
            if (file.containsMatchIn(t)) return Code
            return when {
                code.containsMatchIn(t) -> Code
                task.containsMatchIn(t) -> Task
                writing.containsMatchIn(t) -> Writing
                research.containsMatchIn(t) -> Research
                else -> Talk
            }
        }
    }
}
