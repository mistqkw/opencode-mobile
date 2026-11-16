package app.ocmobile.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Pass-through for the OpenAI-compatible providers the app adds itself (Pollinations, Copilot).
 * Their streams sometimes start a tool call without an "id" or "type", and the AI SDK inside OpenCode
 * then fails the whole answer with "Expected 'id' to be a string" (issue #1). The proxy fills them in.
 * GPT-OSS on Pollinations also starts some calls without a name ("Expected 'function.name' to be a
 * string"), and the broken call stays in the chat, so every later message fails too. Such a call is
 * held back until its name comes; if it never does, it is left out and the answer ends as plain text.
 * OpenCode reaches it at http://127.0.0.1:<bridge port>/llm/<token>/<provider>/…
 */
object ToolCallFix {
    val upstream = mapOf(
        FreeModels.POLLINATIONS_ID to "https://text.pollinations.ai/openai",
        FreeModels.COPILOT_ID to "https://api.githubcopilot.com",
    )

    /** ChatHub looks for these words to move the chat to Pollinations. */
    const val COPILOT_CAPPED = "Copilot's free limit for this month is used up."

    fun baseUrl(provider: String) = "http://127.0.0.1:${PhoneBridge.PORT}/llm/${PhoneBridge.token}/$provider"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).build()

    // headers the providers need; hop-by-hop ones (host, length, connection) are not copied
    private val passHeaders = listOf("authorization", "content-type", "accept", "user-agent", "copilot-integration-id", "editor-version", "openai-intent", "x-initiator")

    /** [rest] is the path after the provider, for example "/chat/completions". */
    fun forward(out: OutputStream, method: String, provider: String, rest: String, headers: Map<String, String>, body: ByteArray) {
        val base = upstream[provider] ?: return status(out, 404)
        val req = Request.Builder().url(base + rest).apply {
            passHeaders.forEach { h -> headers[h]?.let { header(h, it) } }
            if (provider == FreeModels.COPILOT_ID && method == "POST") {
                header("x-initiator", initiator(body)); header("openai-intent", "conversation-edits")
            }
            if (method == "GET") get() else method(method, body.toRequestBody(headers["content-type"]?.toMediaTypeOrNull()))
        }.build()
        val resp = runCatching { client.newCall(req).execute() }.getOrElse { return status(out, 502) }
        resp.use { r ->
            if (!r.isSuccessful) {
                val b = errorBody(provider, r.code, r.body.string()).toByteArray()
                out.write("HTTP/1.1 ${r.code} Error\r\nContent-Type: application/json\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray())
                out.write(b); out.flush(); return
            }
            val type = r.header("content-type") ?: "application/json"
            // no length: the body ends when the connection closes, so the stream can go out line by line
            out.write("HTTP/1.1 ${r.code} OK\r\nContent-Type: $type\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            val src = r.body.source()
            if (!type.contains("event-stream")) { out.write(src.readByteArray()); out.flush(); return }
            val stream = Stream()
            while (true) {
                val line = src.readUtf8Line() ?: break
                out.write((fixLine(line, stream) + "\n").toByteArray())
                if (line.isEmpty()) out.flush()
            }
            out.flush()
        }
    }

    /**
     * Copilot counts every request marked "user" against the free plan's ~50 chat messages a month.
     * OpenCode marks its own Copilot requests, but not ours, so each step of the agent (a tool result
     * going back) used one up and the month was gone in an hour. Only a request that ends with your
     * message is "user"; the steps after it are "agent", as in OpenCode and VS Code.
     */
    fun initiator(body: ByteArray): String = runCatching {
        val msgs = app.ocmobile.api.OcJson.parseToJsonElement(body.decodeToString()).jsonObject["messages"] as JsonArray
        if ((((msgs.last() as JsonObject)["role"]) as JsonPrimitive).content == "user") "user" else "agent"
    }.getOrDefault("user")

    /**
     * A refused request as the OpenAI error the SDK reads. Copilot answers errors in plain text, and the
     * SDK, unable to parse it, showed only the status line ("OK" from us): the reason
     * goes in readable words, with what the provider said after it.
     */
    fun errorBody(provider: String, code: Int, raw: String): String {
        val said = raw.trim().take(300)
        val hint = when {
            provider == "copilot-chat" && (code == 402 || code == 429 || Regex("quota|limit|exceeded", RegexOption.IGNORE_CASE).containsMatchIn(said)) -> COPILOT_CAPPED
            provider == "copilot-chat" && code == 401 -> "GitHub didn't accept the sign-in. Sign in to GitHub again in the model list."
            provider == "copilot-chat" && code == 403 -> "This GitHub account can't use Copilot. Turn on Copilot Free at github.com/settings/copilot, then try again."
            provider == "copilot-chat" && code == 400 && said.contains("model", true) -> "Copilot doesn't offer this model on your plan. Pick another one."
            code == 429 -> "Too many requests to the free model. Wait a minute and try again."
            else -> "The model's server refused the request ($code)."
        }
        val msg = if (said.isEmpty()) hint else "$hint ($code: $said)"
        return JsonObject(mapOf("error" to JsonObject(mapOf("message" to JsonPrimitive(msg), "code" to JsonPrimitive(code))))).toString()
    }

    /** What one streamed answer has passed on so far. */
    class Stream {
        internal val named = HashSet<String>()
        /** Calls that started without a name: their id and the arguments that came meanwhile. */
        internal val waiting = HashMap<String, Pair<String?, String>>()
    }

    /**
     * One SSE line. The first chunk of each tool call goes out with an id, a type and a name: if it
     * came without the name, it waits for it. A finish of "tool_calls" with no call passed on becomes "stop".
     */
    fun fixLine(line: String, s: Stream): String {
        if (!line.startsWith("data:") || !(line.contains("tool_calls") || line.contains("finish_reason"))) return line
        val json = runCatching { app.ocmobile.api.OcJson.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject }.getOrNull() ?: return line
        val choices = json["choices"] as? JsonArray ?: return line
        var changed = false
        val fixed = JsonArray(choices.map { ch ->
            var c = ch as? JsonObject ?: return@map ch
            val ci = (c["index"] as? JsonPrimitive)?.content ?: "0"
            val delta = c["delta"] as? JsonObject
            val calls = delta?.get("tool_calls") as? JsonArray
            if (delta != null && calls != null) {
                val kept = calls.mapIndexedNotNull { n, tc ->
                    val t = tc as? JsonObject ?: return@mapIndexedNotNull tc
                    val key = ci + ":" + ((t["index"] as? JsonPrimitive)?.content ?: n.toString())
                    if (key in s.named) return@mapIndexedNotNull tc
                    val fn = t["function"] as? JsonObject
                    val name = (fn?.get("name") as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }
                    val id = (t["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    val args = (fn?.get("arguments") as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
                    val held = s.waiting.remove(key)
                    if (name == null) {
                        s.waiting[key] = (held?.first ?: id) to (held?.second.orEmpty() + args)
                        changed = true
                        return@mapIndexedNotNull null
                    }
                    s.named.add(key)
                    val m = t.toMutableMap()
                    if (id == null) { m["id"] = JsonPrimitive(held?.first ?: ("call_" + key.replace(':', '_') + "_" + System.nanoTime().toString(36))); changed = true }
                    if ((m["type"] as? JsonPrimitive)?.content != "function") { m["type"] = JsonPrimitive("function"); changed = true }
                    if (held != null && held.second.isNotEmpty()) {
                        m["function"] = JsonObject(fn!! + ("arguments" to JsonPrimitive(held.second + args))); changed = true
                    }
                    JsonObject(m)
                }
                c = JsonObject(c + ("delta" to JsonObject(if (kept.isEmpty()) delta - "tool_calls" else delta + ("tool_calls" to JsonArray(kept)))))
            }
            if ((c["finish_reason"] as? JsonPrimitive)?.content == "tool_calls" && s.named.none { it.startsWith("$ci:") }) {
                c = JsonObject(c + ("finish_reason" to JsonPrimitive("stop"))); changed = true
            }
            c
        })
        return if (changed) "data: " + JsonObject(json + ("choices" to fixed)) else line
    }

    private fun status(out: OutputStream, code: Int) {
        out.write("HTTP/1.1 $code Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush()
    }
}
