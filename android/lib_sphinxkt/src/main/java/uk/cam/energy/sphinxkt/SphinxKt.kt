package uk.cam.energy.sphinxkt

import android.util.Log
import androidx.annotation.Keep

@Keep
object SphinxKt {

    fun init() {
        System.loadLibrary("sphinx_wrapper")

        val actual = verifyJniBinding(10)
        if (actual != 11) {
            Log.e("SphinxKt", "verifyJniBinding failed")
            throw IllegalStateException("actual: $actual (expected: 11)")
        }
    }

    external fun verifyJniBinding(a: Int): Int

    external fun benchSetup(): Long
    external fun benchRun(ptr: Long, iter: Int)
    external fun benchFree(ptr: Long)
}
