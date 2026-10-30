package app.ocmobile.data

import android.content.Intent
import android.speech.RecognizerIntent
import app.ocmobile.Graph
import java.util.Locale

/** Язык речи для диктовки, голосового режима и «Read aloud» — выбирается в Settings → Voice. */
object Speech {
    val languages = listOf(
        "" to "Auto (phone language)", "ru-RU" to "Русский", "en-US" to "English", "uk-UA" to "Українська",
        "be-BY" to "Беларуская", "kk-KZ" to "Қазақ", "de-DE" to "Deutsch", "fr-FR" to "Français", "es-ES" to "Español",
        "it-IT" to "Italiano", "pl-PL" to "Polski", "pt-BR" to "Português", "tr-TR" to "Türkçe", "zh-CN" to "中文", "ja-JP" to "日本語",
    )

    fun label(): String = languages.firstOrNull { it.first == Graph.prefs.speechLang }?.second ?: Graph.prefs.speechLang

    fun tag(): String = Graph.prefs.speechLang.ifBlank { Locale.getDefault().toLanguageTag() }

    fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag())
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag())

    /** Голос для чтения: выбранный язык, а в «Auto» — по тексту (кириллица → русский). */
    fun ttsLocale(text: String): Locale = when {
        Graph.prefs.speechLang.isNotBlank() -> Locale.forLanguageTag(Graph.prefs.speechLang)
        text.any { it in 'а'..'я' || it in 'А'..'Я' } -> Locale.forLanguageTag("ru-RU")
        else -> Locale.getDefault()
    }
}
