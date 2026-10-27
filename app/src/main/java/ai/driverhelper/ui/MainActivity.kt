package ai.driverhelper.ui

import ai.driverhelper.Guidance
import ai.driverhelper.GuidancePipeline
import ai.driverhelper.GuidanceSettings
import ai.driverhelper.data.Contact
import ai.driverhelper.data.GuidanceRecord
import ai.driverhelper.gleo.Faq
import ai.driverhelper.gleo.GleoAction
import ai.driverhelper.rule.MessageId
import ai.driverhelper.voice.Language
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var pipeline: GuidancePipeline
    private lateinit var ux: UxRestrictions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ux = UxRestrictions(this)
        pipeline.start()
        handleGleo(intent)
        setContent {
            DriverHelperTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val restricted by ux.restricted.collectAsStateWithLifecycle()
                    val guidance by pipeline.lastGuidance.collectAsStateWithLifecycle()
                    var emergency by rememberSaveable { mutableStateOf(false) }
                    when {
                        restricted -> DrivingScreen(guidance)
                        emergency -> EmergencyScreen(pipeline) { emergency = false }
                        else -> ParkedScreen(pipeline, debuggable) { emergency = true }
                    }
                }
            }
        }
    }

    /** Gleo re-launches the running activity (singleTop) for each voice request. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGleo(intent)
    }

    private fun handleGleo(intent: Intent?) {
        val action = GleoAction.from(intent) ?: return
        action.messageId?.let(pipeline::speak)
        GleoAction.respond(this, action)
    }

    /** The developer menu is debug-build only, so renters never see it. */
    private val debuggable get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun onDestroy() {
        ux.release()
        super.onDestroy()
    }
}

/** Icon per guidance kind, always shown with its text (guide: icons carry a label). */
private fun iconFor(messageId: String): Pair<ImageVector, Color?> = when (MessageId.category(messageId)) {
    "cat_school_zone" -> Icons.Default.Warning to regulationAmber
    "cat_camera", "cat_section" -> Icons.Default.Place to regulationAmber
    else -> Icons.Default.Info to null
}

