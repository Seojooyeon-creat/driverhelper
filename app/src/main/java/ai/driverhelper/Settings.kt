package ai.driverhelper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the driver chose to hear. [enabled] holds summary categories (`cat_*`); off = never queued, never recorded.
 * [heard] counts plays per category over the rental; once one reaches [ASK_AFTER] the app asks, at the next stop,
 * whether to keep it, and [asked] remembers that the question was answered.
 */
data class GuidanceSettings(
    val enabled: Set<String> = ALL_CATEGORIES,
    val reminderMinutes: Int = 10,
    val heard: Map<String, Int> = emptyMap(),
    val asked: Set<String> = emptySet(),
) {
    companion object {
        val ALL_CATEGORIES = setOf("cat_school_zone", "cat_camera", "cat_section", "cat_right_turn")
        val REMINDER_CHOICES = listOf(5, 10, 30)
        // ponytail: counts live until the app data is cleared; reset them at return once the rental system tells us when that is.
        const val ASK_AFTER = 3
    }
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        GuidanceSettings(
            prefs.getStringSet("enabled", null) ?: GuidanceSettings.ALL_CATEGORIES,
            prefs.getInt("reminderMinutes", 10),
            GuidanceSettings.ALL_CATEGORIES.associateWith { prefs.getInt("heard_$it", 0) }.filterValues { it > 0 },
            prefs.getStringSet("asked", null) ?: emptySet(),
        ),
    )
    val settings: StateFlow<GuidanceSettings> = _settings

    fun update(change: (GuidanceSettings) -> GuidanceSettings) {
        val s = change(_settings.value)
        prefs.edit().putStringSet("enabled", s.enabled).putInt("reminderMinutes", s.reminderMinutes).putStringSet("asked", s.asked)
            .apply { s.heard.forEach { (c, n) -> putInt("heard_$c", n) } }.apply()
        _settings.value = s
    }
}
