package ai.driverhelper.gleo

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

/**
 * Gleo Interface (assets/schema.json): Gleo starts [ai.driverhelper.ui.MainActivity] with action [FUNCTION] and the
 * parameters as a JSON string in [PARAM_EXTRA]. We answer within 7 s via the [RESPONSE_ACTION] broadcast.
 * mode=silent: Gleo stays quiet and our VoiceGuide speaks the FAQ in the driver's language (Gleo has no Japanese).
 */
class GleoAction private constructor(val topic: String?) {
    /** FAQ message to speak, or null when the topic is unknown. */
    val messageId: String? get() = topic?.let { Faq.messageIdFor(it) }

    fun response(): JSONObject = JSONObject().apply {
        put("functionName", FUNCTION)
        if (messageId != null) {
            put("status", "success")
            put("message", "Explaining the Korean traffic rule about ${topic?.replace('_', ' ')} in the driver's language.")
            put("mode", "silent")
        } else {
            put("status", "fail")
            put("message", "No guidance is available for that topic.")
        }
    }

    companion object {
        const val FUNCTION = "EXPLAIN_TRAFFIC_RULE"
        const val PARAM_EXTRA = "ACTION_PARAM"
        const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        private const val TAG = "GleoAction"

        fun from(intent: Intent?): GleoAction? {
            if (intent?.action != FUNCTION) return null
            val raw = intent.getStringExtra(PARAM_EXTRA) ?: "{}"
            return parse(raw).also { Log.i(TAG, "action topic=${it.topic} raw=$raw") }
        }

        /** Pure parsing so it can be unit-tested. Unknown or malformed input → topic null → "fail". */
        fun parse(rawJson: String): GleoAction = GleoAction(
            runCatching { JSONObject(rawJson).optString("topic").takeIf { it.isNotEmpty() } }.getOrNull(),
        )

        fun respond(context: Context, action: GleoAction) {
            val response = action.response()
            Log.i(TAG, "respond $response")
            context.sendBroadcast(
                Intent(RESPONSE_ACTION)
                    .putExtra("packageName", context.packageName)
                    .putExtra("response", response.toString()),
            )
        }
    }
}
