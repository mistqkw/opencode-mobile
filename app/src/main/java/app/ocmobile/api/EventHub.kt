package app.ocmobile.api

import app.ocmobile.Graph
import app.ocmobile.runtime.RuntimeController
import app.ocmobile.runtime.RuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Одно глобальное SSE-соединение `/global/event` — события всех проектов и сессий. */
class EventHub(private val api: OpenCodeApi, private val runtime: RuntimeController) {
    data class Event(val directory: String?, val type: String, val props: JsonObject)

    val events = MutableSharedFlow<Event>(extraBufferCapacity = 1024)
    val connected = MutableStateFlow(false)
    /** Растёт при каждом (пере)подключении: подписчикам надо перечитать состояние. */
    val generation = MutableStateFlow(0)

    fun start(scope: CoroutineScope) {
        scope.launch {
            // Подключаемся к локальному серверу, когда он запущен, либо к выбранному удалённому устройству.
            combine(runtime.state, Graph.devices.activeId) { s, d -> if (d.isNotEmpty()) "remote:$d" else if (s is RuntimeState.Running) "local" else "" }
                .distinctUntilChanged()
                .collectLatest { target ->
                    if (target.isNotEmpty()) connectLoop() else connected.value = false
                }
        }
    }

    private suspend fun connectLoop() {
        var backoff = 500L
        while (currentCoroutineContext().isActive) {
            try {
                withContext(Dispatchers.IO) { stream { backoff = 500L } }
            } catch (_: Exception) { }
            connected.value = false
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(8000)
        }
    }

    private suspend fun stream(onOpen: () -> Unit) {
        val call = api.streamHttp.newCall(api.request("/global/event").header("Accept", "text/event-stream").build())
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { r ->
                if (!r.isSuccessful) return
                onOpen()
                connected.value = true
                generation.value++
                val src = r.body.source()
                val data = StringBuilder()
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        if (data.isNotEmpty()) { dispatch(data.toString()); data.setLength(0) }
                    } else if (line.startsWith("data:")) {
                        data.append(line.removePrefix("data:").removePrefix(" "))
                    }
                }
            }
        } finally { handle.dispose() }
    }

    private fun dispatch(json: String) {
        val o = runCatching { OcJson.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return
        val payload = o.obj("payload") ?: o
        val type = payload.str("type") ?: return
        events.tryEmit(Event(o.str("directory"), type, payload.obj("properties") ?: JsonObject(emptyMap())))
    }
}
