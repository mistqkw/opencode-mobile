package app.ocmobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** App settings. Each field is Compose state stored in SharedPreferences. */
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

    // Appearance
    var recentModels by str("recent_models", "")      // provider/model, comma-separated, most recent first
    var edition by str("edition", "cloud")           // claude | terminal | desktop
    var colorMode by str("color_mode", "system")      // system | light | dark
    var fontStyle by str("font_style", "default")     // default | system
    var textSize by str("text_size", "medium")        // small | medium | large
    var codeFont by str("code_font", "mono")          // mono | system
    var wrapLines by bool("wrap_lines", false)
    var haptics by bool("haptics", true)
    var accessBannerHidden by bool("access_banner_hidden", false)  // dismissed the hint about background work and notifications
    // the cl0udly mascot: the "cl0udly" tab in settings
    var mascotOn by bool("mascot_on", true)
    var mascotHaptics by bool("mascot_haptics", true)
    var mascotSensors by bool("mascot_sensors", true)        // shake, tilt, upside down
    var mascotBattery by bool("mascot_battery", true)        // charging and low battery
    var mascotSleepy by bool("mascot_sleepy", true)          // dozes off if left alone for long
    var mascotMorning by bool("mascot_morning", true)
    var mascotSeasonal by bool("mascot_seasonal", true)      // snow, leaves, confetti, sneezes
    var mascotOutfit by str("mascot_outfit", "auto")        // auto, off or a holiday name
    var mascotPersona by bool("mascot_persona", true)       // the agent introduces itself as the cl0udly cloud
    var petCount by str("pet_count", "0")             // how many times the mascot was petted
    var lastMorning by str("last_morning", "")         // the day the mascot already did its morning stretch
    var pongBest by str("pong_best", "0")              // best ping-pong rally with the mascot
    var navStack by str("nav_stack", "")               // time|screens: bring the user back where they were
    var teamMode by str("team_mode", "off")             // off | manual | auto
    var teamMembers by str("team_members", "[]")        // JSON: [{name, role, provider, model}]
    var teamPool by str("team_pool", "")                // auto: models, comma-separated, provider/model
    var teamMax by str("team_max", "3")                 // auto: the most helpers allowed
    var updateAuto by bool("update_auto", true)          // check GitHub releases on launch
    var phoneTools by bool("phone_tools", true)          // "phone" MCP: the agent can act on this phone
    var phoneToken by str("phone_token", "")
    var phoneToolsOff by str("phone_tools_off", "")      // disabled tools, comma-separated
    var liveNotify by bool("live_notify", true)          // notification with work progress and permission buttons
    var costHint by bool("cost_hint", true)              // price hint under the input
    var busyIds by str("busy_ids", "")                   // sessions that were running at the last known state
    var interruptedIds by str("interrupted_ids", "")     // tasks cut off by a server stop
    var updateCheckedAt by lng("update_checked_at", 0L)
    var updateSkipped by str("update_skipped", "")       // tag of the version the user asked to skip
    var appIcon by str("app_icon", "look_cloud")
    var uiScale by str("ui_scale", "100")             // 90 | 100 | 110 | 120
    var incognitoIds by str("incognito_ids", "")
    var devicesJson by str("devices", "[]")
    var memoryEnabled by bool("memory_enabled", true)
    var memoryCustom by str("memory_custom", "")
    /** Save on its own (true) or only when asked to "remember". */
    var memoryAuto by bool("memory_auto", true)
    /** Intro animation on launch. */
    var introEnabled by bool("intro_enabled", true)
    /** Shelf topics, comma-separated; empty means any. */
    var memoryTopics by str("memory_topics", "")
    var signedIn by str("signed_in", "")          // github | google | email | key | ""
    var loginSeen by bool("login_seen", false)
    var githubLogin by str("github_login", "")           // GitHub sign-in inside the environment (gh holds the token)
    var githubName by str("github_name", "")
    var githubAvatar by str("github_avatar", "")
    var onboarded by bool("onboarded", false)          // first launch done
    var scheduledJson by str("scheduled", "[]")
    var activeDevice by str("active_device", "")

    // Profile
    var userName by str("user_name", "")
    /** Language for speech recognition and read-aloud: "" means the phone's language. */
    var speechLang by str("speech_lang", "")
    var avatarPath by str("avatar_path", "")

    // Server
    var serverPort by str("server_port", if (app.ocmobile.BuildConfig.DEBUG) "4196" else "4096")
    var keepAwake by bool("keep_awake", true)
    var autoStartServer by bool("auto_start", true)
    var noSeccomp by bool("no_seccomp", false)
    /** Run the Ubuntu env as real root (KernelSU chroot) instead of proot, when root is granted. */
    var rootRuntime by bool("root_runtime", false)
    var runtimeReady by bool("runtime_ready", false)
    var opencodeVersion by str("opencode_version", "")

    // Model / mode selection
    var providerId by str("model_provider", "")
    var modelId by str("model_id", "")
    var variant by str("model_variant", "")
    var agent by str("agent", "build")
    var permissionMode by str("permission_mode", "auto")  // ask | edits | auto
    var currentDir by str("current_dir", "/root/work")
    var pinned by str("pinned", "")

    // Subscription limits (in dollars). Default: the published OpenCode Go limits.
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
