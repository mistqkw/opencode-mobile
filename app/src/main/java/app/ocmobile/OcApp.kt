package app.ocmobile

import android.app.Application
import app.ocmobile.api.EventHub
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.data.Catalog
import app.ocmobile.data.LiveNotify
import app.ocmobile.data.PhoneBridge
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.ocmobile.data.Vault

class OcApp : Application() {
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    override fun onCreate() {
        // our own toolbar over selected text (Copy / Select all / Reply / Read aloud), via LocalTextToolbar
        androidx.compose.foundation.ComposeFoundationFlags.isNewContextMenuEnabled = false
        super.onCreate()
        Graph.init(this)
    }
}

/** Single place where dependencies are wired up: the app is small, no DI framework needed. */
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
        // people who used the app before the welcome screen existed don't see it again
        if (!prefs.onboarded && paths.rootfsReady && prefs.loginSeen) prefs.onboarded = true
        // no local server on this phone: the tour waits for it, so go straight to the app and connect a computer
        if (!paths.localSupported) { prefs.onboarded = true; prefs.loginSeen = true }
        events = EventHub(api, runtime)
        chats = ChatHub(api, events, prefs)
        usage = UsageTracker(a, api, prefs, events)
        catalog = Catalog(api, prefs, events)
        events.start(scope)
        chats.start()
        catalog.start()
        usage.start()
        Scheduler.load(a)
        PhoneBridge.start()
        LiveNotify.start()
        // the server is up: connect the "phone" MCP to it (the config entry changes only when needed)
        scope.launch { runtime.state.collect { if (it is app.ocmobile.runtime.RuntimeState.Running) PhoneBridge.register() } }
        Memory.sync()
        Vault.restore()
        Vault.reconcileAccount()
        scope.launch(Dispatchers.IO) { while (true) { Vault.save(); Vault.reconcileAccount(); delay(30_000) } }
    }
}
