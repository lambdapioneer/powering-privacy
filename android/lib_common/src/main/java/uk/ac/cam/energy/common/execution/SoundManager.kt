package uk.ac.cam.energy.common.execution

import android.content.Context
import android.media.MediaPlayer
import uk.ac.cam.energy.common.R
import uk.ac.cam.energy.common.waitUntilPredicateTrue


enum class TtsSounds(val resId: Int) {
    FAILURE(R.raw.tts_failure),
    OPERATIONS_FINISHED(R.raw.tts_operations_finished),
    OPERATIONS_STARTED(R.raw.tts_operations_started),
    SYNC_DONE(R.raw.tts_sync_done),
    USB_CONNECTED(R.raw.tts_usb_connected),
    USB_DISCONNECTED(R.raw.tts_usb_disconnected),
    USB_WAIT_FOR_DISCONNECT(R.raw.tts_waiting_for_disconnect),
}

interface SoundManager {
    fun playTtsSound(sound: TtsSounds)
}

class AndroidSoundManager(private val context: Context) : SoundManager {

    private val timeOutMs = 2_000L

    override fun playTtsSound(sound: TtsSounds) = playSound(sound.resId)

    private fun playSound(resId: Int) {
        MediaPlayer.create(context, resId).apply {
            start()
            waitUntilPredicateTrue(timeOutMs, 10) { !isPlaying }
            stop()
            release()
        }
    }
}
