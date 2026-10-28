package ai.driverhelper.ui

import ai.driverhelper.Guidance
import ai.driverhelper.GuidancePipeline
import ai.driverhelper.GuidanceSettings
import ai.driverhelper.data.Contact
import ai.driverhelper.data.GuideItem
import ai.driverhelper.data.Guides
import ai.driverhelper.data.GuidanceRecord
import ai.driverhelper.gleo.Faq
import ai.driverhelper.gleo.GleoAction
import ai.driverhelper.rule.MessageId
import ai.driverhelper.signal.DrivingSignal
import ai.driverhelper.voice.Language
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
                    var screen by rememberSaveable { mutableStateOf(PARKED) }
                    val back = { screen = PARKED }
                    // Driving ↔ parked: a short fade. Sub-pages slide in from the right and back out the same way.
                    AnimatedContent(restricted, transitionSpec = { fadeIn(tween(DUR_BASE, easing = tdsEase)) togetherWith fadeOut(tween(DUR_BASE, easing = tdsEase)) }, label = "mode") { driving ->
                        if (driving) DrivingScreen(guidance, pipeline) else AnimatedContent(screen, transitionSpec = { slide(targetState != PARKED) }, label = "screen") { s ->
                            when (s) {
                                EMERGENCY -> EmergencyScreen(pipeline, back)
                                TOUR -> TourScreen(pipeline, back)
                                DICTIONARY -> GuideScreen("ui_dictionary", listOf("ui_signs" to Guides.signs, "ui_telltales" to Guides.telltaleItems), pipeline, back)
                                SETTINGS -> SettingsScreen(pipeline, back)
                                else -> ParkedScreen(pipeline, debuggable) { screen = it }
                            }
                        }
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

/** TDS screen transition: 320 ms ease-out, no overshoot. Forward = new page slides in from the right over the home page
 *  easing a quarter left; back = reverse. */
private fun slide(forward: Boolean): androidx.compose.animation.ContentTransform {
    val move = tween<androidx.compose.ui.unit.IntOffset>(DUR_SLOW, easing = tdsEaseOut)
    val fade = tween<Float>(DUR_SLOW, easing = tdsEaseOut)
    return if (forward) slideInHorizontally(move) { it } + fadeIn(fade) togetherWith slideOutHorizontally(move) { -it / 4 } + fadeOut(fade)
    else slideInHorizontally(move) { -it / 4 } + fadeIn(fade) togetherWith slideOutHorizontally(move) { it } + fadeOut(fade)
}

/** Parked-screen sub-pages; one level deep, Back returns to [PARKED]. */
private const val PARKED = "parked"
private const val EMERGENCY = "emergency"
private const val TOUR = "tour"
private const val DICTIONARY = "dictionary"
private const val SETTINGS = "settings"

private val languageLabels = mapOf(Language.KO to "한국어", Language.EN to "English", Language.JA to "日本語")
private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** Guide: give haptic feedback on every touch. */
private fun HapticFeedback.tap(action: () -> Unit): () -> Unit = { performHapticFeedback(HapticFeedbackType.LongPress); action() }
private fun <T> HapticFeedback.tap1(action: (T) -> Unit): (T) -> Unit = { performHapticFeedback(HapticFeedbackType.LongPress); action(it) }

// ---------------------------------------------------------------------------------------------------------------------
// TDS building blocks on the car scale: icon chip, card, list row, pill button, top bar, section header, badge.
// ---------------------------------------------------------------------------------------------------------------------

/** Icon per guidance kind, always shown with its text. Regulation warnings use the TDS warning orange. */
@Composable
private fun iconFor(messageId: String): Pair<ImageVector, Color> = when (MessageId.category(messageId)) {
    "cat_school_zone" -> Icons.Default.Warning to MaterialTheme.colorScheme.tertiary
    "cat_camera", "cat_section" -> Icons.Default.Place to MaterialTheme.colorScheme.tertiary
    "cat_right_turn" -> Icons.AutoMirrored.Filled.ArrowForward to MaterialTheme.colorScheme.primary
    else -> if (messageId.startsWith("telltale_")) Icons.Default.Warning to MaterialTheme.colorScheme.tertiary else Icons.Default.Info to MaterialTheme.colorScheme.primary
}

/** TDS list-row avatar / grid-list icon chip: tinted icon on its washed container. */
@Composable
private fun IconChip(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, size: Int = 64) {
    val container = when (tint) {
        MaterialTheme.colorScheme.error -> MaterialTheme.colorScheme.errorContainer
        MaterialTheme.colorScheme.tertiary -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
        MaterialTheme.colorScheme.onSurfaceVariant -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Box(Modifier.size(size.dp).clip(chipShape(size)).background(container), Alignment.Center) {
        Icon(icon, null, Modifier.size((size / 2).dp), tint = tint)
    }
}

private fun chipShape(size: Int) = androidx.compose.foundation.shape.RoundedCornerShape((size * 0.32).dp)

/** Flat white card with a hairline, TDS radius-3xl. */
@Composable
private fun TdsCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, hairline())
    if (onClick != null) Surface(onClick, modifier, shape = shape, color = MaterialTheme.colorScheme.surface, border = border, content = content)
    else Surface(modifier, shape = shape, color = MaterialTheme.colorScheme.surface, border = border, content = content)
}

