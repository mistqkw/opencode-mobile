package app.ocmobile.data

import app.ocmobile.Graph
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * cl0udly's working rules for OpenCode, the same plugin as in cl0udly for desktop
 * (assets/brain/cl0udly-brain.js): brief replies and a check of every step, long tool output cut
 * to its head and tail with the whole text in a file, and a nudge when the same call repeats.
 * The plugin re-reads cl0udly-brain.json on every request; the helpers' list goes with
 * the team note ([Team.instruction]), so the json leaves it empty.
 */
object Brain {
    private const val PLUGIN = "cl0udly-brain.js"
    private val configDir: File get() = Graph.paths.host("/root/.config/opencode")

    /** Copies the plugin into the environment; the server loads it when it starts. */
    fun install() {
        runCatching {
            val bytes = Graph.app.assets.open("brain/$PLUGIN").use { it.readBytes() }
            val f = File(configDir, "plugin/$PLUGIN")
            f.parentFile?.mkdirs()
            if (!f.exists() || !f.readBytes().contentEquals(bytes)) f.writeBytes(bytes)
        }
        writeSettings()
    }

    fun writeSettings() {
        runCatching {
            val p = Graph.prefs
            configDir.mkdirs()
            File(configDir, "cl0udly-brain.json").writeText(buildJsonObject {
                put("style", p.brainStyle)
                put("pruneChars", p.brainPrune.toIntOrNull() ?: 8000)
                put("helpers", buildJsonArray { })
            }.toString())
        }
    }
}
