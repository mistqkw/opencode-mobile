package app.ocmobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Удалённый сервер OpenCode (например, компьютер): адрес вида http://192.168.0.5:4096 и пароль. */
@Serializable
data class Device(val id: String, val name: String, val url: String, val password: String)

/**
 * Remote control: приложение может работать не только с локальным сервером в proot,
 * но и с любым `opencode serve`, доступным по сети. Активное устройство — пустая строка = этот телефон.
 */
class Devices(private val prefs: Prefs) {
    private val json = Json { ignoreUnknownKeys = true }
    val list = MutableStateFlow(read())
    val activeId = MutableStateFlow(prefs.activeDevice)

    private fun read(): List<Device> =
        runCatching { json.decodeFromString(ListSerializer(Device.serializer()), prefs.devicesJson) }.getOrDefault(emptyList())

    private fun save(l: List<Device>) {
        prefs.devicesJson = json.encodeToString(ListSerializer(Device.serializer()), l)
        list.value = l
    }

    fun active(): Device? = list.value.firstOrNull { it.id == activeId.value }
    val isRemote get() = active() != null

    fun add(name: String, url: String, password: String): Device {
        val d = Device(java.util.UUID.randomUUID().toString().take(8), name.trim().ifBlank { host(url) }, normalize(url), password)
        save(list.value + d)
        return d
    }

    fun remove(id: String) {
        if (activeId.value == id) select("")
        save(list.value.filterNot { it.id == id })
    }

    /** "" — вернуться на локальный сервер. */
    fun select(id: String) {
        if (id == activeId.value) return
        // у каждого устройства свой «текущий проект»: пути на компьютере и на телефоне не совпадают
        prefs.setDir(activeId.value, prefs.currentDir)
        prefs.currentDir = prefs.getDir(id) ?: if (id.isEmpty()) "/root/work" else ""
        prefs.activeDevice = id
        activeId.value = id
    }

    companion object {
        fun normalize(raw: String): String {
            var u = raw.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            // адрес без порта — порт по умолчанию у opencode serve
            val hostPart = u.substringAfter("://").substringBefore('/')
            if (!hostPart.contains(':') && u.startsWith("http://")) u = u.replaceFirst(hostPart, "$hostPart:4096")
            return u
        }
        fun host(url: String) = url.substringAfter("://").substringBefore('/').substringBefore(':')
    }
}
