package app.ocmobile.i18n

/**
 * English → Russian dictionary for the interface.
 *
 * Only exact UI strings are listed here; [tr] falls back to the original text.
 * The dictionary is split over several files to keep each one readable.
 */
object Ru {
    val dict: Map<String, String> by lazy {
        core + screens + chat + settings
    }
}
