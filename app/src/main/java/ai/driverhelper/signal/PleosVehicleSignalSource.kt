package ai.driverhelper.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
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

    /** The SDK sends the whole telltale list on every change; forward only the lights whose state actually changed. */
    private val telltales = callbackFlow {
        val safety = vehicle.getSafety()
        val lastOn = ConcurrentHashMap<TelltaleType, Boolean>()
        val listener = object : TelltaleInfoListener {
            override fun onTelltaleInfoUpdated(info: Telltales?) {
                info?.telltales?.forEach { t ->
                    val on = t.state != TelltaleState.OFF
                    if (lastOn.put(t.type, on) != on) trySend(DrivingSignal.Telltale(t.type.name, on))
                }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "telltales failed", e)
            }
        }
        safety.registerTelltales(listener)
        awaitClose { safety.unregisterTelltales(listener) }
    }

    /** Fuel percent and EV state of charge; whichever this car reports. Read once, then follow changes. */
    private val energy = callbackFlow {
        val fuel = vehicle.getFuel()
        val ev = vehicle.getEvBattery()
        val fuelListener = object : FuelPercentListener {
            override fun onFuelPercentUpdated(percent: Int?) {
                percent?.let { trySend(DrivingSignal.Energy(it, electric = false)) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "fuel percent failed", e)
            }
        }
        val evListener = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(percent: Int?) {
                percent?.let { trySend(DrivingSignal.Energy(it, electric = true)) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "ev state of charge failed", e)
            }
        }
        fuel.getFuelPercent(fuelListener::onFuelPercentUpdated) { Log.i(TAG, "no fuel percent: $it") }
        ev.getEvBatteryStateOfCharge(evListener::onEvBatteryStateOfChargeUpdated) { Log.i(TAG, "no ev state of charge: $it") }
        fuel.registerFuelPercent(fuelListener)
        ev.registerEvBatteryStateOfCharge(evListener)
        awaitClose {
            fuel.unregisterFuelPercent(fuelListener)
            ev.unregisterEvBatteryStateOfCharge(evListener)
        }
    }

    private val brakePedal = callbackFlow {
        val brake = vehicle.getBrake()
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
                pressed?.let { trySend(DrivingSignal.BrakePedal(it)) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "brake pedal failed", e)
            }
        }
        brake.registerBrakePedalStatus(listener)
        awaitClose { brake.unregisterBrakePedalStatus(listener) }
    }

    override val signals = merge(
        turnSignals,
        doorSignals,
        brakePedal.catch { Log.w(TAG, "brake pedal unavailable", it) },
        telltales.catch { Log.w(TAG, "telltales unavailable", it) },
        energy.catch { Log.w(TAG, "energy unavailable", it) },
    )

    private companion object {
        const val TAG = "PleosVehicleSignal"
    }
}
