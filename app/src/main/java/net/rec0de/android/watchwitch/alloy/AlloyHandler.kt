package net.rec0de.android.watchwitch.alloy

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.compression.GzipDecoder
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex
import java.io.DataOutputStream
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

open class AlloyHandler(private val channel: String, var output: DataOutputStream?) {

    private var fragmentBuffer: ByteArray = byteArrayOf()
    private val parser = BPListParser()
    private var handshakeSent = false

    private val shortName = channel.split("/").last().replace("UTunDelivery-", "")

    open fun init(weInitiated: Boolean) {
        AlloyController.registerChannelCreation(channel, this)
        Logger.logUTUN("[$shortName] Creating handler for $channel", 1)

        // the accepting side initiates the handshake
        if(!weInitiated) {
            Logger.logUTUN("[$shortName] Initiating handshake", 1)
            try {
                send(Handshake(4))
                handshakeSent = true
            }
            catch (e: Exception) {
                Logger.logUTUN("[$shortName] exception: $e", 0)
            }

        }
    }

    open fun receive(message: ByteArray) {
        Logger.logUTUN("[$shortName] UTUN rcv raw for $channel: ${message.hex()}", 5)
        val parsed = AlloyMessage.parse(message)
        Logger.logUTUN("[$shortName] seq ${parsed.sequence} UTUN rcv for $channel: $parsed", 3)
        
        when(parsed) {
            is AlloyCommonMessage -> handleCommonMessage(parsed)
            is Handshake -> if(!handshakeSent) send(Handshake(4))
            is FragmentedMessage -> handleFragment(parsed)
            is AckMessage -> {}
            is ServiceMapMessage -> AlloyController.associateStreamWithTopic(parsed.streamID, parsed.serviceName, true)
            else -> Logger.logUTUN("[$shortName] Unhandled UTUN rcv for $channel: $parsed", 0)
        }
    }

    private fun handleCommonMessage(message: AlloyCommonMessage) {
        // message is expired
        if(message.normalizedExpiryDate != null && message.normalizedExpiryDate!!.toInstant() < Instant.now()) {
            send(ExpiredAckMessage(message.sequence))
            return
        }

        // acknowledge everything for now
        send(AckMessage(message.sequence))

        if(message.wantsAppAck)
            send(AppAckMessage(message.sequence, message.streamID, message.messageUUID.toString(), message.topic))

        if(message.hasTopic)
            AlloyController.associateStreamWithTopic(message.streamID, message.topic!!)
        else {
            message.topic = AlloyController.resolveStream(message.streamID)
            Logger.logUTUN("[$shortName] topic from stream map: ${message.topic}", 1)
        }

        // try handing off to supported service
        if(AlloyController.services.containsKey(message.topic) && AlloyController.services[message.topic]!!.acceptsMessageType(message)) {
            val service = AlloyController.services[message.topic]!!
            service.receiveMessage(message, this)
            return
        }

        // as in IDSDaemon::_processIncomingLocalMessage, connectivity monitor messages are just ack'ed and discarded
        if(message.topic == "com.apple.private.alloy.connectivity.monitor") {
            return
        }

        Logger.logUTUN("[$shortName] Unhandled UTUN rcv for $channel: $message", 1)

        when(message) {
            is DataMessage -> {
                if(BPListParser.bufferIsBPList(message.payload))
                    Logger.logUTUN(parser.parse(message.payload).toString(), 2)
                else {
                    // DataMessage payloads can also be protobufs, with either 0, 2, or 3 byte unknown prefixes
                    // *fun*, isn't it?
                    try {
                        Logger.logUTUN(ProtobufParser().parse(message.payload).toString(), 2)
                    }
                    catch(_: Exception) {}
                    try {
                        Logger.logUTUN(ProtobufParser().parse(message.payload.fromIndex(2)).toString(), 2)
                    }
                    catch(_: Exception) {}
                    try {
                        Logger.logUTUN(ProtobufParser().parse(message.payload.fromIndex(3)).toString(), 2)
                    }
                    catch(_: Exception) {}
                }
            }
            is ProtobufMessage -> {
                // for some reason Protobuf messages sometimes carry, guess what, bplists
                if(BPListParser.bufferIsBPList(message.payload))
                    Logger.logUTUN(parser.parse(message.payload).toString(), 2)
                else {
                    try {
                        Logger.logUTUN(ProtobufParser().parse(message.payload).toString(), 2)
                    }
                    catch(_: Exception) {}
                }
            }
        }
    }

    private fun handleFragment(msg: FragmentedMessage) {
        when (msg.fragmentIndex) {
            0 -> fragmentBuffer = msg.payload // first fragment
            msg.fragmentCount - 1 -> receive(fragmentBuffer + msg.payload) // last fragment
            else -> fragmentBuffer += msg.payload // middle fragments
        }
    }

    open fun close() {
        AlloyController.registerChannelClose(channel)
        Logger.logUTUN("[$shortName] Handler closed for $channel", 1)
    }

    fun sendProtobuf(payload: ByteArray, topic: String, type: Int, isResponse: Boolean, compress: Boolean = false) {
        val seq = AlloyController.nextSenderSequence.incrementAndGet()
        var stream = AlloyController.resolveTopic(topic)
        val uuid = UUID.randomUUID()
        var flags = 0

        // we don't have a stream of this type yet, so we need a fresh one (and include the topic)
        val msgTopic = if(stream == null) {
            stream = AlloyController.getFreshStream(topic)
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

        val msg = ProtobufMessage(seq, stream, flags, null, uuid, topic, null, type, if(isResponse) 1 else 0, effectivePayload)
        send(msg)
    }

    // note: messages fragment at 7983b
    fun send(message: AlloyMessage) {
        Logger.logUTUN("[$shortName] UTUN snd for $channel: $message", 1)
        send(message.toBytes())
    }

    protected open fun send(message: ByteArray) {
        // messages typically fragment beyond 7983 bytes, we copy that behaviour
        if(message.size > 7983) {
            var buf = message
            val fragmentCount = ceil(message.size.toDouble() / 7983).roundToInt()
            var fragmentIndex = 0

            while(buf.isNotEmpty()) {
                val endIndex = min(7983, buf.size)
                val chunk = buf.sliceArray(0 until endIndex)
                buf = buf.fromIndex(endIndex)
                val fragment = FragmentedMessage(0, fragmentIndex, fragmentCount, chunk)
                Logger.logUTUN("[$shortName] UTUN snd fragment $fragment", 3)
                output!!.write(fragment.toBytes())
                fragmentIndex += 1
            }
            output!!.flush()
        }
        else {
            Logger.logUTUN("[$shortName] UTUN snd raw ${message.hex()}", 3)
            val toWatch = output!!
            toWatch.write(message)
            toWatch.flush()
        }
    }
}