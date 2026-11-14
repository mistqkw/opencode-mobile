package app.ocmobile.i18n

import app.ocmobile.Graph

/**
 * Interface language. It lives in Prefs, so reading it from a composable
 * subscribes that composable to language changes.
 */
object Lang {
    const val EN = "en"
    const val RU = "ru"

    val code: String get() = Graph.prefs.language

    val isRu: Boolean get() = code == RU

    fun set(value: String) {
        Graph.prefs.language = if (value == RU) RU else EN
    }

    /** Pick the English or the Russian variant of a string. */
    fun pick(en: String, ru: String): String = if (isRu) ru else en
}

/** Placeholder for a value filled in later by [tr] or [trf]. */
private const val SLOT = "%s"

/**
 * Dictionaries whose keys contain [SLOT] become matchers: the literal parts are
 * compared, each placeholder takes whatever stands in its place, and the value
 * is put back with the captured parts. Longest keys first, so the most specific
 * template wins.
 */
private val templates: List<Pair<Regex, String>> by lazy {
    Ru.dict.entries.filter { SLOT in it.key }
        .sortedByDescending { it.key.length }
        .map { (en, ru) -> matcher(en) to ru }
}

private fun matcher(template: String): Regex {
    val body = template.split(SLOT).joinToString("(.+?)") { Regex.escape(it) }
    return Regex("^$body$")
}

/** Translate a UI string through the dictionary; unknown strings pass through. */
fun tr(text: String): String {
    if (!Lang.isRu || text.length < 2) return text
    Ru.dict[text]?.let { return it }
    for ((re, ru) in templates) {
        val m = re.matchEntire(text) ?: continue
        var i = 1
        return SLOT.toRegex().replace(ru) { m.groupValues[i++] }
    }
    return text
}

/** Translate a template such as `"%s files"`, then fill its placeholders. */
fun trf(template: String, vararg args: Any?): String {
    var out = tr(template)
    for (a in args) out = out.replaceFirst(SLOT, a.toString())
    return out
}

/**
 * Russian plural form: 1 → one, 2–4 → few, 5+ and 0 → many.
 * @param one form for 1, 21, 31…
 * @param few form for 2–4, 22–24…
 * @param many form for 0, 5–20, 25–30…
 */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}

/** Same as [plural], with the number in front. */
fun count(n: Int, one: String, few: String, many: String): String = "$n " + plural(n, one, few, many)
