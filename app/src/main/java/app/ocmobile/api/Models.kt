package app.ocmobile.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

val OcJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = false
}

@Serializable
data class CacheTokens(val read: Long = 0, val write: Long = 0)

@Serializable
data class Tokens(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val total: Long? = null,
    val cache: CacheTokens = CacheTokens(),
) {
    val sum get() = total ?: (input + output + reasoning + cache.read + cache.write)
}

@Serializable
data class TimeInfo(
    val created: Long = 0,
    val updated: Long = 0,
    val completed: Long? = null,
    val archived: Long? = null,
)

@Serializable
data class ModelRef(
    val id: String? = null,
    val modelID: String? = null,
    val providerID: String = "",
    val variant: String? = null,
) {
    val model get() = modelID ?: id ?: ""
}

@Serializable
data class Session(
    val id: String,
    val title: String = "",
    val directory: String = "",
    val projectID: String? = null,
    val parentID: String? = null,
    val cost: Double? = null,
    val tokens: Tokens? = null,
    val time: TimeInfo = TimeInfo(),
    val agent: String? = null,
    val model: ModelRef? = null,
    val summary: JsonObject? = null,
) {
    val displayTitle get() =
        if (isDefaultTitle) (if (directory == app.ocmobile.data.CHAT_DIR) "New chat" else "New session") else title
    val isDefaultTitle get() = title.isBlank() || Regex("^(New session|Child session) - \\d{4}-").containsMatchIn(title)
}

@Serializable
data class MessageInfo(
    val id: String,
    val sessionID: String = "",
    val role: String = "",
    val time: TimeInfo = TimeInfo(),
    val parentID: String? = null,
    val modelID: String? = null,
    val providerID: String? = null,
    val model: ModelRef? = null,
    val agent: String? = null,
    val mode: String? = null,
    val cost: Double? = null,
    val tokens: Tokens? = null,
    val finish: String? = null,
    val error: JsonObject? = null,
) {
    val isUser get() = role == "user"
    val modelName get() = modelID ?: model?.model
    val providerName get() = providerID ?: model?.providerID
    val errorMessage: String?
        get() {
            val e = error ?: return null
            val data = e["data"] as? JsonObject
            return data?.str("message") ?: e.str("message") ?: e.str("name")
        }
    val errorName: String? get() = error?.str("name")
}

/** Часть сообщения хранится как есть: набор типов у OpenCode растёт, строгая модель ломалась бы. */
data class Part(val raw: JsonObject) {
    val id get() = raw.str("id") ?: ""
    val messageID get() = raw.str("messageID") ?: ""
    val type get() = raw.str("type") ?: ""
    val text get() = raw.str("text") ?: ""
    fun withText(t: String) = Part(JsonObject(raw + ("text" to JsonPrimitive(t))))
}

data class MessageWithParts(val info: MessageInfo, val parts: List<Part>)

@Serializable
data class ProviderInfo(
    val id: String,
    val name: String = "",
    val env: List<String> = emptyList(),
    val models: Map<String, ModelInfo> = emptyMap(),
)

@Serializable
data class ModelInfo(
    val id: String,
    val name: String = "",
    val providerID: String = "",
    val family: String? = null,
    val status: String? = null,
    val capabilities: Caps? = null,
    val limit: Limit? = null,
    val cost: JsonObject? = null,
    val variants: Map<String, JsonObject>? = null,
    val release_date: String? = null,
) {
    val supportsImages get() = capabilities?.input?.image == true
    val reasoning get() = capabilities?.reasoning == true
    val variantNames get() = variants?.keys?.toList().orEmpty()
}

@Serializable
data class Caps(
    val reasoning: Boolean = false,
    val attachment: Boolean = false,
    val toolcall: Boolean = false,
    val input: Modalities? = null,
)

@Serializable
data class Modalities(val text: Boolean = false, val image: Boolean = false, val pdf: Boolean = false, val audio: Boolean = false, val video: Boolean = false)

@Serializable
data class Limit(val context: Long = 0, val output: Long = 0)

@Serializable
data class ProvidersResponse(val providers: List<ProviderInfo> = emptyList(), val default: Map<String, String> = emptyMap())

@Serializable
data class AllProvider(val id: String, val name: String = "", val env: List<String> = emptyList())

@Serializable
data class AllProvidersResponse(val all: List<AllProvider> = emptyList(), val connected: List<String> = emptyList())

@Serializable
data class AuthMethod(val type: String = "api", val label: String = "")

@Serializable
data class AgentInfo(
    val name: String,
    val description: String? = null,
    val mode: String = "primary",
    val native: Boolean = false,
    val hidden: Boolean = false,
)

@Serializable
data class NamedItem(val name: String, val description: String? = null)

data class PermissionReq(
    val id: String,
    val sessionID: String,
    val permission: String,
    val patterns: List<String>,
    val metadata: JsonObject,
    val callID: String?,
)

data class QuestionOption(val label: String, val description: String)
data class QuestionItem(val question: String, val header: String, val options: List<QuestionOption>, val multiple: Boolean, val custom: Boolean)
data class QuestionReq(val id: String, val sessionID: String, val questions: List<QuestionItem>)

// ---------- помощники для JsonObject ----------

fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray
fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull
fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull

fun JsonElement.asObjOrNull(): JsonObject? = this as? JsonObject

fun parsePermission(o: JsonObject) = PermissionReq(
    id = o.str("id") ?: "",
    sessionID = o.str("sessionID") ?: "",
    permission = o.str("permission") ?: "",
    patterns = o.arr("patterns")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
    metadata = o.obj("metadata") ?: JsonObject(emptyMap()),
    callID = o.obj("tool")?.str("callID"),
)

fun parseQuestion(o: JsonObject) = QuestionReq(
    id = o.str("id") ?: "",
    sessionID = o.str("sessionID") ?: "",
    questions = o.arr("questions")?.map { q ->
        val qo = q.jsonObject
        QuestionItem(
            question = qo.str("question") ?: "",
            header = qo.str("header") ?: "",
            options = qo.arr("options")?.map { opt ->
                val oo = opt.jsonObject
                QuestionOption(oo.str("label") ?: "", oo.str("description") ?: "")
            }.orEmpty(),
            multiple = qo.bool("multiple") ?: false,
            custom = qo.bool("custom") ?: true,
        )
    }.orEmpty(),
)

fun parseMessages(el: JsonElement): List<MessageWithParts> =
    (el as? JsonArray).orEmpty().mapNotNull { m ->
        val o = m as? JsonObject ?: return@mapNotNull null
        val info = o.obj("info")?.let { runCatching { OcJson.decodeFromJsonElement(MessageInfo.serializer(), it) }.getOrNull() }
            ?: return@mapNotNull null
        MessageWithParts(info, o.arr("parts").orEmpty().mapNotNull { (it as? JsonObject)?.let(::Part) })
    }

private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())
