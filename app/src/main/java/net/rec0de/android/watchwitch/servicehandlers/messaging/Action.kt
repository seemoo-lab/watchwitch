package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt

class Action(
    val identifier: String?,
    val appearance: Appearance?,
    val activationMode: Int?,
    val launchURL: String?,
    val behavior: Int?,
    val behaviorParameters: ByteArray?,
    val behaviorParametersNull: ByteArray?
) {
    override fun toString(): String {
        return "Action($identifier, $appearance, $launchURL, actMode $activationMode, behavior $behavior)"
    }

    companion object : PBParsable<Action>() {
        // based on _BLTPBActionReadFrom in BulletinDistributorCompanion binary
        override fun fromSafePB(pb: ProtoBuf): Action {
            val identifier = pb.readOptString(1)
            val appearance = Appearance.fromPB(pb.readOptionalSinglet(2) as ProtoBuf?)
            val activationMode = pb.readOptShortVarInt(3)
            val launchURL = pb.readOptString(4)
            val behavior = pb.readOptShortVarInt(5)
            val behaviorParameters = pb.readOptionalSinglet(6) as ProtoLen?
            val behaviorParametersNulls = pb.readOptionalSinglet(7) as ProtoLen?

            return Action(identifier, appearance, activationMode, launchURL, behavior, behaviorParameters?.value, behaviorParametersNulls?.value)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(identifier != null)
            fields[1] = listOf(ProtoString(identifier))
        if(appearance != null)
            fields[2] = listOf(ProtoLen(appearance.renderProtobuf()))
        if(activationMode != null)
            fields[3] = listOf(ProtoVarInt(activationMode))
        if(launchURL != null)
            fields[4] = listOf(ProtoString(launchURL))
        if(behavior != null)
            fields[5] = listOf(ProtoVarInt(behavior))
        if(behaviorParameters != null)
            fields[6] = listOf(ProtoLen(behaviorParameters))
        if(behaviorParametersNull != null)
            fields[6] = listOf(ProtoLen(behaviorParametersNull))

        return ProtoBuf(fields).renderStandalone()
    }
}

class Appearance(val title: String?, val image: Image?, val destructive: Boolean?) {
    override fun toString(): String {
        return "Appearance(title $title, image $image, destructive? $destructive)"
    }

    companion object : PBParsable<Appearance>() {
        // based on _BLTPBAppearanceReadFrom in BulletinDistributorCompanion binary
        override fun fromSafePB(pb: ProtoBuf): Appearance {
            val title = pb.readOptString(1)
            val image = Image.fromPB(pb.readOptionalSinglet(2) as ProtoBuf)
            val destructive = pb.readOptBool(3)

            return Appearance(title, image, destructive)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(title != null)
            fields[1] = listOf(ProtoString(title))
        if(image != null)
            fields[2] = listOf(ProtoLen(image.renderProtobuf()))
        if(destructive != null)
            fields[3] = listOf(ProtoVarInt(destructive))


        return ProtoBuf(fields).renderStandalone()
    }
}

class Image(val data: ByteArray?) {
    override fun toString(): String {
        return "Image(${data?.hex()})"
    }

    companion object : PBParsable<Image>() {
        // based on _BLTPBImageReadFrom in BulletinDistributorCompanion binary
        override fun fromSafePB(pb: ProtoBuf): Image {
            val data = pb.readOptionalSinglet(1) as ProtoLen?
            return Image(data?.value)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(data != null)
            fields[1] = listOf(ProtoLen(data))

        return ProtoBuf(fields).renderStandalone()
    }
}