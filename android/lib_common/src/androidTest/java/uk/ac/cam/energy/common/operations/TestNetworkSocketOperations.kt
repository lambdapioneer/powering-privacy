package uk.ac.cam.energy.common.operations

import android.net.TrafficStats
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.Before
import org.junit.Test
import uk.ac.cam.energy.common.execution.getTracer
import uk.ac.cam.energy.common.monotonicMs

class TestNetworkSocketOperations {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        getTracer().init(null, monotonicMs())
    }


    private fun txBytes() = TrafficStats.getUidTxBytes(Process.myUid())
    private fun rxBytes() = TrafficStats.getUidRxBytes(Process.myUid())

    @Test
    fun testOperation_whenTcpFor10SecondsEvery5Seconds10KiB_thenTrafficStatsUpdate() {
        internalTest("tcp")
    }

    @Test
    fun testOperation_whenTcpKeepAliveFor10SecondsEvery5Seconds10KiB_thenTrafficStatsUpdate() {
        internalTest("tcp_keep")
    }

    @Test
    fun testOperation_whenUdpFor10SecondsEvery5Seconds10KiB_thenTrafficStatsUpdate() {
        internalTest("udp")
    }

    private fun internalTest(protocol: String) {
        val operation = NetworkContinuousSocketOperation(
            context = context,
            name = "test",
            pause = TEST_PAUSE,
            args = mapOf(
                Pair("payload_write_kib", "10240"),
                Pair("payload_read_kib", "10240"),
                Pair("protocol", protocol),
                Pair("duration_ms", "9990"), // slightly less than 10s
                Pair("interval_ms", "5000"),
            )
        )

        val txBefore = txBytes()
        val rxBefore = rxBytes()

        operation.run()
        Thread.sleep(250)  // let tx/rx values propagate

        val txAfter = txBytes()
        val rxAfter = rxBytes()

        assertThat(operation.debug()).contains("successes=2&failures=0")
        assertThat(txAfter - txBefore).isCloseTo(2 * 10240, Percentage.withPercentage(15.0))
        assertThat(rxAfter - rxBefore).isCloseTo(2 * 10240, Percentage.withPercentage(15.0))
    }

}
