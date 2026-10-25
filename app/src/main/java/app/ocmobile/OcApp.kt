package app.ocmobile

import android.app.Application
import app.ocmobile.api.EventHub
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.data.Catalog
import app.ocmobile.data.Devices
import app.ocmobile.data.ChatHub
import app.ocmobile.data.Memory
import app.ocmobile.data.Prefs
import app.ocmobile.data.Scheduler
import app.ocmobile.data.UsageTracker
import app.ocmobile.runtime.RuntimeController
import app.ocmobile.runtime.RuntimePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** Единая точка сборки зависимостей — приложение маленькое, DI-фреймворк не нужен. */
object Graph {
    lateinit var app: Application
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var prefs: Prefs
    lateinit var paths: RuntimePaths
    lateinit var runtime: RuntimeController
    lateinit var devices: Devices
    lateinit var api: OpenCodeApi
    lateinit var events: EventHub
    lateinit var chats: ChatHub
    lateinit var usage: UsageTracker
    lateinit var catalog: Catalog

    fun init(a: Application) {
        app = a
        prefs = Prefs(a)
        paths = RuntimePaths(a)
        devices = Devices(prefs)
        api = OpenCodeApi(prefs)
        runtime = RuntimeController(a, paths, prefs, api)
        events = EventHub(api, runtime)
        chats = ChatHub(api, events, prefs)
        usage = UsageTracker(a, api, prefs, events)
        catalog = Catalog(api, prefs, events)
        events.start(scope)
        chats.start()
        catalog.start()
        usage.start()
        Scheduler.load(a)
        Memory.sync()
    }
}
