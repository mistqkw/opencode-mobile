package app.ocmobile.data

import app.ocmobile.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Вход в GitHub для окружения на телефоне: код устройства (как у `gh auth login --web`), потом
 * в Ubuntu ставятся git и gh, токен отдаётся gh, git настраивается на имя и noreply-почту аккаунта.
 * После этого агент клонирует, коммитит, пушит и открывает PR от имени пользователя.
 */
object GitHub {
    /** Публичный client id GitHub CLI: токен и правда достаётся gh. */
    private const val CLIENT_ID = "178c6fc778ccc68e1d6a"
    private const val SCOPES = "repo read:org gist workflow"

    val prompt = """
# Git and GitHub

- Commits are made as the user (git config user.name / user.email are already set). Never add "Co-Authored-By", "Generated with", "Signed-off-by" for yourself, or any other AI or assistant attribution to commit messages, pull requests, issues, release notes or code comments.
- If the user signed in to GitHub in the app, the gh CLI is logged in and git can push over https. Use gh for repos, pull requests, issues and releases.
""".trim()

    sealed interface State {
        data object Idle : State
        data class Code(val userCode: String, val url: String) : State
        data class Working(val step: String, val log: String = "") : State
        data object Done : State
        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    val signedIn get() = Graph.prefs.githubLogin.isNotBlank()

    private fun post(url: String, form: Map<String, String>): JsonObject {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(url).post(body).header("Accept", "application/json").build()
        http.newCall(req).execute().use { r -> return json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject }
    }

    private fun user(token: String): JsonObject {
        val req = Request.Builder().url("https://api.github.com/user").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("GitHub said ${r.code}")
            return json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
        }
    }

    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.takeIf { it.isString }?.content

    /** Весь вход: код → ожидание подтверждения → настройка окружения. */
    suspend fun signIn() = withContext(Dispatchers.IO) {
        try {
            val dc = post("https://github.com/login/device/code", mapOf("client_id" to CLIENT_ID, "scope" to SCOPES))
            val device = dc.s("device_code") ?: error(dc.s("error_description") ?: "GitHub did not give a code")
            val code = dc.s("user_code")!!
            var interval = (dc["interval"]?.jsonPrimitive?.int ?: 5).coerceAtLeast(5)
            val until = System.currentTimeMillis() + (dc["expires_in"]?.jsonPrimitive?.long ?: 900) * 1000
            state.value = State.Code(code, dc.s("verification_uri") ?: "https://github.com/login/device")
            var token: String? = null
            while (token == null) {
                if (state.value !is State.Code) return@withContext   // отменили
                if (System.currentTimeMillis() > until) error("The code expired. Try again.")
                delay(interval * 1000L)
                val r = post("https://github.com/login/oauth/access_token", mapOf("client_id" to CLIENT_ID, "device_code" to device, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))
                token = r.s("access_token")
                when (r.s("error")) {
                    null, "authorization_pending" -> {}
                    "slow_down" -> interval += 5
                    "access_denied" -> error("You declined the request on GitHub.")
                    "expired_token" -> error("The code expired. Try again.")
                    else -> if (token == null) error(r.s("error_description") ?: "GitHub sign-in failed")
                }
            }
            setup(token)
        } catch (e: Exception) {
            state.value = State.Failed(e.message ?: "GitHub sign-in failed")
        }
    }

    private suspend fun setup(token: String) {
        state.value = State.Working("Checking your account")
        val u = user(token)
        val login = u.s("login") ?: error("No login in the GitHub answer")
        val id = u["id"]?.jsonPrimitive?.long ?: 0
        val name = u.s("name")?.takeIf { it.isNotBlank() } ?: login
        val email = "$id+$login@users.noreply.github.com"

        state.value = State.Working("Installing git and GitHub CLI")
        val log = StringBuilder()
        val install = "command -v git >/dev/null && command -v gh >/dev/null || (apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends git gh ca-certificates)"
        var rc = Graph.runtime.runGuest(listOf("/bin/bash", "-lc", install), timeoutSec = 1200) { line ->
            log.appendLine(line); state.value = State.Working("Installing git and GitHub CLI", line.take(80))
        }
        if (rc != 0) error("Could not install git and gh:\n" + log.lines().takeLast(4).joinToString("\n"))

        state.value = State.Working("Signing in gh and git")
        val conf = """
            printf %s "${'$'}GH_T" | gh auth login --hostname github.com --git-protocol https --with-token && gh auth setup-git --hostname github.com &&
            git config --global user.name "${'$'}GIT_NAME" && git config --global user.email "${'$'}GIT_EMAIL" &&
            git config --global init.defaultBranch main && git config --global pull.rebase false
        """.trimIndent()
        log.clear()
        rc = Graph.runtime.runGuest(listOf("/bin/bash", "-lc", conf), extraEnv = mapOf("GH_T" to token, "GIT_NAME" to name, "GIT_EMAIL" to email)) { log.appendLine(it) }
        if (rc != 0) error("gh did not accept the sign-in:\n" + log.lines().takeLast(4).joinToString("\n"))

        Graph.prefs.githubLogin = login
        Graph.prefs.githubName = name
        Graph.prefs.githubAvatar = u.s("avatar_url").orEmpty()
        Memory.sync()
        state.value = State.Done
    }

    fun cancel() { state.value = State.Idle }

    suspend fun signOut() {
        runCatching { Graph.runtime.runGuest(listOf("/bin/bash", "-lc", "gh auth logout --hostname github.com </dev/null; git config --global --unset credential.https://github.com.helper; true")) }
        Graph.prefs.githubLogin = ""
        Graph.prefs.githubName = ""
        Graph.prefs.githubAvatar = ""
        state.value = State.Idle
    }

    /** Кем подписываются коммиты сейчас (из git config окружения). */
    suspend fun gitIdentity(): Pair<String, String>? {
        val out = StringBuilder()
        val rc = runCatching { Graph.runtime.runGuest(listOf("/bin/bash", "-lc", "git config --global user.name; git config --global user.email"), timeoutSec = 30) { out.appendLine(it) } }.getOrDefault(-1)
        val l = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (rc == 0 && l.size >= 2) l[0] to l[1] else null
    }
}
