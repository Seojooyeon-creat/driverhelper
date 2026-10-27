package ai.driverhelper.signal

/**
 * Feature 6 (pre-departure route briefing): caution zones on the whole route, summarised before setting off.
 *
 * TODO(ask): NaviHelper 3.0.5 has no route geometry or full-route caution list. TripSummary.routePoints holds
 * waypoints/chargers only, and onCameraAlert/onCautionPointInfo report what is just ahead. Implement once
 * Pleos exposes route polyline or caution points along the route.
 */
interface RouteBriefingSource {
    /** Message IDs to speak before departure, e.g. school zones or section enforcement on the route. */
    suspend fun cautionsOnRoute(): List<String>
}
