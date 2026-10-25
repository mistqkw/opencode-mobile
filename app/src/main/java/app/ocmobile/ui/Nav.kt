package app.ocmobile.ui

import androidx.compose.runtime.mutableStateListOf

sealed interface Route {
    data object Home : Route
    data class Chat(val id: String) : Route
    data object NewCode : Route
    data object Memory : Route
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
    data object Server : Route
    data object ConfigEditor : Route
    data object About : Route
}

/** Минимальная навигация: стек маршрутов и кнопка «назад». */
class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current get() = stack.last()
    /** 1 — переход вперёд, -1 — назад, 0 — смена корня: от этого зависит анимация экрана. */
    var direction = 0
    fun push(r: Route) { if (current != r) { direction = 1; stack.add(r) } }
    fun pop(): Boolean { if (stack.size > 1) { direction = -1; stack.removeAt(stack.lastIndex); return true }; return false }
    fun root(r: Route) { direction = 0; stack.clear(); stack.add(r) }
    /** Открыть сессию из любого места: стек — Home → чат. */
    fun openChat(id: String) { direction = 1; stack.clear(); stack.add(Route.Home); stack.add(Route.Chat(id)) }
    /** Переход без сдвига — после анимации отправки сообщения экран просто «сменяется» на чат. */
    fun openChatInstant(id: String) { direction = 2; stack.clear(); stack.add(Route.Home); stack.add(Route.Chat(id)) }
}
