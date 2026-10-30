package app.ocmobile.data

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import app.ocmobile.Graph
import app.ocmobile.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * Мост между агентом и телефоном. Сервер OpenCode живёт в окружении на этом же телефоне,
 * поэтому приложение поднимает на 127.0.0.1 маленький HTTP-сервер:
 *  - /mcp — MCP-сервер «phone» (Streamable HTTP, только JSON-ответы): уведомления, напоминания, будильник,
 *    буфер обмена, геолокация, камера, «Загрузки», ссылки, превью, состояние телефона;
 *  - /preview/<токен>/<путь> — отдаёт файлы проекта для превью сайта во встроенном браузере.
 * Другие приложения тоже видят localhost, поэтому всё закрыто случайным токеном.
 */
object PhoneBridge {
    const val PORT = 4097
    const val NAME = "phone"

    @Volatile private var server: ServerSocket? = null

    val token: String
        get() = Graph.prefs.phoneToken.ifBlank {
            val b = ByteArray(18).also { SecureRandom().nextBytes(it) }
            b.joinToString("") { "%02x".format(it) }.also { Graph.prefs.phoneToken = it }
        }

    fun previewUrl(guestPath: String) = "http://127.0.0.1:$PORT/preview/$token/" + guestPath.trimStart('/')

    fun start() {
        if (server != null) return
        val s = runCatching { ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1")) }.getOrNull() ?: return
        server = s
        thread(name = "phone-bridge", isDaemon = true) {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: continue
                thread(isDaemon = true) { runCatching { c.use { serve(it) } } }
            }
        }
    }

    // ---------- регистрация в OpenCode ----------

    /** Приводит запись mcp.phone в глобальном конфиге в соответствие с настройкой. Пишет, только если что-то поменялось. */
    suspend fun register() {
        if (Graph.devices.isRemote) return
        val on = Graph.prefs.phoneTools
        val url = "http://127.0.0.1:$PORT/mcp"
        val cfgFile = Graph.paths.host("/root/.config/opencode/opencode.jsonc")
        val cur = runCatching { org.json.JSONObject(cfgFile.readText()).optJSONObject("mcp")?.optJSONObject(NAME) }.getOrNull()
        val same = cur != null && cur.optString("url") == url && cur.optBoolean("enabled", true) == on &&
            cur.optJSONObject("headers")?.optString("Authorization") == "Bearer $token"
        if (same || (cur == null && !on)) return
        val block = buildJsonObject {
            put("type", "remote"); put("url", url); put("enabled", on)
            putJsonObject("headers") { put("Authorization", "Bearer $token") }
        }
        runCatching { Graph.api.patchGlobalConfig(buildJsonObject { putJsonObject("mcp") { put(NAME, block) } }) }
        runCatching { if (on) Graph.api.mcpConnect(NAME) else Graph.api.mcpDisconnect(NAME) }
    }

    // ---------- HTTP ----------

    private fun serve(sock: Socket) {
        sock.soTimeout = 30_000
        val inp = BufferedInputStream(sock.getInputStream())
        val head = readHead(inp) ?: return
        val lines = head.split("\r\n")
        val (method, rawPath) = lines.first().split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
        val headers = lines.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        val body = ByteArray(len).also { var off = 0; while (off < len) { val n = inp.read(it, off, len - off); if (n < 0) break; off += n } }
        val out = sock.getOutputStream()
        val path = rawPath.substringBefore('?')
        when {
            path == "/mcp" && method == "POST" -> {
                if (headers["authorization"] != "Bearer $token") return respond(out, 401, "text/plain", "unauthorized".toByteArray())
                val reply = runCatching { handleRpc(String(body)) }.getOrElse { rpcError(JsonNull, -32700, it.message ?: "parse error") }
                if (reply == null) respond(out, 202, "text/plain", ByteArray(0))
                else respond(out, 200, "application/json", reply.toString().toByteArray())
            }
            path == "/mcp" -> respond(out, 405, "text/plain", ByteArray(0), "Allow: POST\r\n")
            path.startsWith("/preview/$token/") && method == "GET" -> servePreview(out, Uri.decode(path.removePrefix("/preview/$token")))
            else -> respond(out, 404, "text/plain", "not found".toByteArray())
        }
    }

