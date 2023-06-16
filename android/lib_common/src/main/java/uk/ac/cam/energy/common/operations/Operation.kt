package uk.ac.cam.energy.common.operations

import android.content.Context


abstract class Operation(val context: Context, val name: String, val pause: Pause) {

    /**
     * Any setup that needs to happen before the measured code starts. Returns true if actually
     * anything was done (and the runner should add some extra pause in that case).
     */
    open fun before(): Boolean {
        return false
    }

    /**
     * The main part of the measured operation.
     */
    abstract fun run()

    /**
     * Any clean-up that needs to happen after the measured code ended.
     */
    open fun after() {}

    /**
     * Debug string to attach to the log.
     */
    open fun debug(): String = ""

    fun runCompletely() {
        before()
        run()
        after()
    }
}
