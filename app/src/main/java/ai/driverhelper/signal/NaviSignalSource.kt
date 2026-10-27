package ai.driverhelper.signal

import ai.pleos.playground.navi.constants.CameraType
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Camera and school-zone alerts from the stock navigation. Emits nothing if the navi service is unavailable. */
class NaviSignalSource(private val context: Context) : DrivingSignalSource {

    override val signals = callbackFlow {
        val navi = NaviHelper(context)
        val listener = object : NaviHelperEventListener {
            override fun onCameraAlert(alerts: List<CameraAlert>) {
                alerts.forEach { alert -> alert.toSignal()?.let { trySend(it) } }
            }

            override fun onCautionPointInfo(points: List<CautionPoint>) {
                points.filter { it.type == CautionPointType.SCHOOL_ZONE }
                    .forEach { trySend(DrivingSignal.SchoolZoneAlert("navi:${it.id}")) }
            }

            override fun onError(code: NaviErrorCode) {
                Log.w(TAG, "navi error $code")
            }
        }
        navi.initialize(
            { navi.addListener(listener); Log.i(TAG, "navi connected") },
            { Log.w(TAG, "navi unavailable; school zones fall back to geofence/scenarios", it) },
        )
        awaitClose {
            navi.removeListener(listener)
            navi.release()
        }
    }

    // TODO(ask): meaning of CameraAlert.status is undocumented; it is ignored for now.
    private fun CameraAlert.toSignal(): DrivingSignal? = when (type) {
        CameraType.SPEED_CAMERA_SCHOOL_ZONE, CameraType.SPEED_CAMERA_SCHOOL_ZONE_TRAFFIC_LIGHT -> DrivingSignal.SchoolZoneAlert("navi:$id")
        CameraType.SPEED_CAMERA_SECTION_START -> DrivingSignal.Camera(id, CameraKind.SECTION_START, speedLimit)
        CameraType.SECTION_SPEED_ALERT -> if (isOverSectionAvrSpeed) DrivingSignal.Camera(id, CameraKind.SECTION_ALERT, speedLimit) else null
        CameraType.SPEED_CAMERA_SECTION_END -> DrivingSignal.Camera(id, CameraKind.SECTION_END)
        else -> DrivingSignal.Camera(id, CameraKind.FIXED, speedLimit)
    }

    private companion object {
        const val TAG = "NaviSignal"
    }
}
