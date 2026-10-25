package app.ocmobile.data

import app.ocmobile.Graph
import app.ocmobile.api.AgentInfo
import app.ocmobile.api.EventHub
import app.ocmobile.api.ModelInfo
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.ProviderInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Подключённые провайдеры, их модели и агенты — то, что нужно для выбора модели. */
class Catalog(private val api: OpenCodeApi, private val prefs: Prefs, private val events: EventHub) {
    val providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    val agents = MutableStateFlow<List<AgentInfo>>(emptyList())
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
        ensureSelection()
    }

    private fun rank(id: String) = when (id) { "opencode-go" -> 0; "opencode" -> 1; "anthropic" -> 2; "openai" -> 3; else -> 5 }

    fun model(providerId: String, modelId: String): ModelInfo? =
        providers.value.firstOrNull { it.id == providerId }?.models?.get(modelId)

    fun provider(id: String): ProviderInfo? = providers.value.firstOrNull { it.id == id }

    /** Если выбранной модели нет (первый запуск или провайдера отключили) — берём лучшую доступную. */
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

    /** После подключения провайдера делаем его модель выбранной: предпочитаем быстрые («flash») и рекомендованные сервером. */
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
