package app.ocmobile.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocmobile.Graph
import app.ocmobile.runtime.RuntimeController
import app.ocmobile.runtime.RuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * One account on the phone and the computer: when the phone is on the same network as a paired
 * computer (Remote devices, QR from cl0udly for desktop), chats and memory go both ways.
 *
 * Chats travel as `opencode export` JSON and come in with `opencode import`, which adds the
 * messages the other side lacks (so a chat continued on both sides ends up with both halves).
 * What changed is told by each chat's `updated` time against the last sync (the ledger); the
 * computer side is cl0udly's SyncServer on the server port + 1. Memory: our shelves go there,
 * get merged, and the merged ones come back. Plain chats only: a project folder exists on one side.
 */
object Sync {
    var status by mutableStateOf("")
        private set
    var running by mutableStateOf(false)
        private set
    private val lock = Mutex()
    private val prefs get() = Graph.prefs
    private val http get() = Graph.api.http.newBuilder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    /** Home Wi-Fi coming up, and every 15 minutes while the app lives. */
    fun start(ctx: Context) {
        runCatching {
            ctx.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) Graph.scope.launch { delay(8_000); syncAll() }
                }
            })
        }
        Graph.scope.launch { while (true) { delay(15 * 60_000L); syncAll() } }
    }

    /** Every paired computer that answers; quietly nothing when none is reachable. */
    suspend fun syncAll(manual: Boolean = false) {
        if (!manual && !prefs.syncHome) return
        if (Graph.runtime.state.value !is RuntimeState.Running) { if (manual) status = "Start the server on this phone first"; return }
        if (!lock.tryLock()) return
        running = true
        try {
            var found = false
            for (d in Graph.devices.list.value) {
                val r = withContext(Dispatchers.IO) { runCatching { syncWith(d) } }
                r.onSuccess { n -> if (n != null) { found = true; done(d, n) } }
                    .onFailure { status = "Could not sync with ${d.name}: ${it.message ?: it.javaClass.simpleName}" }
            }
            if (!found && manual && !status.startsWith("Could not")) status = "No computer on this network"
        } finally { running = false; lock.unlock() }
    }

    private fun done(d: Device, n: Int) {
        status = if (n == 0) "Up to date with ${d.name}" else "Synced $n chats with ${d.name}"
        prefs.syncLast = System.currentTimeMillis()
        if (n > 0) Graph.chats.toasts.tryEmit("Synced $n chats with ${d.name}")
    }

    /** One computer: null when it isn't there, otherwise how many chats moved. */
    private suspend fun syncWith(d: Device): Int? {
        val url = okhttp3.HttpUrl.Builder().scheme("http").host(d.url.toHttpHost()).port(d.url.toPort() + 1).build().toString().trimEnd('/')
        val info = runCatching { JsonObjectOf(get("$url/sync/info", d.password)) }.getOrNull() ?: return null
        val theirChats = (info["chats"] as? JsonPrimitive)?.content ?: return null
        val here = "http://127.0.0.1:${prefs.serverPort}" to prefs.serverPassword
        val there = d.url.trimEnd('/') to d.password
        val ledger = Ledger(d.id)
        val busyHere = Graph.chats.statuses.value.filterValues { it != "idle" }.keys
        val busyThere = runCatching { JsonObjectOf(get("${there.first}/session/status", there.second)).keys }.getOrDefault(emptySet())
        var moved = 0

        for ((id, upd) in chats(here, CHAT_DIR)) if (id !in busyHere && ledger["l:$id"] != upd) {
            post("$url/sync/import", d.password, export(here, CHAT_DIR, id).toString().toRequestBody(JSON))
            moved++
        }
        for ((id, upd) in chats(there, theirChats)) if (id !in busyThere && ledger["r:$id"] != upd) {
            importHere(export(there, theirChats, id))
            moved++
        }
        // what both sides look like now is the starting point for next time
        chats(here, CHAT_DIR).forEach { (id, u) -> ledger["l:$id"] = u }
        chats(there, theirChats).forEach { (id, u) -> ledger["r:$id"] = u }
        ledger.save()

        syncMemory(url, d.password)
        if (moved > 0) runCatching { Graph.chats.refreshSessions() }
        return moved
    }

    /** Plain chats of a server: id → updated time. */
    private fun chats(server: Pair<String, String>, dir: String): Map<String, Long> {
        val arr = app.ocmobile.api.OcJson.parseToJsonElement(get("${server.first}/session?directory=${enc(dir)}", server.second)) as JsonArray
        return arr.mapNotNull { e ->
            val o = e.jsonObject
            if ((o["directory"] as? JsonPrimitive)?.content != dir || o["parentID"].let { it != null && it !is kotlinx.serialization.json.JsonNull }) return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            id to ((o["time"] as? JsonObject)?.get("updated") as? JsonPrimitive)?.longOrNull.let { it ?: 0L }
        }.toMap()
    }

    /** The same JSON as `opencode export`: the chat and its messages with their parts. */
    private fun export(server: Pair<String, String>, dir: String, id: String): JsonObject {
        val info = JsonObjectOf(get("${server.first}/session/$id?directory=${enc(dir)}", server.second))
        val messages = app.ocmobile.api.OcJson.parseToJsonElement(get("${server.first}/session/$id/message?directory=${enc(dir)}", server.second))
        return buildJsonObject { put("info", info); put("messages", messages) }
    }

    private suspend fun importHere(o: JsonObject) {
        val info = o["info"] as JsonObject
        val moved = JsonObject(o + ("info" to JsonObject(info + mapOf("directory" to JsonPrimitive(CHAT_DIR), "path" to JsonPrimitive(CHAT_DIR.trimStart('/'))))))
        val f = Graph.paths.host("/root/.sync/in.json").apply { parentFile?.mkdirs(); writeText(moved.toString()) }
        val out = StringBuilder()
        val code = Graph.runtime.runGuest(listOf(RuntimeController.OC_BIN, "import", "/root/.sync/in.json"), cwd = CHAT_DIR, timeoutSec = 120) { out.appendLine(it) }
        f.delete()
        if (code != 0) error("import: ${out.toString().trim().takeLast(300)}")
    }

    private fun syncMemory(url: String, password: String) {
        val mine = ByteArrayOutputStream().also { MemoryIO.zip(Memory.dir(), it) }.toByteArray()
        val req = Request.Builder().url("$url/sync/memory").header("Authorization", Credentials.basic("opencode", password))
            .post(mine.toRequestBody("application/zip".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("memory: HTTP ${r.code}")
            val tmp = File(Graph.paths.downloads, "sync-memory").apply { deleteRecursively(); mkdirs() }
            MemoryIO.unzip(r.body.byteStream(), tmp)
            MemoryIO.import(tmp, Memory.dir())
            tmp.deleteRecursively()
        }
        Memory.sync()
    }

    private class Ledger(private val device: String) {
        private val all = runCatching { app.ocmobile.api.OcJson.parseToJsonElement(Graph.prefs.syncLedger) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
        private val map = HashMap<String, Long>().apply {
            (all[device] as? JsonObject)?.forEach { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { put(k, it) } }
        }
        operator fun get(k: String) = map[k]
        operator fun set(k: String, v: Long) { map[k] = v }
        fun save() {
            Graph.prefs.syncLedger = JsonObject(all + (device to JsonObject(map.mapValues { JsonPrimitive(it.value) }))).toString()
        }
    }

    private val JSON = "application/json".toMediaType()

    private fun get(url: String, password: String): String =
        http.newCall(Request.Builder().url(url).header("Authorization", Credentials.basic("opencode", password)).build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}")
            it.body.string()
        }

    private fun post(url: String, password: String, body: okhttp3.RequestBody) =
        http.newCall(Request.Builder().url(url).header("Authorization", Credentials.basic("opencode", password)).post(body).build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}: ${it.body.string().take(200)}")
        }

    private fun JsonObjectOf(text: String) = app.ocmobile.api.OcJson.parseToJsonElement(text) as JsonObject
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun String.toHttpHost() = removePrefix("http://").removePrefix("https://").substringBefore('/').substringBeforeLast(':')
    private fun String.toPort() = removePrefix("http://").removePrefix("https://").substringBefore('/').substringAfterLast(':', "").toIntOrNull() ?: 4096
}
