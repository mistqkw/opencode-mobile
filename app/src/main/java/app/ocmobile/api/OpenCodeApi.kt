package app.ocmobile.api

import app.ocmobile.Graph
import app.ocmobile.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val code: Int, message: String) : IOException(message)

/** Клиент REST-API локального `opencode serve` (OpenCode 1.x). */
class OpenCodeApi(private val prefs: Prefs) {
    val baseUrl get() = Graph.devices.active()?.url ?: "http://127.0.0.1:${prefs.serverPort}"
    private val password get() = Graph.devices.active()?.password ?: prefs.serverPassword
    private val jsonType = "application/json".toMediaType()

    val http: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 32 })
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Клиент для SSE: чтение без таймаута. */
    val streamHttp: OkHttpClient = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    fun request(path: String, query: Map<String, String?> = emptyMap()): Request.Builder {
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
        return Request.Builder().url(url).header("Authorization", Credentials.basic("opencode", password))
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { c ->
        c.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!c.isCancelled) c.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { c.resume(response) }
        })
    }

    private suspend fun call(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: JsonElement? = null,
    ): JsonElement? {
        val rb = body?.let { OcJson.encodeToString(JsonElement.serializer(), it).toRequestBody(jsonType) }
            ?: if (method == "GET" || method == "DELETE" && body == null) null else "{}".toRequestBody(jsonType)
        val req = request(path, query).method(method, rb).build()
        return http.newCall(req).await().use { r ->
            val text = withContext(Dispatchers.IO) { r.body.string() }
            if (!r.isSuccessful) throw ApiException(r.code, errorText(text, r.code))
            if (text.isBlank()) null else runCatching { OcJson.parseToJsonElement(text) }.getOrNull()
        }
    }

    private fun errorText(body: String, code: Int): String {
        val o = runCatching { OcJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val msg = o?.str("message") ?: (o?.obj("data"))?.str("message") ?: (o?.arr("error")?.firstOrNull() as? JsonObject)?.str("message")
        return msg ?: "HTTP $code" + body.take(200).let { if (it.isBlank()) "" else ": $it" }
    }

    private fun dir(d: String?) = mapOf("directory" to d)

    // ---------- сервер ----------

    /** Для опроса готовности: короткий таймаут, чтобы подвисший запрос не задерживал следующую попытку. */
    private val healthHttp by lazy { http.newBuilder().callTimeout(2, TimeUnit.SECONDS).build() }

    suspend fun healthVersionOrThrow(): String? = healthHttp.newCall(request("/global/health").build()).await().use { r ->
        val text = withContext(Dispatchers.IO) { r.body.string() }
        if (!r.isSuccessful) throw ApiException(r.code, "health ${r.code}")
        (runCatching { OcJson.parseToJsonElement(text) as? JsonObject }.getOrNull())?.takeIf { it.bool("healthy") == true }?.str("version")
    }

    suspend fun healthVersion(): String? = try {
        (call("GET", "/global/health") as? JsonObject)?.takeIf { it.bool("healthy") == true }?.str("version")
    } catch (_: Exception) { null }

    /** Проверка адреса и пароля удалённого сервера до сохранения; возвращает версию OpenCode или бросает ошибку с причиной. */
    suspend fun probe(url: String, password: String): String {
        val req = Request.Builder().url(url.trimEnd('/') + "/global/health").header("Authorization", Credentials.basic("opencode", password)).build()
        return http.newCall(req).await().use { r ->
            val text = withContext(Dispatchers.IO) { r.body.string() }
            when {
                r.code == 401 -> throw ApiException(401, "Wrong password")
                !r.isSuccessful -> throw ApiException(r.code, "Server answered ${r.code}")
                else -> (runCatching { OcJson.parseToJsonElement(text) as? JsonObject }.getOrNull())?.str("version") ?: "unknown"
            }
        }
    }

    // ---------- сессии ----------

    suspend fun listSessions(directory: String? = null, limit: Int = 300, search: String? = null): List<Session> =
        OcJson.decodeFromJsonElement(
            ListSerializer(Session.serializer()),
            call("GET", "/session", dir(directory) + mapOf("limit" to limit.toString(), "search" to search)) ?: JsonArray(emptyList()),
        )

    suspend fun createSession(directory: String, title: String? = null, permission: JsonArray? = null): Session {
        val body = buildJsonObject {
            title?.let { put("title", it) }
            permission?.let { put("permission", it) }
        }
        return OcJson.decodeFromJsonElement(Session.serializer(), call("POST", "/session", dir(directory), body)!!)
    }

    suspend fun getSession(id: String, directory: String?): Session =
        OcJson.decodeFromJsonElement(Session.serializer(), call("GET", "/session/$id", dir(directory))!!)

    suspend fun renameSession(id: String, directory: String?, title: String) {
        call("PATCH", "/session/$id", dir(directory), buildJsonObject { put("title", title) })
    }

    suspend fun archiveSession(id: String, directory: String?) {
        call("PATCH", "/session/$id", dir(directory), buildJsonObject { putJsonObject("time") { put("archived", System.currentTimeMillis()) } })
    }

    suspend fun setSessionPermission(id: String, directory: String?, rules: JsonArray) {
        call("PATCH", "/session/$id", dir(directory), buildJsonObject { put("permission", rules) })
    }

    suspend fun deleteSession(id: String, directory: String?) { call("DELETE", "/session/$id", dir(directory)) }

    suspend fun forkSession(id: String, directory: String?, messageID: String?): Session =
        OcJson.decodeFromJsonElement(
            Session.serializer(),
            call("POST", "/session/$id/fork", dir(directory), buildJsonObject { messageID?.let { put("messageID", it) } })!!,
        )

    suspend fun summarize(id: String, directory: String?, providerID: String, modelID: String) {
        call("POST", "/session/$id/summarize", dir(directory), buildJsonObject { put("providerID", providerID); put("modelID", modelID) })
    }

    suspend fun deleteMessage(id: String, directory: String?, messageID: String) { call("DELETE", "/session/$id/message/$messageID", dir(directory)) }

    suspend fun abort(id: String, directory: String?) { call("POST", "/session/$id/abort", dir(directory)) }

    suspend fun revert(id: String, directory: String?, messageID: String) {
        call("POST", "/session/$id/revert", dir(directory), buildJsonObject { put("messageID", messageID) })
    }

    suspend fun unrevert(id: String, directory: String?) { call("POST", "/session/$id/unrevert", dir(directory)) }

    suspend fun statuses(directory: String?): Map<String, JsonObject> =
        (call("GET", "/session/status", dir(directory)) as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to it } }?.toMap().orEmpty()

    suspend fun messages(id: String, directory: String?, limit: Int = 300): List<MessageWithParts> =
        parseMessages(call("GET", "/session/$id/message", dir(directory) + mapOf("limit" to limit.toString())) ?: JsonArray(emptyList()))

    suspend fun todos(id: String, directory: String?): JsonArray =
        call("GET", "/session/$id/todo", dir(directory)) as? JsonArray ?: JsonArray(emptyList())

    suspend fun diff(id: String, directory: String?): JsonArray =
        call("GET", "/session/$id/diff", dir(directory)) as? JsonArray ?: JsonArray(emptyList())

    /** [parts] — готовые части: текст и файлы (картинки — data: URL). */
    suspend fun promptAsync(
        id: String,
        directory: String?,
        parts: JsonArray,
        providerID: String?,
        modelID: String?,
        agent: String?,
        variant: String?,
    ) {
        val body = buildJsonObject {
            if (!providerID.isNullOrBlank() && !modelID.isNullOrBlank()) {
                putJsonObject("model") { put("providerID", providerID); put("modelID", modelID) }
            }
            if (!agent.isNullOrBlank()) put("agent", agent)
            if (!variant.isNullOrBlank()) put("variant", variant)
            put("parts", parts)
        }
        call("POST", "/session/$id/prompt_async", dir(directory), body)
    }

    // ---------- разрешения и вопросы ----------

    suspend fun permissions(directory: String?): List<PermissionReq> =
        (call("GET", "/permission", dir(directory)) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parsePermission) }

    suspend fun replyPermission(requestId: String, directory: String?, reply: String) {
        call("POST", "/permission/$requestId/reply", dir(directory), buildJsonObject { put("reply", reply) })
    }

    suspend fun questions(directory: String?): List<QuestionReq> =
        (call("GET", "/question", dir(directory)) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseQuestion) }

    suspend fun replyQuestion(requestId: String, directory: String?, answers: List<List<String>>) {
        call("POST", "/question/$requestId/reply", dir(directory), buildJsonObject {
            putJsonArray("answers") { answers.forEach { a -> add(buildJsonArray { a.forEach { add(JsonPrimitive(it)) } }) } }
        })
    }

    suspend fun rejectQuestion(requestId: String, directory: String?) { call("POST", "/question/$requestId/reject", dir(directory)) }

    // ---------- провайдеры и модели ----------

    suspend fun connectedProviders(): ProvidersResponse =
        OcJson.decodeFromJsonElement(ProvidersResponse.serializer(), call("GET", "/config/providers")!!)

    suspend fun allProviders(): AllProvidersResponse =
        OcJson.decodeFromJsonElement(AllProvidersResponse.serializer(), call("GET", "/provider")!!)

    suspend fun providerAuthMethods(): Map<String, List<AuthMethod>> =
        OcJson.decodeFromJsonElement(
            MapSerializer(String.serializer(), ListSerializer(AuthMethod.serializer())),
            call("GET", "/provider/auth") ?: JsonObject(emptyMap()),
        )

    suspend fun setApiKey(providerId: String, key: String) {
        call("PUT", "/auth/$providerId", body = buildJsonObject { put("type", "api"); put("key", key) })
    }

    suspend fun removeAuth(providerId: String) { call("DELETE", "/auth/$providerId") }

    /** Возвращает ссылку для входа и подсказку; method — индекс из [providerAuthMethods]. */
    suspend fun oauthAuthorize(providerId: String, method: Int, inputs: Map<String, String> = emptyMap()): JsonObject =
        call("POST", "/provider/$providerId/oauth/authorize", body = buildJsonObject {
            put("method", method)
            if (inputs.isNotEmpty()) putJsonObject("inputs") { inputs.forEach { (k, v) -> put(k, v) } }
        }) as? JsonObject ?: JsonObject(emptyMap())

    /** Вход по коду устройства ждёт, пока пользователь подтвердит его в браузере, — отсюда долгий таймаут. */
    private val longHttp by lazy { http.newBuilder().readTimeout(10, TimeUnit.MINUTES).callTimeout(0, TimeUnit.MILLISECONDS).build() }

    suspend fun oauthCallback(providerId: String, method: Int, code: String?) {
        val body = OcJson.encodeToString(JsonElement.serializer(), buildJsonObject { put("method", method); code?.let { put("code", it) } }).toRequestBody(jsonType)
        val req = request("/provider/$providerId/oauth/callback").post(body).build()
        longHttp.newCall(req).await().use { r ->
            val text = withContext(Dispatchers.IO) { r.body.string() }
            if (!r.isSuccessful) throw ApiException(r.code, errorText(text, r.code))
        }
    }

    /** После смены ключей инстансы надо пересоздать, чтобы они увидели новые провайдеры. */
    suspend fun disposeAll() { call("POST", "/global/dispose") }

    // ---------- конфиг, агенты, MCP ----------

    suspend fun config(): JsonObject = call("GET", "/config") as? JsonObject ?: JsonObject(emptyMap())

    /** Глобальный конфиг (~/.config/opencode): сервер сам пишет файл и применяет изменения без перезапуска. */
    suspend fun patchGlobalConfig(patch: JsonObject): JsonObject = call("PATCH", "/global/config", body = patch) as? JsonObject ?: JsonObject(emptyMap())

    suspend fun patchConfig(patch: JsonObject): JsonObject = call("PATCH", "/config", body = patch) as? JsonObject ?: JsonObject(emptyMap())

    suspend fun agents(): List<AgentInfo> =
        OcJson.decodeFromJsonElement(ListSerializer(AgentInfo.serializer()), call("GET", "/agent") ?: JsonArray(emptyList()))

    suspend fun commands(): List<NamedItem> =
        OcJson.decodeFromJsonElement(ListSerializer(NamedItem.serializer()), call("GET", "/command") ?: JsonArray(emptyList()))

    suspend fun skills(): List<NamedItem> =
        OcJson.decodeFromJsonElement(ListSerializer(NamedItem.serializer()), call("GET", "/skill") ?: JsonArray(emptyList()))

    suspend fun mcpStatus(): JsonObject = call("GET", "/mcp") as? JsonObject ?: JsonObject(emptyMap())

    suspend fun mcpConnect(name: String) { call("POST", "/mcp/$name/connect") }
    suspend fun mcpDisconnect(name: String) { call("POST", "/mcp/$name/disconnect") }

    suspend fun mcpAdd(name: String, config: JsonObject) {
        call("POST", "/mcp", body = buildJsonObject { put("name", name); put("config", config) })
    }

    /** Рабочий каталог сервера по умолчанию (для удалённого устройства, где своих проектов у приложения нет). */
    suspend fun serverDirectory(): String? = (call("GET", "/path") as? JsonObject)?.str("directory")

    suspend fun projects(): JsonArray = call("GET", "/project") as? JsonArray ?: JsonArray(emptyList())

    suspend fun findFiles(query: String, directory: String?): List<String> =
        (call("GET", "/find/file", dir(directory) + mapOf("query" to query, "limit" to "20")) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

    private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())
}
