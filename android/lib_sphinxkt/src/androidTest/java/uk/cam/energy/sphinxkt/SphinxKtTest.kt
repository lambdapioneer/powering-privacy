package uk.cam.energy.sphinxkt

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class SphinxKtTest {

    @Before
    fun setup() {
        SphinxKt.init()
    }

    @Test
    fun init_whenInitCalled_thenNothingThrows() {
        SphinxKt.init()
    }

    @Test
    fun bench_whenCreateRunFree_thenNoCrash() {
        val ptr = SphinxKt.benchSetup()
        SphinxKt.benchRun(ptr, 1)
        SphinxKt.benchFree(ptr)
    }

    @Test
    fun bench_whenRunMultipleTimes_thenCapturesTime() {
        val ptr = SphinxKt.benchSetup()

        val iterations = 100
        val timeStartMs = SystemClock.elapsedRealtime()
        SphinxKt.benchRun(ptr, iterations)
        val timeDeltaMs = SystemClock.elapsedRealtime() - timeStartMs

        Log.i("SphinxKtTest", "iterations=$iterations timeDeltaMs=$timeDeltaMs")

        SphinxKt.benchFree(ptr)
    }

    @Test
    fun bench_whenRunMultipleTimesWithJni_thenCapturesTime() {
        val ptr = SphinxKt.benchSetup()

        val iterations = 100
        val timeStartMs = SystemClock.elapsedRealtime()
        for (i in 0 until iterations) {
            SphinxKt.benchRun(ptr, 1)
        }
        val timeDeltaMs = SystemClock.elapsedRealtime() - timeStartMs

        Log.i("SphinxKtTest", "iterations=$iterations timeDeltaMs=$timeDeltaMs")

        SphinxKt.benchFree(ptr)
    }
}
