package ai.driverhelper.voice

import android.util.Log
import javax.inject.Inject

/** Test/fallback guide: logs the text instead of speaking. */
class LogVoiceGuide @Inject constructor(private val messages: Messages) : VoiceGuide {
    override suspend fun speak(messageId: String, language: Language) {
        Log.i("VoiceGuide", "[${language.code}] $messageId: ${messages.text(messageId, language)}")
    }
}
