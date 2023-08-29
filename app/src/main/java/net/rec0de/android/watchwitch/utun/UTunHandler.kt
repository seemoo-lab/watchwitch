package net.rec0de.android.watchwitch.utun

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.hex
import java.io.DataOutputStream
import java.time.Instant

open class UTunHandler(private val channel: String, var output: DataOutputStream?) {

    private var fragmentBuffer: ByteArray = byteArrayOf()
    private val parser = BPListParser()

    private val streamIdAssociations = mutableMapOf<Int, String>()

    open fun init() {
        UTunController.registerChannelCreation(channel)
        Logger.logUTUN("Creating handler for $channel", 1)
    }

    open fun receive(message: ByteArray) {
        Logger.logUTUN("UTUN rcv raw for $channel: ${message.hex()}", 5)
        val parsed = UTunMessage.parse(message)
        Logger.logUTUN("UTUN rcv for $channel: $parsed", 3)
        
        when(parsed) {
            is UTunCommonMessage -> handleCommonMessage(parsed)
            is Handshake -> send(Handshake(4))
            is FragmentedMessage -> handleFragment(parsed)
        }
    }

    private fun handleCommonMessage(message: UTunCommonMessage) {
        // message is expired
        if(message.normalizedExpiryDate != null && message.normalizedExpiryDate!!.toInstant() < Instant.now()) {
            send(ExpiredAckMessage(message.sequence))
            return
        }

        // acknowledge everything for now
        send(AckMessage(message.sequence))

        if(message.hasTopic)
            associateStreamWithTopic(message.streamID, message.topic!!)
        else {
            message.topic = streamIdAssociations[message.streamID]
            Logger.logUTUN("topic from stream map: ${message.topic}", 1)
        }

        // try handing off to supported service
        if(UTunController.services.containsKey(message.topic) && UTunController.services[message.topic]!!.acceptsMessageType(message)) {
            val service = UTunController.services[message.topic]!!
            service.receiveMessage(message, this)
            return
        }

        // as in IDSDaemon::_processIncomingLocalMessage, connectivity monitor messages are just ack'ed and discarded
        if(message.topic == "com.apple.private.alloy.connectivity.monitor") {
            return
        }

        Logger.logUTUN("Unhandled UTUN rcv for $channel: $message", 1)

        when(message) {
            is DataMessage -> {
                if(BPListParser.bufferIsBPList(message.payload))
                    println(parser.parse(message.payload))
            }
            is ProtobufMessage -> {
                // for some reason Protobuf messages sometimes carry, guess what, bplists
                if(BPListParser.bufferIsBPList(message.payload))
                    println(parser.parse(message.payload))
                else {
                    try {
                        println(ProtobufParser().parse(message.payload))
                    }
                    catch(_: Exception) {}
                }
            }
        }

        if(message.wantsAppAck)
            send(AppAckMessage(message.sequence, message.streamID, message.messageUUID.toString(), message.topic)) // todo: sequence echo correct?
    }

    private fun associateStreamWithTopic(streamID: Int, topic: String) {
        if(streamIdAssociations.containsKey(streamID)) {
            if(streamIdAssociations[streamID] != topic)
                throw Exception("Stream ID $streamID is associated with topic ${streamIdAssociations[streamID]} but trying to rebind with $topic")
        }
        streamIdAssociations[streamID] = topic
    }

    private fun handleFragment(msg: FragmentedMessage) {
        when (msg.fragmentIndex) {
            0 -> fragmentBuffer = msg.payload // first fragment
            msg.fragmentCount - 1 -> receive(fragmentBuffer + msg.payload) // last fragment
            else -> fragmentBuffer += msg.payload // middle fragments
        }
    }

    open fun close() {
        UTunController.registerChannelClose(channel)
        Logger.logUTUN("Handler closed for $channel", 1)
    }

    fun send(message: UTunMessage) {
        Logger.logUTUN("UTUN snd for $channel: $message", 1)
        send(message.toBytes())
    }

    protected open fun send(message: ByteArray) {
        Logger.logUTUN("UTUN snd raw ${message.hex()}", 3)
        val toWatch = output!!
        toWatch.write(message)
        toWatch.flush()
    }
}