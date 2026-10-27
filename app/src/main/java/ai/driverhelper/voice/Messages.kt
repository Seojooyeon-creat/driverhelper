package ai.driverhelper.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Guidance texts from assets/messages.json: { messageId: { ko, en, ja } }. */
@Singleton
class Messages @Inject constructor(@ApplicationContext context: Context) {
    private val json by lazy { JSONObject(context.assets.open("messages.json").bufferedReader().use { it.readText() }) }

    fun text(messageId: String, language: Language): String =
        json.optJSONObject(messageId)?.optString(language.code).orEmpty().ifEmpty { messageId }
}