    private fun readHead(inp: BufferedInputStream): String? {
        val buf = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = inp.read(); if (b < 0) return null
            buf.write(b); last4 = (last4 shl 8) or b
            if (last4 == 0x0d0a0d0a) return buf.toString(Charsets.ISO_8859_1.name()).trimEnd()
            if (buf.size() > 32_000) return null
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray, extra: String = "") {
        val reason = mapOf(200 to "OK", 202 to "Accepted", 401 to "Unauthorized", 404 to "Not Found", 405 to "Method Not Allowed")[code] ?: "OK"
        out.write(("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\n${extra}Connection: close\r\n\r\n").toByteArray())
        out.write(body); out.flush()
    }

    private val mime = mapOf(
        "html" to "text/html; charset=utf-8", "htm" to "text/html; charset=utf-8", "css" to "text/css; charset=utf-8",
        "js" to "text/javascript; charset=utf-8", "mjs" to "text/javascript; charset=utf-8", "json" to "application/json",
        "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif",
        "webp" to "image/webp", "ico" to "image/x-icon", "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf",
        "txt" to "text/plain; charset=utf-8", "md" to "text/plain; charset=utf-8", "mp3" to "audio/mpeg", "mp4" to "video/mp4",
    )

    private fun servePreview(out: OutputStream, guest: String) {
        val root = Graph.paths.host("/").canonicalFile
        var f = Graph.paths.host(guest).canonicalFile
        if (!f.path.startsWith(root.path)) return respond(out, 404, "text/plain", ByteArray(0))
        if (f.isDirectory) f = File(f, "index.html")
        if (!f.isFile) return respond(out, 404, "text/plain", "not found".toByteArray())
        respond(out, 200, mime[f.extension.lowercase()] ?: "application/octet-stream", f.readBytes())
    }

    // ---------- MCP ----------

