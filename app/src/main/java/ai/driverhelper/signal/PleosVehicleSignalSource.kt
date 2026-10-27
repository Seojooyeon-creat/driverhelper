package ai.driverhelper.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import java.util.concurrent.ConcurrentHashMap

/** Signals from the Pleos Vehicle SDK. The SDK queues requests until its service connects. */
class PleosVehicleSignalSource(private val vehicle: Vehicle) : DrivingSignalSource {

    private val turnSignals = callbackFlow {
        val turnSignal = vehicle.getTurnSignal()
        val listener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                val direction = when (state) {
                    TurnSignalValue.LEFT -> TurnDirection.LEFT
                    TurnSignalValue.RIGHT -> TurnDirection.RIGHT
                    else -> TurnDirection.OFF
                }
                trySend(DrivingSignal.TurnSignal(direction))
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "turn signal failed", e)
            }
        }
        turnSignal.checkTurnSignalStateCapabilities(
            onComplete = { Log.i(TAG, "turn signal capability: $it") },
            onFailed = { Log.w(TAG, "turn signal capability check failed", it) },
        )
        turnSignal.registerTurnSignalState(listener)
        awaitClose { turnSignal.unregisterTurnSignalState(listener) }
    }

    /** Area IDs must match the car's config exactly, so subscribe to every cabin door area it reports. */
    private val doorSignals = callbackFlow {
        val door = vehicle.getDoor()
        val openByArea = ConcurrentHashMap<DoorArea, Boolean>()
        val listeners = ConcurrentHashMap<DoorArea, DoorPositionListener>()
        door.checkDoorPositionCapabilities(
            onComplete = { config, areas ->
                Log.i(TAG, "door capability: $config, areas=${areas.keys.map { it.values.toList() }}")
                areas.keys.filter { area -> area.values.none { it == DoorValue.HOOD || it == DoorValue.REAR } }.forEach { area ->
                    val listener = object : DoorPositionListener {
                        override fun onPositionUpdated(pos: Int?) {
                            openByArea[area] = (pos ?: 0) > 0
                            trySend(DrivingSignal.DoorOpen(openByArea.values.any { it }))
                        }

                        override fun onFailed(e: Exception) {
                            Log.w(TAG, "door ${area.values.toList()} failed", e)
                        }
                    }
                    listeners[area] = listener
                    door.registerDoorPosition(area, listener)
                }
            },
            onFailed = { Log.w(TAG, "door capability check failed", it) },
        )
        awaitClose { listeners.forEach { (area, listener) -> door.unregisterDoorPosition(area, listener) } }
    }

    override val signals = merge(turnSignals, doorSignals)

    private companion object {
        const val TAG = "PleosVehicleSignal"
    }
}
