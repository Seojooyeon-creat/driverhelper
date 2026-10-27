package ai.driverhelper.ui

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Exposes whether the car currently requires distraction-optimized (minimal) UI. */
class UxRestrictions(context: Context) {
    private val _restricted = MutableStateFlow(false)
    val restricted: StateFlow<Boolean> = _restricted

    private val car: Car? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) Car.createCar(context) else null
    private val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager

    init {
        manager?.let { m ->
            _restricted.value = m.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            m.registerListener { _restricted.value = it.isRequiresDistractionOptimization }
        }
    }

    fun release() {
        manager?.unregisterListener()
        car?.disconnect()
    }
}