    private fun rpcResult(id: JsonElement, result: JsonObject) = buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }
    private fun rpcError(id: JsonElement, code: Int, msg: String) = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id); putJsonObject("error") { put("code", code); put("message", msg) }
    }

    private fun handleRpc(text: String): JsonElement? {
        val el = app.ocmobile.api.OcJson.parseToJsonElement(text)
        return if (el is JsonArray) el.mapNotNull { one(it.jsonObject) }.takeIf { it.isNotEmpty() }?.let(::JsonArray) else one(el.jsonObject)
    }

    private fun one(req: JsonObject): JsonObject? {
        val id = req["id"] ?: return null // уведомление: ответа не нужно
        val params = req["params"] as? JsonObject ?: JsonObject(emptyMap())
        return when (req["method"]?.jsonPrimitive?.content) {
            "initialize" -> rpcResult(id, buildJsonObject {
                put("protocolVersion", params["protocolVersion"] ?: JsonPrimitive("2025-03-26"))
                putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
                putJsonObject("serverInfo") { put("name", "opencode-mobile-phone"); put("version", "1.0") }
                put("instructions", "Tools that act on the user's Android phone, which also runs this OpenCode server. Use them when the user asks for something on the phone itself: reminders, alarms, notifications, the clipboard, photos, location, saving files to Downloads, opening links or previewing a site.")
            })
            "ping" -> rpcResult(id, JsonObject(emptyMap()))
            "tools/list" -> rpcResult(id, buildJsonObject {
                putJsonArray("tools") {
                    enabledTools().forEach { t -> add(buildJsonObject { put("name", t.name); put("description", t.desc); put("inputSchema", t.schema) }) }
                }
            })
            "tools/call" -> {
                val name = params["name"]?.jsonPrimitive?.content
                val t = enabledTools().firstOrNull { it.name == name } ?: return rpcError(id, -32602, "Unknown or disabled tool: $name")
                val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val (text, err) = runCatching { runBlocking { t.run(args) } to false }.getOrElse { (it.message ?: it.toString()) to true }
                rpcResult(id, buildJsonObject {
                    putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", text) }) }
                    if (err) put("isError", true)
                })
            }
            else -> rpcError(id, -32601, "Method not found")
        }
    }

    // ---------- инструменты ----------

    class Tool(val name: String, val title: String, val desc: String, val schema: JsonObject, val run: suspend (JsonObject) -> String)

    private fun schema(required: List<String> = emptyList(), vararg props: Pair<String, Pair<String, String>>) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props.forEach { (k, v) -> putJsonObject(k) { put("type", v.first); put("description", v.second) } } }
        if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
    private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.intOrNull ?: s(k)?.toDoubleOrNull()?.toInt()

    private val ctx: Context get() = Graph.app

    private fun needOpen() { if (!Graph.chats.foreground) error("Android only allows this while OpenCode is open on the screen. Ask the user to open the app and try again.") }

    val all: List<Tool> by lazy {
        listOf(
            Tool("notify", "Notification", "Show a notification on the phone right now.",
                schema(listOf("text"), "title" to ("string" to "Short title"), "text" to ("string" to "The message"))) { a ->
                notify(a.s("title") ?: "OpenCode", a.s("text").orEmpty(), (System.nanoTime() and 0xffffff).toInt())
                "Shown."
            },
            Tool("remind", "Reminders", "Remind the user later with a notification. Give either in_minutes or at (24h local time HH:MM, today or tomorrow if it has passed).",
                schema(listOf("text"), "text" to ("string" to "What to remind about"), "in_minutes" to ("number" to "Minutes from now"), "at" to ("string" to "Local time HH:MM"))) { a ->
                val text = a.s("text") ?: error("text is required")
                val cal = Calendar.getInstance()
                val at = a.s("at"); val mins = a.i("in_minutes")
                when {
                    mins != null -> cal.add(Calendar.MINUTE, mins)
                    at != null -> {
                        val (h, m) = at.split(":").map { it.trim().toInt() }
                        cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); cal.set(Calendar.SECOND, 0)
                        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    else -> error("Give in_minutes or at")
                }
                val id = (cal.timeInMillis / 1000).toInt()
                val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, Reminder::class.java).putExtra("text", text).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                ctx.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                "Reminder set for " + SimpleDateFormat("EEE HH:mm", Locale.ENGLISH).format(cal.time) + "."
            },
            Tool("set_alarm", "Alarm clock", "Set an alarm in the phone's Clock app.",
                schema(listOf("hour", "minute"), "hour" to ("integer" to "0–23"), "minute" to ("integer" to "0–59"), "label" to ("string" to "Optional label"))) { a ->
                needOpen()
                val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, a.i("hour") ?: error("hour"))
                    .putExtra(AlarmClock.EXTRA_MINUTES, a.i("minute") ?: 0).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .apply { a.s("label")?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                withContext(Dispatchers.Main) { ctx.startActivity(i) }
                "Alarm set for %02d:%02d.".format(a.i("hour"), a.i("minute") ?: 0)
            },
            Tool("clipboard_read", "Read clipboard", "Read the text currently on the phone's clipboard.", schema()) {
                needOpen()
                withContext(Dispatchers.Main) {
                    ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                }?.takeIf { it.isNotEmpty() } ?: "The clipboard is empty."
            },
            Tool("clipboard_write", "Copy to clipboard", "Put text on the phone's clipboard so the user can paste it anywhere.",
                schema(listOf("text"), "text" to ("string" to "Text to copy"))) { a ->
                withContext(Dispatchers.Main) { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("OpenCode", a.s("text").orEmpty())) }
                "Copied."
            },
            Tool("location", "Location", "Get the phone's current location (latitude, longitude, accuracy and, when available, the address).", schema()) { location() },
            Tool("take_photo", "Camera", "Open the camera so the user takes a photo; returns the path of the saved JPEG, which you can then read.",
                schema(emptyList(), "save_to" to ("string" to "Folder to save into (default /root/outbox/photos)"))) { a -> takePhoto(a.s("save_to") ?: "/root/outbox/photos") },
            Tool("save_to_downloads", "Downloads", "Copy a file from this environment into the phone's Downloads folder so the user can open or share it.",
                schema(listOf("path"), "path" to ("string" to "Absolute path of the file"))) { a -> saveToDownloads(a.s("path") ?: error("path is required")) },
            Tool("open_url", "Links", "Open a link on the phone (browser or the app that handles it).",
                schema(listOf("url"), "url" to ("string" to "http(s), tel:, mailto:, geo: …"))) { a ->
                needOpen()
                withContext(Dispatchers.Main) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.s("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                "Opened."
            },
            Tool("preview", "Preview", "Show a web page you built in the app's preview with live reload: give a local dev server URL (http://localhost:5173) or the absolute path of an .html file.",
                schema(listOf("target"), "target" to ("string" to "URL or absolute path to an .html file"))) { a ->
                val t = a.s("target") ?: error("target is required")
                val url = if (t.startsWith("http")) t.replace("://localhost", "://127.0.0.1").replace("://0.0.0.0", "://127.0.0.1") else previewUrl(t)
                Graph.chats.openRequest.value = "route:preview:$url"
                "The preview is open in the app."
            },
            Tool("share", "Share", "Open Android's share sheet with text so the user can send it to any app (Telegram, mail…).",
                schema(listOf("text"), "text" to ("string" to "Text to share"))) { a ->
                needOpen()
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a.s("text").orEmpty())
                withContext(Dispatchers.Main) { ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                "The share sheet is open."
            },
            Tool("device_status", "Phone status", "Battery, charging, network, local time and free storage of the phone.", schema()) { status() },
        )
    }

    fun enabledTools(): List<Tool> {
        val off = Graph.prefs.phoneToolsOff.split(',').filter { it.isNotBlank() }.toSet()
        return all.filter { it.name !in off }
    }

    // ---------- реализации ----------

    fun notify(title: String, text: String, id: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel("phone") == null) nm.createNotificationChannel(NotificationChannel("phone", "From OpenCode", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, app.ocmobile.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id, Notification.Builder(ctx, "phone").setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build())
    }

    class Reminder : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = notify("Reminder", i.getStringExtra("text").orEmpty(), i.getIntExtra("id", 1))
    }

    private suspend fun location(): String {
        val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) error("Location is off for OpenCode. Ask the user to allow it in Settings → Phone tools.")
        val lm = ctx.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: error("Location is turned off on the phone.")
        @Suppress("MissingPermission")
        val loc = if (Build.VERSION.SDK_INT >= 30) withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<android.location.Location?> { cont -> lm.getCurrentLocation(provider, null, ctx.mainExecutor) { cont.resume(it) } }
        } else null
        @Suppress("MissingPermission")
        val l = loc ?: lm.getLastKnownLocation(provider) ?: error("Could not get a location fix.")
        val addr = runCatching {
            @Suppress("DEPRECATION")
            android.location.Geocoder(ctx, Locale.ENGLISH).getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()?.getAddressLine(0)
        }.getOrNull()
        return "%.6f, %.6f (±%.0f m)".format(Locale.US, l.latitude, l.longitude, l.accuracy) + (addr?.let { "\n$it" } ?: "")
    }

    /** Ждёт снимок от [PhotoActivity]. */
    @Volatile var photo: CompletableDeferred<File?>? = null

    private suspend fun takePhoto(dir: String): String {
        needOpen()
        val d = CompletableDeferred<File?>()
        photo = d
        withContext(Dispatchers.Main) { ctx.startActivity(Intent(ctx, PhotoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        val shot = withTimeoutOrNull(180_000) { d.await() } ?: error("No photo was taken.")
        val name = "photo-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".jpg"
        val dest = File(Graph.paths.host(dir), name).apply { parentFile?.mkdirs() }
        shot.copyTo(dest, overwrite = true); shot.delete()
        return "Saved ${dir.trimEnd('/')}/$name"
    }

    private fun saveToDownloads(guest: String): String {
        val f = Graph.paths.host(guest)
        if (!f.isFile) error("No such file: $guest")
        val mt = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, f.name); put(MediaStore.Downloads.MIME_TYPE, mt); put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenCode") }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("Android refused to create the file")
            ctx.contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OpenCode").apply { mkdirs() }
            f.copyTo(File(dir, f.name), overwrite = true)
        }
        return "Saved to Downloads/OpenCode/${f.name}"
    }

    private fun status(): String {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            else -> "online"
        }
        val free = Graph.paths.host("/").usableSpace / 1_000_000_000.0
        val now = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        return "Battery $level%" + (if (charging) ", charging" else "") + "\nNetwork: $net\nLocal time: $now (${java.util.TimeZone.getDefault().id})\nFree storage: %.1f GB".format(Locale.US, free) +
            "\nPhone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
    }
}
