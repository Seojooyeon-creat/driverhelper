package ai.driverhelper.rule

import ai.driverhelper.signal.CameraKind.FIXED
import ai.driverhelper.signal.CameraKind.SECTION_ALERT
import ai.driverhelper.signal.CameraKind.SECTION_END
import ai.driverhelper.signal.CameraKind.SECTION_START
import ai.driverhelper.signal.DrivingSignal.Camera
import ai.driverhelper.signal.DrivingSignal.Location
import ai.driverhelper.signal.DrivingSignal.SchoolZoneAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchoolZoneAndCameraRuleTest {
    private val zone = SchoolZone("A", 37.5665, 126.9780)
    private val engine = RuleEngine(schoolZones = listOf(zone), sectionAlertCooldownMs = 1_000)

    /** A fix [m] metres south of the zone centre. */
    private fun south(m: Int, at: Long = 0) = engine.onSignal(Location(zone.lat - m / 111_195.0, zone.lon), at)

    @Test
    fun schoolZoneAnnouncedOnceBeforeEntryAndReArmedAfterLeaving() {
        assertNull(south(600))
        assertEquals(MessageId.SCHOOL_ZONE, south(450))
        assertNull(south(100))
        assertNull("still between approach and re-arm distance", south(600))
        assertNull("re-armed only once well clear", south(800))
        assertEquals(MessageId.SCHOOL_ZONE, south(450, at = 60_000))
    }

    @Test
    fun sameZoneFromSeveralSourcesSpokenOnce() {
        assertEquals(MessageId.SCHOOL_ZONE, south(450))
        assertNull("geofence for the same zone", engine.onSignal(SchoolZoneAlert("geofence:A"), 1_000))
        assertNull("navi caution point", engine.onSignal(SchoolZoneAlert("navi:1"), 2_000))
        assertNull("repeat of a spoken key", engine.onSignal(SchoolZoneAlert("navi:1"), 90_000))
        assertEquals("new zone after the dedup window", MessageId.SCHOOL_ZONE, engine.onSignal(SchoolZoneAlert("navi:2"), 90_000))
        engine.onSignal(SchoolZoneAlert("navi:2", entered = false), 100_000)
        assertEquals("re-armed by exit", MessageId.SCHOOL_ZONE, engine.onSignal(SchoolZoneAlert("navi:2"), 200_000))
    }

    @Test
    fun cameraOncePerIdWithLimitSpecificMessage() {
        assertEquals("camera_fixed_80", engine.onSignal(Camera("c1", FIXED, 80), 0))
        assertNull(engine.onSignal(Camera("c1", FIXED, 80), 0))
        assertEquals("no clip for 65 → generic", MessageId.CAMERA_FIXED, engine.onSignal(Camera("c2", FIXED, 65), 0))
        assertEquals("section_start_100", engine.onSignal(Camera("s1", SECTION_START, 100), 0))
        assertEquals(MessageId.SECTION_SPEED_ALERT, engine.onSignal(Camera("s1", SECTION_ALERT), 0))
        assertNull("alert cooldown", engine.onSignal(Camera("s1", SECTION_ALERT), 500))
        assertEquals(MessageId.SECTION_SPEED_ALERT, engine.onSignal(Camera("s1", SECTION_ALERT), 1_000))
        assertEquals(MessageId.SECTION_END, engine.onSignal(Camera("s1", SECTION_END), 2_000))
    }

    @Test
    fun priorityOrder() {
        val ids = listOf(MessageId.BOARDING_BRIEFING, MessageId.RIGHT_TURN_STOP, "section_start_100", MessageId.SCHOOL_ZONE)
        assertEquals(ids.reversed(), ids.sortedBy(MessageId::priority))
    }
}
