package ai.driverhelper.signal

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Standard AAOS properties (gear, parking brake) via CarPropertyManager. */
class CarPropertySignalSource(private val context: Context) : DrivingSignalSource {

    override val signals = callbackFlow {
        val car = Car.createCar(context)
        val properties = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                when (value.propertyId) {
                    VehiclePropertyIds.GEAR_SELECTION -> trySend(DrivingSignal.Gear(isPark = value.value == VehicleGear.GEAR_PARK))
                    VehiclePropertyIds.PARKING_BRAKE_ON -> trySend(DrivingSignal.ParkingBrake(on = value.value == true))
                }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "property $propertyId error (area $areaId)")
            }
        }
        listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.PARKING_BRAKE_ON).forEach { id ->
            val registered = properties?.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE) == true
            Log.i(TAG, "${VehiclePropertyIds.toString(id)} subscription: $registered")
        }
        awaitClose {
            properties?.unregisterCallback(callback)
            car?.disconnect()
        }
    }

    private companion object {
        const val TAG = "CarPropertySignal"
    }
}
