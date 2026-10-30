package app.ocmobile.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.ocmobile.BuildConfig
import app.ocmobile.Graph
import app.ocmobile.runtime.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Обновления из релизов GitHub: раз в несколько часов смотрит mistqkw/opencode-mobile,
 * сравнивает версию с установленной, по «Install» качает APK и отдаёт его системному установщику.
 */
object Updater {
    const val REPO = "mistqkw/opencode-mobile"
    private const val EVERY_MS = 6 * 60 * 60 * 1000L

    data class Release(val tag: String, val name: String, val notes: String, val apkUrl: String, val size: Long, val page: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val r: Release) : State
        data class Downloading(val r: Release, val progress: Float) : State
        data class Installing(val r: Release) : State
        data class Failed(val message: String, val r: Release? = null) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    private var job: Job? = null
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    val current: String get() = BuildConfig.VERSION_NAME

    /** Автопроверка при запуске: не чаще раза в 6 часов и только если она включена. */
    fun autoCheck() {
        val p = Graph.prefs
        if (!p.updateAuto || System.currentTimeMillis() - p.updateCheckedAt < EVERY_MS) return
        check()
    }

    fun check() {
        if (job?.isActive == true) return
        state.value = State.Checking
        job = Graph.scope.launch {
            state.value = runCatching { withContext(Dispatchers.IO) { latest() } }.fold(
                onSuccess = { r ->
                    Graph.prefs.updateCheckedAt = System.currentTimeMillis()
                    if (r != null && compare(r.tag, current) > 0) State.Available(r) else State.UpToDate
                },
                onFailure = { State.Failed(it.message ?: "Could not reach GitHub") },
            )
        }
    }

    private fun latest(): Release? {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases?per_page=10")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "opencode-mobile").build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("GitHub answered HTTP ${r.code}")
            r.body.string()
        }
        return Json.parseToJsonElement(body).jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            if (o["draft"]?.jsonPrimitive?.boolean == true) return@mapNotNull null
            val apk = (o["assets"] as? JsonArray)?.map { it.jsonObject }?.firstOrNull { it.str("name").endsWith(".apk") } ?: return@mapNotNull null
            Release(
                tag = o.str("tag_name"), name = o.str("name").ifBlank { o.str("tag_name") }, notes = o.str("body"),
                apkUrl = apk.str("browser_download_url"), size = apk["size"]?.jsonPrimitive?.long ?: 0, page = o.str("html_url"),
            )
        }.maxWithOrNull { a, b -> compare(a.tag, b.tag) }
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content.orEmpty()

    /** «v0.0.1-beta-3» против «0.0.1 beta-2»: сначала числа, потом стадия (alpha < beta < rc < релиз) и её номер. */
    fun compare(a: String, b: String): Int {
        fun key(v: String): List<Int> {
            val m = Regex("""(\d+)\.(\d+)(?:\.(\d+))?(?:[\s._-]*(alpha|beta|rc)[\s._-]*(\d+)?)?""", RegexOption.IGNORE_CASE).find(v) ?: return listOf(0, 0, 0, 0, 0)
            val stage = when (m.groupValues[4].lowercase()) { "alpha" -> 0; "beta" -> 1; "rc" -> 2; else -> 3 }
            return listOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0, stage, m.groupValues[5].toIntOrNull() ?: 0)
        }
        val ka = key(a); val kb = key(b)
        for (i in ka.indices) if (ka[i] != kb[i]) return ka[i].compareTo(kb[i])
        return 0
    }

    /** Можно ли ставить APK: Android спрашивает разрешение «Установка неизвестных приложений» один раз. */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) = ctx.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    /** Скачать и сразу передать установщику. */
    fun install(ctx: Context, r: Release) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        state.value = State.Downloading(r, 0f)
        job = Graph.scope.launch {
            try {
                val apk = File(app.cacheDir, "updates/${r.tag}.apk")
                withContext(Dispatchers.IO) {
                    File(app.cacheDir, "updates").listFiles()?.filter { it.name != apk.name && it.name != apk.name + ".part" }?.forEach { it.delete() }
                    Downloader.fetch(r.apkUrl, apk, null) { done, total ->
                        state.value = State.Downloading(r, if (total > 0) done.toFloat() / total else 0f)
                    }
                    if (r.size > 0 && apk.length() != r.size) { apk.delete(); error("The download was cut short, try again") }
                }
                state.value = State.Installing(r)
                withContext(Dispatchers.IO) { commit(app, apk) }
            } catch (e: Exception) {
                state.value = State.Failed(e.message ?: "Update failed", r)
            }
        }
    }

    private fun commit(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out, 1 shl 16) }; s.fsync(out) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val p = PendingIntent.getBroadcast(ctx, id, Intent(ctx, Receiver::class.java).setPackage(ctx.packageName), flags)
            s.commit(p.intentSender)
        }
    }

    /** Ответ установщика: попросить подтверждение у пользователя или сообщить об ошибке. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val r = (state.value as? State.Installing)?.r
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> state.value = State.UpToDate
                PackageInstaller.STATUS_FAILURE_ABORTED -> state.value = if (r != null) State.Available(r) else State.Idle
                else -> state.value = State.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android refused the update", r)
            }
        }
    }
}
