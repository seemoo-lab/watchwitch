package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import java.util.UUID

class Trailer(
    val uuid: UUID,
    val sequence: Int,
    val flag: Boolean?,
    val state: Int?,
) {
    override fun toString(): String {
        return "Trailer(session $uuid, seq $sequence, flag $flag, state $state)"
    }

    companion object : PBParsable<Trailer>() {
        override fun fromSafePB(pb: ProtoBuf): Trailer {
            val sequence = pb.readShortVarInt(1)
            val flag = pb.readOptBool(2)
            val session = Utils.uuidFromBytes((pb.readAssertedSinglet(3) as ProtoLen).value)
            val state = pb.readOptShortVarInt(4)
            return Trailer(session, sequence, flag, state)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        fields[1] = listOf(ProtoVarInt(sequence))
        if(flag != null)
            fields[2] = listOf(ProtoVarInt(flag))
        fields[3] = listOf(ProtoLen(Utils.uuidToBytes(uuid)))
        if(state != null)
            fields[4] = listOf(ProtoVarInt(state))

        return ProtoBuf(fields).renderStandalone()
    }
}