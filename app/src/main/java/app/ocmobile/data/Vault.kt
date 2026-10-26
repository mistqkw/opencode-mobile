package app.ocmobile.data

import app.ocmobile.Graph
import org.json.JSONObject
import java.io.File

/**
 * Запасная копия того, что нельзя терять: вход в аккаунт (auth.json), память и правила.
 * Лежит рядом с rootfs, но вне него: переустановка окружения или «Reset runtime» её не трогают,
 * а Android Auto Backup возвращает её после переустановки приложения.
 */
object Vault {
    private val root get() = File(Graph.paths.rootfs.parentFile, "vault")
    private const val AUTH = "root/.local/share/opencode/auth.json"
    private const val RULES = "root/.config/opencode/AGENTS.md"
    private const val CONFIG = "root/.config/opencode/opencode.jsonc"
    private const val MEMORY = "root/memory"

    private fun copyIfNewer(from: File, to: File) {
        if (!from.isFile) return
        if (to.isFile && to.length() == from.length() && to.lastModified() >= from.lastModified()) return
        to.parentFile?.mkdirs()
        from.copyTo(to, overwrite = true)
        to.setLastModified(from.lastModified())
    }

    /** Из rootfs в хранилище. Пустой или повреждённый auth.json не затирает хорошую копию. */
    fun save() {
        runCatching {
            val rf = Graph.paths.rootfs
            val auth = File(rf, AUTH)
            if (auth.isFile && auth.length() > 2 && runCatching { JSONObject(auth.readText()).length() > 0 }.getOrDefault(false)) copyIfNewer(auth, File(root, AUTH))
            copyIfNewer(File(rf, RULES), File(root, RULES))
            copyIfNewer(File(rf, CONFIG), File(root, CONFIG))
            File(rf, MEMORY).listFiles()?.forEach { copyIfNewer(it, File(root, "$MEMORY/${it.name}")) }
        }
    }

    /** Из хранилища в rootfs — только то, чего там нет (после переустановки окружения). */
    fun restore() {
        runCatching {
            val rf = Graph.paths.rootfs
            fun back(rel: String) { val v = File(root, rel); val t = File(rf, rel); if (v.isFile && !t.isFile) { t.parentFile?.mkdirs(); v.copyTo(t) } }
            back(AUTH); back(RULES); back(CONFIG)
            File(root, MEMORY).listFiles()?.forEach { back("$MEMORY/${it.name}") }
        }
    }

    /** Явный выход: стираем и копию, иначе вход вернулся бы после сброса окружения. */
    fun forgetAccount() { runCatching { File(root, AUTH).delete() } }

    /** Флаг «вошли» в настройках берём из того, что реально сохранено на сервере; сам он никогда не сбрасывается. */
    fun reconcileAccount() {
        runCatching {
            if (Graph.prefs.signedIn.isNotBlank()) return
            val f = File(Graph.paths.rootfs, AUTH)
            if (!f.isFile) return
            val keys = JSONObject(f.readText()).keys().asSequence().toSet()
            val m = when {
                "github-copilot" in keys -> "github"
                keys.any { it == "opencode" || it == "opencode-go" || it == "google" || it == "groq" || it == "openrouter" } -> "key"
                else -> ""
            }
            if (m.isNotEmpty()) { Graph.prefs.signedIn = m; Graph.prefs.loginSeen = true }
        }
    }
}
