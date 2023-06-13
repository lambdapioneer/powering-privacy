import java.io.File
import java.time.Duration


class FileTrailingDataSource(
    private val file: File,
    private val includeLastLines: Int = 100_000,
) : ArrayListBackedDataSource() {

    private val defaultDelay = Duration.ofMillis(10)
    private var stopped = false

    /**
     * A method that works like the tail CLI program. It reads the file line by line and calls the callback with each
     * line. If the end of the file is reached, it sleeps for a while and then tries again.
     */
    fun run(callback: (String) -> Unit) {
        file.bufferedReader().use {
            it.readLine() // skip header

            // read the last few lines
            val lastLines = ArrayDeque<String>(includeLastLines)
            while (true) {
                val line = it.readLine() ?: break
                lastLines.addLast(line)
                if (lastLines.size > includeLastLines) {
                    lastLines.removeFirst()
                }
            }
            lastLines.forEach(callback)
            lastLines.clear()

            // continuously read lines from the file
            while (!stopped) {
                val line = it.readLine()
                if (line == null) {
                    Thread.sleep(defaultDelay.toMillis())
                } else {
                    callback(line)
                }
            }
        }
    }

    fun start() {
        val r = Runnable {
            run {line ->
                try {
                    val parts = line.split(",")
                    val timeSeconds = parts[0].toFloat()
                    val powerMw = parts[1].toFloat()
                    add(timeSeconds * 1_000, powerMw)
                } catch (e: Exception) {
                    println("Error parsing line: $line")
                }
            }
        }
        Thread(r).start()
    }

    override fun close() {
        stopped = true
    }
}
