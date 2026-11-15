package app.ocmobile.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.ocmobile.Graph
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Agent team. Helpers are OpenCode subagents (the agent section of the config, mode = subagent);
 * the main agent hands them parts of the task with the task tool, and they work in parallel in child sessions.
 *  - manual: the user sets the lineup: name, role and model of each;
 *  - auto: the user gives a set of models and a cap, the main agent decides how many helpers
 *    to start and on which model (none for a simple task).
 * All helpers are named team-… so they can be found and removed from the config.
 */
object Team {
    @Serializable
    data class Member(val name: String, val role: String, val provider: String = "", val model: String = "", val on: Boolean = true)

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs get() = Graph.prefs

    val mode: String get() = prefs.teamMode

    /** "Add sub-agent" from the + sheet: the Sub-agents screen opens with the new-helper form. */
    var addRequested = false

    /** A helper just created: its cloud flies into the row above the input once. */
    var justAdded by androidx.compose.runtime.mutableStateOf<String?>(null)
    val on: Boolean get() = mode != "off"

    var members: List<Member>
        get() = runCatching { json.decodeFromString<List<Member>>(prefs.teamMembers) }.getOrDefault(emptyList())
        set(v) { prefs.teamMembers = json.encodeToString(v) }

    /** Models for auto mode: provider/model; empty means the current model. */
    var pool: List<String>
        get() = prefs.teamPool.split(',').map { it.trim() }.filter { it.contains('/') }
        set(v) { prefs.teamPool = v.joinToString(",") }

    val max: Int get() = prefs.teamMax.toIntOrNull()?.coerceIn(1, 6) ?: 3

    /** Helpers that work without asking first ("Always" in Team or on the OK card), as provider/model. */
    val always: Set<String> get() = prefs.teamAlways.lines().filter { it.isNotBlank() }.toSet()
    fun setAlways(ref: String, on: Boolean) { prefs.teamAlways = (if (on) always + ref else always - ref).joinToString("\n") }

    /** The helper name that runs on [ref], if one does. */
    fun helperFor(ref: String): String? = when (mode) {
        "manual" -> members.firstOrNull { "${it.provider}/${it.model}" == ref }?.let { slug(it.name) }
        "auto" -> slug(label(ref)).takeIf { ref in pool.ifEmpty { listOf("${prefs.providerId}/${prefs.modelId}") } }
        else -> null
    }

    /** The model a helper runs on, as provider/model, from its config entry. */
    fun refOf(agent: String): String? = when (mode) {
        "manual" -> members.firstOrNull { slug(it.name) == agent }?.let { m -> if (m.provider.isNotBlank() && m.model.isNotBlank()) "${m.provider}/${m.model}" else null }
        "auto" -> pool.ifEmpty { listOf("${prefs.providerId}/${prefs.modelId}") }.firstOrNull { slug(label(it)) == agent }
        else -> null
    }

    /** A helper's name for people: the member's name, or its model. */
    fun display(agent: String?): String = when {
        agent.isNullOrBlank() -> "Helper"
        agent == "general" -> "General helper"
        agent == "explore" -> "Explorer"
        else -> members.firstOrNull { slug(it.name) == agent }?.name ?: refOf(agent)?.let(::label) ?: agent.removePrefix("team-")
    }

