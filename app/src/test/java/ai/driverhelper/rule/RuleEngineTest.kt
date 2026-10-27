package ai.driverhelper.rule

import ai.driverhelper.signal.DrivingSignal.TurnSignal
import ai.driverhelper.signal.TurnDirection.LEFT
import ai.driverhelper.signal.TurnDirection.OFF
import ai.driverhelper.signal.TurnDirection.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEngineTest {
    private val engine = RuleEngine(reminderCooldownMs = 1_000)

    private fun turnRight(at: Long): String? {
        engine.onSignal(TurnSignal(OFF), at)
        return engine.onSignal(TurnSignal(RIGHT), at)
    }

    @Test
    fun fullRuleOnceThenShortReminderWithCooldown() {
        assertEquals(MessageId.RIGHT_TURN_STOP, turnRight(0))
        assertNull("repeated RIGHT is not a new turn", engine.onSignal(TurnSignal(RIGHT), 10))
        assertNull("reminder waits a cooldown after the full rule", turnRight(500))
        assertEquals(MessageId.RIGHT_TURN_REMINDER, turnRight(1_000))
        assertNull("within reminder cooldown", turnRight(1_500))
        assertEquals(MessageId.RIGHT_TURN_REMINDER, turnRight(2_000))
    }

    @Test
    fun leftTurnIsSilent() {
        assertNull(engine.onSignal(TurnSignal(LEFT), 0))
        assertEquals(MessageId.RIGHT_TURN_STOP, engine.onSignal(TurnSignal(RIGHT), 10))
    }
}
