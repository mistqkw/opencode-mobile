package app.ocmobile.runtime

import app.ocmobile.data.Vault
import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Владелец локального сервера: ставит Ubuntu rootfs и OpenCode, запускает
 * `opencode serve` внутри proot и следит, чтобы процесс жил.
 */
class RuntimeController(
    private val app: Application,
    val paths: RuntimePaths,
    private val prefs: Prefs,
    private val api: OpenCodeApi,
) {
    companion object {
        const val ROOTFS_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"
        const val ROOTFS_SHA = "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"
        // Пинним проверенную версию: клиент написан под её API.
        const val OC_VERSION = "1.19.4"
        const val OC_SHA = "8568230ccd847dc93e95b9f6894379077d3e014a16b48040743573e77c23e85f"
        const val OC_BIN = "/usr/local/bin/opencode"
        fun ocUrl(v: String) = "https://github.com/anomalyco/opencode/releases/download/v$v/opencode-linux-arm64.tar.gz"
    }

    val state = MutableStateFlow<RuntimeState>(
        if (prefs.runtimeReady && paths.opencodeBin.exists()) RuntimeState.Stopped else RuntimeState.NotInstalled
    )
    val log = MutableStateFlow<List<String>>(emptyList())

    private val proot = ProotCommand(paths, prefs)
    @Volatile private var process: Process? = null
    @Volatile private var stopRequested = false
    @Volatile private var installing = false

    fun logLine(s: String) = log.update { (it + s).takeLast(600) }

    // ---------- установка ----------

    suspend fun install() = withContext(Dispatchers.IO) {
        if (installing) return@withContext
        installing = true
        try {
            step("Preparing", null)
            paths.libDir.mkdirs()
            File(paths.nativeDir, "libtalloc.so").copyTo(File(paths.libDir, "libtalloc.so.2"), overwrite = true)

            if (!paths.rootfsReady) {
                val tgz = File(paths.downloads, "ubuntu-base.tar.gz")
                step("Downloading Ubuntu", 0f)
                Downloader.fetch(ROOTFS_URL, tgz, ROOTFS_SHA) { d, t -> step("Downloading Ubuntu", frac(d, t), mb(d, t)) }
                step("Unpacking Ubuntu", 0f)
                paths.rootfs.deleteRecursively()
                val r = TarExtractor.extract(tgz, paths.rootfs) { d, t -> step("Unpacking Ubuntu", frac(d, t)) }
                logLine("rootfs: ${r.files} files, skipped ${r.skipped}")
                configureRootfs()
                tgz.delete()
            }

            Vault.restore()
            installOpencode(OC_VERSION, OC_URL_OR(OC_VERSION), if (OC_VERSION == "1.19.4") OC_SHA else null)
            smokeTest()

            prefs.runtimeReady = true
            state.value = RuntimeState.Stopped
        } catch (e: Throwable) {
            logLine("Install failed: ${e.message}")
            state.value = RuntimeState.InstallFailed(e.message ?: e.javaClass.simpleName)
        } finally {
            installing = false
        }
    }

    private fun OC_URL_OR(v: String) = ocUrl(v)

    /** Обновить OpenCode до последней версии ветки 1.x (клиент проверен на 1.19.4). */
    suspend fun updateOpencode(): String = withContext(Dispatchers.IO) {
        val wasRunning = state.value is RuntimeState.Running
        val latest = Regex("\"version\":\\s*\"([0-9.]+)\"").find(Downloader.getText("https://registry.npmjs.org/opencode-ai/latest"))?.groupValues?.get(1)
            ?: error("Could not determine the latest version")
        if (!latest.startsWith("1.")) error("Latest version $latest is not on the 1.x line; this client was only verified against 1.x")
        if (latest == prefs.opencodeVersion) return@withContext latest
        requestStop(); delay(1500)
        installOpencode(latest, ocUrl(latest), null)
        smokeTest()
        if (wasRunning) startServer()
        latest
    }

    private fun installOpencode(version: String, url: String, sha: String?) {
        val tgz = File(paths.downloads, "opencode-$version.tar.gz")
        step("Downloading OpenCode $version", 0f)
        Downloader.fetch(url, tgz, sha) { d, t -> step("Downloading OpenCode $version", frac(d, t), mb(d, t)) }
        step("Installing OpenCode", 0f)
        val tmp = File(paths.downloads, "oc-extract")
        tmp.deleteRecursively()
        TarExtractor.extract(tgz, tmp) { d, t -> step("Installing OpenCode", frac(d, t)) }
        val bin = tmp.walkTopDown().firstOrNull { it.isFile && it.name == "opencode" } ?: error("No opencode binary in the archive")
        paths.opencodeBin.parentFile?.mkdirs()
        paths.opencodeBin.delete()
        bin.copyTo(paths.opencodeBin, overwrite = true)
        paths.opencodeBin.setExecutable(true, false)
        tmp.deleteRecursively()
        tgz.delete()
        prefs.opencodeVersion = version
    }

    private suspend fun smokeTest() {
        step("Checking startup", null)
        suspend fun once(): Pair<Int, String> {
            val out = StringBuilder()
            val code = runGuest(listOf(OC_BIN, "--version"), timeoutSec = 120) { out.appendLine(it) }
            return code to out.toString()
        }
        var (code, out) = once()
        if (code != 0 || !Regex("\\d+\\.\\d+\\.\\d+").containsMatchIn(out)) {
            logLine("First launch failed (code $code), retrying with PROOT_NO_SECCOMP=1")
            prefs.noSeccomp = true
            val second = once()
            code = second.first; out = second.second
            if (code != 0 || !Regex("\\d+\\.\\d+\\.\\d+").containsMatchIn(out)) {
                prefs.noSeccomp = false
                error("opencode does not start in proot (code $code): ${out.trim().takeLast(400)}")
            }
        }
        logLine("opencode: ${out.trim()}")
    }

    private fun configureRootfs() {
        fun w(guest: String, text: String) {
            val f = paths.host(guest)
            f.parentFile?.mkdirs()
            f.delete()
            f.writeText(text)
        }
        w("/etc/resolv.conf", "nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        w("/etc/hosts", "127.0.0.1 localhost\n::1 localhost ip6-localhost\n")
        w("/etc/hostname", "opencode-mobile\n")
        // apt внутри proot не может сбросить привилегии до пользователя _apt.
        w("/etc/apt/apt.conf.d/99proot", "APT::Sandbox::User \"root\";\nAcquire::Retries \"3\";\n")
        val pem = app.assets.open("cacert.pem").use { it.readBytes() }
        for (p in listOf("/etc/ssl/certs/ca-certificates.crt", "/etc/ssl/cert.pem")) {
            val f = paths.host(p); f.parentFile?.mkdirs(); f.delete(); f.writeBytes(pem)
        }
        paths.host("/root/work").mkdirs()
        paths.host("/root/.config/opencode").mkdirs()
        paths.host("/tmp").mkdirs()
        File(paths.rootfs, ".oc-configured").writeText("1")
    }

    private fun step(name: String, p: Float?, detail: String = "") {
        state.value = RuntimeState.Installing(name, p, detail)
    }
    private fun frac(d: Long, t: Long) = if (t > 0) (d.toFloat() / t).coerceIn(0f, 1f) else null
    private fun mb(d: Long, t: Long) = if (t > 0) "%.0f / %.0f MB".format(d / 1e6, t / 1e6) else "%.0f MB".format(d / 1e6)

    // ---------- запуск ----------

    suspend fun supervise() = withContext(Dispatchers.IO) {
        stopRequested = false
        var failures = 0
        var windowStart = System.currentTimeMillis()
        while (isActive && !stopRequested) {
            state.value = RuntimeState.Starting
            val cmd = proot.build(
                listOf(OC_BIN, "serve", "--hostname", "127.0.0.1", "--port", prefs.serverPort),
                cwd = "/root/work",
                extraEnv = mapOf(
                    "OPENCODE_SERVER_PASSWORD" to prefs.serverPassword,
                    "OPENCODE_DISABLE_AUTOUPDATE" to "true",
                ),
            )
            val pb = ProcessBuilder(cmd).redirectErrorStream(true)
            pb.environment().putAll(proot.env())
            val p = try { pb.start() } catch (e: Exception) {
                state.value = RuntimeState.Crashed("Could not start proot: ${e.message}")
                return@withContext
            }
            process = p
            val reader = launch { runCatching { p.inputStream.bufferedReader().forEachLine { logLine(it) } } }
            try {
                var version: String? = null
                val deadline = System.currentTimeMillis() + 120_000
                while (isActive && p.isAlive && System.currentTimeMillis() < deadline && version == null) {
                    val t0 = System.currentTimeMillis()
                    version = try { api.healthVersionOrThrow() } catch (e: Exception) { android.util.Log.d("ocm-health", "err ${e.javaClass.simpleName} ${e.message}"); logLine("health: ${e.javaClass.simpleName} ${e.message}"); null }
                    android.util.Log.d("ocm-health", "version=$version in ${System.currentTimeMillis() - t0}ms")
                    if (version == null) delay(250)
                }
                if (version != null) {
                    state.value = RuntimeState.Running(version)
                    prefs.opencodeVersion = version
                    failures = 0
                }
                // Сторож: процесс жив, но не отвечает (завис) — перезапускаем, а не ждём вечно.
                val dog = launch {
                    var bad = 0
                    while (isActive && p.isAlive) {
                        delay(15_000)
                        val ok = runCatching { kotlinx.coroutines.withTimeout(10_000) { api.healthVersionOrThrow() } }.isSuccess
                        bad = if (ok) 0 else bad + 1
                        if (bad >= 3) { logLine("Server stopped answering — restarting"); p.destroy(); break }
                    }
                }
                runInterruptible { p.waitFor() }
                dog.cancel()
            } finally {
                p.destroy()
                if (p.isAlive) { runCatching { p.waitFor(3, TimeUnit.SECONDS) }; if (p.isAlive) p.destroyForcibly() }
                process = null
                reader.cancel()
            }
            if (stopRequested) break
            val code = runCatching { p.exitValue() }.getOrDefault(-1)
            logLine("Server exited (code $code)")
            val now = System.currentTimeMillis()
            if (now - windowStart > 120_000) { failures = 0; windowStart = now }
            failures++
            state.value = RuntimeState.Crashed("Server stopped (code $code). Restarting…")
            // не сдаёмся: перезапуск с растущей паузой (до 30 с), пока пользователь сам не остановит сервер
            delay((1500L * failures).coerceAtMost(30_000))
        }
        state.value = RuntimeState.Stopped
    }

    /** Пользователь сам остановил сервер — тогда не поднимаем его при возврате в приложение. */
    @Volatile var userStopped = false

    /** Процесс сервера ещё жив (после stopServer он завершается не сразу). */
    val serverAlive get() = process != null

    fun requestStop() {
        stopRequested = true
        process?.destroy()
    }

    /** Запуск сервера через foreground-сервис: иначе Android убьёт процесс в фоне. */
    fun startServer() {
        val s = state.value
        if (s is RuntimeState.NotInstalled || s is RuntimeState.Installing || s is RuntimeState.InstallFailed) return
        userStopped = false
        ContextCompat.startForegroundService(app, Intent(app, ServerService::class.java).setAction(ServerService.ACTION_START))
    }

    fun stopServer() {
        userStopped = true
        requestStop()
        app.stopService(Intent(app, ServerService::class.java))
        if (state.value !is RuntimeState.NotInstalled) state.value = RuntimeState.Stopped
    }

    /** Выполнить команду внутри Ubuntu (установка git и т. п.). */
    suspend fun runGuest(
        argv: List<String>,
        cwd: String = "/root",
        timeoutSec: Long = 900,
        extraEnv: Map<String, String> = emptyMap(),
        onLine: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        val pb = ProcessBuilder(proot.build(argv, cwd, extraEnv)).redirectErrorStream(true)
        pb.environment().putAll(proot.env())
        val p = pb.start()
        val rd = launch { runCatching { p.inputStream.bufferedReader().forEachLine { onLine(it); logLine(it) } } }
        val done = try { runInterruptible { p.waitFor(timeoutSec, TimeUnit.SECONDS) } } finally { if (p.isAlive) p.destroyForcibly() }
        if (!done) { rd.cancel(); return@withContext -1 }
        rd.join()
        p.exitValue()
    }

    /** git, ripgrep и т. п. — без них агент в Ubuntu почти бесполезен. */
    suspend fun installDevTools(onLine: (String) -> Unit): Int {
        val script = "apt-get update && apt-get install -y --no-install-recommends git ripgrep curl ca-certificates openssh-client unzip less nano python3 build-essential"
        return runGuest(listOf("/bin/bash", "-lc", script), onLine = onLine)
    }

    fun resetRuntime() {
        Vault.save()
        stopServer()
        paths.rootfs.deleteRecursively()
        paths.downloads.deleteRecursively()
        prefs.runtimeReady = false
        state.value = RuntimeState.NotInstalled
    }
}