/** TDS list-row: leading slot, title + optional subtitle, trailing slot. The whole row is the hit area. */
@Composable
private fun ListRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = touchTarget).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 28.dp, vertical = 16.dp),
        Arrangement.spacedBy(20.dp),
        Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}

private enum class Variant { PRIMARY, SECONDARY, DANGER }

/** TDS XL button as a pill: one PRIMARY per screen, SECONDARY grey-100, DANGER red. */
@Composable
private fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, variant: Variant = Variant.SECONDARY, icon: ImageVector? = null) {
    val (bg, fg) = when (variant) {
        Variant.PRIMARY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        Variant.SECONDARY -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
        Variant.DANGER -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
    }
    Surface(onClick, modifier.fillMaxWidth().heightIn(min = touchTarget), shape = pill, color = bg, contentColor = fg) {
        Row(Modifier.padding(horizontal = 32.dp), Arrangement.Center, Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(32.dp)); Spacer(Modifier.width(12.dp)) }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** TDS top-app-bar: back icon-button + left-aligned title. */
@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().height(touchTarget), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        Box(Modifier.size(touchTarget).clip(pill).clickable(onClick = haptic.tap(onBack)), Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/** TDS list-header. */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(start = 8.dp, top = 8.dp))
}

/** TDS badge: washed container, semantic text. */
@Composable
private fun Badge(text: String, container: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = content, modifier = Modifier.clip(pill).background(container).padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun Chevron(open: Boolean) {
    val angle by animateFloatAsState(if (open) 180f else 0f, tween(DUR_BASE, easing = tdsEase), label = "chevron")
    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(32.dp).rotate(angle), tint = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 24.dp),
        Arrangement.spacedBy(20.dp),
    ) { content() }
}

// ---------------------------------------------------------------------------------------------------------------------
// Screens
// ---------------------------------------------------------------------------------------------------------------------

/** Shown while driving: one glanceable card, ≤3 lines, nothing moves. The only controls are the two answer buttons,
 *  and they exist only while the car is stopped at a light with a question pending. */
