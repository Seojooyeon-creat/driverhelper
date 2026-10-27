package ai.driverhelper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the driver chose to hear. [enabled] holds summary categories (`cat_*`); off = never queued, never recorded. */
data class GuidanceSettings(val enabled: Set<String> = ALL_CATEGORIES, val reminderMinutes: Int = 10) {
    companion object {
        val ALL_CATEGORIES = setOf("cat_school_zone", "cat_camera", "cat_section", "cat_right_turn")
        val REMINDER_CHOICES = listOf(5, 10, 30)
    }
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        GuidanceSettings(prefs.getStringSet("enabled", null) ?: GuidanceSettings.ALL_CATEGORIES, prefs.getInt("reminderMinutes", 10)),
    )
    val settings: StateFlow<GuidanceSettings> = _settings

    fun update(change: (GuidanceSettings) -> GuidanceSettings) {
        val s = change(_settings.value)
        prefs.edit().putStringSet("enabled", s.enabled).putInt("reminderMinutes", s.reminderMinutes).apply()
        _settings.value = s
    }
}
