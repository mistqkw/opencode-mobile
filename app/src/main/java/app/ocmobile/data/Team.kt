package app.ocmobile.data

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
    data class Member(val name: String, val role: String, val provider: String = "", val model: String = "")

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs get() = Graph.prefs

    val mode: String get() = prefs.teamMode
    val on: Boolean get() = mode != "off"

    var members: List<Member>
        get() = runCatching { json.decodeFromString<List<Member>>(prefs.teamMembers) }.getOrDefault(emptyList())
        set(v) { prefs.teamMembers = json.encodeToString(v) }

    /** Models for auto mode: provider/model; empty means the current model. */
    var pool: List<String>
        get() = prefs.teamPool.split(',').map { it.trim() }.filter { it.contains('/') }
        set(v) { prefs.teamPool = v.joinToString(",") }

    val max: Int get() = prefs.teamMax.toIntOrNull()?.coerceIn(1, 6) ?: 3

    fun slug(name: String) = "team-" + name.lowercase().replace(Regex("[^a-z0-9а-яё]+"), "-").trim('-').ifBlank { "helper" }

    private fun label(ref: String): String {
        val (p, m) = ref.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return Graph.catalog.model(p, m)?.name?.ifBlank { null } ?: m
    }

    /** Helpers that should be in the config now: name → agent description. */
    private fun agents(): Map<String, JsonObject> = when (mode) {
        "manual" -> members.associate { m ->
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

    /** Note to the main agent attached to the message while the team is on; null means the team is off. */
    fun instruction(): String? {
        val list = agents()
        if (list.isEmpty()) return null
        val names = list.entries.joinToString("\n") { (k, v) -> "- $k — " + ((v["description"] as? JsonPrimitive)?.content ?: "") }
        return when (mode) {
            "manual" -> "You lead a team. Split the task and hand parts to these subagents with the task tool " +
                "(run independent parts in parallel, several task calls in one message), then check and combine their results:\n$names"
            else -> "You can lead a team of up to $max helpers. First decide whether the task needs help at all — for small tasks do it yourself. " +
                "If it does, split it into self-contained parts and hand them out with the task tool (several task calls in one message run in parallel). " +
                "Pick a stronger model for hard parts and a faster one for simple parts. Then check and combine the results. Helpers:\n$names"
        }
    }
}
