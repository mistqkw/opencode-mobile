package app.ocmobile.data

import app.ocmobile.Graph

/**
 * Running out of a limit, said plainly: an error that is really "the free allowance is used up"
 * becomes a card with a way out (another free model, and the last message sent again), and when the
 * counted spending nears a limit the app says so once per window. The computer has the same.
 */
object Limits {
    private val re = Regex(
        "rate.?limit|quota|too many requests|\\b429\\b|insufficient|credit|free (tier|usage|plan)|usage limit|limit (reached|exceeded)|exceeded.*limit|daily limit",
        RegexOption.IGNORE_CASE,
    )

    /** Whether an error is about a used-up allowance rather than something broken. */
    fun isLimit(name: String?, message: String): Boolean = re.containsMatchIn(message) || name?.contains("RateLimit", true) == true

    /** Another free model to go on with: (provider, model, its name), never the current one. */
    fun nextFree(): Triple<String, String, String>? {
        val p = Graph.prefs
        return Graph.catalog.providers.value.flatMap { pr -> pr.models.values.map { pr.id to it } }
            .filter { (pid, m) -> !(pid == p.providerId && m.id == p.modelId) && Team.cost("$pid/${m.id}") == "free" }
            // the same provider first: its other free models often have their own allowance
            .sortedByDescending { it.first == p.providerId }
            .firstOrNull()?.let { (pid, m) -> Triple(pid, m.id, m.name.ifBlank { m.id }) }
    }

    /** After a reply: if a counted window is 85% spent, say it once for that window. */
    fun checkSpending() {
        val s = Graph.usage.snapshot.value
        val w = listOf(s.fiveHour to "5-hour", s.week to "weekly", s.month to "monthly").firstOrNull { it.first.limit > 0 && it.first.percent >= 85 } ?: return
        val key = w.second + "@" + (w.first.resetAt ?: 0)
        if (Graph.prefs.limitWarned == key) return
        Graph.prefs.limitWarned = key
        Graph.chats.toasts.tryEmit("${w.first.percent.toInt()}% of the ${w.second} limit is used. Free models keep working when it runs out.")
    }
}
