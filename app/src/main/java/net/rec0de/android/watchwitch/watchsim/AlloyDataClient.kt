package net.rec0de.android.watchwitch.watchsim

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.alloy.AlloyMessage
import net.rec0de.android.watchwitch.alloy.Handshake
import net.rec0de.android.watchwitch.alloy.ProtobufMessage
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.fromIndex
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.nwsc.NWSCPacket
import net.rec0de.android.watchwitch.servicehandlers.messaging.BulletinRequest
import net.rec0de.android.watchwitch.servicehandlers.messaging.DidPlayLightsAndSirens
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import java.util.UUID

// based on https://stackoverflow.com/questions/38162775/really-simple-tcp-client
class AlloyDataClient(val service: String, val controller: AlloyControlClient) : Thread() {

    private var toPhone: DataOutputStream? = null
    private var fromPhone: DataInputStream? = null

    fun sendMessage(message: ByteArray, lengthPrefixed: Boolean = false) {
        val runnable = Runnable {
            if (toPhone != null) {
                if(lengthPrefixed)
                    toPhone!!.writeShort(message.size)
                toPhone!!.write(message)
                toPhone!!.flush()
            }
        }
        val thread = Thread(runnable)
        thread.start()
    }


    fun stopClient() {
        if (toPhone != null) {
            toPhone!!.flush()
            toPhone!!.close()
        }
        fromPhone?.close()
        fromPhone = null
        toPhone = null
    }

    override fun run() {
        try {

            runBlocking {
                delay(200)
            }
            val serverAddr = InetAddress.getByName("127.0.0.1")
            Log.d("SimAlloyData", "Connecting to Alloy port 61314...")

            //create a socket to make the connection with the server
            val socket = Socket(serverAddr, 61314)
            try {
                toPhone = DataOutputStream(socket.getOutputStream())
                fromPhone = DataInputStream(socket.getInputStream())

                val controlChannelNWSCRequest = NwscSim.buildServiceRequest(61314, service)
                Log.d("SimAlloyData", "Sending NWSC request $controlChannelNWSCRequest")
                sendMessage(controlChannelNWSCRequest.toByteArray())

                // receive NWSC feedback
                val reqLen = fromPhone!!.readUnsignedShort()
                //Log.d("WatchSim", "Reading NWSC reply, $reqLen bytes")

                val request = ByteArray(reqLen)
                fromPhone!!.readFully(request, 0, request.size)
                //Log.d("WatchSim", "NWSC reply: ${request.hex()}")

                val packet = NWSCPacket.parseFeedback(request)
                Log.d("SimAlloyData", "NWSC reply: $packet")

                while(true) {
                    val type = fromPhone!!.readByte()
                    val length = fromPhone!!.readInt() // read length of incoming message
                    if (length > 0) {
                        val message = ByteArray(length+5)
                        val buf = ByteBuffer.wrap(message, 0, 5)
                        buf.put(type)
                        buf.putInt(length)
                        fromPhone!!.readFully(message, 5, message.size-5)

                        val msg = AlloyMessage.parse(message)
                        Log.d("SimAlloyData", "AlloyData receive: $msg")

                        when(msg) {
                            is Handshake -> sendMessage(Handshake(4).toBytes())
                            is ProtobufMessage -> {
                                if(msg.hasTopic)
                                    controller.associateStreamWithTopic(msg.streamID, msg.topic!!)

                                val topic = controller.resolveStream(msg.streamID)

                                when(topic) {
                                    "com.apple.private.alloy.bulletindistributor" -> handleBulletin(msg)
                                }
                            }
                        }

                        if(msg is Handshake)
                            sendMessage(Handshake(4).toBytes())

                    }
                }
            } catch (e: Exception) {
                Log.e("TCP", "S: Error", e)
            } finally {
                //the socket must be closed. It is not possible to reconnect to this socket
                // after it is closed, which means a new socket instance has to be created.
                socket.close()
            }
        } catch (e: Exception) {
            Log.e("TCP", "C: Error", e)
        }
    }

    private fun handleBulletin(msg: ProtobufMessage) {
        // trailer handling
        val trailerLen = Int.fromBytes(msg.payload.fromIndex(msg.payload.size-2), net.rec0de.android.watchwitch.bitmage.ByteOrder.LITTLE)
        if(trailerLen > msg.payload.size - 2 || trailerLen > 100) {
            Log.d("SimAlloyData", "[bulletin] got message without expected trailer: ${msg.payload.hex()}")
            return
        }

        val endIndex = msg.payload.size - 2
        val startIndex = endIndex - trailerLen

        val payload = msg.payload.sliceArray(0 until startIndex)
        val pb = try {
            ProtobufParser().parse(payload)
        }
        catch(e: Exception) {
            Log.d("SimAlloyData", "[bulletin] failed parsing payload: ${payload.hex()}")
            return
        }

        when(msg.type) {
            1 -> {
                Log.d("SimAlloyData", "[bulletin] got ${BulletinRequest.fromSafePB(pb)}")

                runBlocking { delay(500) }

                val trailer = genTrailer()
                controller.sendProtobuf(
                    DidPlayLightsAndSirens(true, "", "", Date(), replyToken = null).renderProtobuf() + trailer,
                    "com.apple.private.alloy.bulletindistributor",
                    isResponse = false,
                    type = 9
                )
            }
        }
    }

    private fun genTrailer(): ByteArray {
        val fields = mutableMapOf(
            1 to listOf(ProtoVarInt(1)),
            3 to listOf(ProtoLen(Utils.uuidToBytes(UUID.randomUUID())))
        )

        val trailer = ProtoBuf(fields)

        val bytes = trailer.renderStandalone()
        val len = bytes.size
        val buf = ByteBuffer.allocate(len+2)
        buf.put(bytes)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(len.toShort())
        return buf.array()
    }
}