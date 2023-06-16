package uk.ac.cam.energy.autopilot.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import uk.ac.cam.energy.autopilot.ScenariosManager
import uk.ac.cam.energy.common.RunnableWithWakelock
import uk.ac.cam.energy.common.execution.*
import uk.ac.cam.energy.common.monotonicMs
import uk.ac.cam.energy.common.operations.Pause
import uk.ac.cam.energy.common.operations.PauseType
import uk.ac.cam.energy.common.operations.getPauseMs

private const val DEFAULT_PAUSE_MS = 10_000L
private const val MIN_PAUSE_MS = 5_000L

/**
 * Can be set from the ForegroundService to ensure that the background alarms shuts down. This is
 * the most reliable way to deliver an "interrupt-like" signal into the thread started by the
 * `AlarmReceiver`. An alternative would see the `AlarmReceiver` bind to the `ForegroundService`
 * to receive updates.. but that would get very messy quickly.
 */
var globalKillFlag: Boolean = false

data class AsyncExecutorData(
    val timeRefMs: Double,
    val timeScheduledMs: Double,
    val operationIndex: Int,
    val operationLines: Array<String>,
    val scenarioBasename: String,
    val startRealtimeMs: Long
) {
    companion object {
        fun fromExtras(intent: Intent) = AsyncExecutorData(
            intent.getDoubleExtra("timeRefMs", Double.NaN),
            intent.getDoubleExtra("timeScheduledMs", Double.NaN),
            intent.getIntExtra("operationIndex", -1),
            intent.getStringArrayExtra("operationLines")!!,
            intent.getStringExtra("scenarioBasename")!!,
            intent.getLongExtra("startRealtimeMs", -1),
        )
    }

    init {
        check(timeRefMs > 0L)
        check(timeScheduledMs > 0L)
        check(operationIndex >= 0)
        check(operationLines.isNotEmpty())
        check(scenarioBasename.isNotEmpty())
        check(startRealtimeMs > 0)
    }

    fun addToExtras(intent: Intent) {
        intent.putExtra("timeRefMs", timeRefMs)
        intent.putExtra("timeScheduledMs", timeScheduledMs)
        intent.putExtra("operationIndex", operationIndex)
        intent.putExtra("operationLines", operationLines)
        intent.putExtra("scenarioBasename", scenarioBasename)
        intent.putExtra("startRealtimeMs", startRealtimeMs)
    }

    fun getCurrentOperationLine() = operationLines[operationIndex]
    fun getNextOperationLine() = operationLines[operationIndex + 1]
    fun isLastOperation() = (operationIndex >= operationLines.size - 1)
    fun relativeNowMs() = monotonicMs() - timeRefMs

    fun getCopyForNext(nextTimeScheduledMs: Double): AsyncExecutorData = AsyncExecutorData(
        timeRefMs = timeRefMs,
        timeScheduledMs = nextTimeScheduledMs,
        operationIndex = operationIndex + 1,
        operationLines = operationLines,
        scenarioBasename = scenarioBasename,
        startRealtimeMs = startRealtimeMs
    )
}

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ALARM_INTENT_ACTION = "metronom_alarm"

        internal fun createAlarmIntent(
            context: Context,
            asyncExecutorData: AsyncExecutorData?
        ): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java)
            intent.action = ALARM_INTENT_ACTION
            asyncExecutorData?.addToExtras(intent) // extras are not considered in the #filterEquals method of Intents
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private val wakeLockTimeoutMs = 10 * 60 * 1000L // 10 minutes

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ALARM_INTENT_ACTION) {
            Log.i("AlarmReceiver", "unknown intent: $intent")
            return
        }

        val asyncExecutorData = AsyncExecutorData.fromExtras(intent)
        Log.i("AlarmReceiver", "asyncExecutorData=$asyncExecutorData")

        // get operation for this execution
        val parser = ScenarioFileParser(context)
        val operationLine = asyncExecutorData.getCurrentOperationLine()
        val operation = parser.parseLineOnlyOne(operationLine)

        // (re-) init the global tracer
        val tracer = getTracer()
        tracer.init(asyncExecutorData.scenarioBasename, asyncExecutorData.timeRefMs)

        // calculate difference between expected and actual scheduling to compensate for this
        // going forward
        val timeActualStartMs = monotonicMs()
        val durationPreMs = timeActualStartMs - asyncExecutorData.timeScheduledMs
        tracer.trace(asyncExecutorData.timeScheduledMs, "scheduled")
        Log.i("AlarmReceiver", "timeActualStartMs=${timeActualStartMs}")
        Log.i("AlarmReceiver", "timeScheduledMs=${asyncExecutorData.timeScheduledMs}")
        Log.i("AlarmReceiver", "durationPreMs=$durationPreMs")

        // setup wakelock for runnable as receivers can be stopped after 10 seconds
        val powerManager = context.getSystemService(Service.POWER_SERVICE) as PowerManager
        val cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarm:cpu")

        // Run everything in a background thread so the main thread becomes available asap. This
        // is important as some operations (e.g. the WebOperation and its WebView) run on the
        // main thread. Otherwise we'll have hard to debug dead-locks...
        val runnable = RunnableWithWakelock(cpuWakeLock, wakeLockTimeoutMs) {
            try {
                Log.i("AlarmReceiver", "operation '${operation}' start")

                val ts = asyncExecutorData.relativeNowMs()
                operation.runCompletely()
                val te = asyncExecutorData.relativeNowMs()
                Log.i("AlarmReceiver", "operation success: dT=${te - ts}ms")

                // save execution log and traces
                val executionLog = ExecutionLog(asyncExecutorData.scenarioBasename)
                executionLog.log(LogEntry(operation.name, ts, te, operation.debug()))
                tracer.trace(ts, "op start")
                tracer.trace(te, "op end")

                // append to disk
                executionLog.syncToDisk(context, asyncExecutorData.startRealtimeMs)
                tracer.syncToDisk(context, asyncExecutorData.startRealtimeMs)

            } catch (e: Exception) {
                Log.e("AlarmReceiver", "operation fail e=${e.message}")
                Log.e("AlarmReceiver", e.stackTraceToString())
            }

            if (asyncExecutorData.isLastOperation()) {
                // will loop indefinitely
                playFinishSound(context)
            } else {
                // update data for next intent
                val nextOperationLine = asyncExecutorData.getNextOperationLine()
                val nextOperation = parser.parseLineOnlyOne(nextOperationLine)
                val timeNowRelativeMs = monotonicMs() - asyncExecutorData.timeRefMs
                val nextTimeScheduledMs = nextAlarmTimeRelativeMs(
                    pause = nextOperation.pause,
                    timeActualStartMs = timeActualStartMs,
                    timeNowRelativeMs = timeNowRelativeMs,
                    durationPreMs = durationPreMs
                )
                val nextData = asyncExecutorData.getCopyForNext(nextTimeScheduledMs)

                // set new alarm
                setAlarm(
                    context = context,
                    asyncExecutorData = nextData,
                    timeNowRelativeMs = timeNowRelativeMs
                )
            }
        }
        val t = Thread(runnable)
        t.start()
    }

    private fun playFinishSound(context: Context) {
        val soundManager = AndroidSoundManager(context)
        // loop until stopped to draw attention of operator
        while (!globalKillFlag) {
            soundManager.playTtsSound(TtsSounds.OPERATIONS_FINISHED)
            Thread.sleep(10000)
        }
    }
}

