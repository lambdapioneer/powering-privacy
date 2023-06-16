package uk.ac.cam.energy.common.operations

import androidx.test.platform.app.InstrumentationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import uk.ac.cam.energy.common.execution.getTracer
import uk.ac.cam.energy.common.monotonicMs

class TestLoopixOperations {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        getTracer().init(null, monotonicMs())
    }

    @Test
    fun testLoopixOperation_whenFast_thenCanKeepUp() {
        val operation = LoopixOperation(
            context = context,
            name = "test",
            pause = TEST_PAUSE,
            args = mapOf(
                Pair("lambda_ms", "20"),
                Pair("duration_ms", "2000"),
                Pair("read_kib", "1"),
                Pair("write_kib", "1"),
                Pair("protocol", "udp"),
            )
        )
        operation.before()
        operation.run()
        operation.after()

        assertThat(operation.successes).isGreaterThanOrEqualTo(80)  // expected 100
    }

    @Test
    fun testLoopixOperation_whenSlow_thenWorks() {
        val operation = LoopixOperation(
            context = context,
            name = "test",
            pause = TEST_PAUSE,
            args = mapOf(
                Pair("lambda_ms", "200"),
                Pair("duration_ms", "4000"),
                Pair("read_kib", "32"),
                Pair("write_kib", "32"),
                Pair("protocol", "udp"),
            )
        )
        operation.before()
        operation.run()
        operation.after()

        assertThat(operation.successes).isGreaterThanOrEqualTo(15) // expected 100
    }
}
