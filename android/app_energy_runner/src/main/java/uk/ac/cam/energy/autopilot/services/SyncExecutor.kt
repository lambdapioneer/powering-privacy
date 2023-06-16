package uk.ac.cam.energy.autopilot.services

import android.content.Context
import android.util.Log
import uk.ac.cam.energy.autopilot.ScenariosManager
import uk.ac.cam.energy.common.execution.*
import uk.ac.cam.energy.common.monotonicMs
import uk.ac.cam.energy.common.operations.getPauseMs
import uk.ac.cam.energy.common.waitUntilPredicateTrue


private const val DEFAULT_PAUSE_MS = 1L

private const val SYNC_EDGES_NUM = 8
private const val SYNC_EDGES_DELTA_T = 500L

@Suppress("LeakingThis")
abstract class SyncExecutor(
    protected val applicationContext: Context,
    protected val runConfig: RunConfig,
    protected val listener: StatusListener,
    protected val startRealtimeMs: Long,
    private val timeRefMs: Double,
    val status: RunnerStatus = RunnerStatus()
) : Runnable {
    // members
    protected val executionLog = ExecutionLog(runConfig.scenarioBasename)
    protected val scenarioFileParser = ScenarioFileParser(applicationContext)
    protected val scenariosManager = ScenariosManager(applicationContext)
    protected val soundManager = AndroidSoundManager(applicationContext)
    protected val tracer = getTracer()

    // async running
    private lateinit var thread: Thread
    private lateinit var onFinishCallback: () -> Unit

    fun start(onFinishCallback: () -> Unit) {
        this.onFinishCallback = onFinishCallback

        thread = Thread(this@SyncExecutor)
        thread.start()
    }

    fun stop() {
        thread.interrupt()
    }

    override fun run() {
        // let others know about start of execution
        status.isRunning = true
        listener.onNewStatus(status)

        // re-init tracer
        tracer.init(runConfig.scenarioBasename, timeRefMs)

        try {
            runMain()
            onFinishCallback()
        } catch (e: InterruptedException) {
            Log.i("PreparationWithWakelock", "got interrupted: $e")
            status.lastMessage = "got interrupted: $e"
            listener.onNewStatus(status)
        } catch (e: java.lang.Exception) {
            Log.e("PreparationWithWakelock", "failure: ${e.stackTraceToString()}")
            status.lastMessage = "failure: $e"
            listener.onNewStatus(status)

            while (true) { // repeat to get operators attention
                soundManager.playTtsSound(TtsSounds.FAILURE)
                Thread.sleep(2000)
            }
        }
    }

    abstract fun runMain()

    protected fun relativeNowMs(): Double = monotonicMs() - timeRefMs
}

