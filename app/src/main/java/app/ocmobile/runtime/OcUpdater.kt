package app.ocmobile.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocmobile.Graph
import app.ocmobile.data.I18n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Credentials
import okhttp3.Request
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/**
 * OpenCode keeps itself up to date, like on the computer: once a day GitHub is asked for a newer
 * build on the same major line; it is downloaded next to the running one, and when no chat is
 * working it is tried inside Ubuntu on a spare port with a copy of the settings (the server
 * answers, models load, our plugin loads). Only then does it take over; the old binary stays as
 * opencode.prev to go back to. A build that fails is skipped and the reason shown in Server settings.
 */
class OcUpdater(private val rt: RuntimeController, private val scope: CoroutineScope) {
    /** What it's doing right now, for Settings; null when nothing. */
    var busy by mutableStateOf<String?>(null)
        private set

    private val prefs get() = Graph.prefs
    private val major = RuntimeController.OC_VERSION.substringBefore('.')
    private val bin get() = rt.paths.opencodeBin
    private val next get() = File(bin.parentFile, "opencode.next")
    private val prev get() = File(bin.parentFile, "opencode.prev")
    private val current get() = prefs.opencodeVersion.ifBlank { RuntimeController.OC_VERSION }

    /** The version kept to go back to, or null. */
    val previous get() = prefs.ocPrevVersion.takeIf { it.isNotBlank() && prev.exists() }

    fun start() {
        scope.launch {
            delay(180_000)
            while (true) {
                if (System.currentTimeMillis() - prefs.ocChecked > 24 * 3600_000L) check()
                delay(3600_000)
            }
        }
    }

    /** One pass: look, fetch, wait for a quiet moment, test, switch. */
    suspend fun check(manual: Boolean = false) {
        if (busy != null || Graph.devices.active() != null || !rt.paths.localSupported || !prefs.runtimeReady) return
        try {
            busy = "Checking for a new OpenCode…"
            val found = withContext(Dispatchers.IO) { latest() }
            prefs.ocChecked = System.currentTimeMillis()
            if (found == null) { if (manual) prefs.ocUpdateError = ""; return }
            val (v, url, sha) = found
            busy = "Downloading OpenCode $v…"
            withContext(Dispatchers.IO) { fetch(url, sha) }
            busy = "OpenCode $v is waiting for the chats to finish"
            waitQuiet()
            busy = "Testing OpenCode $v…"
            val why = withContext(Dispatchers.IO) { smoke(v) }
            if (why != null) {
                next.delete()
                prefs.ocSkip = v; prefs.ocUpdateError = "OpenCode $v stays off: ${I18n.t(why)}"
                return
            }
            busy = "Switching to OpenCode $v…"
            val fail = switch(next, v)
            if (fail != null) { prefs.ocSkip = v; prefs.ocUpdateError = "OpenCode $v did not start, back on the previous one: ${I18n.t(fail)}"; return }
            prefs.ocUpdateError = ""; prefs.ocNotice = v
        } catch (e: Exception) {
            prefs.ocUpdateError = "Could not update OpenCode: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            busy = null
        }
    }

    /** Back to opencode.prev. */
    suspend fun rollback() {
        val v = previous ?: return
        if (busy != null) return
        busy = "Going back to OpenCode $v…"
        try {
            val cur = current
            val fail = switch(prev, v)
            if (fail == null) { prefs.ocSkip = cur; prefs.ocUpdateError = ""; prefs.ocNotice = v } else prefs.ocUpdateError = "Could not go back: ${I18n.t(fail)}"
        } finally { busy = null }
    }

    /** Two quiet looks a minute apart: no chat working, the server up. */
    private suspend fun waitQuiet() {
        var quiet = 0
        while (quiet < 2) {
            delay(60_000)
            val idle = Graph.chats.statuses.value.values.none { it != "idle" }
            quiet = if (idle && rt.state.value is RuntimeState.Running) quiet + 1 else 0
        }
    }