    /** What a model costs: free, part of a subscription, or the price per million tokens. */
    fun cost(ref: String?): String {
        if (ref == null) return "chat model"
        val (p, m) = ref.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val c = Graph.catalog.model(p, m)?.cost
        fun num(k: String) = (c?.get(k) as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
        val inp = num("input"); val out = num("output")
        fun f(d: Double) = if (d >= 10) String.format(java.util.Locale.US, "%.0f", d) else String.format(java.util.Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')
        return when {
            m.endsWith("-free") || p == "pollinations" -> "free"
            p == "opencode-go" -> "subscription: OpenCode Go"
            p == "github-copilot" || p == "copilot-chat" -> "subscription: GitHub Copilot"
            inp == 0.0 && out == 0.0 -> "free"
            else -> "paid: \$${f(inp)} in, \$${f(out)} out per 1M tokens"
        }
    }

    /** What is left of the subscription, as the main agent sees it. */
    private fun budget(): String? {
        val s = Graph.usage.snapshot.value
        val w = listOf(s.fiveHour, s.week, s.month).filter { it.limit > 0 }
        if (w.isEmpty()) return null
        return "Subscription spent so far (counted by the app): " + w.joinToString("; ") {
            "${it.label} \$${String.format(java.util.Locale.US, "%.2f", it.used)} of \$${String.format(java.util.Locale.US, "%.0f", it.limit)}"
        } + ". When a window is nearly used up, prefer free helpers."
    }

    fun slug(name: String) = "team-" + name.lowercase().replace(Regex("[^a-z0-9а-яё]+"), "-").trim('-').ifBlank { "helper" }

    private fun label(ref: String): String {
        val (p, m) = ref.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return Graph.catalog.model(p, m)?.name?.ifBlank { null } ?: m
    }

    /** Helpers that should be in the config now: name → agent description. */
    private fun agents(): Map<String, JsonObject> = when (mode) {
        "manual" -> members.filter { it.on }.associate { m ->
            slug(m.name) to buildJsonObject {
                put("mode", "subagent")
                put("description", "${m.name}: ${m.role}".take(300))
                if (m.provider.isNotBlank() && m.model.isNotBlank()) put("model", "${m.provider}/${m.model}")
                put("prompt", "You are ${m.name}, a member of a small team led by the main agent. Your role: ${m.role}. " +
                    "Do only the part you were given, do it well, and finish with a short report of what you did and what you found.")
            }
        }
        "auto" -> pool.ifEmpty { listOf("${prefs.providerId}/${prefs.modelId}") }.associate { ref ->
            slug(label(ref)) to buildJsonObject {
                put("mode", "subagent")
                put("description", "General helper on ${label(ref)}. Give it a self-contained part of the task.")
                put("model", ref)
                put("prompt", "You are a helper in a team led by the main agent. Do exactly the part you were given and finish with a short report.")
            }
        }
        else -> emptyMap()
    }

    /**
     * Writes the helpers to the shared OpenCode config and disables old team-… ones. Called when the mode or lineup changes
     * and before sending, if the team is on.
     */
    @Volatile private var synced: String? = null

    suspend fun sync(force: Boolean = false) {
        readFile()
        writeFile()
        val want = agents()
        // the same lineup is already written: leave the config alone, OpenCode rereads it on every edit
        val key = want.toString()
        if (!force && key == synced) return
        val have = runCatching { Graph.api.agents().map { it.name }.filter { it.startsWith("team-") } }.getOrDefault(emptyList())
        val patch = buildJsonObject {
            put("agent", buildJsonObject {
                want.forEach { (k, v) -> put(k, v) }
                (have - want.keys).forEach { put(it, buildJsonObject { put("disable", true) }) }
            })
        }
        // shared config: chats and projects live in different folders, helpers are needed in all of them
        if (runCatching { Graph.api.patchGlobalConfig(patch) }.isSuccess) synced = key
    }

    // ---------- the team file: the main agent can change its helpers itself ----------

    /** Where the agent sees the lineup (inside the environment) and where the app reads it back. */
    const val FILE = "/root/.config/opencode/cl0udly-team.json"
    private val file get() = Graph.paths.host(FILE)
    private val pretty = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    private data class Lineup(val mode: String, val max: Int, val members: List<FileMember>, val pool: List<String>)
    @Serializable
    private data class FileMember(val name: String, val role: String, val model: String = "", val on: Boolean = true)

    private fun lineupText() = pretty.encodeToString(Lineup(
        mode, max,
        members.map { FileMember(it.name, it.role, if (it.model.isBlank()) "" else "${it.provider}/${it.model}", it.on) },
        pool,
    ))

    /** The agent edited the file since the app last wrote it: take its lineup. */
    fun readFile() {
        if (Graph.devices.isRemote) return
        val text = runCatching { file.readText() }.getOrNull() ?: return
        if (text == prefs.teamFileLast) return
        runCatching {
            val l = json.decodeFromString<Lineup>(text)
            if (l.mode in setOf("off", "manual", "auto")) prefs.teamMode = l.mode
            prefs.teamMax = l.max.coerceIn(1, 6).toString()
            members = l.members.filter { it.name.isNotBlank() }.map { m ->
                val (p, id) = m.model.split('/', limit = 2).let { if (it.size == 2) it[0] to it[1] else "" to "" }
                Member(m.name.trim(), m.role.trim(), p, id, m.on)
            }
            pool = l.pool.filter { it.contains('/') }
        }
    }

    fun writeFile() {
        if (Graph.devices.isRemote) return
        val text = lineupText()
        if (text == prefs.teamFileLast && file.exists()) return
        runCatching { file.parentFile?.mkdirs(); file.writeText(text); prefs.teamFileLast = text }
    }

    /** How the agent changes its helpers: one short line, so it costs almost nothing. */
    private fun manage(): String? = if (Graph.devices.isRemote) null else
        "You can manage your helper subagents yourself: edit $FILE (mode off|manual|auto; members: name, role, model \"provider/model\", on true|false; " +
            "pool: models for auto; max). Add, rename, switch off or change a helper's model there; it applies from the next message. Tell the user what you changed."

    /** Note to the main agent attached to every message: how to use the team and how to change it. */
    fun instruction(): String? {
        val list = agents()
        if (list.isEmpty() || !on) return manage()
        val names = list.entries.joinToString("\n") { (k, v) -> "- $k — " + ((v["description"] as? JsonPrimitive)?.content ?: "") + " (" + cost(refOf(k)) + ")" } +
            "\nThe user approves each helper the first time it is used. Prefer free and subscription helpers; paid ones spend the user's money." +
            (budget()?.let { "\n$it" } ?: "")
        return listOfNotNull(when (mode) {
            "manual" -> "You lead a team. Split the task and hand parts to these subagents with the task tool " +
                "(run independent parts in parallel, several task calls in one message), then check and combine their results:\n$names"
            else -> "You can lead a team of up to $max helpers. First decide whether the task needs help at all — for small tasks do it yourself. " +
                "If it does, split it into self-contained parts and hand them out with the task tool (several task calls in one message run in parallel). " +
                "Pick a stronger model for hard parts and a faster one for simple parts. Then check and combine the results. Helpers:\n$names"
        }, manage()).joinToString("\n\n")
    }
}
