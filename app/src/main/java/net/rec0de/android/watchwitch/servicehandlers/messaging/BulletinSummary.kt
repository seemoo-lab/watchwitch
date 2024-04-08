package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.bitmage.hex
import java.util.Date

class AddBulletinSummaryRequest(
    val bulletin: BulletinSummary?,
) {
    override fun toString(): String {
        return "AddBulletinSummaryRequest($bulletin)"
    }

    companion object : PBParsable<AddBulletinSummaryRequest>() {
        override fun fromSafePB(pb: ProtoBuf): AddBulletinSummaryRequest {
            val bulletin = BulletinSummary.fromPB(pb.readOptPB(1))
            return AddBulletinSummaryRequest(bulletin)
        }
    }
}

class UpdateBulletinListRequest(
    val bulletin: BulletinList?,
) {
    override fun toString(): String {
        return "UpdateBulletinListRequest($bulletin)"
    }

    companion object : PBParsable<UpdateBulletinListRequest>() {
        override fun fromSafePB(pb: ProtoBuf): UpdateBulletinListRequest {
            val bulletin = BulletinList.fromPB(pb.readOptPB(1))
            return UpdateBulletinListRequest(bulletin)
        }
    }
}

class CancelBulletinRequest(
    val universalSectionID: String?,
    val publisherMatchID: String?,
    val feed: Int?,
    val date: Date?
) {
    override fun toString(): String {
        return "CancelBulletinRequest(universalSecID $universalSectionID, pubMatchID $publisherMatchID, feed $feed, date $date)"
    }

    companion object : PBParsable<CancelBulletinRequest>() {
        override fun fromSafePB(pb: ProtoBuf): CancelBulletinRequest {
            val universalSectionID = pb.readOptString(1)
            val publisherMatchID = pb.readOptString(2)
            val feed = pb.readOptShortVarInt(3)
            val date = pb.readOptDate(4)

            return CancelBulletinRequest(universalSectionID, publisherMatchID, feed, date)
        }
    }
}

class BulletinSummary(val publisherBulletinID: String?, val recordID: String?, val sectionID: String?, val destinations: Int?, val keys: List<BulletinSummaryKey>){
    override fun toString(): String {
        return "BulletinSummary(pubID $publisherBulletinID, recID $recordID, secID $sectionID, desitnations $destinations, keys ${keys.joinToString(", ")})"
    }

    companion object : PBParsable<BulletinSummary>() {
        override fun fromSafePB(pb: ProtoBuf): BulletinSummary {
            val publisherBulletinID = pb.readOptString(1)
            val recordID = pb.readOptString(2)
            val sectionID = pb.readOptString(3)
            val destinations = pb.readOptShortVarInt(4)
            val keys = pb.readMulti(5).map { BulletinSummaryKey.fromSafePB(it as ProtoBuf) }
            return BulletinSummary(publisherBulletinID, recordID, sectionID, destinations, keys)
        }
    }
}

class BulletinList(val sectionBulletinLists: List<SectionBulletinList>){
    override fun toString(): String {
        return "BulletinList(${sectionBulletinLists.joinToString(", ")})"
    }

    companion object : PBParsable<BulletinList>() {
        override fun fromSafePB(pb: ProtoBuf): BulletinList {
            val sectionBulletinLists = pb.readMulti(1).map { SectionBulletinList.fromSafePB(it as ProtoBuf) }
            return BulletinList(sectionBulletinLists)
        }
    }
}

class SectionBulletinList(val sectionID: String?, val bulletinIdentifier: BulletinIdentifier?){
    override fun toString(): String {
        return "SectionBulletinList(section $sectionID, id $bulletinIdentifier)"
    }

    companion object : PBParsable<SectionBulletinList>() {
        override fun fromSafePB(pb: ProtoBuf): SectionBulletinList {
            val sectionID = pb.readOptString(1)
            val bulletinIdentifier = BulletinIdentifier.fromPB(pb.readOptPB(2))
            return SectionBulletinList(sectionID, bulletinIdentifier)
        }
    }
}

class BulletinIdentifier(val publisherID: String?, val recordID: String?){
    override fun toString(): String {
        return "BulletinIdentifier(pubID $publisherID, recordID $recordID)"
    }

    companion object : PBParsable<BulletinIdentifier>() {
        override fun fromSafePB(pb: ProtoBuf): BulletinIdentifier {
            val publisherBulletinID = pb.readOptString(1)
            val recordID = pb.readOptString(2)
            return BulletinIdentifier(publisherBulletinID, recordID)
        }
    }
}

class BulletinSummaryKey(val key: String?, val value: ByteArray?, val valueNulls: ByteArray?){
    override fun toString(): String {
        return "BulletinSummaryKey($key, value ${value?.hex()}, valueNulls ${valueNulls?.hex()})"
    }

    companion object : PBParsable<BulletinSummaryKey>() {
        override fun fromSafePB(pb: ProtoBuf): BulletinSummaryKey {
            val key = pb.readOptString(1)
            val value = pb.readOptionalSinglet(2) as ProtoLen?
            val valueNulls = pb.readOptionalSinglet(3) as ProtoLen?

            return BulletinSummaryKey(key, value?.value, valueNulls?.value)
        }
    }
}