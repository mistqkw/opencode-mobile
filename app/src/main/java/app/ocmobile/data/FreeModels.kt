package app.ocmobile.data

import app.ocmobile.Graph
import org.json.JSONObject
import java.io.File

/**
 * Free fallback routes for when Zen's built-in free models hit the server limit
 * (it's counted on Zen's side, doesn't always reset and doesn't depend on the client):
 *  - GitHub Copilot: the sign-in is there, but OpenCode server 1.19.4 hides Copilot models unless the API marks them
 *    "model_picker_enabled", so the models that really answer on the free plan are added as our own provider;
 *  - Pollinations: an anonymous OpenAI-compatible gateway, no account needed (messages go to pollinations.ai).
 * Everything is written to the global opencode.jsonc inside the environment.
 */
object FreeModels {
    const val COPILOT_ID = "copilot-chat"
    const val POLLINATIONS_ID = "pollinations"
    const val COPILOT_DEFAULT = "gpt-4.1-2025-04-14"
    const val POLLINATIONS_DEFAULT = "openai-fast"

    private val configFile get() = Graph.paths.host("/root/.config/opencode/opencode.jsonc")
    private val authFile get() = Graph.paths.host("/root/.local/share/opencode/auth.json")

    private fun copilotToken(): String? = runCatching {
        JSONObject(authFile.readText()).optJSONObject("github-copilot")?.optString("refresh")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun copilotAvailable(): Boolean = copilotToken() != null

    fun isEnabled(providerId: String): Boolean = runCatching {
        JSONObject(configFile.readText()).optJSONObject("provider")?.has(providerId) == true
    }.getOrDefault(false)

    private fun model(name: String, context: Int, output: Int, tools: Boolean, reasoning: Boolean, images: Boolean) = JSONObject()
        .put("name", name).put("tool_call", tools).put("reasoning", reasoning).put("attachment", images)
        .put("limit", JSONObject().put("context", context).put("output", output))

    private fun disabledList(): List<String> = runCatching {
        val arr = JSONObject(configFile.readText()).optJSONArray("disabled_providers") ?: return emptyList()
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    /** Applied through the server: it writes the global config and picks it up at once (editing the file by hand would need a restart). */
    private suspend fun apply(providerId: String, block: JSONObject?) {
        val disabled = disabledList().filter { it != providerId }.toMutableList()
        if (block == null) disabled.add(providerId)
        val patch = JSONObject().put("disabled_providers", org.json.JSONArray(disabled))
        if (block != null) patch.put("provider", JSONObject().put(providerId, block))
        Graph.api.patchGlobalConfig(app.ocmobile.api.OcJson.parseToJsonElement(patch.toString()) as kotlinx.serialization.json.JsonObject)
    }

    /** Copilot models that answer on the free plan via /chat/completions. */
    /** [select] = false: only add the models without changing the selected one (GitHub sign-in shouldn't force Copilot). */
    suspend fun enableCopilot(select: Boolean = true): Boolean {
        val token = copilotToken() ?: return false
        apply(
            COPILOT_ID,
            JSONObject()
                .put("name", "GitHub Copilot")
                .put("npm", "@ai-sdk/openai-compatible")
                .put(
                    "options",
                    JSONObject().put("baseURL", "https://api.githubcopilot.com").put("apiKey", token).put(
                        "headers",
                        JSONObject().put("Copilot-Integration-Id", "vscode-chat").put("Editor-Version", "vscode/1.107.0").put("User-Agent", "opencode-mobile"),
                    ),
                )
                .put(
                    "models",
                    JSONObject()
                        .put("gpt-4.1-2025-04-14", model("GPT-4.1", 128000, 16384, true, false, true))
                        .put("gpt-4o-2024-11-20", model("GPT-4o", 128000, 16384, true, false, true))
                        .put("gpt-4o-mini-2024-07-18", model("GPT-4o mini", 128000, 16384, true, false, true)),
                ),
        )
        if (select) finish(COPILOT_ID, COPILOT_DEFAULT) else { runCatching { Graph.api.disposeAll() }; runCatching { Graph.catalog.refresh() }; Vault.save() }
        return true
    }

    suspend fun enablePollinations() {
        apply(
            POLLINATIONS_ID,
            JSONObject()
                .put("name", "Pollinations (no account)")
                .put("npm", "@ai-sdk/openai-compatible")
                .put("options", JSONObject().put("baseURL", "https://text.pollinations.ai/openai").put("apiKey", "anonymous"))
                .put("models", JSONObject().put(POLLINATIONS_DEFAULT, model("GPT-OSS 20B", 131072, 8192, true, true, false))),
        )
        finish(POLLINATIONS_ID, POLLINATIONS_DEFAULT)
    }

    suspend fun disable(providerId: String) {
        runCatching { apply(providerId, null) }
        runCatching { Graph.catalog.refresh() }
        Vault.save()
    }

    private suspend fun finish(providerId: String, modelId: String) {
        runCatching { Graph.api.disposeAll() }
        runCatching { Graph.catalog.refresh() }
        Graph.prefs.providerId = providerId; Graph.prefs.modelId = modelId; Graph.prefs.variant = ""
        Vault.save()
    }
}