/** Shown while driving: one glanceable card, ≤3 lines, no controls, nothing moves. */
@Composable
private fun DrivingScreen(guidance: Guidance?) {
    Column(Modifier.fillMaxSize().padding(40.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        if (guidance == null) {
            Text("Driver Helper", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val (icon, tint) = iconFor(guidance.id)
            Icon(icon, null, Modifier.size(96.dp), tint = tint ?: MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(24.dp))
            Text(
                guidance.text,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Guide: give haptic or audio feedback on every touch. */
private fun HapticFeedback.tap(action: () -> Unit): () -> Unit = { performHapticFeedback(HapticFeedbackType.LongPress); action() }
private fun <T> HapticFeedback.tap1(action: (T) -> Unit): (T) -> Unit = { performHapticFeedback(HapticFeedbackType.LongPress); action(it) }

private val languageLabels = mapOf(Language.KO to "한국어", Language.EN to "English", Language.JA to "日本語")

/** Full-width outlined button with a leading icon; the common shape on the parked screen. */
@Composable
private fun WideButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, colors: androidx.compose.material3.ButtonColors = ButtonDefaults.outlinedButtonColors()) {
    OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min = touchTarget), shape = MaterialTheme.shapes.medium, colors = colors) {
        Icon(icon, null, Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Parked: one level deep. Language → latest guidance → trip summary → replay → emergency → settings. Developer tools collapsed. */
@Composable
private fun ParkedScreen(pipeline: GuidancePipeline, debuggable: Boolean, onEmergency: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val language by pipeline.language.collectAsStateWithLifecycle()
    val guidance by pipeline.lastGuidance.collectAsStateWithLifecycle()
    val summary by pipeline.tripSummary.collectAsStateWithLifecycle()
    val scenario by pipeline.scenario.collectAsStateWithLifecycle()
    val settings by pipeline.settings.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp),
        Arrangement.spacedBy(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
            Language.entries.forEach { lang ->
                val modifier = Modifier.weight(1f).heightIn(min = touchTarget)
                val label: @Composable () -> Unit = { Text(languageLabels.getValue(lang), style = MaterialTheme.typography.titleLarge) }
                if (lang == language) Button(haptic.tap { pipeline.selectLanguage(lang) }, modifier, shape = MaterialTheme.shapes.medium) { label() }
                else OutlinedButton(haptic.tap { pipeline.selectLanguage(lang) }, modifier, shape = MaterialTheme.shapes.medium) { label() }
            }
        }

        guidance?.let { g ->
            val (icon, tint) = iconFor(g.id)
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(28.dp), Arrangement.spacedBy(20.dp), Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(56.dp), tint = tint ?: MaterialTheme.colorScheme.primary)
                    Text(g.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        summary?.let { TripSummary(it, pipeline::uiText, haptic.tap1(pipeline::speak)) }

        if (language != null) WideButton(pipeline.uiText("ui_replay_briefing"), Icons.Default.Refresh, haptic.tap(pipeline::replayBriefing))

        WideButton(
            pipeline.uiText("ui_emergency"), Icons.Default.Call, haptic.tap(onEmergency),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        )

        SettingsPanel(settings, pipeline::uiText, haptic.tap1(pipeline::updateSettings))

        if (debuggable) {
            DeveloperMenu(pipeline.scenarios, scenario, pipeline::playScenario)
            FaqReview(pipeline::text, pipeline::speak)
        }
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** Count per guidance kind, then when and where each one was given. Tap a kind to hear and read the rule and its fine. */
@Composable
private fun TripSummary(records: List<GuidanceRecord>, text: (String) -> String, onSpeak: (String) -> Unit) {
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(28.dp), Arrangement.spacedBy(12.dp)) {
            Text(text("ui_summary_title"), style = MaterialTheme.typography.titleLarge)
            if (records.isEmpty()) Text(text("ui_summary_empty"), style = MaterialTheme.typography.bodyLarge)
            val byCategory = records.groupBy { MessageId.category(it.messageId) }.filterKeys { it != null }
            byCategory.forEach { (category, items) ->
                val (icon, tint) = iconFor(items.first().messageId)
                val detail = MessageId.detail(category!!)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                        openCategory = if (openCategory == category) null else category
                        if (openCategory == category) onSpeak(detail)
                    },
                    Arrangement.spacedBy(12.dp),
                    Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(32.dp), tint = tint ?: MaterialTheme.colorScheme.primary)
                    Text("${text(category)} · ${items.size}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (openCategory == category) {
                    Text(text(detail), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 44.dp))
                }
                items.forEach { r ->
                    val time = clock.format(Instant.ofEpochMilli(r.timeMs).atZone(ZoneId.systemDefault()))
                    val place = if (r.lat != null && r.lon != null) String.format(Locale.ROOT, "%.3f, %.3f", r.lat, r.lon) else "—"
                    Text("$time   $place", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 44.dp))
                }
            }
        }
    }
}

/** Shown to the other party: Korean card first, driver's language under it, then one-tap calls. */
@Composable
private fun EmergencyScreen(pipeline: GuidancePipeline, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val lang = pipeline.language.collectAsStateWithLifecycle().value ?: Language.fromLocale()
    val text = { id: String -> pipeline.text(id, lang) }
    val korean = pipeline.text("ui_emergency_card", Language.KO)
    val own = text("ui_emergency_card")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp),
        Arrangement.spacedBy(24.dp),
    ) {
        Text(text("ui_emergency_hint"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(28.dp), Arrangement.spacedBy(16.dp)) {
                Text(korean, style = MaterialTheme.typography.headlineLarge)
                if (own != korean) Text(own, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        pipeline.contacts.forEach { c -> ContactRow(c, text("contact_${c.id}"), text("ui_not_set"), haptic.tap { dial(context, c.number) }) }
        WideButton(text("ui_emergency_steps"), Icons.Default.PlayArrow, haptic.tap { pipeline.speak("faq_accident") })
        WideButton(text("ui_back"), Icons.Default.Refresh, haptic.tap(onBack))
    }
}

@Composable
private fun ContactRow(contact: Contact, label: String, notSet: String, onCall: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), Arrangement.spacedBy(20.dp), Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(contact.number.ifEmpty { notSet }, style = MaterialTheme.typography.titleLarge, color = if (contact.number.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            }
            if (contact.number.isNotEmpty()) {
                Button(onCall, Modifier.size(touchTarget), shape = MaterialTheme.shapes.medium) { Icon(Icons.Default.Call, null, Modifier.size(40.dp)) }
            }
        }
    }
}

