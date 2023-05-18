package net.rec0de.android.watchwitch

import java.net.DatagramPacket
import java.net.DatagramSocket

object IKEDispatcher {
    private val ikeSessions = mutableMapOf<String, IKEv2Session>()

    fun dispatch(packet: DatagramPacket, socket: DatagramSocket, main: MainActivity) {
        val payload = packet.data.sliceArray(0 until packet.length)
        val initiatorSPI = payload.sliceArray(0 until 8)
        val hexspi = initiatorSPI.hex()

        if(ikeSessions.containsKey(hexspi))
            ikeSessions[hexspi]!!.ingestPacket(payload)
        else {
            val session = IKEv2Session(socket, packet.address, packet.port, initiatorSPI, main)
            session.ingestPacket(payload)
            ikeSessions[hexspi] = session
        }
    }
}

class UDPHandler(private val main: MainActivity, private val serverPort: Int) : Thread() {
    private val maxDatagramSize = 10000
    private var bKeepRunning = true

    override fun run() {
        val lmessage = ByteArray(maxDatagramSize)
        val packet = DatagramPacket(lmessage, lmessage.size)
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(serverPort)
            main.runOnUiThread { main.statusListening(serverPort) }
            while (bKeepRunning) {
                socket.receive(packet)

                IKEDispatcher.dispatch(packet, socket, main)

                main.runOnUiThread { main.logData("rcv on $serverPort") }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        finally {
            socket?.close()
            main.runOnUiThread { main.statusIdle() }
        }
    }

    fun kill() {
        bKeepRunning = false
    }
}