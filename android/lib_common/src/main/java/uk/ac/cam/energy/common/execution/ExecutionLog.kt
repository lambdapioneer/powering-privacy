package uk.ac.cam.energy.common.execution

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import uk.ac.cam.energy.common.mkdirIfNotExists
import uk.ac.cam.energy.common.monotonicMs
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayDeque


/** idempotent */
fun getScenarioBasename(scenarioFileName: String) =
    scenarioFileName.replace(".scenario", "")

fun getLogCsvFilename(scenarioName: String, timeRefMs: Long): String {
    val dateString = getLogDateString(timeRefMs)
    return "${scenarioName}_${dateString}_android.csv"
}

fun getTraceCsvFilename(scenarioName: String, timeRefMs: Long): String {
    val dateString = getLogDateString(timeRefMs)
    return "${scenarioName}_${dateString}_trace.csv"
}

fun ensureLogsDir(context: Context): File {
    val dir = File(context.filesDir, "logs")
    dir.mkdirIfNotExists()
    return dir
}

private fun getLogDateString(timeRefMs: Long) =
    SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timeRefMs))

data class LogEntry(
    val operation: String,
    val ts_ms: Double,
    val te_ms: Double,
    val debug: String
)

class ExecutionLog(private val scenarioName: String) {

    private val entries = ArrayDeque<LogEntry>(128)

    fun log(entry: LogEntry) {
        entries.add(entry)
    }

    fun syncToDisk(context: Context, startRealtimeMs: Long) {
        val file = File(
            ensureLogsDir(context),
            getLogCsvFilename(getScenarioBasename(scenarioName), startRealtimeMs)
        )
        Log.d("ExecutionLog", "syncToDisk $file")

        // ensure file and header
        if (file.createNewFile()) {
            FileWriter(file).use { fw ->
                BufferedWriter(fw).use { bw ->
                    bw.write("scenario,operation,ts,te,debug\n")
                }
            }
        }

        // append data
        val append = true
        FileWriter(file, append).use { fw ->
            BufferedWriter(fw).use { bw ->
                for (entry in entries) {
                    val row = String.format(
                        "%s,%s,%.6f,%.6f,%s\n",
                        scenarioName,
                        entry.operation,
                        entry.ts_ms / 1000.0, // file uses seconds
                        entry.te_ms / 1000.0, // file uses seconds
                        entry.debug
                    )
                    bw.write(row)
                }
            }
        }
    }
}


@SuppressLint("StaticFieldLeak")
private var singleton: Tracer? = null
fun getTracer(): Tracer {
    singleton?.also { return it }
    synchronized(Tracer::class.java) {
        singleton?.also { return it }
        singleton = Tracer()
        return singleton!!
    }
}

class Tracer {
    // for timekeeping relative to the ExecutionLog
    private var isInit = false
    private var _timeRefMs = 0.0
    private var scenarioName: String? = null

    private var traces = ArrayList<Pair<Double, String>>(1024)

    fun trace(s: String) {
        trace(nowMs(), s)
    }

    fun trace(t: Double, s: String) {
        traces.add(Pair(t, s))
    }

    fun init(scenarioName: String?, timeRefMs: Double) {
        isInit = true
        setScenarioName(scenarioName)
        traces.clear()
        _timeRefMs = timeRefMs
    }

    private fun setScenarioName(name: String?) {
        scenarioName = name
    }

    private fun nowMs(): Double {
        val nowMs = monotonicMs()
        if (!isInit) throw IllegalStateException("notinit")//init(scenarioName = null, timeRefMs = nowMs)
        return nowMs - _timeRefMs
    }

    fun syncToDisk(context: Context, startRealtimeMs: Long) {
        if (scenarioName == null) return

        val file = File(
            ensureLogsDir(context),
            getTraceCsvFilename(getScenarioBasename(scenarioName!!), startRealtimeMs)
        )
        Log.d("Tracer", "syncToDisk $file")

        // ensure file and header
        if (!file.exists()) {
            FileWriter(file).use { fw ->
                BufferedWriter(fw).use { bw ->
                    bw.write("time,event\n")
                }
            }
        }

        // append data
        val append = true
        FileWriter(file, append).use { fw ->
            BufferedWriter(fw).use { bw ->
                for (trace in traces) {
                    val row = String.format(
                        "%.6f,%s\n",
                        trace.first / 1000.0, // file uses seconds
                        trace.second
                    )
                    bw.write(row)
                }
            }
        }
    }
}
