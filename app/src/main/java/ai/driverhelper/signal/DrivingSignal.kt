package ai.driverhelper.signal

import kotlinx.coroutines.flow.Flow
import java.util.Locale

enum class TurnDirection { OFF, LEFT, RIGHT }

/** Mirrors NaviHelper camera alerts: fixed camera and section (average speed) enforcement. */
enum class CameraKind { FIXED, SECTION_START, SECTION_ALERT, SECTION_END }

sealed interface DrivingSignal {
    data class TurnSignal(val direction: TurnDirection) : DrivingSignal
    /** Any cabin door (not hood/trunk) is open. */
    data class DoorOpen(val open: Boolean) : DrivingSignal
    data class Gear(val isPark: Boolean) : DrivingSignal
    data class ParkingBrake(val on: Boolean) : DrivingSignal

    data class Location(val lat: Double, val lon: Double) : DrivingSignal {
        /** Masked to ~1 km so logs never carry the exact position. */
        override fun toString() = String.format(Locale.ROOT, "Location(%.2f, %.2f)", lat, lon)
    }

    /** Approaching a camera. [id] is stable per camera/section so repeated alerts can be deduplicated. */
    data class Camera(val id: String, val kind: CameraKind, val limitKmh: Int? = null) : DrivingSignal

    /** A school zone reported by navi or a geofence. [key] dedups repeats; `entered = false` re-arms it. */
    data class SchoolZoneAlert(val key: String, val entered: Boolean = true) : DrivingSignal
}

/** A stream of vehicle/environment events. Collecting subscribes; cancelling unsubscribes. */
interface DrivingSignalSource {
    val signals: Flow<DrivingSignal>
}
