package uk.ac.cam.energy.common.execution

import android.util.Log
import java.io.Closeable
import kotlin.random.Random


interface Signaller : Closeable {
    fun initialize(): Boolean
    fun isConnected() : Boolean
    fun signalHigh()
    fun signalLow()
}

class LogCatSignaller : Signaller {
    override fun initialize() = true

    override fun isConnected(): Boolean {
        // random return to not block the preparation steps if we use LogCat instead of USB
        return Random(System.currentTimeMillis()).nextBoolean()
    }

    override fun signalHigh() {
        Log.i("Signaller", "start")
    }

    override fun signalLow() {
        Log.i("Signaller", "stop")
    }

    override fun close() {
        // nothing to do here
    }
}
