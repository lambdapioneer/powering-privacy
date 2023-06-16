package uk.ac.cam.energy.socketclient

import android.content.Context
import android.content.Intent
import uk.ac.cam.energy.common.operations.IdleOperation
import uk.ac.cam.energy.common.operations.Pause
import uk.ac.cam.energy.common.operations.PauseType
import uk.ac.cam.energy.common.operations.WebOperation

data class WorkLoad(
    val pauseMs: Long,
    val operation: String,
) {
    companion object {
        fun fromExtras(intent: Intent) = WorkLoad(
            intent.getLongExtra("pausesMs", 0),
            intent.getStringExtra("operation")!!
        )
    }

    fun addToExtras(intent: Intent) {
        intent.putExtra("pausesMs", pauseMs)
        intent.putExtra("operation", operation)
    }
}

fun createOperationsList(context: Context) = listOf(
    WebOperation(
        context,
        "visit WEB nytimes.com",
        Pause(PauseType.NONE),
        mapOf(Pair("url", "https://www.nytimes.com"))
    ),
    WebOperation(
        context,
        "visit ONION nytimes...onion",
        Pause(PauseType.NONE),
        mapOf(Pair("url", "https://www.nytimesn7cgmftshazwhfgzm37qxb44r64ytbb2dj3x62d2lljsciiyd.onion/"))
    ),
    IdleOperation(
        context,
        "nop",
        Pause(PauseType.NONE),
        mapOf(Pair("duration", "0"))
    )
)

data class Pause(val pauseMs: Long, val name: String)

fun createPausesList() = listOf(
    Pause(60 * 1000L, "60s"),
    Pause(30 * 1000L, "30s"),
    Pause(10 * 1000L, "10s"),
)
