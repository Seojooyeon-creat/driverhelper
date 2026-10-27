package ai.driverhelper.voice

import java.util.Locale

enum class Language(val code: String) {
    KO("ko"), EN("en"), JA("ja");

    companion object {
        fun fromLocale(locale: Locale = Locale.getDefault()) = entries.find { it.code == locale.language } ?: EN
    }
}

/** Speaks a guidance message. Suspends until playback ends; cancelling stops playback. */
interface VoiceGuide {
    suspend fun speak(messageId: String, language: Language)
}
