package ai.driverhelper.voice

import android.content.Context
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Plays assets/audio/{lang}/{messageId}.wav. Files must carry their own 150–200 ms leading silence.
 * Falls back to [PleosTtsVoiceGuide] (then log) when the file is missing.
 */
class PrerecordedVoiceGuide @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fallback: PleosTtsVoiceGuide,
) : VoiceGuide {

    override suspend fun speak(messageId: String, language: Language) {
        val afd = try {
            context.assets.openFd("audio/${language.code}/$messageId.wav")
        } catch (e: IOException) {
            return fallback.speak(messageId, language)
        }
        suspendCancellableCoroutine { cont ->
            val player = MediaPlayer()
            val done = { player.release(); if (cont.isActive) cont.resume(Unit) }
            player.setAudioAttributes(AudioFocus.attributes)
            afd.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            player.setOnCompletionListener { done() }
            player.setOnErrorListener { _, _, _ -> done(); true }
            cont.invokeOnCancellation { player.release() }
            player.prepare()
            player.start()
        }
    }
}
