package ai.driverhelper.rule

import ai.driverhelper.signal.DrivingSignal.Energy
import ai.driverhelper.signal.DrivingSignal.Telltale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelltaleRuleTest {
    private val engine = RuleEngine()

    @Test
    fun warningExplainedOncePerLighting() {
        assertEquals("telltale_low_ev_battery", engine.onSignal(Telltale("LOW_EV_BATTERY", on = true), 0))
        assertNull("repeat while still on", engine.onSignal(Telltale("LOW_EV_BATTERY", on = true), 1_000))
        assertNull(engine.onSignal(Telltale("LOW_EV_BATTERY", on = false), 2_000))
        assertEquals("re-armed after going off", "telltale_low_ev_battery", engine.onSignal(Telltale("LOW_EV_BATTERY", on = true), 3_000))
    }

    @Test
    fun indicatorsAndEnergyAreSilent() {
        assertNull(engine.onSignal(Telltale("HEADLAMP_HIGH", on = true), 0))
        assertNull(engine.onSignal(Telltale("PARKING_BRAKE", on = true), 0))
        assertNull(engine.onSignal(Telltale("UNKNOWN_TYPE", on = true), 0))
        assertNull(engine.onSignal(Energy(12, electric = false), 0))
    }
}
