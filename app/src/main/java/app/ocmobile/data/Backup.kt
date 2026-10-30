package app.ocmobile.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import app.ocmobile.Graph
import app.ocmobile.MainActivity
import app.ocmobile.ui.theme.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Перенос всего на другую сборку или телефон: домашняя папка Ubuntu (чаты, вход в провайдеры, память,
 * навыки, проекты, git и gh) плюс настройки приложения — одним архивом в «Загрузки/OpenCode».
 */
object Backup {
    private const val PREFS_FILE = "/root/.ocmobile-prefs.json"
    private const val TMP = "/tmp/ocmobile-backup.tgz"
    /** Тяжёлое и восстановимое не берём. */
    private val EXCLUDE = listOf("root/.cache", "root/.npm", "root/.bun/install/cache", "root/.local/share/opencode/log", "root/.local/share/opencode/bin", "root/outbox/opencode-backup-*", "*/node_modules")

    sealed interface State {
        data object Idle : State
        data class Working(val step: String) : State
        data class Saved(val where: String, val bytes: Long) : State
        data object Restored : State
        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)

    private fun prefsJson(ctx: Context): String = buildJsonObject {
        ctx.getSharedPreferences("oc", Context.MODE_PRIVATE).all.forEach { (k, v) ->
            when (v) {
                is String -> put(k, v)
                is Boolean -> put(k, v)
                is Int -> put(k, "i:$v")
                is Long -> put(k, v)
                is Float -> put(k, "f:$v")
                else -> {}
            }
        }
    }.toString()

    /** Сервер на время архива останавливаем: база чатов — SQLite, её нельзя копировать на ходу. */
    private suspend fun <T> withServerStopped(block: suspend () -> T): T {
        val rt = Graph.runtime
        rt.stopServer()
        // старый цикл сервера должен целиком закончиться, иначе он допишет «остановлен» поверх нового запуска
        kotlinx.coroutines.withTimeoutOrNull(20_000) { while (rt.serverAlive) delay(200) }
        delay(800)
        try { return block() } finally { rt.startServer() }
    }

    suspend fun export(ctx: Context) = withContext(Dispatchers.IO) {
        try {
            state.value = State.Working("Stopping the server")
            val out = withServerStopped {
                Graph.paths.host(PREFS_FILE).writeText(prefsJson(ctx))
                state.value = State.Working("Packing chats, memory, skills and projects")
                val ex = EXCLUDE.joinToString(" ") { "--exclude='$it'" }
                val log = StringBuilder()
                val rc = Graph.runtime.runGuest(listOf("/bin/bash", "-lc", "rm -f $TMP; tar -czf $TMP $ex -C / root"), timeoutSec = 1800) { log.appendLine(it) }
                // tar отвечает 1, если файл поменялся на ходу, — архив при этом годный
                if (rc != 0 && rc != 1) error("tar failed: " + log.lines().takeLast(3).joinToString(" "))
                Graph.paths.host(TMP)
            }
            state.value = State.Working("Saving to Downloads")
            val name = "opencode-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".tar.gz"
            val size = out.length()
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, "application/gzip")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenCode")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("Android refused to create the file")
                ctx.contentResolver.openOutputStream(uri)!!.use { o -> out.inputStream().use { it.copyTo(o) } }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OpenCode").apply { mkdirs() }
                out.copyTo(File(dir, name), overwrite = true)
            }
            out.delete()
            Graph.paths.host(PREFS_FILE).delete()
            state.value = State.Saved("Downloads/OpenCode/$name", size)
        } catch (e: Exception) {
            state.value = State.Failed(e.message ?: "Backup failed")
        }
    }

    /** Восстановление: нужен установленный Ubuntu. После — перезапуск приложения, чтобы подхватить настройки. */
    suspend fun restore(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            require(Graph.paths.rootfsReady) { "Set up the environment first, then restore." }
            state.value = State.Working("Copying the backup")
            val tmp = Graph.paths.host(TMP).apply { parentFile?.mkdirs() }
            ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } } ?: error("Could not read the file")
            state.value = State.Working("Stopping the server")
            withServerStopped {
                state.value = State.Working("Unpacking")
                val log = StringBuilder()
                val rc = Graph.runtime.runGuest(listOf("/bin/bash", "-lc", "tar -xzf $TMP -C / && rm -f $TMP"), timeoutSec = 1800) { log.appendLine(it) }
                if (rc != 0) error("tar failed: " + log.lines().takeLast(3).joinToString(" "))
            }
            val pf = Graph.paths.host(PREFS_FILE)
            if (pf.isFile) {
                applyPrefs(ctx, kotlinx.serialization.json.Json.parseToJsonElement(pf.readText()).jsonObject)
                pf.delete()
            }
            state.value = State.Restored
            delay(900)
            restart(ctx)
        } catch (e: Exception) {
            state.value = State.Failed(e.message ?: "Restore failed")
        }
    }

    private fun applyPrefs(ctx: Context, o: JsonObject) {
        val e = ctx.getSharedPreferences("oc", Context.MODE_PRIVATE).edit()
        o.forEach { (k, v) ->
            val p = v as? JsonPrimitive ?: return@forEach
            when {
                p.isString && p.content.startsWith("i:") -> p.content.drop(2).toIntOrNull()?.let { e.putInt(k, it) }
                p.isString && p.content.startsWith("f:") -> p.content.drop(2).toFloatOrNull()?.let { e.putFloat(k, it) }
                p.isString -> e.putString(k, p.content)
                p.booleanOrNull != null -> e.putBoolean(k, p.booleanOrNull!!)
                p.longOrNull != null -> e.putLong(k, p.longOrNull!!)
            }
        }
        e.putBoolean("onboarded", true)
        e.commit()
        o["app_icon"]?.let { (it as? JsonPrimitive)?.content }?.let { runCatching { AppIcons.apply(ctx, it) } }
    }

    /** Новый процесс с чистыми настройками: открываем приложение заново и завершаем этот. */
    private suspend fun restart(ctx: Context) {
        Graph.runtime.stopServer()
        withContext(Dispatchers.Main) {
            ctx.startActivity(Intent.makeRestartActivityTask(android.content.ComponentName(ctx, MainActivity::class.java)))
        }
        delay(300)
        Runtime.getRuntime().exit(0)
    }
}
