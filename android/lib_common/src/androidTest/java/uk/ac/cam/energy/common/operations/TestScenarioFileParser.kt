package uk.ac.cam.energy.common.operations

import androidx.test.platform.app.InstrumentationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import uk.ac.cam.energy.common.execution.ScenarioFileParser

class TestScenarioFileParser {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testParse_whenInputEmpty_thenOutputEmpty() {
        val parser = ScenarioFileParser(context)
        val output = parser.parse("")
        assertThat(output).isEmpty()
    }

    @Test
    fun testParse_whenInputContainsOnlyComments_thenOutputEmpty() {
        val parser = ScenarioFileParser(context)
        val output = parser.parse("//comment\n#comment")
        assertThat(output).isEmpty()
    }


    @Test
    fun testParse_whenInputContainsMultipleOperations_thenOutputContainsOneForEachIteration() {
        val parser = ScenarioFileParser(context)
        val input = """
            2;P2000;test-idle;idle;duration_ms=100
            3;S1000;test-loopix;loopix;lambda_ms=500
            1;;test-idle;idle;duration_ms=200
        """.trimIndent()

        val output = parser.parse(input)
        assertThat(output).hasSize(2 + 3 + 1)

        assertThat(output[0]).isInstanceOf(IdleOperation::class.java)
        assertThat((output[0] as IdleOperation).durationMs).isEqualTo(100)
        assertThat(output[0].pause).isEqualTo(Pause(PauseType.PAUSE, 2000))
        assertThat(output[1]).isInstanceOf(IdleOperation::class.java)
        assertThat((output[1] as IdleOperation).durationMs).isEqualTo(100)
        assertThat(output[1].pause).isEqualTo(Pause(PauseType.PAUSE, 2000))

        assertThat(output[2]).isInstanceOf(LoopixOperation::class.java)
        assertThat((output[2] as LoopixOperation).context).isEqualTo(context)
        assertThat(output[2].pause).isEqualTo(Pause(PauseType.SCHEDULE, 1000))
        assertThat(output[3]).isInstanceOf(LoopixOperation::class.java)
        assertThat((output[3] as LoopixOperation).context).isEqualTo(context)
        assertThat(output[3].pause).isEqualTo(Pause(PauseType.SCHEDULE, 1000))
        assertThat(output[4]).isInstanceOf(LoopixOperation::class.java)
        assertThat((output[4] as LoopixOperation).context).isEqualTo(context)
        assertThat(output[4].pause).isEqualTo(Pause(PauseType.SCHEDULE, 1000))

        assertThat(output[5]).isInstanceOf(IdleOperation::class.java)
        assertThat((output[5] as IdleOperation).durationMs).isEqualTo(200)
        assertThat(output[5].pause).isEqualTo(Pause(PauseType.NONE))
    }

}
