package uk.ac.cam.energy.autopilot.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import uk.ac.cam.energy.autopilot.R
import uk.ac.cam.energy.autopilot.ui.MainActivity
import uk.ac.cam.energy.common.monotonicMs


class ForegroundRunnerService : Service(), StatusListener {

    // service maintenance
    private var mIsStarted = false
    private var mStatusListener: StatusListener? = null

    inner class RunnerBinder : Binder() {
        fun getService(): ForegroundRunnerService {
            return this@ForegroundRunnerService
        }
    }

    // notifications
    private val notificationId = 1000
    private val channelId = "background_service_notification_channel"
    private lateinit var notificationManager: NotificationManager

    // running thread
    private lateinit var runConfig: RunConfig
    private val timeRefMs = monotonicMs()
    private val startRealtimeMs = System.currentTimeMillis()
    private var currentSyncExecutor: SyncExecutor? = null

    // wake locks
    private val wakeLockTimeoutMs = 6 * 3600 * 1000L // 6 hours
    private lateinit var powerManager: PowerManager
    private lateinit var cpuWakeLock: PowerManager.WakeLock

    override fun onBind(intent: Intent?): IBinder {
        return RunnerBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("ForegroundRunnerService", "onStartCommand($intent)")

        // only start once
        if (mIsStarted) {
            Log.i("ForegroundRunnerService", "already started -> skip")
            return START_NOT_STICKY
        }
        mIsStarted = true
        runConfig = RunConfig.fromExtras(intent!!)

        // late init services
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "runner:cpu")

        // make foreground app
        makeForeground()

        // run preparations
        enableCpuWakeLock(true)
        currentSyncExecutor = PreparationSyncExecutor(
            applicationContext = applicationContext,
            runConfig = runConfig,
            listener = this,
            startRealtimeMs = startRealtimeMs,
            timeRefMs = timeRefMs
        )
        currentSyncExecutor!!.start(onFinishCallback = ::onPreparationFinished)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Stop both potential executors regardless of current configuration as the UI might have
        // reloaded and it is not clear which one is active
        currentSyncExecutor?.stop() // Option 1: EXECUTION_WAKELOCK
        stopAlarmManagerExecutor(applicationContext) // Option 2: EXECUTION_ALARM_MANAGER

        enableCpuWakeLock(false)
        super.onDestroy()
    }

    fun registerStatusListener(statusListener: StatusListener?) {
        mStatusListener = statusListener

        // send initial state directly (or default is in weird state)
        onNewStatus(currentSyncExecutor?.status ?: RunnerStatus())
    }

    private fun onPreparationFinished() {
        when (runConfig.executionType) {
            // for the wakelock execution type we replace the preparation SyncExecutor with its
            // sibling and keep the already active wakelock
            RunConfig.EXECUTION_WAKELOCK -> {
                currentSyncExecutor!!.stop()

                // already enabled, but doing here once more for clarity
                enableCpuWakeLock(true)
                currentSyncExecutor = RunOperationsSyncExecutor(
                    applicationContext = applicationContext,
                    runConfig = runConfig,
                    listener = this,
                    startRealtimeMs = startRealtimeMs,
                    timeRefMs = timeRefMs,
                    status = currentSyncExecutor!!.status
                )
                currentSyncExecutor!!.start(onFinishCallback = ::onCurrentSyncExecutorFinished)
            }

            // for the alarm manager execution type we first release the wakelock and then schedule
            // the schedule in the background; this service remains in foreground, but does no
            // longer has any wakelock
            RunConfig.EXECUTION_ALARM_MANAGER -> {
                enableCpuWakeLock(false) // do not need wakelock anymore
                startAlarmManagerExecutor(
                    context = applicationContext,
                    runConfig = runConfig,
                    startRealtimeMs = startRealtimeMs,
                    timeRefMs = timeRefMs,
                )
            }

            else -> throw IllegalArgumentException("bad runConfig: $runConfig")

        }
    }

    private fun onCurrentSyncExecutorFinished() {
        enableCpuWakeLock(false)
    }

    override fun onNewStatus(status: RunnerStatus) {
        try {
            mStatusListener?.onNewStatus(status)
        } catch (e: Exception) {
            // any failings of the UI update shall not stop our experiments please
            Log.e(
                "ForegroundRunnerService",
                "status update failed because ${e.stackTraceToString()}"
            )
            mStatusListener = null
        }
    }

    private fun enableCpuWakeLock(enabled: Boolean) {
        val currentlyActive = cpuWakeLock.isHeld
        if (enabled && !currentlyActive) {
            cpuWakeLock.acquire(wakeLockTimeoutMs)
        } else if (!enabled && currentlyActive) {
            cpuWakeLock.release()
        }
    }

    @SuppressLint("NewApi")
    private fun makeForeground() {
        val channel = NotificationChannel(
            channelId,
            channelId,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.vibrationPattern = LongArray(0)
        notificationManager.createNotificationChannel(channel)

        val notification: Notification = createNotification()
        startForeground(notificationId, notification)
    }

    @SuppressLint("NewApi")
    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }
        val contentText = "Running..."
        return Notification.Builder(this, channelId)
            .setContentTitle("Energy Runner Service")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker(contentText)
            .setOnlyAlertOnce(true)
            .build()
    }
}
