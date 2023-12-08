package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue

class RemoveBulletinRequest(
    val publisherBulletinID: String?,
    val recordID: String?,
    val sectionID: String?,
) {
    override fun toString(): String {
        return "RemoveBulletinRequest(pubID $publisherBulletinID, recID $recordID, secID $sectionID)"
    }

    companion object : PBParsable<RemoveBulletinRequest>() {
        override fun fromSafePB(pb: ProtoBuf): RemoveBulletinRequest {
            val publisherBulletinID = pb.readOptString(1)
            val recordID = pb.readOptString(2)
            val sectionID = pb.readOptString(3)

            return RemoveBulletinRequest(publisherBulletinID, recordID, sectionID)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(publisherBulletinID != null)
            fields[1] = listOf(ProtoString(publisherBulletinID))
        if(recordID != null)
            fields[3] = listOf(ProtoString(recordID))
        if(sectionID != null)
            fields[4] = listOf(ProtoString(sectionID))

        return ProtoBuf(fields).renderStandalone()
    }
}