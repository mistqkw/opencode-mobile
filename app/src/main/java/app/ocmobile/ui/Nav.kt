package app.ocmobile.ui

import androidx.compose.runtime.mutableStateListOf

sealed interface Route {
    data object Home : Route
    data class Chat(val id: String) : Route
    data object NewCode : Route
    data object Memory : Route
    data object Edition : Route
    data object Login : Route
    data class ConsoleLogin(val method: String) : Route
    data class Connect(val providerId: String) : Route
    data object Code : Route
    data object Artifacts : Route
    data object Scheduled : Route
    data object Recents : Route
    data object Projects : Route
    data object Settings : Route
    data object Usage : Route
    data object Profile : Route
    data object Providers : Route
    data object Models : Route
    data object PermissionsCfg : Route
    data object Connectors : Route
    data object Agents : Route
    data object Skills : Route
    /** "" is a new skill, "builtin:name" a built-in one. */
    data class SkillEdit(val name: String) : Route
    data object Server : Route
    data object ConfigEditor : Route
    data object About : Route
    data object Capabilities : Route
    data object OcSettings : Route
    data object Appearance : Route
    data object PhoneTools : Route
    data object GitHub : Route
    data object Backup : Route
    data object Team : Route
    data object Mascot : Route
    data object MascotSettings : Route
    data class Preview(val url: String, val sessionId: String?) : Route
}

/** Minimal navigation: a route stack and the back button. */
class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    init { NavMemory.restore()?.let { stack.clear(); stack.addAll(it) } }
    val current get() = stack.last()
    /** 1 is forward, -1 back, 0 a root change: the screen animation depends on it. */
    var direction = 0
    fun push(r: Route) { if (current != r) { direction = 1; stack.add(r) }; NavMemory.save(stack) }
    fun pop(): Boolean {
        if (stack.size > 1) { direction = -1; stack.removeAt(stack.lastIndex); NavMemory.save(stack); return true }
        // section opened from the menu as root: "back" goes to the home screen
        if (current != Route.Home) { direction = -1; stack.clear(); stack.add(Route.Home); NavMemory.save(stack); return true }
        return false
    }
    fun root(r: Route) { direction = 0; stack.clear(); stack.add(r); NavMemory.save(stack) }
    /** Open a session from anywhere: the stack is Home → chat. */
    fun openChat(id: String) { direction = 1; stack.clear(); stack.add(Route.Home); stack.add(Route.Chat(id)); NavMemory.save(stack) }
    /** A switch without sliding: after the send animation the screen just "changes" to the chat. */
    fun openChatInstant(id: String) { direction = 2; stack.clear(); stack.add(Route.Home); stack.add(Route.Chat(id)); NavMemory.save(stack) }
}

/**
 * Where the user was: the screen stack outlives the activity (Android may recreate it in the background)
 * and is saved to disk, so coming back to the app opens the same screen, not home.
 * After an hour away from the app we start from home again.
 */
object NavMemory {
    private const val TTL = 60 * 60 * 1000L
    private val objects: Map<String, Route> by lazy {
        listOf(
        Route.Home,
        Route.NewCode,
        Route.Memory,
        Route.Edition,
        Route.Login,
        Route.Code,
        Route.Artifacts,
        Route.Scheduled,
        Route.Recents,
        Route.Projects,
        Route.Settings,
        Route.Usage,
        Route.Profile,
        Route.Providers,
        Route.Models,
        Route.PermissionsCfg,
        Route.Connectors,
        Route.Agents,
        Route.Skills,
        Route.Server,
        Route.ConfigEditor,
        Route.About,
        Route.Capabilities,
        Route.OcSettings,
        Route.Appearance,
        Route.PhoneTools,
        Route.GitHub,
        Route.Backup,
        Route.Team,
        Route.Mascot,
        Route.MascotSettings,
        ).associateBy { it.toString() }
    }

    fun save(stack: List<Route>) {
        // an anonymous chat isn't remembered: it's deleted as soon as you leave it
        val parts = stack.mapNotNull { r -> if (r is Route.Chat) ("Chat:" + r.id).takeUnless { app.ocmobile.Graph.chats.isIncognito(r.id) } else r.toString().takeIf { it in objects } }
        app.ocmobile.Graph.prefs.navStack = System.currentTimeMillis().toString() + "|" + parts.joinToString(",")
    }

    fun restore(): List<Route>? = runCatching {
        val raw = app.ocmobile.Graph.prefs.navStack
        val at = raw.substringBefore('|').toLongOrNull() ?: return null
        if (System.currentTimeMillis() - at > TTL) return null
        raw.substringAfter('|').split(',').filter { it.isNotBlank() }.map { k -> if (k.startsWith("Chat:")) Route.Chat(k.removePrefix("Chat:")) else objects[k] ?: return null }
            .takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** One stack per process: a recreated activity picks it up instead of starting over. */
    private var current: Nav? = null
    val nav: Nav get() = current ?: Nav().also { current = it }

    /** A fresh start (the app was fully closed): begin at home and forget the old stack. */
    fun startFresh() {
        app.ocmobile.Graph.prefs.navStack = ""
        current = Nav()
    }
}
