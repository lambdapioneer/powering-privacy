package uk.ac.cam.energy.common.operations

import android.content.Context
import uk.ac.cam.energy.common.getWithDefault


class IdleOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : Operation(context, name, pause) {

    val durationMs = args.getWithDefault("duration_ms", "1000").toLong()

    override fun run() {
        if (durationMs > 0) {
            Thread.sleep(durationMs)
        }
    }

}
