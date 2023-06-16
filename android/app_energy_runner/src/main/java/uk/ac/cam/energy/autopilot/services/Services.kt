package uk.ac.cam.energy.autopilot.services

import android.content.Intent
import uk.ac.cam.energy.common.all
import uk.ac.cam.energy.common.execution.getScenarioBasename


data class RunConfig(
    val useUsb: Boolean,
    val executionType: Int,
    val scenarioFileName: String,
) {
    companion object {
        const val EXECUTION_WAKELOCK = 1
        const val EXECUTION_ALARM_MANAGER = 2

        fun fromExtras(intent: Intent) = RunConfig(
            // using boolean array to use null-check for existence check (cheeky)
            intent.getBooleanArrayExtra("use_usb")!![0],
            intent.getIntArrayExtra("execution_type")!![0],
            intent.getStringExtra("scenario_name")!!
        )
    }

    val scenarioBasename = getScenarioBasename(scenarioFileName)

    init {
        if (executionType != EXECUTION_WAKELOCK && executionType != EXECUTION_ALARM_MANAGER)
            throw IllegalArgumentException("bad executionType: $executionType")
    }

    fun addToExtras(intent: Intent) {
        intent.putExtra("use_usb", booleanArrayOf(useUsb))
        intent.putExtra("execution_type", intArrayOf(executionType))
        intent.putExtra("scenario_name", scenarioFileName)
    }

}

data class RunnerStatus(
    var isRunning: Boolean = false,
    var step1_usbConnected: Boolean = false,
    var step2_sending: Boolean = false,
    var step3_usbDisconnected: Boolean = false,
    var step5_startedOperations: Boolean = false,
    var step6_finishedOperations: Boolean = false,
    var lastMessage: String = ""
)

interface StatusListener {
    fun onNewStatus(status: RunnerStatus)
}
