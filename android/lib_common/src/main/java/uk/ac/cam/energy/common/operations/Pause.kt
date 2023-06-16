package uk.ac.cam.energy.common.operations

enum class PauseType {
    PAUSE,
    SCHEDULE,
    NONE
}

data class Pause(val type: PauseType, val ms: Long = 0L) {
    init {
        // must not be both schedule and <= 0
        require(!(type == PauseType.SCHEDULE && ms <= 0L))
    }
}

val EMPTY_PAUSE = Pause(PauseType.PAUSE, 0L)

fun getPauseMs(
    previousStartTimeMs: Double,
    nowMs: Double,
    pause: Pause,
    minAndDefaultPause: Long
): Long {
    val pauseMs = when (pause.type) {
        PauseType.PAUSE -> pause.ms
        PauseType.SCHEDULE -> {
            val timeSinceLastStartMs = nowMs - previousStartTimeMs
            var x = pause.ms - timeSinceLastStartMs
            while (x < minAndDefaultPause) x += pause.ms // line up with next interval
            x.toLong()
        }
        PauseType.NONE -> minAndDefaultPause
    }
    return pauseMs
}
