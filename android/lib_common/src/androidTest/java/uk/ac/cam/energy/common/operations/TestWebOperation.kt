package uk.ac.cam.energy.common.operations

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test


class TestWebOperation {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testWebOperation_whenCalledWithTheGuardian_thenWebsiteLoaded() {
        val op = WebOperation(
            context = context,
            name = "test",
            pause = TEST_PAUSE,
            args = mapOf(Pair("url", "https://www.theguardian.com"))
        )
        op.runCompletely()
    }

    @Test
    fun testWebOperation_whenCalledWithNYTimes_thenWebsiteLoaded() {
        val op = WebOperation(
            context = context,
            name = "test",
            pause = TEST_PAUSE,
            args = mapOf(Pair("url", "https://www.nytimes.com"))
        )
        op.runCompletely()
    }
}
