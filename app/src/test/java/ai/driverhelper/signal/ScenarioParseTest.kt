package ai.driverhelper.signal

import ai.driverhelper.data.parseSchoolZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScenarioParseTest {
    private val assets = File("src/main/assets")

    @Test
    fun shippedScenariosAndZonesParseAndAreTimeOrdered() {
        val files = File(assets, "scenarios").listFiles().orEmpty()
        assertTrue(files.isNotEmpty())
        files.forEach { f ->
            val times = parseScenario(f.readText()).map { it.first }
            assertEquals("${f.name} sorted by t", times.sorted(), times)
        }
        assertTrue(parseSchoolZones(File(assets, "school_zones.json").readText()).isNotEmpty())
    }

    @Test
    fun mapsEventTypes() {
        val json = """[{"t":0,"type":"turn","direction":"RIGHT"},{"t":5,"type":"camera","id":"c","kind":"FIXED"},{"t":6,"type":"telltale","telltale":"TPMS","on":true},{"t":7,"type":"energy","percent":40,"electric":true},{"t":8,"type":"brake_pedal","pressed":true}]"""
        assertEquals(
            listOf(
                0L to DrivingSignal.TurnSignal(TurnDirection.RIGHT),
                5L to DrivingSignal.Camera("c", CameraKind.FIXED, null),
                6L to DrivingSignal.Telltale("TPMS", true),
                7L to DrivingSignal.Energy(40, true),
                8L to DrivingSignal.BrakePedal(true),
            ),
            parseScenario(json),
        )
    }
}