@Composable
private fun DrivingScreen(guidance: Guidance?, pipeline: GuidancePipeline) {
    val question by pipeline.question.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(40.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        if (guidance == null) {
            Text("Driver Helper", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val (icon, tint) = iconFor(guidance.id)
            IconChip(icon, tint, size = 144)
            Spacer(Modifier.size(32.dp))
            Text(guidance.text, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        question?.let {
            Spacer(Modifier.size(40.dp))
            AnswerButtons(pipeline::uiText, pipeline::answer)
        }
    }
}

/** "Keep hearing this?" — keep is the one primary action, stop is secondary. */
@Composable
private fun AnswerButtons(text: (String) -> String, onAnswer: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(20.dp)) {
        PillButton(text("ui_keep"), haptic.tap { onAnswer(true) }, Modifier.weight(1f), Variant.PRIMARY, Icons.Default.Check)
        PillButton(text("ui_stop"), haptic.tap { onAnswer(false) }, Modifier.weight(1f), Variant.SECONDARY, Icons.Default.Clear)
    }
}

/** Parked home. Greeting + language chips → last guidance → one primary CTA → trip summary + checklist → 2×2 grid-list → developer tools. */
@Composable
private fun ParkedScreen(pipeline: GuidancePipeline, debuggable: Boolean, onOpen: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val language by pipeline.language.collectAsStateWithLifecycle()
    val guidance by pipeline.lastGuidance.collectAsStateWithLifecycle()
    val summary by pipeline.tripSummary.collectAsStateWithLifecycle()
    val scenario by pipeline.scenario.collectAsStateWithLifecycle()
    val energy by pipeline.energy.collectAsStateWithLifecycle()
    val text = pipeline::uiText

    val question by pipeline.question.collectAsStateWithLifecycle()

    Page {
        question?.let { category ->
            TdsCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(32.dp), Arrangement.spacedBy(24.dp)) {
                    Text(text("ask_" + category.removePrefix("cat_")), style = MaterialTheme.typography.titleLarge)
                    AnswerButtons(text, pipeline::answer)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(24.dp), Alignment.CenterVertically) {
            Text(text("ui_greeting"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Language.entries.forEach { lang -> LanguageChip(languageLabels.getValue(lang), lang == language, haptic.tap { pipeline.selectLanguage(lang) }) }
            }
        }

        TdsCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), Arrangement.spacedBy(16.dp)) {
                SectionHeader(text("ui_last_guidance"), Modifier.padding(0.dp))
                val g = guidance
                if (g == null) {
                    ListRow(text("ui_no_guidance"), text("ui_no_guidance_hint"), leading = { IconChip(Icons.Default.Info, MaterialTheme.colorScheme.onSurfaceVariant) })
                } else {
                    val (icon, tint) = iconFor(g.id)
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(20.dp), Alignment.CenterVertically) {
                        IconChip(icon, tint)
                        Text(g.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (language != null) PillButton(text("ui_replay_briefing"), haptic.tap(pipeline::replayBriefing), variant = Variant.PRIMARY, icon = Icons.Default.Refresh)

        summary?.let {
            TripSummary(it, text, haptic.tap1(pipeline::speak))
            ReturnChecklist(energy, text)
        }

        val tiles = listOf(
            Tile(TOUR, Icons.Default.Build, MaterialTheme.colorScheme.primary, text("ui_tour")),
            Tile(DICTIONARY, Icons.AutoMirrored.Filled.List, MaterialTheme.colorScheme.primary, text("ui_dictionary")),
            Tile(EMERGENCY, Icons.Default.Call, MaterialTheme.colorScheme.error, text("ui_emergency")),
            Tile(SETTINGS, Icons.Default.Settings, MaterialTheme.colorScheme.onSurfaceVariant, text("ui_settings")),
        )
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(20.dp)) {
                pair.forEach { t ->
                    TdsCard(Modifier.weight(1f), onClick = haptic.tap { onOpen(t.screen) }) {
                        Row(Modifier.padding(horizontal = 32.dp, vertical = 36.dp), Arrangement.spacedBy(24.dp), Alignment.CenterVertically) {
                            IconChip(t.icon, t.tint, size = 80)
                            Text(t.title, style = MaterialTheme.typography.titleMedium, color = if (t.screen == EMERGENCY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        if (debuggable) {
            DeveloperMenu(pipeline.scenarios, scenario, pipeline::playScenario)
            FaqReview(pipeline::text, pipeline::speak)
        }
    }
}

/** A message ID in each summary category, so category rows can share [iconFor]. */
private val sampleIdFor = mapOf("cat_school_zone" to MessageId.SCHOOL_ZONE, "cat_camera" to MessageId.CAMERA_FIXED, "cat_section" to MessageId.SECTION_START, "cat_right_turn" to MessageId.RIGHT_TURN_STOP)

private data class Tile(val screen: String, val icon: ImageVector, val tint: Color, val title: String)

/** TDS chip: resting white + hairline, selected = brand variant (blue-50 + blue text). */
@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick,
        Modifier.heightIn(min = 88.dp),
        shape = pill,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.padding(horizontal = 28.dp), Alignment.Center) {
            Text(label, style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
        }
    }
}

/** Count per guidance kind as list rows with a badge; tap a kind to hear and read the rule and its fine. */
@Composable
private fun TripSummary(records: List<GuidanceRecord>, text: (String) -> String, onSpeak: (String) -> Unit) {
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }
    TdsCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            SectionHeader(text("ui_summary_title"), Modifier.padding(start = 28.dp, bottom = 4.dp))
            if (records.isEmpty()) ListRow(text("ui_summary_empty"), leading = { IconChip(Icons.Default.Check, MaterialTheme.colorScheme.onSurfaceVariant) })
            val byCategory = records.groupBy { MessageId.category(it.messageId) }.filterKeys { it != null }
            byCategory.entries.forEachIndexed { i, (category, items) ->
                val (icon, tint) = iconFor(items.first().messageId)
                val detail = MessageId.detail(category!!)
                val open = openCategory == category
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 28.dp), color = MaterialTheme.colorScheme.outline)
                Column(Modifier.animateContentSize(tween(DUR_BASE, easing = tdsEase))) {
                    ListRow(
                        text(category),
                        leading = { IconChip(icon, tint) },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Badge(String.format(Locale.ROOT, text("ui_times"), items.size))
                                Chevron(open)
                            }
                        },
                        onClick = {
                            openCategory = if (open) null else category
                            if (!open) onSpeak(detail)
                        },
                    )
                    if (open) {
                        Column(Modifier.padding(start = 112.dp, end = 28.dp, bottom = 20.dp), Arrangement.spacedBy(8.dp)) {
                            Text(text(detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            items.forEach { r ->
                                val time = clock.format(Instant.ofEpochMilli(r.timeMs).atZone(ZoneId.systemDefault()))
                                val place = if (r.lat != null && r.lon != null) String.format(Locale.ROOT, "%.3f, %.3f", r.lat, r.lon) else "—"
                                Text("$time   $place", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shown with the trip summary. Ticks live until the app restarts; the fuel/charge row reads the car when it can. */
@Composable
private fun ReturnChecklist(energy: DrivingSignal.Energy?, text: (String) -> String) {
    var done by rememberSaveable { mutableStateOf(setOf<String>()) }
    TdsCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            SectionHeader(text("ui_checklist"), Modifier.padding(start = 28.dp, bottom = 4.dp))
            Guides.checklist.forEachIndexed { i, id ->
                val checked = id in done
                val detail = if (id == "check_energy") {
                    energy?.let { "${text(if (it.electric) "ui_battery" else "ui_fuel")}  ${it.percent}%" } ?: text("ui_level_unknown")
                } else text(id)
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 28.dp), color = MaterialTheme.colorScheme.outline)
                ListRow(
                    text("${id}_title"), detail,
                    leading = {
                        Checkbox(
                            checked, null, Modifier.size(48.dp),
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, uncheckedColor = MaterialTheme.colorScheme.outlineVariant),
                        )
                    },
                    onClick = { done = if (checked) done - id else done + id },
                    titleColor = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Dictionary: tap a row to hear and read it. Signs show the Korean wording as a brand chip, since that is what the driver sees on the road. */
@Composable
private fun GuideScreen(titleId: String, sections: List<Pair<String?, List<GuideItem>>>, pipeline: GuidancePipeline, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Page {
        TopBar(pipeline.uiText(titleId), onBack)
        sections.forEach { (heading, items) ->
            heading?.let { SectionHeader(pipeline.uiText(it)) }
            TdsCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    items.forEachIndexed { i, item ->
                        val isOpen = item.id == open
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 28.dp), color = MaterialTheme.colorScheme.outline)
                        Column(Modifier.animateContentSize(tween(DUR_BASE, easing = tdsEase))) {
                            ListRow(
                                pipeline.uiText("${item.id}_title"),
                                leading = {
                                    val label = item.label
                                    if (label != null) Badge(label, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                    else {
                                        val warns = Guides.telltales.any { it.value == item.id && it.key in Guides.autoExplain }
                                        val (icon, tint) = when {
                                            item.id.startsWith("ctrl_") -> Icons.Default.Build to MaterialTheme.colorScheme.primary
                                            warns -> Icons.Default.Warning to MaterialTheme.colorScheme.tertiary
                                            else -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        IconChip(icon, tint)
                                    }
                                },
                                trailing = { Chevron(isOpen) },
                                onClick = haptic.tap {
                                    open = if (isOpen) null else item.id
                                    if (!isOpen) pipeline.speak(item.id)
                                },
                            )
                            if (isOpen) {
                                Text(pipeline.uiText(item.id), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** assets/tour/<id>.png or .jpg (see assets/tour/README.md), or null when the fleet has not supplied one. */
@Composable
private fun tourBitmap(id: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(id) {
        listOf("png", "jpg").firstNotNullOfOrNull { ext ->
            runCatching { context.assets.open("tour/$id.$ext").use(BitmapFactory::decodeStream) }.getOrNull()
        }?.asImageBitmap()
    }
}

/** Fallback glyph per control while there is no photo. */
private val controlIcons = mapOf(
    "ctrl_start" to Icons.Default.PlayArrow,
    "ctrl_gear" to Icons.Default.Build,
    "ctrl_parking_brake" to Icons.Default.Lock,
    "ctrl_regen" to Icons.Default.Refresh,
    "ctrl_wiper" to Icons.Default.Clear,
    "ctrl_lights" to Icons.Default.Star,
    "ctrl_fuel_port" to Icons.Default.Place,
    "ctrl_charge_port" to Icons.Default.Place,
    "ctrl_hipass" to Icons.Default.Check,
)

/** Controls tour as a TDS grid-list: four tiles per row, the chosen one opens a photo + text card below and is spoken. */
@Composable
private fun TourScreen(pipeline: GuidancePipeline, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var selected by rememberSaveable { mutableStateOf(Guides.controls.first().id) }
    Page {
        TopBar(pipeline.uiText("ui_tour"), onBack)
        Guides.controls.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                row.forEach { item ->
                    val on = item.id == selected
                    val title = pipeline.uiText("${item.id}_title")
                    val thumb = tourBitmap(item.id)
                    Surface(
                        haptic.tap { selected = item.id; pipeline.speak(item.id) },
                        Modifier.weight(1f).aspectRatio(2.2f),
                        shape = MaterialTheme.shapes.small,
                        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else hairline()),
                    ) {
                        if (thumb != null) {
                            // Photo fills the tile; the title sits on a solid strip underneath (no gradient scrims in TDS).
                            Column {
                                Image(thumb, null, Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Crop)
                                Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        } else {
                            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                                IconChip(controlIcons[item.id] ?: Icons.Default.Info, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, size = 48)
                                Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        TdsCard(Modifier.fillMaxWidth().animateContentSize(tween(DUR_BASE, easing = tdsEase))) {
            Column(Modifier.padding(32.dp), Arrangement.spacedBy(20.dp)) {
                Text(pipeline.uiText("${selected}_title"), style = MaterialTheme.typography.titleLarge)
                TourImage(selected)
                Text(pipeline.uiText(selected), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Full-width photo for the chosen control; nothing when there is none. */
@Composable
private fun TourImage(id: String) {
    val bitmap = tourBitmap(id) ?: return
    Image(bitmap, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
}

/** Shown to the other party: inverse-surface show-card (Korean first, driver's language under it), contacts as list rows, one primary CTA. */
@Composable
private fun EmergencyScreen(pipeline: GuidancePipeline, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val lang = pipeline.language.collectAsStateWithLifecycle().value ?: Language.fromLocale()
    val text = { id: String -> pipeline.text(id, lang) }
    val korean = pipeline.text("ui_emergency_card", Language.KO)
    val own = text("ui_emergency_card")

    Page {
        TopBar(text("ui_emergency"), onBack)
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
            Column(Modifier.padding(36.dp), Arrangement.spacedBy(16.dp)) {
                Text(text("ui_emergency_hint"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f))
                Text(korean, style = MaterialTheme.typography.headlineLarge)
                if (own != korean) Text(own, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f))
            }
        }
        SectionHeader(text("ui_contacts"))
        TdsCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                pipeline.contacts.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 28.dp), color = MaterialTheme.colorScheme.outline)
                    ContactRow(c, text("contact_${c.id}"), text("ui_not_set"), haptic.tap { dial(context, c.number) })
                }
            }
        }
        PillButton(text("ui_emergency_steps"), haptic.tap { pipeline.speak("faq_accident") }, variant = Variant.PRIMARY, icon = Icons.Default.PlayArrow)
    }
}

@Composable
private fun ContactRow(contact: Contact, label: String, notSet: String, onCall: () -> Unit) {
    val set = contact.number.isNotEmpty()
    ListRow(
        contact.number.ifEmpty { notSet }, label,
        titleColor = if (set) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            if (set) Box(Modifier.size(touchTarget).clip(pill).background(MaterialTheme.colorScheme.primary).clickable(onClick = onCall), Alignment.Center) {
                Icon(Icons.Default.Call, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        onClick = if (set) onCall else null,
    )
}

/** Opens the car's dialer if there is one; the number stays on screen either way. */
private fun dial(context: android.content.Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    } catch (e: ActivityNotFoundException) {
        Log.w("Emergency", "no dialer for $number")
    }
}

/** Which kinds of guidance to hear (switch rows) and how often the right-turn reminder may repeat (segmented control). */
@Composable
private fun SettingsScreen(pipeline: GuidancePipeline, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val settings by pipeline.settings.collectAsStateWithLifecycle()
    val text = pipeline::uiText
    val onChange = haptic.tap1(pipeline::updateSettings)
    Page {
        TopBar(text("ui_settings"), onBack)
        TdsCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                GuidanceSettings.ALL_CATEGORIES.forEachIndexed { i, category ->
                    val on = category in settings.enabled
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 28.dp), color = MaterialTheme.colorScheme.outline)
                    ListRow(
                        text(category),
                        leading = { val (icon, tint) = iconFor(sampleIdFor.getValue(category)); IconChip(icon, tint) },
                        trailing = {
                            Switch(
                                on, null,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                                    uncheckedBorderColor = Color.Transparent,
                                ),
                            )
                        },
                        onClick = { onChange { it.copy(enabled = if (on) it.enabled - category else it.enabled + category) } },
                    )
                }
            }
        }
        SectionHeader(text("ui_reminder_interval"))
        SegmentedControl(
            GuidanceSettings.REMINDER_CHOICES.map { String.format(Locale.ROOT, text("ui_minutes"), it) },
            GuidanceSettings.REMINDER_CHOICES.indexOf(settings.reminderMinutes),
        ) { i -> onChange { it.copy(reminderMinutes = GuidanceSettings.REMINDER_CHOICES[i]) } }
    }
}

/** TDS segmented-control: grey-100 track, the selected segment floats on white with a shadow-1. */
@Composable
private fun SegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp), Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Surface(
                { onSelect(i) },
                Modifier.weight(1f).heightIn(min = 84.dp),
                shape = chipShape(60),
                color = if (on) MaterialTheme.colorScheme.surface else Color.Transparent,
                contentColor = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = if (on) 2.dp else 0.dp,
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** Debug-only proofreading list: every FAQ in all three languages, playable one by one or in order. */
@Composable
private fun FaqReview(textIn: (String, Language) -> String, onSpeak: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
        DeveloperToggle("Developer · FAQ review (${Faq.topics.size})", open) { open = !open }
        if (open) {
            PillButton("Play all in selected language", { Faq.topics.values.forEach(onSpeak) }, icon = Icons.Default.PlayArrow)
            Faq.topics.forEach { (topic, id) ->
                TdsCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), Arrangement.spacedBy(12.dp)) {
                        ListRow("$topic  →  $id", trailing = { IconChip(Icons.Default.PlayArrow) }, onClick = { onSpeak(id) })
                        Language.entries.forEach { lang ->
                            Text("${languageLabels.getValue(lang)}  ${textIn(id, lang)}", style = MaterialTheme.typography.bodyMedium)
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
        DeveloperToggle("Developer · scenarios", open) { open = !open }
        if (open) scenarios.forEach { file ->
            val isRunning = file == running
            PillButton(
                if (isRunning) "Stop  $file" else file,
                { onScenario(if (isRunning) null else file) },
                variant = if (isRunning) Variant.DANGER else Variant.SECONDARY,
                icon = Icons.Default.PlayArrow,
            )
        }
    }
}

@Composable
private fun DeveloperToggle(label: String, open: Boolean, onClick: () -> Unit) {
    TextButton(onClick, Modifier.heightIn(min = 64.dp)) {
        Chevron(open)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