    private fun latest(): Triple<String, String, String>? {
        val body = Downloader.getText("https://api.github.com/repos/anomalyco/opencode/releases?per_page=40")
        return Json.parseToJsonElement(body).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if (o.str("draft") == "true" || o.str("prerelease") == "true") return@mapNotNull null
            val v = o.str("tag_name")?.removePrefix("v") ?: return@mapNotNull null
            if (!Regex("""\d+\.\d+\.\d+""").matches(v) || v.substringBefore('.') != major) return@mapNotNull null
            if (v == prefs.ocSkip || !newer(v, current)) return@mapNotNull null
            val asset = (o["assets"] as? JsonArray)?.map { it.jsonObject }?.firstOrNull { it.str("name") == "opencode-linux-arm64.tar.gz" } ?: return@mapNotNull null
            val sha = asset.str("digest")?.removePrefix("sha256:") ?: return@mapNotNull null
            Triple(v, asset.str("browser_download_url") ?: return@mapNotNull null, sha)
        }.maxWithOrNull { a, b -> if (newer(a.first, b.first)) 1 else if (a.first == b.first) 0 else -1 }
    }

    private fun fetch(url: String, sha: String) {
        val tgz = File(rt.paths.downloads, "opencode-next.tar.gz")
        Downloader.fetch(url, tgz, sha) { _, _ -> }
        val tmp = File(rt.paths.downloads, "oc-next").apply { deleteRecursively() }
        TarExtractor.extract(tgz, tmp)
        val b = tmp.walkTopDown().firstOrNull { it.isFile && it.name == "opencode" } ?: error("no opencode binary in the archive")
        b.copyTo(next, overwrite = true)
        next.setExecutable(true, false)
        tmp.deleteRecursively(); tgz.delete()
    }

    /** Runs opencode.next inside Ubuntu on a spare port with a copy of our settings and keys; null when it works the way the app needs. */
    private fun smoke(v: String): String? {
        val root = File(rt.paths.rootfs, "root/.oc-smoke").apply { deleteRecursively(); mkdirs() }
        File(rt.paths.rootfs, "root/.config/opencode").takeIf { it.isDirectory }?.copyRecursively(File(root, "config/opencode"))
        File(rt.paths.rootfs, "root/.local/share/opencode/auth.json").takeIf { it.isFile }?.copyTo(File(root, "data/opencode/auth.json"))
        listOf("state", "cache", "work").forEach { File(root, it).mkdirs() }
        val port = ServerSocket(0).use { it.localPort }
        val pass = java.util.UUID.randomUUID().toString()
        val g = "/root/.oc-smoke"
        val p = rt.startGuest(
            listOf("/usr/local/bin/opencode.next", "serve", "--hostname", "127.0.0.1", "--port", "$port"), "$g/work",
            mapOf(
                "OPENCODE_SERVER_PASSWORD" to pass, "OPENCODE_DISABLE_AUTOUPDATE" to "true",
                "XDG_CONFIG_HOME" to "$g/config", "XDG_DATA_HOME" to "$g/data", "XDG_STATE_HOME" to "$g/state", "XDG_CACHE_HOME" to "$g/cache",
            ),
            File(rt.paths.downloads, "oc-smoke.log"),
        )
        val http = Graph.api.http.newBuilder().readTimeout(15, TimeUnit.SECONDS).build()
        fun get(path: String): String? = runCatching {
            http.newCall(Request.Builder().url("http://127.0.0.1:$port$path").header("Authorization", Credentials.basic("opencode", pass)).build())
                .execute().use { if (it.isSuccessful) it.body.string() else null }
        }.getOrNull()
        try {
            var health: String? = null
            val until = System.currentTimeMillis() + 150_000
            while (System.currentTimeMillis() < until) { if (!p.isAlive) break; health = get("/global/health"); if (health != null) break; Thread.sleep(500) }
            if (health == null) return "the server did not answer" + (if (!p.isAlive) " (exit ${p.exitValue()})" else "")
            val h = runCatching { Json.parseToJsonElement(health).jsonObject }.getOrNull()
                ?: return "its API changed (the app speaks OpenCode $major.x)"
            if (h.str("version") != v) return "it reports version ${h.str("version")}"
            if (runCatching { Json.parseToJsonElement(get("/session")!!) as JsonArray }.isFailure) return "its API changed (the app speaks OpenCode $major.x)"
            val providers = runCatching { Json.parseToJsonElement(get("/config/providers")!!).jsonObject["providers"]!!.jsonArray }.getOrNull()
            if (providers == null || providers.none { (it.jsonObject["models"] as? JsonObject)?.isNotEmpty() == true }) return "the models did not load"
            val plugins = runCatching { Json.parseToJsonElement(get("/config")!!).jsonObject["plugin"]!!.jsonArray.map { (it as JsonPrimitive).content } }.getOrNull()
            if (plugins == null || plugins.none { "cl0udly-brain" in it }) return "the cl0udly plugin did not load"
            return null
        } finally {
            p.destroy(); if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
            root.deleteRecursively()
        }
    }

    /**
     * Puts [file] (version [v]) in place of the running binary, which becomes opencode.prev, and
     * starts the server. When it doesn't come up within two minutes the old one goes back: returns why, or null.
     */
    private suspend fun switch(file: File, v: String): String? {
        val was = current
        stopAndWait()
        val ok = withContext(Dispatchers.IO) { swapIn(file) }
        if (!ok) { rt.startServer(); return "could not move files" }
        prefs.opencodeVersion = v; prefs.ocPrevVersion = was
        rt.startServer()
        val up = withTimeoutOrNull(120_000) { rt.state.first { it is RuntimeState.Running } } != null
        if (up) return null
        val why = (rt.state.value as? RuntimeState.Crashed)?.message ?: "it did not start"
        stopAndWait()
        withContext(Dispatchers.IO) { swapIn(prev) }
        prefs.opencodeVersion = was; prefs.ocPrevVersion = v
        rt.startServer()
        return why
    }

    /** The server service stopped and its process gone, so the binary can be swapped and started fresh. */
    private suspend fun stopAndWait() {
        rt.stopServer()
        withTimeoutOrNull(15_000) { while (rt.serverAlive) delay(200) }
        delay(1200)
    }

    /** [file] becomes the binary, the current binary becomes opencode.prev. */
    private fun swapIn(file: File): Boolean {
        val tmp = File(bin.parentFile, "opencode.swap").apply { delete() }
        if (!bin.renameTo(tmp)) return false
        if (!file.renameTo(bin)) { tmp.renameTo(bin); return false }
        prev.delete()
        return tmp.renameTo(prev)
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.content

    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }
}
