package net.rec0de.android.watchwitch

import android.util.Log
import net.rec0de.android.watchwitch.activities.MainActivity
import net.rec0de.android.watchwitch.bitmage.ByteOrder
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.ike.DeletePayload
import net.rec0de.android.watchwitch.ike.IKEMessage
import net.rec0de.android.watchwitch.ike.IKETransport
import net.rec0de.android.watchwitch.ike.IKEv2Session
import net.rec0de.android.watchwitch.ike.NrlpOverUdpTransport
import net.rec0de.android.watchwitch.ike.UDPTransport
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object IKEDispatcher {
    private val ikeSessions = mutableMapOf<String, IKEv2Session>()

    fun dispatch(payload: ByteArray, transport: IKETransport, main: MainActivity) {
        val initiatorSPI = payload.sliceArray(0 until 8)
        val responderSPI = payload.sliceArray(8 until 16)
        val hexspi = initiatorSPI.hex()

        // existing session
        if(ikeSessions.containsKey(hexspi))
            ikeSessions[hexspi]!!.ingestPacket(payload)
        // fresh session
        else if(Long.fromBytes(responderSPI, ByteOrder.BIG) == 0L) {
            main.hideWatchSimButton()
            val session = IKEv2Session(transport, initiatorSPI)
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
            transport.send(delete)
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
                val payload = packet.data.sliceArray(0 until packet.length)
                IKEDispatcher.dispatch(payload, UDPTransport(socket!!, packet.address, packet.port), main)
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

class NRLPoverUDPhandler(private val main: MainActivity, private val serverPort: Int) : Thread() {
    private val maxDatagramSize = 10000

    override fun run() {
        val socket = DatagramSocket()
        val sendData = "nrlp-hello".toByteArray()
        val sendPacket = DatagramPacket(sendData, sendData.size, InetAddress.getByName("10.0.2.2"), 0x5757)
        socket.send(sendPacket)
        Log.d("NRLPoverUDP", "sent nrlp hello")

        val lmessage = ByteArray(maxDatagramSize)
        val packet = DatagramPacket(lmessage, lmessage.size)
        val transport = NrlpOverUdpTransport(socket, sendPacket.address, sendPacket.port)

        while (true) {
            socket.receive(packet)
            val data = packet.data.sliceArray(0 until packet.length)
            IKEDispatcher.dispatch(data, transport, main)
            Log.d("NRLPoverUDP", packet.data.hex())
        }

    }
}