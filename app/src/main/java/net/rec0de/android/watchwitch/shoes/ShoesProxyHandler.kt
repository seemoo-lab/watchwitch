package net.rec0de.android.watchwitch.shoes

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.RoutingManager
import net.rec0de.android.watchwitch.fromBytesBig
import net.rec0de.android.watchwitch.hex
import net.rec0de.android.watchwitch.hexBytes
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.net.SocketException

class ShoesProxyHandler(private val fromWatch: DataInputStream, private val toWatch: DataOutputStream) : Thread() {
    override fun run() {
        try {
            val headerLen = fromWatch.readUnsignedShort()

            Logger.logShoes("TLS rcv trying to read $headerLen header bytes", 10)

            val header = ByteArray(headerLen)
            fromWatch.readFully(header, 0, header.size)

            // first byte is always 0x01, may be command byte from SOCKS spec (0x01 = establish TCP/IP connection)
            val headerMagic = header[0]
            val targetPort = UInt.fromBytesBig(header.sliceArray(1 until 3))
            val hostnameLen = header[3].toUByte().toInt()
            val hostname = header.sliceArray(4 until 4+hostnameLen).toString(Charsets.UTF_8)

            Logger.logShoes("TLS req to $hostname", 0)


            // next up we have what looks like TLVs
            var offset = 4 + hostnameLen
            var bundleID: String? = null
            while(offset < headerLen) {
                val type = header[offset].toInt()
                val len = UInt.fromBytesBig(header.sliceArray(offset+1 until offset+3)).toInt()
                offset += 3
                val content = header.sliceArray(offset until offset+len)
                if(type == 0x03) {
                    Logger.logShoes("from process ${content.toString(Charsets.UTF_8)}", 1)
                    bundleID = content.toString(Charsets.UTF_8)
                }
                else
                    Logger.logShoes("TLS req unknown TLV: type $type, payload ${content.hex()}", 1)
                offset += len
            }

            NetworkStats.connect(hostname, bundleID)

            val clientSocket = Socket(hostname, targetPort.toInt())
            val toRemote = clientSocket.getOutputStream()
            val fromRemote = DataInputStream(clientSocket.getInputStream())

            // send acknowledge, exact purpose unknown
            // best guess: 0006 is high-level length (handled at unknown location)
            // 0000 is unknown (possibly type 0 length 0 TLV?)
            // 04000120 is type 4 length 1 TLV carrying connection type info
            // type byte: expensive? | cellular? | wifi? | constrained? | denied interface |  000 (unknown / reserved bits)

            // see ___nw_shoes_read_reply_tlvs_block_invoke in libnetwork.dylib

            val wifiFlag = (if(RoutingManager.isConnectionWifi()) 0x20 else 0x00).toUByte()
            val cellFlag = (if(RoutingManager.isConnectionCellular()) 0x40 else 0x00).toUByte()
            val expensiveFlag = (if(RoutingManager.isConnectionExpensive()) 0x80 else 0x00).toUByte()
            val networkByte = wifiFlag or cellFlag or expensiveFlag

            toWatch.write("00060000040001".hexBytes())
            toWatch.writeByte(networkByte.toInt())
            toWatch.flush()

            ShoesProxyReplyForwarder(fromRemote, toWatch, hostname).start()

            while(true) {
                // we expect a TLS record header here, which is 1 byte record type, 2 bytes TLS version, 2 bytes length
                val recordHeader = ByteArray(5)
                fromWatch.readFully(recordHeader)
                val len = UInt.fromBytesBig(recordHeader.sliceArray(3 until 5)).toInt()
                val packet = ByteArray(5+len)
                recordHeader.copyInto(packet)
                fromWatch.readFully(packet, 5, len)

                Logger.logShoes("TLS to remote: ${packet.hex()}", 10)
                toRemote.write(packet)
                toRemote.flush()

                NetworkStats.packetSent(hostname, len)
            }
        } catch (e: Exception) {
            try {
                fromWatch.close()
                toWatch.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
}

class ShoesProxyReplyForwarder(private val fromRemoteStream: DataInputStream, private val toLocalStream: OutputStream, private val hostname: String) : Thread() {
    override fun run() {
        try {
            while(true) {
                // we expect a TLS record header here, which is 1 byte record type, 2 bytes TLS version, 2 bytes length
                val recordHeader = ByteArray(5)
                fromRemoteStream.readFully(recordHeader)
                val len = UInt.fromBytesBig(recordHeader.sliceArray(3 until 5)).toInt()
                val packet = ByteArray(5+len)
                recordHeader.copyInto(packet)
                fromRemoteStream.readFully(packet, 5, len)

                Logger.logShoes("TLS from remote: ${packet.hex()}", 10)
                toLocalStream.write(packet)
                toLocalStream.flush()
                NetworkStats.packetReceived(hostname, len)
            }
        } catch (e: Exception) {
            try {
                fromRemoteStream.close()
                toLocalStream.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
}