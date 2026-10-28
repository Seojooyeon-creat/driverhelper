package ai.driverhelper.rule

import ai.driverhelper.data.Guides
import ai.driverhelper.signal.CameraKind
import ai.driverhelper.signal.DrivingSignal
import ai.driverhelper.signal.TurnDirection
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object MessageId {
    const val RIGHT_TURN_STOP = "right_turn_stop"
    const val RIGHT_TURN_REMINDER = "right_turn_reminder"
    /** Spoken in every language, since none is chosen yet. */
    const val LANGUAGE_PROMPT = "language_prompt"
    const val BOARDING_BRIEFING = "boarding_briefing"
    const val SCHOOL_ZONE = "school_zone"
    /** Suffixed with `_<limit>` for limits in [SPEED_LIMITS]; bare when the limit is unknown. */
    const val CAMERA_FIXED = "camera_fixed"
    const val SECTION_START = "section_start"
    const val SECTION_SPEED_ALERT = "section_speed_alert"
    const val SECTION_END = "section_end"
    /** Parked after driving: show the trip summary. */
    const val TRIP_SUMMARY = "trip_summary"

    /** Limits that have their own recorded message, since a prerecorded clip cannot say an arbitrary number. */
    val SPEED_LIMITS = setOf(30, 40, 50, 60, 70, 80, 90, 100, 110)

    /** Lower plays first when several are waiting: school zone > camera > right turn > the rest. */
    fun priority(id: String) = when {
        id == SCHOOL_ZONE -> 0
        id.startsWith(CAMERA_FIXED) || id.startsWith("section_") -> 1
        id.startsWith("right_turn") -> 2
        else -> 3
    }

    /** Summary group (also its label's message ID), or null for messages not counted in the trip summary. */
    fun category(id: String) = when {
        id == SCHOOL_ZONE -> "cat_school_zone"
        id.startsWith(CAMERA_FIXED) -> "cat_camera"
        id.startsWith("section_") -> "cat_section"
        id.startsWith("right_turn") -> "cat_right_turn"
        else -> null
    }

    /** Rule and fine explanation shown when the driver taps a summary category. */
    fun detail(category: String) = "fine_" + category.removePrefix("cat_")
}

data class SchoolZone(val name: String, val lat: Double, val lon: Double)

