package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.hex

abstract class AbstractActionRequest(
    val publisherBulletinID: String?,
    val recordID: String?,
    val sectionID: String?,
    val actionInfo: ActionInfo?
) {
    abstract val name: String

    override fun toString(): String {
        return "$name(pubID $publisherBulletinID, recID $recordID, secID $sectionID, actionInfo $actionInfo)"
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(publisherBulletinID != null)
            fields[1] = listOf(ProtoString(publisherBulletinID))
        if(recordID != null)
            fields[3] = listOf(ProtoString(recordID))
        if(sectionID != null)
            fields[4] = listOf(ProtoString(sectionID))
        if(actionInfo != null)
            fields[5] = listOf(ProtoLen(actionInfo.renderProtobuf()))

        return ProtoBuf(fields).renderStandalone()
    }
}

class DismissActionRequest(
    publisherBulletinID: String?,
    recordID: String?,
    sectionID: String?,
    actionInfo: ActionInfo?
) : AbstractActionRequest(publisherBulletinID, recordID, sectionID, actionInfo) {
    override val name = "DismissActionRequest"
    companion object : PBParsable<DismissActionRequest>() {
        override fun fromSafePB(pb: ProtoBuf): DismissActionRequest {
            val publisherBulletinID = pb.readOptString(1)
            val recordID = pb.readOptString(2)
            val sectionID = pb.readOptString(3)
            val actionInfo = ActionInfo.fromPB(pb.readOptPB(4))

            return DismissActionRequest(publisherBulletinID, recordID, sectionID, actionInfo)
        }
    }
}

class SnoozeActionRequest(
    publisherBulletinID: String?,
    recordID: String?,
    sectionID: String?,
    actionInfo: ActionInfo?
) : AbstractActionRequest(publisherBulletinID, recordID, sectionID, actionInfo) {
    override val name = "SnoozeActionRequest"
    companion object : PBParsable<SnoozeActionRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SnoozeActionRequest {
            val publisherBulletinID = pb.readOptString(1)
            val recordID = pb.readOptString(2)
            val sectionID = pb.readOptString(3)
            val actionInfo = ActionInfo.fromPB(pb.readOptPB(4))

            return SnoozeActionRequest(publisherBulletinID, recordID, sectionID, actionInfo)
        }
    }
}

class SupplementaryActionRequest(
    publisherBulletinID: String?,
    recordID: String?,
    sectionID: String?,
    actionInfo: ActionInfo?
) : AbstractActionRequest(publisherBulletinID, recordID, sectionID, actionInfo) {
    override val name = "SupplementaryActionRequest"
    companion object : PBParsable<SupplementaryActionRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SupplementaryActionRequest {
            val publisherBulletinID = pb.readOptString(1)
            val recordID = pb.readOptString(2)
            val sectionID = pb.readOptString(3)
            val actionInfo = ActionInfo.fromPB(pb.readOptPB(4))

            return SupplementaryActionRequest(publisherBulletinID, recordID, sectionID, actionInfo)
        }
    }
}

class AcknowledgeActionRequest(
    publisherBulletinID: String?,
    recordID: String?,
    sectionID: String?,
    actionInfo: ActionInfo?
) : AbstractActionRequest(publisherBulletinID, recordID, sectionID, actionInfo) {
    override val name = "AcknowledgeActionRequest"
    companion object : PBParsable<SupplementaryActionRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SupplementaryActionRequest {
            val publisherBulletinID = pb.readOptString(1)
            val recordID = pb.readOptString(2)
            val sectionID = pb.readOptString(3)
            val actionInfo = ActionInfo.fromPB(pb.readOptPB(4))

            return SupplementaryActionRequest(publisherBulletinID, recordID, sectionID, actionInfo)
        }
    }
}

class ActionInfo(val context: ByteArray?, val contextNulls: ByteArray?) {
    override fun toString(): String {
        return "ActionInfo(context: ${context?.hex()}, contextNulls: ${contextNulls?.hex()})"
    }

    companion object : PBParsable<ActionInfo>() {
        override fun fromSafePB(pb: ProtoBuf): ActionInfo {
            val context = pb.readOptionalSinglet(4) as ProtoLen?
            val contextNulls = pb.readOptionalSinglet(5) as ProtoLen?

            return ActionInfo(context?.value, contextNulls?.value)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(context != null)
            fields[4] = listOf(ProtoLen(context))
        if(contextNulls != null)
            fields[5] = listOf(ProtoLen(contextNulls))

        return ProtoBuf(fields).renderStandalone()
    }
}