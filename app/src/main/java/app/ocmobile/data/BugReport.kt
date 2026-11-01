package app.ocmobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.ocmobile.BuildConfig
import app.ocmobile.Graph

/**
 * "Report a bug": collects the description, phone details and the tail of the server log, and opens
 * a new GitHub issue with the text filled in. The user sees everything on GitHub before sending.
 * The server password and tokens are cut out of the log.
 */
object BugReport {
    private const val REPO = "mistqkw/opencode-mobile"
    // GitHub cuts links that are too long; the log is shortened so everything fits
    private const val MAX_URL = 7000

    fun body(what: String, withLog: Boolean): String {
        val rt = Graph.runtime.state.value
        val info = listOf(
            "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Interface: ${Graph.prefs.edition}",
            "Server: ${if (Graph.devices.isRemote) "remote" else rt::class.simpleName}",
        ).joinToString("\n")
        val sb = StringBuilder()
        sb.append("**What happened**\n\n").append(what.trim().ifBlank { "(describe what you did and what went wrong)" }).append("\n\n")
        sb.append("**Device**\n\n```\n").append(info).append("\n```\n")
        if (withLog) {
            val log = clean(Graph.runtime.log.value.takeLast(60))
            if (log.isNotEmpty()) sb.append("\n**Server log (last lines)**\n\n```\n").append(log.joinToString("\n")).append("\n```\n")
        }
        return sb.toString()
    }

    /** Removes the server password and anything that looks like keys and tokens from log lines. */
    private fun clean(lines: List<String>): List<String> {
        val pass = runCatching { Graph.prefs.serverPassword }.getOrDefault("")
        val secret = Regex("""(?i)(password|token|secret|api[_-]?key|authorization)(["'\s:=]+)\S+""")
        val keyLike = Regex("""\b(sk|ghp|gho|ghu|github_pat|xox[abp])[-_][A-Za-z0-9_\-]{8,}""")
        return lines.map { l ->
            var s = if (pass.isNotEmpty()) l.replace(pass, "***") else l
            s = secret.replace(s) { it.groupValues[1] + it.groupValues[2] + "***" }
            keyLike.replace(s, "***")
        }
    }

    fun open(ctx: Context, title: String, what: String, withLog: Boolean) {
        var b = body(what, withLog)
        fun url() = "https://github.com/$REPO/issues/new?title=" + Uri.encode(title.ifBlank { "Bug: " }) + "&body=" + Uri.encode(b)
        // if it doesn't fit, trim the log from the top first, then drop it entirely
        while (url().length > MAX_URL && b.contains("**Server log")) {
            val lines = b.lines().toMutableList()
            val start = lines.indexOfFirst { it.startsWith("**Server log") } + 3
            if (start >= lines.size - 2) { b = body(what, false); break }
            lines.removeAt(start); b = lines.joinToString("\n")
        }
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
