package ai.driverhelper.signal

import ai.driverhelper.rule.RuleEngine
import ai.driverhelper.rule.SchoolZone
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Position fixes plus a proximity alert per school zone (radius = zone + approach distance).
 * Emits nothing when the SDK is unsupported or the permission is missing; scenarios still work.
 */
class FusedLocationSignalSource(private val context: Context, private val zones: List<SchoolZone>) : DrivingSignalSource {

    override val signals = callbackFlow {
        val location = FusedLocation(context)
        if (!location.isSupported()) {
            Log.i(TAG, "fused location not supported")
            awaitClose()
            return@callbackFlow
        }
        location.initialize()
        val cleanup = try {
            register(location)
        } catch (e: Exception) { // e.g. SecurityException until FUSED_LOCATION is granted
            location.release()
            throw e
        }
        awaitClose(cleanup)
    }

    /** Registers listeners and returns the matching cleanup. */
    private fun ProducerScope<DrivingSignal>.register(location: FusedLocation): () -> Unit {
        val listener = object : FusedLocationListener {
            override fun onChanged(data: LocationData?) {
                data ?: return
                trySend(DrivingSignal.Location(data.latitude, data.longitude))
            }

            override fun onError(e: Exception) {
                Log.w(TAG, "location failed", e)
            }
        }
        location.registerFusedLocationCallback(listener)

        // ponytail: one alert per zone; register only zones near the car if the full dataset hits an SDK limit (none documented).
        val alertIds = ConcurrentHashMap.newKeySet<String>()
        val radius = (RuleEngine.SCHOOL_ZONE_RADIUS_M + RuleEngine.APPROACH_M).toFloat()
        zones.forEach { zone ->
            val request = ProximityAlertRequest(zone.lat, zone.lon, radius, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT))
            location.addProximityAlert(request, object : ProximityAlertListener {
                override fun onRegistered(alertId: String) {
                    alertIds += alertId
                }

                override fun onTransition(event: ProximityAlertEvent) {
                    trySend(DrivingSignal.SchoolZoneAlert("geofence:${zone.name}", entered = event.transition == ProximityTransition.ENTER))
                }

                override fun onError(e: Exception) {
                    Log.w(TAG, "proximity alert failed", e)
                }
            })
        }
        Log.i(TAG, "location registered, ${zones.size} proximity alerts requested")

        return {
            alertIds.forEach(location::removeProximityAlert)
            location.unregisterFusedLocationCallback(listener)
            location.release()
        }
    }

    private companion object {
        const val TAG = "FusedLocationSignal"
    }
}
