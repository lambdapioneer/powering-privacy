package uk.ac.cam.energy.common.operations

import android.content.Context
import android.util.Log
import uk.ac.cam.energy.common.*

const val KiB = 1024

abstract class NetworkSocketOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : Operation(context, name, pause) {
    // socket configuration
    protected val writeKiB = args.getWithDefault("write_kib", "10").toInt()
    protected val readKiB = args.getWithDefault("read_kib", "10").toInt()
    protected val protocol = args.getWithDefaultAndList(
        "protocol",
        "tcp",
        arrayOf("tcp", "tcp_keep", "udp")
    )
    private val waitForRead = args.getWithDefault("wait_for_read", "true").toBooleanStrict()

    protected val socketClient: SocketClient = when (protocol) {
        "tcp" -> TcpSocketClient(writeKiB * KiB, readKiB * KiB)
        "tcp_keep" -> TcpKeepAliveSocketClient(writeKiB * KiB, readKiB * KiB)
        "udp" -> UdpSocketClient(writeKiB * KiB, readKiB * KiB, waitForRead)
        else -> throw IllegalArgumentException("bad protocol: $protocol")
    }

    protected fun nowMs() = monotonicMs()
}

class NetworkSingleSocketOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : NetworkSocketOperation(context, name, pause, args) {

    override fun run() {
        socketClient.send()
    }

    override fun debug() = "write_kib=$writeKiB" +
            "&read_kib=$readKiB" +
            "&protocol=$protocol"
}

class NetworkContinuousSocketOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : NetworkSocketOperation(context, name, pause, args) {
    // timing configurations
    private val durationMs = args.getWithDefault("duration_ms", "30000").toLong()
    private val intervalMs = args.getWithDefault("interval_ms", "5000").toLong()

    // keeping track
    private var successes = 0
    private var failures = 0

    override fun run() {
        val startMs = nowMs()
        val finishMs = startMs + durationMs

        while (nowMs() < finishMs) {
            val intervalStart = nowMs()
            // execute
            try {
                socketClient.send()
                successes++
            } catch (e: Exception) {
                Log.e(
                    "NetworkOperations",
                    "failed for $protocol $writeKiB $readKiB",
                    e
                )
                failures++
            }

            // wait until next following interval; in case we are overrunning wait for alignment
            val intervalUsed = nowMs() - intervalStart
            var sleepMs = intervalMs - intervalUsed
            while (sleepMs <= 0) sleepMs += intervalMs

            // if we would sleep until after our finish time, just sleep until finish time and then
            // finish our work here
            if (nowMs() + sleepMs >= finishMs) {
                val remainingSleepMs = (finishMs - nowMs()).toLong()
                if (remainingSleepMs > 0) {
                    Thread.sleep(remainingSleepMs)
                }
                return
            }

            // otherwise sleep until next interval
            Thread.sleep(sleepMs.toLong())
        }
    }

    override fun debug() = "write_kib=$writeKiB" +
            "&read_kib=$readKiB" +
            "&protocol=$protocol" +
            "&duration_ms=$durationMs" +
            "&interval_ms=$intervalMs" +
            "&successes=$successes" +
            "&failures=$failures"
}
