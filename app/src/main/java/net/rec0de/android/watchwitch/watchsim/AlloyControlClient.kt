package net.rec0de.android.watchwitch.watchsim

import android.util.Log
import net.rec0de.android.watchwitch.alloy.DataMessage
import net.rec0de.android.watchwitch.alloy.ProtobufMessage
import net.rec0de.android.watchwitch.alloy.SetupChannel
import net.rec0de.android.watchwitch.alloy.UTunControlMessage
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.compression.GzipDecoder
import net.rec0de.android.watchwitch.nwsc.NWSCPacket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.UUID

// based on https://stackoverflow.com/questions/38162775/really-simple-tcp-client
class AlloyControlClient : Thread() {

    private var toPhone: DataOutputStream? = null
    private var fromPhone: DataInputStream? = null

    private val channelMap = mutableMapOf<String, AlloyDataClient>()
    private val streamMap = mutableMapOf<Int, String>()
    private var sequence = 0

    @Synchronized
    fun sendProtobuf(payload: ByteArray, topic: String, type: Int, isResponse: Boolean, compress: Boolean = false) {
        sequence += 1
        val seq = sequence
        var stream = resolveTopic(topic)
        val uuid = UUID.randomUUID()
        var flags = 0

        // we don't have a stream of this type yet, so we need a fresh one (and include the topic)
        val msgTopic = if(stream == null) {
            stream = getFreshStream(topic)
            flags = flags or 0x10 // set hasTopic flag
            topic
        }
        else
            null

        val effectivePayload = if(compress) {
            GzipDecoder.compress(payload)
        } else {
            payload
        }

        val msg = ProtobufMessage(seq, stream, flags, null, uuid, msgTopic, null, type, if(isResponse) 1 else 0, effectivePayload)

        // we'll just send on any channel, receiving implementation does not care
        val channel = channelMap.values.firstOrNull()
        channel?.sendMessage(msg.toBytes())
    }

    @Synchronized
    fun sendData(topic: String, responseIdentifier: String?, payload: ByteArray) {
        sequence += 1
        val seq = sequence
        var stream = resolveTopic(topic)
        val uuid = UUID.randomUUID()
        var flags = 0

        // we don't have a stream of this type yet, so we need a fresh one (and include the topic)
        val msgTopic = if(stream == null) {
            stream = getFreshStream(topic)
            flags = flags or 0x10 // set hasTopic flag
            topic
        }
        else
            null

        val msg = DataMessage(seq, stream, flags, responseIdentifier, uuid, msgTopic, null, payload)

        // we'll just send on any channel, receiving implementation does not care
        val channel = channelMap.values.firstOrNull()
        channel?.sendMessage(msg.toBytes())
    }

    fun getFreshStream(topic: String): Int {
        val id = (streamMap.keys.maxOrNull() ?: 0) + 1
        streamMap[id] = topic
        return id
    }

    fun associateStreamWithTopic(streamID: Int, topic: String) {
        streamMap[streamID] = topic
    }

    fun resolveStream(streamID: Int): String? {
        return streamMap[streamID]
    }

    fun resolveTopic(topic: String): Int? {
        return streamMap.toList().firstOrNull { it.second == topic }?.first
    }

    private fun sendMessage(message: ByteArray, lengthPrefixed: Boolean = false) {
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
            //here you must put your computer's IP address.
            val serverAddr = InetAddress.getByName("127.0.0.1")
            Log.d("SimAlloyControl", "Connecting to Alloy port 61315...")

            //create a socket to make the connection with the server
            val socket = Socket(serverAddr, 61315)
            try {
                toPhone = DataOutputStream(socket.getOutputStream())
                fromPhone = DataInputStream(socket.getInputStream())

                val controlChannelNWSCRequest = NwscSim.buildServiceRequest(61315, "ids-control-channel")
                Log.d("SimAlloyControl", "Sending NWSC request $controlChannelNWSCRequest")
                sendMessage(controlChannelNWSCRequest.toByteArray())

                // receive NWSC feedback
                val reqLen = fromPhone!!.readUnsignedShort()
                //Log.d("WatchSim", "Reading NWSC reply, $reqLen bytes")

                val request = ByteArray(reqLen)
                fromPhone!!.readFully(request, 0, request.size)
                //Log.d("WatchSim", "NWSC reply: ${request.hex()}")

                val packet = NWSCPacket.parseFeedback(request)
                Log.d("SimAlloyControl", "NWSC reply: $packet")


                while(true) {
                    val length = fromPhone!!.readUnsignedShort() // read length of incoming message
                    if (length > 0) {
                        val message = ByteArray(length)
                        fromPhone!!.readFully(message, 0, message.size)

                        Log.d("SimAlloyControl", "AlloyCtrl receive raw: ${message.hex()}")
                        val msg = UTunControlMessage.parse(message)
                        Log.d("SimAlloyControl", "AlloyCtrl receive: $msg")

                        // immediately accept all opened channels and open NWSC connections for them
                        if(msg is SetupChannel) {
                            val reply = SetupChannel(msg.protocol, msg.receiverPort, msg.senderPort, UUID.randomUUID(), msg.senderUUID, msg.account, msg.service, msg.name)
                            Log.d("SimAlloyControl", "AlloyCtrl accepting channel: $reply")

                            sendMessage(reply.toBytes(), lengthPrefixed = true)
                            val service = "${msg.account}/${msg.service}/${msg.name}"
                            val client = AlloyDataClient(service, this)
                            client.start()
                            channelMap[service] = client
                        }

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
}