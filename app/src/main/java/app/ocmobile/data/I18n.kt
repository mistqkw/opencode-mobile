package app.ocmobile.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Russian and English. The app is written in English; in Russian every text shown through [t] is
 * looked up in i18n/ru.tsv (English, tab, Russian), and phrases with numbers or names in them go
 * through the patterns below. A text without a translation stays English. The computer has the same.
 */
object I18n {
    /** "system", "en" or "ru". Changing it repaints every text at once. */
    var lang by mutableStateOf("system")

    val ru: Boolean get() = lang == "ru" || (lang == "system" && java.util.Locale.getDefault().language == "ru")

    private val dict: Map<String, String> by lazy {
        runCatching { open().bufferedReader().readLines() }.getOrDefault(emptyList()).mapNotNull { l ->
            val i = l.indexOf('\t'); if (i <= 0) null else l.substring(0, i) to l.substring(i + 1)
        }.toMap()
    }

    private fun open(): java.io.InputStream = app.ocmobile.Graph.app.assets.open("i18n/ru.tsv")

    private val patterns = listOf(
        "Read (.+)" to "Прочитал $1", "Edited (.+)" to "Изменил $1", "Wrote (.+)" to "Записал $1",
        "Searched for (.+)" to "Искал $1", "Opened (.+)" to "Открыл $1", "Frees up in (.+)" to "Освободится через $1",
        "Imported (\\d+) notes into memory" to "В память добавлено заметок: $1", "Saved to (.+)" to "Сохранено в $1",
        "Couldn't import: (.+)" to "Не получилось импортировать: $1", "Couldn't save: (.+)" to "Не получилось сохранить: $1",
        "Updated to v(.+)" to "Обновлено до v$1", "(v\\S+) is out" to "Вышла $1",
        "(\\d+) helpers?" to "Помощников: $1", "(\\d+) on" to "$1 вкл.", "(\\d+) facts on (\\d+) shelves" to "$1 фактов на $2 полках",
        "level (\\d+)  ·  best (\\d+)" to "уровень $1  ·  рекорд $2", "best (\\d+)(.*)" to "рекорд $1$2", "rally (\\d+)" to "розыгрыш $1",
        "Working on (\\d+) chats?" to "В работе чатов: $1", "Yes! (\\d+) in a row" to "Да! $1 подряд",
        "Oops\\. That was (\\d+)\\. Tap to try again\\." to "Ой. Было $1. Нажми, чтобы ещё раз.",
        "(\\d+) min" to "$1 мин", "(\\d+) hr (\\d+) min" to "$1 ч $2 мин", "(\\d+) d (\\d+) hr" to "$1 д $2 ч",
        "Go on with (.+)" to "Продолжить на $1",
        "Version (.+)" to "Версия $1", "OpenCode (\\S+) is kept" to "Сохранена OpenCode $1", "OpenCode updated to (.+)" to "OpenCode обновлён до $1",
        "Downloading OpenCode (\\S+)…" to "Скачиваю OpenCode $1…", "OpenCode (\\S+) is waiting for the chats to finish" to "OpenCode $1 ждёт, пока чаты закончат работу",
        "Testing OpenCode (\\S+)…" to "Проверяю OpenCode $1…", "Switching to OpenCode (\\S+)…" to "Переключаюсь на OpenCode $1…", "Going back to OpenCode (\\S+)…" to "Возвращаю OpenCode $1…",
        "OpenCode (\\S+) stays off: (.+)" to "OpenCode $1 не включена: $2", "OpenCode (\\S+) did not start, back on the previous one: (.+)" to "OpenCode $1 не запустилась, вернул прошлую: $2",
        "Could not update OpenCode: (.+)" to "Не получилось обновить OpenCode: $1", "Could not go back: (.+)" to "Не получилось вернуть: $1",
        "it reports version (.+)" to "она называет себя версией $1", "its API changed \\(the app speaks OpenCode (.+)\\)" to "у неё другой API (приложение работает с OpenCode $1)",
        "the server did not answer(.*)" to "сервер не ответил$1",
        "(\\d+)% of the 5-hour limit is used\\. Free models keep working when it runs out\\." to "Потрачено $1% пятичасового лимита. Когда он кончится, бесплатные модели продолжат работать.",
        "(\\d+)% of the weekly limit is used\\. Free models keep working when it runs out\\." to "Потрачено $1% недельного лимита. Когда он кончится, бесплатные модели продолжат работать.",
        "(\\d+)% of the monthly limit is used\\. Free models keep working when it runs out\\." to "Потрачено $1% месячного лимита. Когда он кончится, бесплатные модели продолжат работать.",
        "Today (.+)" to "Сегодня $1", "14 days (.+)" to "За 14 дней $1", "Last 14 days (.+)" to "За 14 дней $1",
    ).map { (a, b) -> Regex("^$a$") to b }

    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>) = size > 3000
    }

    /** [s] in the chosen language. */
    fun t(s: String): String {
        if (!ru || s.length < 2) return s
        dict[s]?.let { return it }
        synchronized(cache) { cache[s]?.let { return it } }
        val out = patterns.firstNotNullOfOrNull { (re, to) -> re.find(s)?.let { m -> re.replace(s, to).takeIf { m.value.isNotEmpty() } } } ?: s
        synchronized(cache) { cache[s] = out }
        return out
    }
}
