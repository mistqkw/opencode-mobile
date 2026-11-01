package app.ocmobile.data

import app.ocmobile.Graph
import org.json.JSONObject
import java.io.File

/**
 * A backup of what must not be lost: the account sign-in (auth.json), memory and rules.
 * Lives next to the rootfs but outside it: reinstalling the environment or "Reset runtime" doesn't touch it,
 * and Android Auto Backup brings it back after the app is reinstalled.
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

    /** From the rootfs to the vault. An empty or broken auth.json doesn't overwrite a good copy. */
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

    /** From the vault to the rootfs: only what's missing there (after reinstalling the environment). */
    fun restore() {
        runCatching {
            val rf = Graph.paths.rootfs
            fun back(rel: String) { val v = File(root, rel); val t = File(rf, rel); if (v.isFile && !t.isFile) { t.parentFile?.mkdirs(); v.copyTo(t) } }
            back(AUTH); back(RULES); back(CONFIG)
            File(root, MEMORY).listFiles()?.forEach { back("$MEMORY/${it.name}") }
        }
    }

    /** Explicit sign-out: wipe the copy too, otherwise the sign-in would come back after a runtime reset. */
    fun forgetAccount() { runCatching { File(root, AUTH).delete() } }

    /** The "signed in" flag in settings comes from what's actually saved on the server; the flag itself is never reset. */
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
