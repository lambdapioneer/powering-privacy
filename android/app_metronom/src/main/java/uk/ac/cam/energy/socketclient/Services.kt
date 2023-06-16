package uk.ac.cam.energy.socketclient

import android.annotation.SuppressLint
import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import uk.ac.cam.energy.common.operations.Pause
import uk.ac.cam.energy.common.operations.PauseType

private const val ALARM_INTENT_ACTION = "metronom_alarm"

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Is triggered when alarm goes off, i.e. receiving a system broadcast
        if (intent.action != ALARM_INTENT_ACTION) {
            throw IllegalArgumentException("expected alarm, but got: $intent")
        }

        val workload = WorkLoad.fromExtras(intent)

        // Run everything in a background thread so the main thread becomes available asap. This
        // is important as some operations (e.g. the WebOperation and its WebView) run on the
        // main thread. Otherwise we'll have hard to debug dead-locks..
        val runnable = Runnable {
            // first: set next alarm
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextAlarmTimeMs(workload.pauseMs),
                createAlarmIntent(context, workload)
            )
            Log.i("AlarmReceiver", "next alarm scheduled in ${workload.pauseMs}ms")

            // second: find our operation from the common operations list
            val operationsList = createOperationsList(context)
            val operation = operationsList.first { it.name == workload.operation }

            try {
                Log.i("AlarmReceiver", "operation '${workload.operation}' start")

                val startNs = System.nanoTime()
                operation.runCompletely()
                val deltaMs = (System.nanoTime() - startNs) / 1_000_000

                Log.i("AlarmReceiver", "operation success time=${deltaMs}ms")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "operation fail e=${e.message}")
                Log.e("AlarmReceiver", e.stackTraceToString())
            }
        }
        val t = Thread(runnable)
        t.start()
    }
}


fun createAlarmIntent(context: Context, workLoad: WorkLoad): PendingIntent {
    val intent = Intent(context, AlarmReceiver::class.java)
    intent.action = ALARM_INTENT_ACTION
    workLoad.addToExtras(intent)
    return PendingIntent.getBroadcast(context, 0, intent, FLAG_UPDATE_CURRENT)
}

fun nextAlarmTimeMs(pauseMs: Long): Long {
    return System.currentTimeMillis() + pauseMs
}

class ForegroundService : Service() {

    private val notificationid = 1000
    private val channelId = "background_service_notif_channel"

    private lateinit var workload: WorkLoad
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        workload = WorkLoad.fromExtras(intent!!)
        Log.i("ForegroundService", "workload=$workload")

        stopRunnable()
        makeForeground()
        startWorkload()

        return START_REDELIVER_INTENT
    }

    private fun startWorkload() {
        val nextAlarmTimeMs = nextAlarmTimeMs(workload.pauseMs)
        val pendingIntent = createAlarmIntent(this, workload)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextAlarmTimeMs,
            pendingIntent
        )
    }

    private fun stopRunnable() {
        alarmManager.cancel(createAlarmIntent(this, workload))
        stopForeground(true)
    }

    override fun onDestroy() {
        stopRunnable()
        super.onDestroy()
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
        startForeground(notificationid, notification)
    }

    @SuppressLint("NewApi")
    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, FLAG_UPDATE_CURRENT)
        }
        val titleText = workload.toString()
        val contentText = "Running..."
        return Notification.Builder(this, channelId)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker(contentText)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
