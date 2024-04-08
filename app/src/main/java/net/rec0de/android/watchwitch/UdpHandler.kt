package net.rec0de.android.watchwitch

import net.rec0de.android.watchwitch.activities.MainActivity
import net.rec0de.android.watchwitch.bitmage.ByteOrder
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.ike.DeletePayload
import net.rec0de.android.watchwitch.ike.IKEMessage
import net.rec0de.android.watchwitch.ike.IKEv2Session
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket

object IKEDispatcher {
    private val ikeSessions = mutableMapOf<String, IKEv2Session>()

    fun dispatch(packet: DatagramPacket, socket: DatagramSocket, main: MainActivity) {
        val payload = packet.data.sliceArray(0 until packet.length)
        val initiatorSPI = payload.sliceArray(0 until 8)
        val responderSPI = payload.sliceArray(8 until 16)
        val hexspi = initiatorSPI.hex()

        // existing session
        if(ikeSessions.containsKey(hexspi))
            ikeSessions[hexspi]!!.ingestPacket(payload)
        // fresh session
        else if(Long.fromBytes(responderSPI, ByteOrder.BIG) == 0L) {
            val session = IKEv2Session(socket, packet.address, packet.port, initiatorSPI)
            session.ingestPacket(payload)
            ikeSessions[hexspi] = session
        }
        // existing session that we have no memory of
        else {
            Logger.logIKE("Got orphaned IKE, sending delete", 0) // this does not seem to actually do anything
            // informational exchange
            val msg = IKEMessage(37, 0, initiatorSPI, responderSPI, false)
            msg.addPayload(DeletePayload())
            val delete = msg.assemble()
            val reply = DatagramPacket(delete, delete.size, packet.address, packet.port)
            socket.send(reply)
        }
    }
}

class UDPHandler(private val main: MainActivity, private val serverPort: Int) : Thread() {
    private val maxDatagramSize = 10000
    var socket: DatagramSocket? = null
    override fun run() {
        val lmessage = ByteArray(maxDatagramSize)
        val packet = DatagramPacket(lmessage, lmessage.size)
        try {
            socket = DatagramSocket(serverPort)
            main.runOnUiThread { main.statusListening(serverPort) }
            while (true) {
                socket!!.receive(packet)
                IKEDispatcher.dispatch(packet, socket!!, main)
            }
        } catch (e: Throwable) {
            if(e is BindException) {
                Logger.setError("socket already in use")
            }
            e.printStackTrace()
        }
        finally {
            socket?.close()
            main.runOnUiThread { main.statusIdle() }
        }
    }

    fun kill() {
        socket?.close()
    }
}