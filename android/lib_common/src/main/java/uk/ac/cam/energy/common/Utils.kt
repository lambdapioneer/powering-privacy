package uk.ac.cam.energy.common

import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import kotlin.random.Random

fun Map<String, String>.getWithDefault(key: String, default: String) = this[key] ?: default

fun Map<String, String>.getWithList(
    key: String,
    validChoices: Array<String>
): String {
    if (!validChoices.contains(this[key])) {
        throw IllegalArgumentException("${this[key]} is not from valid choices: $validChoices")
    }
    return this[key]!!
}

fun Map<String, String>.getWithDefaultAndList(
    key: String,
    default: String,
    validChoices: Array<String>
): String {
    val value = this[key]
    if (value != null && !validChoices.contains(value)) {
        throw IllegalArgumentException("$value is not from valid choices: $validChoices")
    }
    return this.getWithDefault(key, default)
}

fun randomArray(size: Int): ByteArray {
    return Random(randomSeed()).nextBytes(size)
}

fun randomPassword(size: Int): CharArray {
    val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val r = Random(randomSeed())
    return CharArray(size) { alphabet[r.nextInt(alphabet.length)] }
}

fun randomInt(until: Int): Int {
    return Random(randomSeed()).nextInt(until)
}

private fun randomSeed() = monotonicMs().toLong()

fun waitUntilPredicateTrue(
    timeoutMs: Long,
    sleepMs: Long = 100,
    predicate: () -> Boolean
): Boolean {
    val timeoutEnd = monotonicMs() + timeoutMs
    var result = false
    while (monotonicMs() < timeoutEnd) {
        if (predicate()) {
            result = true
            break
        }
        Thread.sleep(sleepMs)
    }
    return result
}

fun File.mkdirIfNotExists() {
    if (!exists()) mkdir()
}

fun all(vararg booleans: Boolean) = booleans.all { it }

fun monotonicMs() = SystemClock.elapsedRealtimeNanos() / 1_000_000.0

class RunnableWithWakelock(
    private val wakeLock: PowerManager.WakeLock,
    private val wakeLockTimeoutMs: Long,
    private val runnable: () -> Unit
) : Runnable {
    override fun run() {
        try {
            wakeLock.acquire(wakeLockTimeoutMs)
            runnable()
        } finally {
            wakeLock.release()
        }
    }

}
