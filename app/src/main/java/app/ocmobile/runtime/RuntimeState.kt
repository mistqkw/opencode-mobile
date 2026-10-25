package app.ocmobile.runtime

sealed interface RuntimeState {
    /** Окружение ещё не установлено. */
    data object NotInstalled : RuntimeState
    data class Installing(val step: String, val progress: Float?, val detail: String = "") : RuntimeState
    data class InstallFailed(val message: String) : RuntimeState
    data object Stopped : RuntimeState
    data object Starting : RuntimeState
    data class Running(val version: String) : RuntimeState
    data class Crashed(val message: String) : RuntimeState
}
