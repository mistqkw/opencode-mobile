package app.ocmobile.data

import app.ocmobile.Graph
import app.ocmobile.api.AgentInfo
import app.ocmobile.api.EventHub
import app.ocmobile.api.ModelInfo
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.ProviderInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Connected providers, their models and agents: what model selection needs. */
class Catalog(private val api: OpenCodeApi, private val prefs: Prefs, private val events: EventHub) {
    val providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    val agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    /** Commands and skills for the "/" menu in the input. */
    val commands = MutableStateFlow<List<app.ocmobile.api.NamedItem>>(emptyList())
    val loaded = MutableStateFlow(false)
    private var defaults: Map<String, String> = emptyMap()

    fun start() {
        Graph.scope.launch { events.generation.collect { if (it > 0) refresh() } }
    }

    suspend fun refresh() {
        runCatching {
            val r = api.connectedProviders()
            providers.value = r.providers.sortedWith(compareBy({ rank(it.id) }, { it.name }))
            defaults = r.default
            loaded.value = true
        }
        runCatching { agents.value = api.agents().filter { !it.hidden && it.mode != "subagent" } }
        runCatching { commands.value = api.commands().sortedWith(compareBy({ if (it.source == "skill") 0 else 1 }, { it.name })) }
        ensureSelection()
    }

    private fun rank(id: String) = when (id) { "opencode-go" -> 0; "opencode" -> 1; "anthropic" -> 2; "openai" -> 3; else -> 5 }

    fun model(providerId: String, modelId: String): ModelInfo? =
        providers.value.firstOrNull { it.id == providerId }?.models?.get(modelId)

    fun provider(id: String): ProviderInfo? = providers.value.firstOrNull { it.id == id }

    /** If the selected model is gone (first launch or the provider was disconnected), take the best available one. */
    private fun ensureSelection() {
        val list = providers.value
        if (list.isEmpty()) return
        if (prefs.hasModel && model(prefs.providerId, prefs.modelId) != null) return
        for (p in list) {
            val m = defaults[p.id]?.let { p.models[it] } ?: p.models.values.firstOrNull() ?: continue
            prefs.providerId = p.id; prefs.modelId = m.id; prefs.variant = ""
            return
        }
    }

    /** After a provider is connected, select its model: prefer fast ("flash") ones and the server's recommended ones. */
    fun selectBestFor(providerId: String) {
        val p = provider(providerId) ?: return
        val m = defaults[providerId]?.let { p.models[it] }
            ?: p.models.values.filter { it.id.contains("flash", true) && !it.id.contains("lite", true) }.maxByOrNull { it.id }
            ?: p.models.values.firstOrNull() ?: return
        prefs.providerId = p.id; prefs.modelId = m.id; prefs.variant = ""
    }

    fun selectedLabel(): String {
        val m = model(prefs.providerId, prefs.modelId)
        return m?.name?.takeIf { it.isNotBlank() } ?: prefs.modelId.ifBlank { "Select model" }
    }
}
