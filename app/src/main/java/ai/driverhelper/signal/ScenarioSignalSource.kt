package ai.driverhelper.signal

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import org.json.JSONArray

/** Replays assets/scenarios/[file] for signals the emulator cannot produce (speed, GPS, navi events). */
class ScenarioSignalSource(private val context: Context, private val file: String) : DrivingSignalSource {

    override val signals = flow {
        val events = parseScenario(context.assets.open("scenarios/$file").bufferedReader().use { it.readText() })
        var elapsed = 0L
        for ((t, signal) in events) {
            delay(t - elapsed)
            elapsed = t
            emit(signal)
        }
    }
}

/** `[{ "t": ms from start, "type": turn|door|gear|brake|location|camera, ...fields }]`, sorted by t. */
fun parseScenario(json: String): List<Pair<Long, DrivingSignal>> {
    val events = JSONArray(json)
    return (0 until events.length()).map { i ->
        val e = events.getJSONObject(i)
        e.getLong("t") to when (val type = e.getString("type")) {
            "turn" -> DrivingSignal.TurnSignal(TurnDirection.valueOf(e.getString("direction")))
            "door" -> DrivingSignal.DoorOpen(e.getBoolean("open"))
            "gear" -> DrivingSignal.Gear(isPark = e.getBoolean("park"))
            "brake" -> DrivingSignal.ParkingBrake(on = e.getBoolean("on"))
            "location" -> DrivingSignal.Location(e.getDouble("lat"), e.getDouble("lon"))
            "camera" -> DrivingSignal.Camera(
                e.getString("id"),
                CameraKind.valueOf(e.getString("kind")),
                if (e.has("limit")) e.getInt("limit") else null,
            )
            else -> error("unknown scenario event type: $type")
        }
    }
}
