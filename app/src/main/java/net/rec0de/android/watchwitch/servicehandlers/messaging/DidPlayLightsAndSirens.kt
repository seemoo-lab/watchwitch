package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.toCanonicalTimestamp
import java.util.Date

class DidPlayLightsAndSirens(
    val didPlayLightsAndSirens: Boolean?,
    val publisherMatchID: String?,
    val phoneSectionID: String?,
    val date: Date?,
    val replyToken: String?
) {
    override fun toString(): String {
        return "DidPlayLightsAndSirens(didPlay? $didPlayLightsAndSirens, $date, pubID $publisherMatchID, phoneSecID $phoneSectionID, replyToken: $replyToken)"
    }

    companion object : PBParsable<DidPlayLightsAndSirens>() {
        // based on _BLTPBHandleDidPlayLightsAndSirensReplyRequestReadFrom in BulletinDistributorCompanion binary
        override fun fromSafePB(pb: ProtoBuf): DidPlayLightsAndSirens {
            val didPlayLightsAndSirens = pb.readOptBool(1)
            val publisherMatchID = pb.readOptString(2)
            val phoneSectionID = pb.readOptString(3)
            val date = pb.readOptDate(4, false)
            val replyToken = pb.readOptString(5)
            return DidPlayLightsAndSirens(didPlayLightsAndSirens, publisherMatchID, phoneSectionID, date, replyToken)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(didPlayLightsAndSirens != null)
            fields[1] = listOf(ProtoVarInt(didPlayLightsAndSirens))
        if(publisherMatchID != null)
            fields[2] = listOf(ProtoString(publisherMatchID))
        if(phoneSectionID != null)
            fields[3] = listOf(ProtoString(phoneSectionID))
        if(date != null)
            fields[4] = listOf(ProtoI64(date.toCanonicalTimestamp()))
        if(replyToken != null)
            fields[5] = listOf(ProtoString(replyToken))
        return ProtoBuf(fields).renderStandalone()
    }
}

class WillSendLightsAndSirens(
    val publisherBulletinID: String?,
    val recordID: String?,
    val sectionID: String?,
    val systemApp: Boolean?
) {
    override fun toString(): String {
        return "WillSendLightsAndSirens(pubID $publisherBulletinID, recordID $recordID, sectionID: $sectionID systemApp: $systemApp)"
    }

    companion object : PBParsable<WillSendLightsAndSirens>() {
        override fun fromSafePB(pb: ProtoBuf): WillSendLightsAndSirens {
            val publisherBulletinID = pb.readOptString(1)
            val recordID = pb.readOptString(2)
            val sectionID = pb.readOptString(3)
            val systemApp = pb.readOptBool(4)
            return WillSendLightsAndSirens(publisherBulletinID, recordID, sectionID, systemApp)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(publisherBulletinID != null)
            fields[1] = listOf(ProtoString(publisherBulletinID))
        if(recordID != null)
            fields[2] = listOf(ProtoString(recordID))
        if(sectionID != null)
            fields[3] = listOf(ProtoString(sectionID))
        if(systemApp != null)
            fields[4] = listOf(ProtoVarInt(systemApp))
        return ProtoBuf(fields).renderStandalone()
    }
}