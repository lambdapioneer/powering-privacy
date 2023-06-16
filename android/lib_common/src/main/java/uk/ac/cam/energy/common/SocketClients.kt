package uk.ac.cam.energy.common

import android.util.Log
import okhttp3.internal.closeQuietly
import uk.ac.cam.energy.common.execution.getTracer
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

const val SERVER_IP = BuildConfig.REPLY_SERVICE_IP
val SERVER_SECRET = BuildConfig.REPLY_SERVICE_SECRET.encodeToByteArray()

const val SERVER_PORT_TCP = 10042
const val SERVER_PORT_TCP_KEEP_ALIVE = 10041
const val SERVER_PORT_UDP = 10043

const val SOCKET_TIMEOUT = 5_000 // 5 seconds
const val MAX_UDP_PACKET_SIZE = 1 * 1024
const val MAX_UDP_RATE_BYTES_PER_SECOND = 4 * 1024 * 1024  // limit to 4 MiB/s

abstract class SocketClient(private val payloadLengthWrite: Int, val payloadLengthRead: Int) {
    protected val tracer = getTracer()

    abstract fun send()

    protected fun getPayload(): ByteArray {
        if (payloadLengthWrite < 18) throw IllegalArgumentException("payloadLengthWrite to low: $payloadLengthWrite")
        val data = ByteBuffer.allocate(payloadLengthWrite)
        data.put(SERVER_SECRET)

        val clientLength = payloadLengthWrite
        data.putInt(clientLength)

        val serverLength = payloadLengthRead
        data.putInt(serverLength)

        return data.array()
    }
}

class TcpSocketClient(
    payloadLengthWrite: Int,
    payloadLengthRead: Int
) : SocketClient(payloadLengthWrite, payloadLengthRead) {
    override fun send() {
        tracer.trace("SOCKET_OPENING")
        Socket(SERVER_IP, SERVER_PORT_TCP).use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT

            tracer.trace("SEND_START")
            socket.getOutputStream().let { outputStream ->
                outputStream.write(getPayload())
            }

            tracer.trace("READ_START")
            socket.getInputStream().let { inputStream ->
                var readRemaining = payloadLengthRead
                while (readRemaining > 0) {
                    val data = ByteArray(readRemaining)
                    val readData = inputStream.read(data)
                    if (readData < 1) throw IllegalStateException("Read bad or empty: $readData")
                    readRemaining -= readData
                }
                tracer.trace("READ_FINISHED")
            }
        }
        tracer.trace("SOCKET_CLOSE")
    }
}


var keepAliveSocket: Socket? = null
val keepAliveSocketLock = ReentrantLock()

class TcpKeepAliveSocketClient(
    payloadLengthWrite: Int,
    payloadLengthRead: Int
) : SocketClient(payloadLengthWrite, payloadLengthRead) {
    override fun send() {
        try {
            internalSend()
            // Some exceptions such as "Broken Pipe" are only visible when one actually tries to
            // write. In these cases, we reconnect and try again one.
        } catch (e: SocketException) {
            Log.w("TcpKeepAliveSocket", "somewhat expected exception:", e)
            keepAliveSocketLock.withLock {
                closeQuietly()
            }
            internalSend()
        } catch (e: SocketTimeoutException) {
            Log.w("TcpKeepAliveSocket", "somewhat expected exception:", e)
            keepAliveSocketLock.withLock {
                closeQuietly()
            }
            internalSend()
        }
    }

    private fun closeQuietly() {
        try {
            keepAliveSocket?.close()
        } catch (ignore: IOException) {
            // ignore
        } finally {
            keepAliveSocket = null
        }
    }

    private fun internalSend() {
        keepAliveSocketLock.withLock {
            // re-create socket if it got closed in the mean time
            keepAliveSocket?.let { socket ->
                if (socket.isClosed) {
                    tracer.trace("SOCKET_CLOSED")
                    keepAliveSocket = null
                }
            }

            // if no active socket, create one
            if (keepAliveSocket == null) {
                Log.w("TcpKeepAliveSocket", "opening")
                tracer.trace("SOCKET_OPENING")
                keepAliveSocket = Socket(SERVER_IP, SERVER_PORT_TCP_KEEP_ALIVE)
                keepAliveSocket?.soTimeout = SOCKET_TIMEOUT
            }

            keepAliveSocket?.let { socket ->
                tracer.trace("SEND_START")
                socket.getOutputStream().let { outputStream ->
                    outputStream.write(getPayload())
                }

                tracer.trace("READ_START")
                socket.getInputStream().let { inputStream ->
                    var readRemaining = payloadLengthRead
                    while (readRemaining > 0) {
                        val data = ByteArray(readRemaining)
                        val readData = inputStream.read(data)
                        if (readData < 1) throw IllegalStateException("Read bad or empty: $readData")
                        readRemaining -= readData
                    }
                    tracer.trace("READ_FINISHED")
                }
            }
        }
    }
}

class UdpSocketClient(
    payloadLengthWrite: Int,
    payloadLengthRead: Int,
    private val waitForRead: Boolean = true,
) : SocketClient(payloadLengthWrite, payloadLengthRead) {

    private val serverSocketAddress = InetSocketAddress(SERVER_IP, SERVER_PORT_UDP)

    override fun send() {
        tracer.trace("SOCKET_OPENING")
        val socket = DatagramSocket()
        socket.soTimeout = SOCKET_TIMEOUT

        val payload = getPayload()

        tracer.trace("SEND_START")
        var writeRemaining = payload.size
        while (writeRemaining > 0) {
            val startIndex = payload.size - writeRemaining
            val sz = min(writeRemaining, MAX_UDP_PACKET_SIZE)
            writeRemaining -= sz

            val data = payload.copyOfRange(startIndex, startIndex + sz)
            socket.send(DatagramPacket(data, data.size, serverSocketAddress))

            // wait proportionally to not exceed UDP bandwidth (leads to packet drop)
            val sleepMs = 1000L * data.size / MAX_UDP_RATE_BYTES_PER_SECOND
            Thread.sleep(sleepMs)
        }

        tracer.trace("READ_START")
        var readRemaining = payloadLengthRead
        var packetCnt = 0
        while (waitForRead && readRemaining > 0) {
            val incomingData = ByteArray(MAX_UDP_PACKET_SIZE)
            val incomingPayload =
                DatagramPacket(incomingData, incomingData.size, serverSocketAddress)
            try {
                socket.receive(incomingPayload)
                packetCnt++
            } catch (e: SocketTimeoutException) {
                Log.d("UdpSocketClient", "timeout with $readRemaining left")
                tracer.trace("READ_TIMEOUT")
                break
            }

            readRemaining -= incomingPayload.length
        }

        socket.closeQuietly()
        tracer.trace("SOCKET_CLOSE")
    }
}
