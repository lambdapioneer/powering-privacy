package uk.ac.cam.energy.common.execution

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber


@SuppressLint("StaticFieldLeak")
private var singleton: UsbSignaller? = null
fun getGlobalUsbSignaller(context: Context): UsbSignaller {
    singleton?.also { return it }
    synchronized(UsbSignaller::class.java) {
        singleton?.also { return it }
        singleton = UsbSignaller(context)
        return singleton!!
    }
}

class UsbSignaller(private val context: Context) : Signaller {

    private val tag = "UsbSignaller"
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null

    private fun isInitialized() = port != null

    override fun initialize(): Boolean {
        if (isInitialized()) {
            return true
        }

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        Log.d(tag, "availableDrivers=$availableDrivers")

        if (availableDrivers.isEmpty()) {
            return false
        }

        // Open a connection to the first available driver.
        val driver = availableDrivers[0]
        if (!usbManager.hasPermission(driver.device)) {
            Log.d(tag, "no permission; please follow dialog and try again")
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent("uk.ac.cam.energyrunner.USB_PERMISSION"),
                0
            )
            usbManager.requestPermission(driver.device, permissionIntent)
            return false
        }

        val connection = usbManager.openDevice(driver.device) ?: return false
        Log.d(tag, "connection=$connection")

        port = driver.ports[0]
        Log.d(tag, "port=$port")

        port!!.open(connection)
        port!!.setParameters(3_000_000, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        setLatency(1)

        signalLow() // set to `0` before first session
        return true;
    }

    override  fun isConnected(): Boolean {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        return availableDrivers.isNotEmpty()
//        if(!isInitialized()) return false
//        return false
    }

    override fun signalHigh() {
        if (!isInitialized()) throw IllegalStateException("must be initialized first")
        port?.rts = false
    }

    override fun signalLow() {
        if (!isInitialized()) throw IllegalStateException("must be initialized first")
        port?.rts = true
    }

    override fun close() {
        port?.close()
    }

    private fun setLatency(latencyMs: Int) {
        (port!! as FtdiSerialDriver.FtdiSerialPort).latencyTimer = latencyMs
    }
}
