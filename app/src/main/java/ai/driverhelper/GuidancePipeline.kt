package ai.driverhelper

import ai.driverhelper.data.GuidanceDao
import ai.driverhelper.data.GuidanceRecord
import ai.driverhelper.data.parseSchoolZones
import ai.driverhelper.rule.MessageId
import ai.driverhelper.rule.RuleEngine
import ai.driverhelper.signal.CarPropertySignalSource
import ai.driverhelper.signal.DrivingSignal
import ai.driverhelper.signal.FusedLocationSignalSource
import ai.driverhelper.signal.NaviSignalSource
import ai.driverhelper.signal.PleosVehicleSignalSource
import ai.driverhelper.signal.ScenarioSignalSource
import ai.driverhelper.voice.AudioFocus
import ai.driverhelper.voice.Language
import ai.driverhelper.voice.Messages
import ai.driverhelper.voice.VoiceGuide
import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** What was last spoken: [id] picks the icon on screen, [text] is the on-screen wording. */
data class Guidance(val id: String, val text: String)

/** signal → RuleEngine → priority queue → audio focus → VoiceGuide. Guidance plays one at a time. */
@Singleton
class GuidancePipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voice: VoiceGuide,
    private val focus: AudioFocus,
    private val messages: Messages,
    private val records: GuidanceDao,
) {
    // ponytail: the summary covers everything since app start; use the rental start once the rental system provides it.
    private val sessionStartMs = System.currentTimeMillis()
    private var lastLocation: DrivingSignal.Location? = null

    private val schoolZones by lazy { parseSchoolZones(context.assets.open("school_zones.json").bufferedReader().use { it.readText() }) }
    private val engine by lazy { RuleEngine(schoolZones = schoolZones) }

    /** Waiting message IDs, highest priority first. Collecting never blocks on playback. */
    private val queue = MutableStateFlow<List<String>>(emptyList())

    /** null until the driver picks one. */
    private val _language = MutableStateFlow<Language?>(null)
    val language: StateFlow<Language?> = _language

    private val _lastGuidance = MutableStateFlow<Guidance?>(null)
    val lastGuidance: StateFlow<Guidance?> = _lastGuidance

    val scenarios: List<String> by lazy { context.assets.list("scenarios").orEmpty().sorted() }
    private val _scenario = MutableStateFlow<String?>(null)
    /** File name of the scenario being replayed, or null. */
    val scenario: StateFlow<String?> = _scenario

    private val _tripSummary = MutableStateFlow<List<GuidanceRecord>?>(null)
    /** Guidance received this session, set when the car is parked after driving. */
    val tripSummary: StateFlow<List<GuidanceRecord>?> = _tripSummary

    // ponytail: app-lifetime singleton so language/guidance survive Activity recreation (night mode, split screen);
    // the Vehicle SDK is released at process death. Move to a foreground service if guidance must run with no UI.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                val id = queue.first { it.isNotEmpty() }.first()
                queue.update { it - id }
                play(id)
            }
        }
        scope.launch {
            val vehicle = Vehicle(context).apply { initialize() }
            try {
                val scenarioSignals = _scenario.flatMapLatest { file ->
                    if (file == null) emptyFlow()
                    else ScenarioSignalSource(context, file).signals
                        .catch { Log.w(TAG, "scenario $file failed", it) }
                        .onCompletion { if (it == null) _scenario.compareAndSet(file, null) }
                }
                // One source failing (missing permission, no service) must not take the others or the app down.
                merge(
                    PleosVehicleSignalSource(vehicle).signals.orLog("vehicle"),
                    CarPropertySignalSource(context).signals.orLog("car property"),
                    NaviSignalSource(context).signals.orLog("navi"),
                    FusedLocationSignalSource(context, schoolZones).signals.orLog("fused location"),
                    scenarioSignals,
                )
                    .collect { signal ->
                        Log.d(TAG, "signal $signal")
                        if (signal is DrivingSignal.Location) lastLocation = signal
                        engine.onSignal(signal, SystemClock.elapsedRealtime())?.let(::enqueue)
                    }
            } finally {
                vehicle.release()
            }
        }
    }

    fun selectLanguage(language: Language) {
        _language.value = language
        engine.onLanguageSelected()?.let(::enqueue)
    }

    fun replayBriefing() = enqueue(MessageId.BOARDING_BRIEFING)

    /** On-demand guidance, e.g. a Gleo FAQ request. */
    fun speak(messageId: String) = enqueue(messageId)

    /** Starts replaying [file], or stops the current scenario when null. */
    fun playScenario(file: String?) {
        _scenario.value = file
    }

    fun uiText(messageId: String) = messages.text(messageId, language.value ?: Language.fromLocale())
    fun text(messageId: String, language: Language) = messages.text(messageId, language)

    // ponytail: queued items never expire; drop stale ones (e.g. a right-turn reminder behind a 30 s briefing) if that bites.
    private fun enqueue(id: String) = queue.update { q -> (q + id).distinct().sortedBy(MessageId::priority) }

    private suspend fun play(id: String) {
        if (MessageId.category(id) != null) {
            val at = lastLocation
            records.insert(GuidanceRecord(messageId = id, timeMs = System.currentTimeMillis(), lat = at?.lat?.round3(), lon = at?.lon?.round3()))
        }
        if (id == MessageId.TRIP_SUMMARY) _tripSummary.value = records.since(sessionStartMs)
        val languages = if (id == MessageId.LANGUAGE_PROMPT) Language.entries else listOf(language.value ?: Language.fromLocale())
        _lastGuidance.value = Guidance(id, languages.joinToString("\n") { messages.text(id, it) })
        focus.withFocus { languages.forEach { voice.speak(id, it) } }
    }

    private fun Flow<DrivingSignal>.orLog(name: String) = catch { Log.w(TAG, "$name source failed; continuing without it", it) }

    private fun Double.round3() = Math.round(this * 1000) / 1000.0

    private companion object {
        const val TAG = "GuidancePipeline"
    }
}
