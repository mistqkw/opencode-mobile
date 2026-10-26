package app.ocmobile.data

import app.ocmobile.Graph
import org.json.JSONObject
import java.io.File

/**
 * Запасные бесплатные пути, когда встроенные бесплатные модели Zen упираются в серверный лимит
 * (он считается на стороне Zen, сбрасывается не всегда и от клиента не зависит):
 *  - GitHub Copilot: вход уже есть, но сервер OpenCode 1.19.4 скрывает модели Copilot, если API не помечает их
 *    «model_picker_enabled», поэтому модели, которые реально отвечают на бесплатном тарифе, подключаем как свой провайдер;
 *  - Pollinations: анонимный OpenAI-совместимый шлюз, аккаунт не нужен (сообщения уходят на pollinations.ai).
 * Всё пишется в глобальный opencode.jsonc внутри окружения.
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

    /** Применяем через сервер: он пишет глобальный конфиг и подхватывает его сразу (правка файла руками требовала бы перезапуска). */
    private suspend fun apply(providerId: String, block: JSONObject?) {
        val disabled = disabledList().filter { it != providerId }.toMutableList()
        if (block == null) disabled.add(providerId)
        val patch = JSONObject().put("disabled_providers", org.json.JSONArray(disabled))
        if (block != null) patch.put("provider", JSONObject().put(providerId, block))
        Graph.api.patchGlobalConfig(app.ocmobile.api.OcJson.parseToJsonElement(patch.toString()) as kotlinx.serialization.json.JsonObject)
    }

    /** Модели Copilot, которые отвечают на бесплатном тарифе через /chat/completions. */
    suspend fun enableCopilot(): Boolean {
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
        finish(COPILOT_ID, COPILOT_DEFAULT)
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
