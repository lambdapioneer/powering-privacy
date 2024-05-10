package uk.ac.cam.energy.common.operations

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import uk.ac.cam.energy.common.execution.getTracer
import uk.ac.cam.energy.common.monotonicMs

class TestSnarkOperations {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        getTracer().init(null, monotonicMs())
    }

    @Test
    fun testSnarkVerifyOperation_whenRunCompletely_thenDoesNotThrow() {
        val operation = SnarkVerifyOperation(
            context = context,
            name = "test",
            pause = TEST_PAUSE,
            args = mapOf(
                Pair("iterations", "2"),
                Pair("num_constraints", "100"),
                Pair("num_variables", "100"),
            )
        )
        operation.runCompletely()
    }

    @Test
    fun testSnarkProofOperation_whenRunCompletely_thenDoesNotThrow() {
        val operation = SnarkProofOperation(
            context = context,
            name = "test",
            pause = TEST_PAUSE,
            args = mapOf(
                Pair("iterations", "2"),
                Pair("num_constraints", "100"),
                Pair("num_variables", "100"),
            )
        )
        operation.runCompletely()
    }
}
