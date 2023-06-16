package uk.ac.cam.energy.common.operations

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Process
import android.util.Log
import uk.ac.cam.energy.common.execution.WebOperationService
import uk.ac.cam.energy.common.getWithDefault
import uk.ac.cam.energy.common.waitUntilPredicateTrue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Global variable to keep track if website is displayed. This avoids a lot of fuzz with binding
 * to background services and doing work in main threads.
 *
 * TODO: use better synchronisation that can handle error conditions in the service
 */
val globalWebsiteDisplayedLock = AtomicInteger()

/**
 * Loads a website in an overlay window that is created from a background service. This is to
 * ensure that the operation can be run from any context (even tests).
 */
class WebOperation(
    context: Context,
    name: String,
    pause: Pause,
    args: Map<String, String>
) : Operation(context, name, pause) {
    private val url = args.getWithDefault("url", "https://www.google.com")

    override fun run() {
        // Set the lock (will be unlocked by the service)
        globalWebsiteDisplayedLock.set(1)

        // Create service
        val serviceIntent = Intent(context, WebOperationService::class.java)
        serviceIntent.putExtra("url", url)
        context.startService(serviceIntent)

        val rxBytesBefore = rxBytes()

        // Wait for the service to unlock
        waitUntilPredicateTrue(30_000) { globalWebsiteDisplayedLock.get() == 0 }
        if (globalWebsiteDisplayedLock.get() != 0) {
            throw IllegalStateException("globalWebsiteDisplayedLock=$globalWebsiteDisplayedLock")
        }

        // determine total amount of data loaded
        val rxBytesDeltaKiB = (rxBytes() - rxBytesBefore) / 1024
        Log.d("WebOperation", "rxBytesDelta=${rxBytesDeltaKiB} KiB")
    }

    override fun debug() = "url=$url"

    private fun rxBytes() = TrafficStats.getUidRxBytes(Process.myUid())
}
