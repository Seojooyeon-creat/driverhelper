package ai.driverhelper.rule

import ai.driverhelper.signal.DrivingSignal.DoorOpen
import ai.driverhelper.signal.DrivingSignal.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardingRuleTest {
    private val engine = RuleEngine()

    private fun doorCycle(): String? {
        engine.onSignal(DoorOpen(true), 0)
        return engine.onSignal(DoorOpen(false), 0)
    }

    @Test
    fun boardingWithoutLanguagePromptsThenSelectionBriefsOnce() {
        engine.onSignal(Gear(isPark = true), 0)
        assertEquals(MessageId.LANGUAGE_PROMPT, doorCycle())
        assertEquals(MessageId.BOARDING_BRIEFING, engine.onLanguageSelected())
        assertNull("changing language does not re-brief", engine.onLanguageSelected())
        assertNull("boarding fires once per session", doorCycle())
    }

    @Test
    fun boardingAfterLanguageChosenBriefsDirectlyAndOnlyOnce() {
        engine.onSignal(Gear(isPark = true), 0)
        assertEquals(MessageId.BOARDING_BRIEFING, engine.onLanguageSelected())
        assertNull(doorCycle())
    }

    @Test
    fun noTriggerOutsidePark() {
        engine.onSignal(Gear(isPark = false), 0)
        assertNull(doorCycle())
        engine.onSignal(Gear(isPark = true), 0)
        assertNull("closing alone is not boarding", engine.onSignal(DoorOpen(false), 0))
        assertEquals(MessageId.LANGUAGE_PROMPT, doorCycle())
    }

    @Test
    fun initialClosedDoorIsNotBoarding() {
        engine.onSignal(Gear(isPark = true), 0)
        assertNull(engine.onSignal(DoorOpen(false), 0))
    }
}
