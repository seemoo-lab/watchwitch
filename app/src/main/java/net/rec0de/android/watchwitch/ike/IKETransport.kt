package net.rec0de.android.watchwitch.ike

import android.util.Log
import net.rec0de.android.watchwitch.TunnelBuilder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

// IKE usually arrives over UDP from the watch in the WiFi case
// working towards supporting Bluetooth connections also, we also accept IKE over generic transports
// like NRLP over UDP, in which case we also have to handle ESP decryption in-house, which changes
// the tunnel setup logic as we no longer outsource to kernel ip xfrm

interface IKETransport {
    fun send(msg: ByteArray)

    fun setupTunnel(
        spiI: ByteArray,
        spiR: ByteArray,
        keyI: ByteArray,
        saltI: ByteArray,
        keyR: ByteArray,
        saltR: ByteArray,
        isClassC: Boolean
    )
}

class UDPTransport(private val socket: DatagramSocket, private val host: InetAddress, private val port: Int): IKETransport {
    override fun send(msg: ByteArray) {
        val packet = DatagramPacket(msg, msg.size, host, port)
        socket.send(packet)
    }

    override fun setupTunnel(
        spiI: ByteArray,
        spiR: ByteArray,
        keyI: ByteArray,
        saltI: ByteArray,
        keyR: ByteArray,
        saltR: ByteArray,
        isClassC: Boolean
    ) {
        // ip xfrm expects key and salt material in a single bytestring
        val ki = keyI + saltI
        val kr = keyR + saltR

        val thread = TunnelBuilder(host.hostAddress!!, spiI, spiR, ki, kr, isClassC)
        thread.start()
    }
}

class NrlpOverUdpTransport(private val socket: DatagramSocket, private val host: InetAddress, private val port: Int): IKETransport {
    override fun send(msg: ByteArray) {
        val packet = DatagramPacket(msg, msg.size, host, port)
        socket.send(packet)
    }

    override fun setupTunnel(
        spiI: ByteArray,
        spiR: ByteArray,
        keyI: ByteArray,
        saltI: ByteArray,
        keyR: ByteArray,
        saltR: ByteArray,
        isClassC: Boolean
    ) {
        Log.d("NRLPoverUDP", "got ESP secrets, we don't know what to do now")
    }
}