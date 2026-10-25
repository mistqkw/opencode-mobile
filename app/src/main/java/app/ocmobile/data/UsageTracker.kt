package app.ocmobile.data

import android.content.Context
import app.ocmobile.Graph
import app.ocmobile.api.EventHub
import app.ocmobile.api.OcJson
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.dbl
import app.ocmobile.api.long
import app.ocmobile.api.obj
import app.ocmobile.api.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

data class UsageWindow(
    val label: String,
    val used: Double,
    val limit: Double,
    /** Когда начнёт «отпускать»: самая старая запись в окне + длина окна. */
    val resetAt: Long?,
) {
    val fraction get() = if (limit > 0) (used / limit).coerceIn(0.0, 1.0) else 0.0
    val percent get() = if (limit > 0) (used / limit * 100) else 0.0
}

data class ModelUsage(val provider: String, val model: String, val cost: Double, val tokens: Long, val messages: Int)

data class UsageSnapshot(
    val fiveHour: UsageWindow,
    val week: UsageWindow,
    val month: UsageWindow,
    val byModel: List<ModelUsage>,
    val totalMonth: Double,
    val tracked: Int,
)

/**
 * У OpenCode нет публичного API для остатка лимитов Go, поэтому считаем сами:
 * копим стоимость каждого ответа (поле cost сообщения) и складываем по скользящим окнам
 * 5 часов / 7 дней / 30 дней. Считается только то, что прошло через этот сервер.
 */
class UsageTracker(
    private val ctx: Context,
    private val api: OpenCodeApi,
    private val prefs: Prefs,
    private val events: EventHub,
) {
    private data class Rec(val t: Long, val provider: String, val model: String, val cost: Double, val tokens: Long)

    private val file = File(ctx.filesDir, "usage.json")
    private val recs = LinkedHashMap<String, Rec>()
    private val backfilled = HashMap<String, Long>()
    val snapshot = MutableStateFlow(compute())
    val refreshing = MutableStateFlow(false)

    fun start() {
        load()
        snapshot.value = compute()
        Graph.scope.launch {
            events.events.collect { e ->
                if (e.type == "message.updated" && !Graph.devices.isRemote) e.props.obj("info")?.let { record(it); }
            }
        }
        Graph.scope.launch { events.generation.collect { if (it > 0 && !Graph.devices.isRemote) backfill() } }
    }

    private fun record(info: JsonObject, publish: Boolean = true) {
        if (info.str("role") != "assistant") return
        val id = info.str("id") ?: return
        val cost = info.dbl("cost") ?: return
        val provider = info.str("providerID") ?: return
        val time = info.obj("time")
        val t = time?.long("completed") ?: time?.long("created") ?: return
        val tokens = info.obj("tokens")?.let { o ->
            (o.long("total") ?: ((o.long("input") ?: 0) + (o.long("output") ?: 0) + (o.long("reasoning") ?: 0)))
        } ?: 0
        val old = recs[id]
        if (old != null && old.cost == cost && old.tokens == tokens) return
        if (cost <= 0.0 && old == null && tokens == 0L) return
        recs[id] = Rec(t, provider, info.str("modelID") ?: "", cost, tokens)
        if (publish) { snapshot.value = compute(); save() }
    }

    /** Перечитывает недавние сессии: так учитывается то, что было до установки счётчика. */
    suspend fun backfill() {
        if (refreshing.value) return
        refreshing.value = true
        try {
            val since = System.currentTimeMillis() - 31L * DAY
            val list = api.listSessions(limit = 500).filter { it.time.updated >= since }
            for (s in list) {
                if ((backfilled[s.id] ?: 0) >= s.time.updated) continue
                val msgs = runCatching { api.messages(s.id, s.directory, 1000) }.getOrNull() ?: continue
                for (m in msgs) if (!m.info.isUser) {
                    record(
                        buildJsonObject {
                            put("role", "assistant"); put("id", m.info.id)
                            m.info.cost?.let { put("cost", it) }
                            m.info.providerID?.let { put("providerID", it) }
                            m.info.modelID?.let { put("modelID", it) }
                            put("time", buildJsonObject {
                                (m.info.time.completed ?: m.info.time.created).let { put("completed", it) }
                            })
                            m.info.tokens?.let { put("tokens", buildJsonObject { put("total", it.sum) }) }
                        },
                        publish = false,
                    )
                }
                backfilled[s.id] = s.time.updated
            }
            snapshot.value = compute()
            save()
        } finally { refreshing.value = false }
    }

    fun compute(now: Long = System.currentTimeMillis()): UsageSnapshot {
        val counted = prefs.limitProviders.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val relevant = recs.values.filter { counted.isEmpty() || it.provider in counted }
        fun window(label: String, ms: Long, limit: Double): UsageWindow {
            val inWin = relevant.filter { it.t > now - ms }
            return UsageWindow(label, inWin.sumOf { it.cost }, limit, inWin.minOfOrNull { it.t }?.plus(ms))
        }
        val monthRecs = recs.values.filter { it.t > now - 30 * DAY }
        val byModel = monthRecs.groupBy { it.provider to it.model }.map { (k, v) ->
            ModelUsage(k.first, k.second, v.sumOf { it.cost }, v.sumOf { it.tokens }, v.size)
        }.sortedByDescending { it.cost }
        return UsageSnapshot(
            fiveHour = window("5h", 5 * HOUR, prefs.limit5h),
            week = window("week", 7 * DAY, prefs.limitWeek),
            month = window("month", 30 * DAY, prefs.limitMonth),
            byModel = byModel,
            totalMonth = monthRecs.sumOf { it.cost },
            tracked = recs.size,
        )
    }

    fun recompute() { snapshot.value = compute() }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val o = OcJson.parseToJsonElement(file.readText()) as JsonObject
            o.obj("recs")?.forEach { (id, v) ->
                val r = v as? JsonObject ?: return@forEach
                recs[id] = Rec(r.long("t") ?: 0, r.str("p") ?: "", r.str("m") ?: "", r.dbl("c") ?: 0.0, r.long("k") ?: 0)
            }
            o.obj("bf")?.forEach { (id, v) -> (v as? JsonPrimitive)?.content?.toLongOrNull()?.let { backfilled[id] = it } }
        }
    }

    private fun save() {
        Graph.scope.launch(Dispatchers.IO) {
            runCatching {
                val cutoff = System.currentTimeMillis() - 35 * DAY
                recs.entries.removeAll { it.value.t < cutoff }
                val o = buildJsonObject {
                    put("recs", buildJsonObject {
                        recs.forEach { (id, r) ->
                            put(id, buildJsonObject { put("t", r.t); put("p", r.provider); put("m", r.model); put("c", r.cost); put("k", r.tokens) })
                        }
                    })
                    put("bf", buildJsonObject { backfilled.forEach { (id, t) -> put(id, t.toString()) } })
                }
                val tmp = File(file.path + ".tmp")
                tmp.writeText(o.toString())
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