/** Decides which guidance to play. Pure Kotlin: time is passed in so it is testable. */
class RuleEngine(
    var reminderCooldownMs: Long = 10 * 60_000,
    private val schoolZones: List<SchoolZone> = emptyList(),
    private val sectionAlertCooldownMs: Long = 60_000,
    /** Geofence, distance check and navi can all report the same zone within moments; speak it once. */
    private val schoolZoneDedupMs: Long = 60_000,
) {
    private var turn = TurnDirection.OFF
    private var rightTurnExplained = false
    private val lastPlayed = mutableMapOf<String, Long>()

    private var inPark = false
    private var doorWasOpened = false
    private var boarded = false
    private var languageChosen = false
    private var briefed = false
    /** Gear left P since the last summary. */
    private var drove = false

    /** Zones already announced; re-armed once the car is well clear of them. */
    private val announcedZones = mutableSetOf<SchoolZone>()
    /** Camera and school-zone-alert keys already spoken. */
    private val announcedKeys = mutableSetOf<String>()

    fun onSignal(signal: DrivingSignal, nowMs: Long): String? = when (signal) {
        is DrivingSignal.TurnSignal -> {
            val rising = signal.direction == TurnDirection.RIGHT && turn != TurnDirection.RIGHT
            turn = signal.direction
            if (rising) rightTurn(nowMs) else null
        }
        is DrivingSignal.Gear -> {
            inPark = signal.isPark
            if (!inPark) {
                doorWasOpened = false
                drove = true
            }
            if (inPark) tripEnded() else null
        }
        is DrivingSignal.ParkingBrake -> if (signal.on) tripEnded() else null
        is DrivingSignal.DoorOpen -> {
            if (signal.open) doorWasOpened = inPark
            if (!signal.open && doorWasOpened && inPark && !boarded) onBoarded() else null
        }
        is DrivingSignal.Location -> schoolZone(signal, nowMs)
        is DrivingSignal.Camera -> camera(signal, nowMs)
        is DrivingSignal.SchoolZoneAlert -> when {
            !signal.entered -> { announcedKeys.remove(signal.key); null }
            announcedKeys.add(signal.key) -> fire(MessageId.SCHOOL_ZONE, nowMs, schoolZoneDedupMs)
            else -> null
        }
        is DrivingSignal.Telltale -> telltale(signal)
        is DrivingSignal.Energy, is DrivingSignal.BrakePedal -> null
    }

    /** A warning light is explained once each time it comes on; indicators (headlamps, auto hold) are never spoken. */
    private fun telltale(t: DrivingSignal.Telltale): String? {
        if (t.type !in Guides.autoExplain) return null
        val key = "telltale:${t.type}"
        if (!t.on) { announcedKeys.remove(key); return null }
        return if (announcedKeys.add(key)) Guides.telltales.getValue(t.type) else null
    }

    /** The user picked a language on screen (only possible while parked). */
    fun onLanguageSelected(): String? {
        languageChosen = true
        return brief()
    }

    /** Door opened and then closed, both while in P. */
    private fun onBoarded(): String? {
        boarded = true
        return if (languageChosen) brief() else MessageId.LANGUAGE_PROMPT
    }

    private fun brief(): String? {
        if (briefed) return null
        briefed = true
        return MessageId.BOARDING_BRIEFING
    }

    /** P or parking brake after driving; both arriving together still summarise once. */
    private fun tripEnded(): String? {
        if (!drove) return null
        drove = false
        return MessageId.TRIP_SUMMARY
    }

    /** Full rule once per session, then a short reminder at most once per cooldown. */
    private fun rightTurn(nowMs: Long): String? {
        if (rightTurnExplained) return fire(MessageId.RIGHT_TURN_REMINDER, nowMs)
        rightTurnExplained = true
        lastPlayed[MessageId.RIGHT_TURN_REMINDER] = nowMs
        return MessageId.RIGHT_TURN_STOP
    }

    // ponytail: linear scan over all zones per fix; add a grid index if the nationwide dataset (~16k) is loaded.
    private fun schoolZone(at: DrivingSignal.Location, nowMs: Long): String? {
        var entered = false
        for (zone in schoolZones) {
            val d = distanceM(at.lat, at.lon, zone.lat, zone.lon)
            if (d <= SCHOOL_ZONE_RADIUS_M + APPROACH_M) entered = announcedZones.add(zone) || entered
            else if (d > SCHOOL_ZONE_RADIUS_M + 2 * APPROACH_M) announcedZones.remove(zone)
        }
        return if (entered) fire(MessageId.SCHOOL_ZONE, nowMs, schoolZoneDedupMs) else null
    }

    /** Navi repeats alerts while approaching, so each camera/section is announced once. */
    private fun camera(camera: DrivingSignal.Camera, nowMs: Long): String? {
        if (camera.kind == CameraKind.SECTION_ALERT) return fire(MessageId.SECTION_SPEED_ALERT, nowMs, sectionAlertCooldownMs)
        if (!announcedKeys.add("${camera.kind}:${camera.id}")) return null
        return when (camera.kind) {
            CameraKind.FIXED -> withLimit(MessageId.CAMERA_FIXED, camera.limitKmh)
            CameraKind.SECTION_START -> withLimit(MessageId.SECTION_START, camera.limitKmh)
            else -> MessageId.SECTION_END
        }
    }

    private fun withLimit(id: String, limitKmh: Int?) =
        if (limitKmh != null && limitKmh in MessageId.SPEED_LIMITS) "${id}_$limitKmh" else id

    private fun fire(id: String, nowMs: Long, cooldownMs: Long = reminderCooldownMs): String? {
        val last = lastPlayed[id]
        if (last != null && nowMs - last < cooldownMs) return null
        lastPlayed[id] = nowMs
        return id
    }

    companion object {
        /** Korean school zones extend up to 300 m from the facility gate in most cases. */
        const val SCHOOL_ZONE_RADIUS_M = 300.0
        /** Announce this far before the zone boundary. */
        const val APPROACH_M = 200.0

        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * 6_371_000 * asin(sqrt(a))
        }
    }
}
