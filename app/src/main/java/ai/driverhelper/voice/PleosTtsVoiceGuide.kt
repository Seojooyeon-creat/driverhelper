package ai.driverhelper.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Pleos on-device TTS for ko/en (the SDK has no Japanese). Falls back to [LogVoiceGuide] when the language
 * is unsupported, the permission is missing, or the engine fails. Text has no leading silence; prefer recordings.
 */
@Singleton
class PleosTtsVoiceGuide @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messages: Messages,
    private val fallback: LogVoiceGuide,
) : VoiceGuide {

    // ponytail: process-lifetime singleton, never released; add release() if the SDK ever leaks on reinstall.
    private val tts by lazy { TextToSpeech(context, Mode.ON_DEVICE) }
    private var ready: CompletableDeferred<Boolean>? = null

    override suspend fun speak(messageId: String, language: Language) {
        val locale = locales[language]
        if (locale == null || !initialize()) return fallback.speak(messageId, language)
        val text = messages.text(messageId, language)
        val done = withTimeoutOrNull(SPEAK_TIMEOUT_MS) { speak(text, locale) }
        if (done != true) fallback.speak(messageId, language)
    }

    /** First call initialises; later calls reuse the result. False when TTS is unavailable. */
    private suspend fun initialize(): Boolean {
        ready?.let { return it.await() }
        val result = CompletableDeferred<Boolean>()
        ready = result
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.setAudioAttributes(AudioFocus.attributes)
                    result.complete(true)
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "tts init failed: $errorType; using log fallback")
                    result.complete(false)
                }
            })
        } catch (e: Exception) { // e.g. SecurityException until TTS_SERVICE is granted
            Log.w(TAG, "tts unavailable; using log fallback", e)
            result.complete(false)
        }
        return withTimeoutOrNull(INIT_TIMEOUT_MS) { result.await() } ?: false.also {
            Log.w(TAG, "tts init timed out; using log fallback")
            result.complete(false)
        }
    }

    /** True when playback finished; false on engine error. Cancelling stops playback. */
    private suspend fun speak(text: String, locale: Locale): Boolean = suspendCancellableCoroutine { cont ->
        val listener = object : EventListener {
            override fun onDone() = finish(true)
            override fun onStop() = finish(false)
            override fun onError(errorType: ErrorType) {
                Log.w(TAG, "tts error: $errorType")
                finish(false)
            }

            override fun onStart() {}
            override fun onReady() {}
            override fun onUpdatedRms(rms: Double) {}

            private fun finish(ok: Boolean) {
                tts.removeEventListener(this)
                if (cont.isActive) cont.resume(ok)
            }
        }
        tts.addEventListener(listener)
        cont.invokeOnCancellation {
            tts.removeEventListener(listener)
            tts.stop()
        }
        tts.setLocale(locale)
        tts.speak(text, true)
    }

    private companion object {
        const val TAG = "VoiceGuide"
        const val INIT_TIMEOUT_MS = 5_000L
        /** Longest message (boarding briefing) is ~30 s; anything beyond this is a hung engine. */
        const val SPEAK_TIMEOUT_MS = 60_000L
        val locales = mapOf(Language.KO to Locale.KOREA, Language.EN to Locale.US)
    }
}
