package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.utun.ProtobufMessage
import net.rec0de.android.watchwitch.utun.UTunHandler
import net.rec0de.android.watchwitch.utun.UTunMessage
import java.util.UUID

object FindMyLocalDevice : UTunService {
    override val name = "com.apple.private.alloy.findmylocaldevice"

    private var utunSequence = 0

    override fun acceptsMessageType(msg: UTunMessage) = msg is ProtobufMessage

    override fun receiveMessage(msg: UTunMessage, handler: UTunHandler) {
        if(msg !is ProtobufMessage)
            throw Exception("FindMyLocalDevice expects ProtobufMessage but got $msg")

        val fields = ProtobufParser().parse(msg.payload).value

        val timestamp = (fields[1]?.first() as ProtoI64?)?.asDate()
        println("rcv findmylocaldevice: $timestamp")

        val replyBytes = ProtoBuf(mapOf(1 to listOf(ProtoVarInt(0) as ProtoValue))).renderStandalone()
        handler.send(ProtobufMessage(utunSequence, msg.streamID, 0, null, UUID.randomUUID(), null, null, 1, 1, replyBytes))
        utunSequence += 1
    }
}