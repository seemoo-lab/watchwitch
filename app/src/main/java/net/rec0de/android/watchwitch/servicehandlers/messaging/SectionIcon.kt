package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.hex

class SectionIcon(val variants: List<SectionIconVariant>) {
    override fun toString(): String {
        return "SectionIcon(${variants.joinToString(", ")})"
    }

    companion object : PBParsable<SectionIcon>() {
        // based on _BLTPBSectionIconReadFrom in BulletinDistributorCompanion binary
        override fun fromSafePB(pb: ProtoBuf): SectionIcon {
            val variants = pb.readMulti(1).map { SectionIconVariant.fromSafePB(it as ProtoBuf) }
            return SectionIcon(variants)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        fields[1] = variants.map { ProtoLen(it.renderProtobuf()) }
        return ProtoBuf(fields).renderStandalone()
    }
}

class SectionIconVariant(val format: Int?, val imageData: ByteArray, val precomposed: Boolean?) {
    override fun toString(): String {
        return "SectionIconVariant(format $format, precomposed? $precomposed, data: ${imageData.hex()})"
    }

    companion object : PBParsable<SectionIconVariant>() {
        override fun fromSafePB(pb: ProtoBuf): SectionIconVariant {
            val format = pb.readOptShortVarInt(1)
            val imageData = (pb.readAssertedSinglet(2) as ProtoLen).value
            val precomposed = pb.readOptBool(3)

            return SectionIconVariant(format, imageData, precomposed)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        if(format != null)
            fields[1] = listOf(ProtoVarInt(format))

        fields[2] = listOf(ProtoLen(imageData))

        if(precomposed != null)
            fields[3] = listOf(ProtoVarInt(precomposed))

        return ProtoBuf(fields).renderStandalone()
    }
}