class PreparationSyncExecutor(
    applicationContext: Context,
    runConfig: RunConfig,
    listener: StatusListener,
    startRealtimeMs: Long,
    timeRefMs: Double
) : SyncExecutor(applicationContext, runConfig, listener, startRealtimeMs, timeRefMs) {

    private val prepareStepTimeoutMs = 10_000L
    private val signaller = createSignaller()

    override fun runMain() {
        // get USB ready
        signaller.initialize()

        // load and prepare operations (just to check all access and parsing is okay)
        val scenarioFile = scenariosManager.getFileForName(runConfig.scenarioFileName)
        scenarioFileParser.parseFromStorage(scenarioFile)

        // Step 1: wait for USB to be connected (that is usually already the case)
        status.lastMessage = "waiting for USB to connect"
        val connected = waitUntilPredicateTrue(prepareStepTimeoutMs, 1000) {
            isSignallerConnected()
        }
        if (!connected) throw IllegalStateException("Signaller not connected after timeout. Abort.")

        status.step1_usbConnected = true
        listener.onNewStatus(status)
        soundManager.playTtsSound(TtsSounds.USB_CONNECTED)

        // Step 2: execute sync sequence
        status.lastMessage = "executing sync sequence"
        Thread.sleep(DEFAULT_PAUSE_MS)
        signalSyncEdges()
        Thread.sleep(DEFAULT_PAUSE_MS)

        status.step2_sending = true
        listener.onNewStatus(status)
        soundManager.playTtsSound(TtsSounds.SYNC_DONE)

        // Step 3: wait for operator to disconnect USB
        soundManager.playTtsSound(TtsSounds.USB_WAIT_FOR_DISCONNECT)
        status.lastMessage = "waiting for USB to disconnect"
        val disconnected = waitUntilPredicateTrue(prepareStepTimeoutMs, 1000) {
            !isSignallerConnected()
        }
        if (!disconnected) throw IllegalStateException("Signaller not disconnected after timeout. Abort.")

        status.step3_usbDisconnected = true
        listener.onNewStatus(status)
        soundManager.playTtsSound(TtsSounds.USB_DISCONNECTED)
    }

    private fun isSignallerConnected() = try {
        signaller.isConnected()
    } catch (e: Exception) {
        false
    }

    private fun signalSyncEdges() {
        for (i in 0 until SYNC_EDGES_NUM) {
            tracer.trace("ts_a")
            val tsa = relativeNowMs()
            signaller.signalHigh()
            val tsb = relativeNowMs()
            tracer.trace("ts_b")

            Thread.sleep(SYNC_EDGES_DELTA_T)

            tracer.trace("te_a")
            val tea = relativeNowMs()
            signaller.signalLow()
            val teb = relativeNowMs()
            tracer.trace("te_b")

            Thread.sleep(SYNC_EDGES_DELTA_T)

            val ts = (tsa + tsb) / 2
            val te = (tea + teb) / 2
            Log.i("Runner", "signalSyncEdges TS $tsa $tsb $ts")
            Log.i("Runner", "signalSyncEdges TE $tea $teb $te")

            executionLog.log(LogEntry("sync", ts, te, ""))
        }

        // sync to disk
        executionLog.syncToDisk(applicationContext, startRealtimeMs)
        tracer.syncToDisk(applicationContext, startRealtimeMs)
    }

    private fun createSignaller(): Signaller =
        when (runConfig.useUsb) {
            true -> getGlobalUsbSignaller(applicationContext)
            false -> LogCatSignaller()
        }
}


class RunOperationsSyncExecutor(
    applicationContext: Context,
    runConfig: RunConfig,
    listener: StatusListener,
    startRealtimeMs: Long,
    timeRefMs: Double,
    status: RunnerStatus
) : SyncExecutor(applicationContext, runConfig, listener, startRealtimeMs, timeRefMs, status) {

    override fun runMain() {
        // load and prepare operations
        val scenarioFile = scenariosManager.getFileForName(runConfig.scenarioFileName)
        val operations = scenarioFileParser.parseFromStorage(scenarioFile)

        status.lastMessage = "starting operations..."
        status.step5_startedOperations = true
        soundManager.playTtsSound(TtsSounds.OPERATIONS_STARTED)
        listener.onNewStatus(status)

        var previousStartTimeMs = relativeNowMs() // required for PauseType.SCHEDULE
        for (operation in operations) {
            val beforeWasBusy = operation.before()
            if (beforeWasBusy) {
                // add extra pause in case we did a lot of work in preparation
                Thread.sleep(DEFAULT_PAUSE_MS)
            }

            @Suppress("LeakingThis", "LeakingThis") val pauseMs = getPauseMs(
                previousStartTimeMs = previousStartTimeMs,
                nowMs = relativeNowMs(),
                pause = operation.pause,
                minAndDefaultPause = DEFAULT_PAUSE_MS
            )
            Log.v("RunOperationsSyncExecut", "sleepMs=$pauseMs")
            Thread.sleep(pauseMs)

            val ts = relativeNowMs()
            operation.run()
            val te = relativeNowMs()

            operation.after()

            previousStartTimeMs = ts

            // log execution times
            executionLog.log(LogEntry(operation.name, ts, te, operation.debug()))
            tracer.trace(ts, "op start")
            tracer.trace(te, "op end")
        }

        // save logs
        executionLog.syncToDisk(applicationContext, startRealtimeMs)
        tracer.syncToDisk(applicationContext, startRealtimeMs)
        Log.i("RunOperationsSyncExecut", "logs saved")

        status.lastMessage = "finished operations"
        status.step6_finishedOperations = true
        listener.onNewStatus(status)

        // loop until stopped to draw attention of operator
        while (true) {
            soundManager.playTtsSound(TtsSounds.OPERATIONS_FINISHED)
            Thread.sleep(10000)
        }
    }
}
