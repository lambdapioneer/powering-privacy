package uk.ac.cam.energy.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before

import org.junit.Test
import org.junit.runner.RunWith
import uk.ac.cam.energy.common.execution.getTracer

const val KiB = 1024
const val MiB = 1024 * KiB

@RunWith(AndroidJUnit4::class)
class SocketClientsTest {

    @Before
    fun setup() {
        getTracer().init(null, monotonicMs())
    }

    @Test
    fun tcpSocket_whenSend1KAndReceive10K_thenAllFine() {
        val client = TcpSocketClient(1 * KiB, 10 * KiB)
        client.send()
    }

    @Test
    fun tcpSocket_whenSend1MAndReceive1M_thenAllFine() {
        val client = TcpSocketClient(1 * MiB, 1 * MiB)
        client.send()
    }

    @Test
    fun udpSocket_whenSendAndReceive1K_thenAllFine() {
        val client = UdpSocketClient(1 * KiB, 1 * KiB)
        client.send()
    }

    @Test
    fun udpSocket_whenSendAndReceive1M_thenAllFine() {
        val client = UdpSocketClient(1 * MiB, 1 * MiB)
        client.send()
    }
}
