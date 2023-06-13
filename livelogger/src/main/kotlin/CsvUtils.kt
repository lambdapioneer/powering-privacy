import java.io.File
import kotlin.math.roundToInt

fun readCsv(file: File): ArrayListBackedDataSource {
    val lines = file.readLines()
    if (lines.size < 2) {
        return EmptyDataSource()
    }

    val dataSource = ArrayListBackedDataSource()
    for (line in lines.listIterator(1)) {
        val parts = line.split(",")
        val timeSeconds = parts[0].toFloat()
        val powerMw = parts[1].toFloat()

        dataSource.add(timeSeconds * 1_000, powerMw)
    }

    return dataSource
}

fun writeCsv(data: ArrayList<DataPoint>, file: File) {
    file.printWriter().use {
        it.print("time_s,power_mw,input_pin\n")
        for (dp in data) {
            it.printf("%.6f,%d,0\n", dp.x / 1000f, dp.y.roundToInt())
        }
    }
}
