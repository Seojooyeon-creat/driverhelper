package ai.driverhelper.ui

import ai.driverhelper.Guidance
import ai.driverhelper.GuidancePipeline
import ai.driverhelper.data.GuidanceRecord
import ai.driverhelper.gleo.Faq
import ai.driverhelper.gleo.GleoAction
import ai.driverhelper.rule.MessageId
import ai.driverhelper.voice.Language
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
                    val language by pipeline.language.collectAsStateWithLifecycle()
                    val scenario by pipeline.scenario.collectAsStateWithLifecycle()
                    val summary by pipeline.tripSummary.collectAsStateWithLifecycle()
                    if (restricted) {
                        DrivingScreen(guidance)
                    } else {
                        ParkedScreen(
                            language = language,
                            guidance = guidance,
                            replayLabel = pipeline.uiText("ui_replay_briefing").takeIf { language != null },
                            onSelect = pipeline::selectLanguage,
                            onReplay = pipeline::replayBriefing,
                            summary = summary,
                            text = pipeline::uiText,
                            scenarios = if (debuggable) pipeline.scenarios else emptyList(),
                            runningScenario = scenario,
                            onScenario = pipeline::playScenario,
                            textIn = pipeline::text,
                            onSpeak = pipeline::speak,
                        )
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

private val languageLabels = mapOf(Language.KO to "한국어", Language.EN to "English", Language.JA to "日本語")

/** Parked: one level deep. Language → latest guidance → trip summary → replay. Developer tools collapsed. */
@Composable
private fun ParkedScreen(
    language: Language?,
    guidance: Guidance?,
    replayLabel: String?,
    onSelect: (Language) -> Unit,
    onReplay: () -> Unit,
    summary: List<GuidanceRecord>?,
    text: (String) -> String,
    scenarios: List<String>,
    runningScenario: String?,
    onScenario: (String?) -> Unit,
    textIn: (String, Language) -> String,
    onSpeak: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp),
        Arrangement.spacedBy(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
            Language.entries.forEach { lang ->
                val modifier = Modifier.weight(1f).heightIn(min = touchTarget)
                val label: @Composable () -> Unit = { Text(languageLabels.getValue(lang), style = MaterialTheme.typography.titleLarge) }
                if (lang == language) Button(haptic.tap { onSelect(lang) }, modifier, shape = MaterialTheme.shapes.medium) { label() }
                else OutlinedButton(haptic.tap { onSelect(lang) }, modifier, shape = MaterialTheme.shapes.medium) { label() }
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

        summary?.let { TripSummary(it, text) }

        if (replayLabel != null) {
            OutlinedButton(haptic.tap(onReplay), Modifier.fillMaxWidth().heightIn(min = touchTarget), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.Refresh, null, Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Text(replayLabel, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (scenarios.isNotEmpty()) {
            DeveloperMenu(scenarios, runningScenario, onScenario)
            FaqReview(textIn, onSpeak)
        }
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** Count per guidance kind, then when and where each one was given. */
@Composable
private fun TripSummary(records: List<GuidanceRecord>, text: (String) -> String) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(28.dp), Arrangement.spacedBy(12.dp)) {
            Text(text("ui_summary_title"), style = MaterialTheme.typography.titleLarge)
            if (records.isEmpty()) Text(text("ui_summary_empty"), style = MaterialTheme.typography.bodyLarge)
            val byCategory = records.groupBy { MessageId.category(it.messageId) }.filterKeys { it != null }
            byCategory.forEach { (category, items) ->
                val (icon, tint) = iconFor(items.first().messageId)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, null, Modifier.size(32.dp), tint = tint ?: MaterialTheme.colorScheme.primary)
                    Text("${text(category!!)} · ${items.size}", style = MaterialTheme.typography.bodyLarge)
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
            OutlinedButton({ Faq.topics.values.forEach(onSpeak) }, Modifier.fillMaxWidth().heightIn(min = touchTarget), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(12.dp))
                Text("Play all in selected language", style = MaterialTheme.typography.labelLarge)
            }
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
            OutlinedButton(
                { onScenario(if (isRunning) null else file) },
                Modifier.fillMaxWidth().heightIn(min = touchTarget),
                shape = MaterialTheme.shapes.medium,
                colors = if (isRunning) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors(),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(12.dp))
                Text(if (isRunning) "Stop  $file" else file, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