/** Opens the car's dialer if there is one; the number stays on screen either way. */
private fun dial(context: android.content.Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    } catch (e: ActivityNotFoundException) {
        Log.w("Emergency", "no dialer for $number")
    }
}

/** Which kinds of guidance to hear, and how often the right-turn reminder may repeat. Collapsed by default. */
@Composable
private fun SettingsPanel(settings: GuidanceSettings, text: (String) -> String, onChange: ((GuidanceSettings) -> GuidanceSettings) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
        TextButton({ open = !open }, Modifier.heightIn(min = 64.dp)) {
            Icon(Icons.Default.Settings, null)
            Spacer(Modifier.width(8.dp))
            Text(text("ui_settings"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!open) return@Column
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(28.dp), Arrangement.spacedBy(8.dp)) {
                GuidanceSettings.ALL_CATEGORIES.forEach { category ->
                    val on = category in settings.enabled
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onChange { it.copy(enabled = if (on) it.enabled - category else it.enabled + category) } },
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically,
                    ) {
                        Text(text(category), style = MaterialTheme.typography.bodyLarge)
                        Switch(on, null)
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(text("ui_reminder_interval"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                    GuidanceSettings.REMINDER_CHOICES.forEach { minutes ->
                        val label = String.format(Locale.ROOT, text("ui_minutes"), minutes)
                        val modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                        if (minutes == settings.reminderMinutes) Button({}, modifier, shape = MaterialTheme.shapes.medium) { Text(label, style = MaterialTheme.typography.labelLarge) }
                        else OutlinedButton({ onChange { it.copy(reminderMinutes = minutes) } }, modifier, shape = MaterialTheme.shapes.medium) { Text(label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
    }
}

/** Debug-only proofreading list: every FAQ in all three languages, playable one by one or in order. */
@Composable
private fun FaqReview(textIn: (String, Language) -> String, onSpeak: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
        TextButton({ open = !open }, Modifier.heightIn(min = 64.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, null)
            Spacer(Modifier.width(8.dp))
            Text("Developer · FAQ review (${Faq.topics.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            WideButton("Play all in selected language", Icons.Default.PlayArrow, { Faq.topics.values.forEach(onSpeak) })
            Faq.topics.forEach { (topic, id) ->
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(24.dp), Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("$topic  →  $id", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton({ onSpeak(id) }, Modifier.heightIn(min = 64.dp), shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.Default.PlayArrow, null)
                            }
                        }
                        Language.entries.forEach { lang ->
                            Text("${languageLabels.getValue(lang)}  ${textIn(id, lang)}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Replays signal scenarios. Collapsed by default; only reachable while parked. */
@Composable
private fun DeveloperMenu(scenarios: List<String>, running: String?, onScenario: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(running != null) }
    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
        TextButton({ open = !open }, Modifier.heightIn(min = 64.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, null)
            Spacer(Modifier.width(8.dp))
            Text("Developer · scenarios", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) scenarios.forEach { file ->
            val isRunning = file == running
            WideButton(
                if (isRunning) "Stop  $file" else file, Icons.Default.PlayArrow,
                { onScenario(if (isRunning) null else file) },
                colors = if (isRunning) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors(),
            )
        }
    }
}
