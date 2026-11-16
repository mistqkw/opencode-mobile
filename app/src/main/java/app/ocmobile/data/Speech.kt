package app.ocmobile.data

import android.content.Intent
import android.speech.RecognizerIntent
import app.ocmobile.Graph
import java.util.Locale

/** Speech language for dictation, voice mode and "Read aloud": picked in Settings → Voice. */
object Speech {
    val languages = listOf(
        "" to "Auto (the language you type in)", "ru-RU" to "Русский", "en-US" to "English", "uk-UA" to "Українська",
        "be-BY" to "Беларуская", "kk-KZ" to "Қазақ", "de-DE" to "Deutsch", "fr-FR" to "Français", "es-ES" to "Español",
        "it-IT" to "Italiano", "pl-PL" to "Polski", "pt-BR" to "Português", "tr-TR" to "Türkçe", "zh-CN" to "中文", "ja-JP" to "日本語",
    )

    fun label(): String = languages.firstOrNull { it.first == Graph.prefs.speechLang }?.second ?: Graph.prefs.speechLang

    fun tag(): String = Graph.prefs.speechLang.ifBlank { auto() }

    /**
     * "Auto" is the language the person actually speaks, not the phone's menus: many keep the phone in
     * English and talk in their own language. So: the first non-English language among the system
     * languages, then among the keyboards that are turned on; only then the phone's own.
     */
    fun auto(): String {
        val sys = android.os.LocaleList.getDefault().let { l -> (0 until l.size()).map { l[it] } }
        sys.firstOrNull { it.language != "en" }?.let { return full(it) }
        runCatching {
            val imm = Graph.app.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            imm.enabledInputMethodList.flatMap { imm.getEnabledInputMethodSubtypeList(it, true) }
                .mapNotNull { st -> st.languageTag.ifBlank { @Suppress("DEPRECATION") st.locale.replace('_', '-') }.takeIf { it.isNotBlank() } }
                .map { Locale.forLanguageTag(it) }.firstOrNull { it.language.isNotBlank() && it.language != "en" }?.let { return full(it) }
        }
        return Locale.getDefault().toLanguageTag()
    }

    /** "ru" → "ru-RU": recognizers want the full tag. */
    private fun full(l: Locale): String = languages.firstOrNull { it.first.startsWith(l.language + "-") }?.first
        ?: if (l.country.isNotBlank()) l.toLanguageTag() else "${l.language}-${l.language.uppercase()}"

    fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag())
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag())
        .apply {
            // Android 14+: the recognizer may follow the speaker between their language and English
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf(tag(), "en-US").distinct().let { ArrayList(it) })
            }
        }

    /** Voice for reading: the chosen language, or in "Auto" by the text (Cyrillic → Russian). */
    fun ttsLocale(text: String): Locale = when {
        Graph.prefs.speechLang.isNotBlank() -> Locale.forLanguageTag(Graph.prefs.speechLang)
        text.any { it in 'а'..'я' || it in 'А'..'Я' } -> Locale.forLanguageTag("ru-RU")
        else -> Locale.getDefault()
    }

    /**
     * Sets up reading: the language, then the most natural installed voice for it (or the one picked in
     * Settings), at a normal pitch and pace. The engine's default voice is often its oldest, flattest one,
     * which is what made reading sound robotic.
     */
    fun applyVoice(tts: android.speech.tts.TextToSpeech, text: String, pitch: Float = 1f) {
        val loc = ttsLocale(text)
        tts.language = loc
        voicesFor(tts, loc).let { vs -> (vs.firstOrNull { it.name == Graph.prefs.ttsVoice } ?: vs.firstOrNull())?.let { tts.voice = it } }
        tts.setPitch(pitch)
        tts.setSpeechRate(1f)
    }

    /** Installed voices for a language, the best first: higher quality, then on the phone before online. */
    fun voicesFor(tts: android.speech.tts.TextToSpeech, loc: Locale): List<android.speech.tts.Voice> =
        runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == loc.language && android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
}
