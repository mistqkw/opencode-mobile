package app.ocmobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Настройки приложения. Каждое поле — Compose-состояние, записанное в SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("oc", Context.MODE_PRIVATE)

    private fun str(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        var s by mutableStateOf(sp.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            s = value; sp.edit().putString(key, value).apply()
        }
    }

    private fun bool(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        var s by mutableStateOf(sp.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            s = value; sp.edit().putBoolean(key, value).apply()
        }
    }

    private fun lng(key: String, def: Long) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getLong(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { sp.edit().putLong(key, value).apply() }
    }
    private fun dbl(key: String, def: Double) = object : ReadWriteProperty<Any?, Double> {
        var s by mutableStateOf(sp.getFloat(key, def.toFloat()).toDouble())
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) {
            s = value; sp.edit().putFloat(key, value.toFloat()).apply()
        }
    }

    // Внешний вид
    var recentModels by str("recent_models", "")      // provider/model через запятую, свежие первыми
    var edition by str("edition", "claude")           // claude | terminal | desktop
    var colorMode by str("color_mode", "system")      // system | light | dark
    var fontStyle by str("font_style", "default")     // default | system
    var textSize by str("text_size", "medium")        // small | medium | large
    var codeFont by str("code_font", "mono")          // mono | system
    var wrapLines by bool("wrap_lines", false)
    var haptics by bool("haptics", true)
    var updateAuto by bool("update_auto", true)
    var phoneTools by bool("phone_tools", true)          // MCP «phone»: агент может действовать на этом телефоне
    var phoneToken by str("phone_token", "")
    var phoneToolsOff by str("phone_tools_off", "")      // выключенные инструменты через запятую
    var liveNotify by bool("live_notify", true)          // уведомление с ходом работы и кнопками разрешений
    var costHint by bool("cost_hint", true)
    var busyIds by str("busy_ids", "")                   // сессии, которые работали при последнем известном состоянии
    var interruptedIds by str("interrupted_ids", "")     // задачи, оборванные остановкой сервера              // подсказка цены под полем ввода          // проверять релизы GitHub при запуске
    var updateCheckedAt by lng("update_checked_at", 0L)
    var updateSkipped by str("update_skipped", "")       // тег версии, которую попросили пропустить
    var appIcon by str("app_icon", "look_desktop")
    var uiScale by str("ui_scale", "100")             // 90 | 100 | 110 | 120
    var incognitoIds by str("incognito_ids", "")
    var devicesJson by str("devices", "[]")
    var memoryEnabled by bool("memory_enabled", true)
    var memoryCustom by str("memory_custom", "")
    /** Сохранять самому (true) или только когда попросят «запомни». */
    var memoryAuto by bool("memory_auto", true)
    /** Анимация входа при запуске. */
    var introEnabled by bool("intro_enabled", true)
    /** Темы полок через запятую; пусто — любые. */
    var memoryTopics by str("memory_topics", "")
    var signedIn by str("signed_in", "")          // github | google | email | key | ""
    var loginSeen by bool("login_seen", false)
    var githubLogin by str("github_login", "")           // вход в GitHub внутри окружения (токен лежит у gh)
    var githubName by str("github_name", "")
    var githubAvatar by str("github_avatar", "")
    var onboarded by bool("onboarded", false)          // первый запуск пройден
    var scheduledJson by str("scheduled", "[]")
    var activeDevice by str("active_device", "")

    // Профиль
    var userName by str("user_name", "")
    /** Язык распознавания речи и озвучки: "" — язык телефона. */
    var speechLang by str("speech_lang", "")
    var avatarPath by str("avatar_path", "")

    // Сервер
    var serverPort by str("server_port", "4096")
    var keepAwake by bool("keep_awake", true)
    var autoStartServer by bool("auto_start", true)
    var noSeccomp by bool("no_seccomp", false)
    var runtimeReady by bool("runtime_ready", false)
    var opencodeVersion by str("opencode_version", "")

    // Выбор модели / режимов
    var providerId by str("model_provider", "")
    var modelId by str("model_id", "")
    var variant by str("model_variant", "")
    var agent by str("agent", "build")
    var permissionMode by str("permission_mode", "auto")  // ask | edits | auto
    var currentDir by str("current_dir", "/root/work")
    var pinned by str("pinned", "")

    // Лимиты подписки (в долларах). По умолчанию — опубликованные лимиты OpenCode Go.
    var limit5h by dbl("limit_5h", 12.0)
    var limitWeek by dbl("limit_week", 30.0)
    var limitMonth by dbl("limit_month", 60.0)
    var limitProviders by str("limit_providers", "opencode-go")

    val serverPassword: String
        get() {
            var p = sp.getString("server_password", null)
            if (p == null) {
                val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                val r = SecureRandom()
                p = (1..32).map { chars[r.nextInt(chars.length)] }.joinToString("")
                sp.edit().putString("server_password", p).apply()
            }
            return p
        }

    fun pinnedSet(): Set<String> = pinned.split(',').filter { it.isNotBlank() }.toSet()
    fun togglePin(id: String) {
        val s = pinnedSet().toMutableSet()
        if (!s.add(id)) s.remove(id)
        pinned = s.joinToString(",")
    }

    fun setDir(deviceId: String, dir: String) { sp.edit().putString("dir_$deviceId", dir).apply() }
    fun getDir(deviceId: String): String? = sp.getString("dir_$deviceId", null)

    val hasModel get() = providerId.isNotBlank() && modelId.isNotBlank()
}
