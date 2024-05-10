package uk.cam.energy.arkworkskt

import android.util.Log
import androidx.annotation.Keep

@Keep
object ArkworksKt {

    fun init() {
        System.loadLibrary("arkworks_wrapper")

        val actual = verifyJniBinding(10)
        if (actual != 11) {
            Log.e("ArkworksKt", "verifyJniBinding failed")
            throw IllegalStateException("actual: $actual (expected: 11)")
        }
    }

    external fun verifyJniBinding(a: Int): Int

    /**
     * @returns `ptr* PostSetup`
     */
    external fun benchSetup(numConstraints: Int, numVariables: Int): Long

    /**
     * @param ptr `ptr* PostSetup`
     * @returns `ptr* PostProof`
     */
    external fun benchProve(ptr: Long): Long

    /**
     * @param ptr `ptr* PostProof`
     * @return verification result casted to int (1 if `true`, 0 otherwise)
     */
    external fun benchVerify(ptr: Long): Int

    /**
     * @param ptr `ptr* PostSetup`
     */
    external fun benchFreePostSetup(ptr: Long)

    /**
     * @param ptr `ptr* PostProof`
     */
    external fun benchFreePostProof(ptr: Long)
}
