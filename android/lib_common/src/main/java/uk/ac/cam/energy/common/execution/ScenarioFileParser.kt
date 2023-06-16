package uk.ac.cam.energy.common.execution

import android.content.Context
import uk.ac.cam.energy.common.operations.Operation
import uk.ac.cam.energy.common.operations.OperationFactory
import uk.ac.cam.energy.common.operations.Pause
import uk.ac.cam.energy.common.operations.PauseType
import java.io.File

class ScenarioFileParser(
    private val context: Context,
    private val operationFactory: OperationFactory = OperationFactory
) {
    fun parseFromStorage(file: File): List<Operation> {
        val lines = getLinesFromStorage(file)
        return parseLines(lines)
    }

    fun getLinesFromStorage(file: File): List<String> {
        val content = file.readText()
        return getActiveLines(content)
    }

    fun parse(fileContent: String): List<Operation> = parseLines(getActiveLines(fileContent))

    private fun getActiveLines(fileContent: String): List<String> {
        return fileContent
            .split('\n')
            .filterNot { line -> line.startsWith("#") || line.startsWith("//") }
            .filterNot { line -> line.isEmpty() }
    }

    fun parseLines(lines: List<String>): List<Operation> {
        return lines.flatMap { line -> parseLine(line) }
    }

    fun parseLineOnlyOne(line: String): Operation {
        val result = parseLine(line)
        if (result.size > 1) throw IllegalArgumentException("result too large: ${result.size}")
        return result[0]
    }

    fun parseLine(line: String): List<Operation> {
        val parts = line.split(";")
        if (parts.size != 5) {
            throw IllegalArgumentException("'$line' has ${parts.size} parts but expected 5")
        }

        val iterations = parts[0].toInt()
        val pause = parsePause(parts[1])
        val identifier = parts[2]
        val operation = parts[3]
        val parameters = parseMap(parts[4])

        return List(iterations) {
            operationFactory.create(
                operation,
                context,
                identifier,
                pause,
                parameters
            )
        }
    }

    private fun parsePause(pauseString: String): Pause {
        if (pauseString.isEmpty()) return Pause(PauseType.NONE)
        return when (pauseString[0]) {
            'P' -> Pause(PauseType.PAUSE, pauseString.substring(1).toLong())
            'S' -> Pause(PauseType.SCHEDULE, pauseString.substring(1).toLong())
            else -> throw IllegalArgumentException("bad pause: $pauseString")
        }
    }

    private fun parseMap(mapString: String): Map<String, String> = mapString
        .split(',')
        .filterNot { it.isEmpty() }
        .map { it.split("=") }
        .associate { Pair(it[0], it[1]) }
}
