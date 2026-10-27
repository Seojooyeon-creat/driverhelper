package ai.driverhelper.rule

import ai.driverhelper.signal.DrivingSignal.Gear
import ai.driverhelper.signal.DrivingSignal.ParkingBrake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripSummaryRuleTest {
    private val engine = RuleEngine()

    @Test
    fun summaryOncePerParkAfterDriving() {
        assertNull("initial P at startup is not a return", engine.onSignal(Gear(isPark = true), 0))
        assertNull(engine.onSignal(ParkingBrake(on = true), 0))
        engine.onSignal(Gear(isPark = false), 0)
        assertEquals(MessageId.TRIP_SUMMARY, engine.onSignal(Gear(isPark = true), 0))
        assertNull("brake right after P is the same return", engine.onSignal(ParkingBrake(on = true), 0))
        engine.onSignal(Gear(isPark = false), 0)
        assertEquals("brake alone also ends a trip", MessageId.TRIP_SUMMARY, engine.onSignal(ParkingBrake(on = true), 0))
    }

    @Test
    fun onlyDrivingGuidanceIsCounted() {
        assertEquals("cat_camera", MessageId.category("camera_fixed_80"))
        assertEquals("cat_section", MessageId.category(MessageId.SECTION_END))
        assertNull(MessageId.category(MessageId.BOARDING_BRIEFING))
        assertNull(MessageId.category(MessageId.TRIP_SUMMARY))
    }
}