fun startAlarmManagerExecutor(
    context: Context,
    runConfig: RunConfig,
    startRealtimeMs: Long,
    timeRefMs: Double
) {
    Log.i("AsyncExecutor", "startAlarmManagerExecutor()")
    globalKillFlag = false

    // load and prepare operations
    val scenarioFileParser = ScenarioFileParser(context)
    val scenariosManager = ScenariosManager(context)
    val scenarioFile = scenariosManager.getFileForName(runConfig.scenarioFileName)
    val operationLines = scenarioFileParser.getLinesFromStorage(scenarioFile).toTypedArray()

    // initial setup of timing references
    val operationIndex = 0
    val timeNowRelativeMs = monotonicMs() - timeRefMs
    val timeScheduledMs = nextAlarmTimeRelativeMs(
        pause = Pause(PauseType.PAUSE, DEFAULT_PAUSE_MS),
        timeActualStartMs = timeNowRelativeMs,
        timeNowRelativeMs = timeNowRelativeMs
    )

    // setup intent data and crew intent
    val data = AsyncExecutorData(
        timeRefMs = timeRefMs,
        timeScheduledMs = timeScheduledMs,
        operationIndex = operationIndex,
        operationLines = operationLines,
        scenarioBasename = runConfig.scenarioBasename,
        startRealtimeMs = startRealtimeMs // used for naming files
    )
    setAlarm(
        context = context,
        asyncExecutorData = data,
        timeNowRelativeMs = timeNowRelativeMs
    )
}

fun stopAlarmManagerExecutor(context: Context) {
    globalKillFlag = true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(AlarmReceiver.createAlarmIntent(context, null))
}

/**
 * Returns the time to schedule the next alarm on the `timeNowRelativeMs` timeline.
 */
private fun nextAlarmTimeRelativeMs(
    pause: Pause,
    timeActualStartMs: Double,
    timeNowRelativeMs: Double,
    durationPreMs: Double = 0.0
): Double {
    var pauseMs =
        getPauseMs(timeActualStartMs, timeNowRelativeMs, pause, MIN_PAUSE_MS) - durationPreMs
    Log.v("AsyncExecutor", "nextAlarmTimeRelativeMs#pauseMs=$pauseMs")
    while (pauseMs < MIN_PAUSE_MS) pauseMs += MIN_PAUSE_MS
    Log.v("AsyncExecutor", "nextAlarmTimeRelativeMs#pauseMs=$pauseMs")
    return pauseMs + timeNowRelativeMs
}

@RequiresApi(Build.VERSION_CODES.M)
private fun setAlarm(
    context: Context,
    asyncExecutorData: AsyncExecutorData,
    timeNowRelativeMs: Double
) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // convert from relative schedule time to system time
    val pauseMs = asyncExecutorData.timeScheduledMs - timeNowRelativeMs
    val scheduleSystemTimeMs = System.currentTimeMillis() + pauseMs
    if (scheduleSystemTimeMs - System.currentTimeMillis() < MIN_PAUSE_MS) {
        Log.w("AsyncExecutor", "Alarm scheduled too (?) close by: $pauseMs $scheduleSystemTimeMs")
    }

    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        scheduleSystemTimeMs.toLong(),
        AlarmReceiver.createAlarmIntent(context, asyncExecutorData)
    )
    Log.v("AsyncExecutor", "pauseMs=$pauseMs, scheduleSystemTimeMs=$scheduleSystemTimeMs")
}
