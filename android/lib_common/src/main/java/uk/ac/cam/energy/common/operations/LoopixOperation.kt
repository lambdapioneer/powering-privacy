package uk.ac.cam.energy.common.operations

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import uk.ac.cam.energy.common.getWithDefault
import uk.ac.cam.energy.common.monotonicMs
import kotlin.math.ln
import kotlin.random.Random

class LoopixOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) :
    Operation(context, name, pause) {

    private val cryptoOperation = CryptoSphinxOperation(
        context = context,
        name = name + "_sphinx",
        pause = EMPTY_PAUSE,
        args = args
    )

    private val radioOperation = NetworkSingleSocketOperation(
        context = context,
        name = name + "_radio",
        pause = EMPTY_PAUSE,
        args = args + mapOf("wait_for_read" to "false")
    )

    private val lambdaMs = args.getWithDefault("lambda_ms", "1000").toInt()
    private val durationMs = args.getWithDefault("duration_ms", "10000").toInt()
    private val random: Random = Random(0)

    @VisibleForTesting
    internal var successes = 0

    override fun before(): Boolean {
        return cryptoOperation.before() or radioOperation.before()
    }

    override fun run() {
        val end = monotonicMs() + durationMs
        Log.d("LoopixOperation", "Start")

        // moving target (ensures we make up when we are falling behind)
        var startNextOne = monotonicMs()

        while (monotonicMs() < end) {
            // sleep until this operation should start
            val sleepMs = startNextOne - monotonicMs() - 1 // minus one to account for overhead
            Log.d("LoopixOperation", "sleepMs=$sleepMs")
            if (sleepMs > 0) {
                Thread.sleep(sleepMs.toLong())
            }

            // run
            cryptoOperation.run()
            radioOperation.run()
            successes += 1

            // schedule next one
            val delayMs = exponentialRandomMs(lambdaMs, random)
            startNextOne += delayMs
        }
        Log.d("LoopixOperation", "Finished")
    }

    override fun after() {
        cryptoOperation.after()
        radioOperation.after()
    }

    override fun debug(): String {
        return cryptoOperation.debug() + "&" + radioOperation.debug() +
                "&lambda_ms=$lambdaMs" +
                "&duration_ms=$durationMs" +
                "&successes=$successes"
    }
}

fun exponentialRandomMs(lambdaMs: Int, random: Random = Random(0)): Double {
    val u = random.nextDouble()
    return (-lambdaMs * ln(1.0 - u))
}
