package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.decoders.bplist.BPArray
import net.rec0de.android.watchwitch.decoders.bplist.BPAsciiString
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPFalse
import net.rec0de.android.watchwitch.decoders.bplist.KeyedArchiveEncoder
import net.rec0de.android.watchwitch.decoders.bplist.NSArray
import net.rec0de.android.watchwitch.decoders.bplist.NSDate
import net.rec0de.android.watchwitch.decoders.bplist.NSDict
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.toCanonicalTimestamp
import java.util.Date
import java.util.UUID


class BulletinRequest(val bulletin: Bulletin?, val shouldPlaySoundsAndSirens: Boolean?, val date: Date?, val updateType: Int?, val trafficRestricted: Boolean?) {
    override fun toString(): String {
        return "BulletinRequest($date, sirens? $shouldPlaySoundsAndSirens, type $updateType, restricted? $trafficRestricted, $bulletin)"
    }

    companion object : PBParsable<BulletinRequest>() {
        // based on _BLTPBAddBulletinRequestReadFrom in BulletinDistributorCompanion binary
        override fun fromSafePB(pb: ProtoBuf): BulletinRequest {
            val bulletin = Bulletin.fromPB(pb.readOptPB(1))
            val shouldPlayLightsAndSirens = pb.readOptBool(2)
            val date = pb.readOptDate(3, appleEpoch = false)
            val updateType = pb.readOptShortVarInt(4)
            val trafficRestricted = pb.readOptBool(5)
            return BulletinRequest(bulletin, shouldPlayLightsAndSirens, date, updateType, trafficRestricted)
        }

        fun mimicIMessage(sender: String, text: String, senderID: String): BulletinRequest {
            val now = Date()
            val pubUUID = UUID.randomUUID().toString().uppercase()

            val context = NSDict(mapOf(
                BPAsciiString("launchImage") to BPAsciiString(""),
                BPAsciiString("recordDate") to NSDate(now),
                BPAsciiString("shouldIgnoreDoNotDisturb") to BPFalse,
                BPAsciiString("contentDate") to NSDate(now),
                BPAsciiString("userInfo") to NSDict(mapOf(
                    BPAsciiString("CKBBContextKeySenderName") to BPAsciiString(sender),
                    BPAsciiString("CKBBUserInfoKeyChatIdentifier") to BPAsciiString(senderID),
                    BPAsciiString("CKBBContextKeyMessageGUID") to BPAsciiString(pubUUID),
                    BPAsciiString("CKBBContextKeyChatGUIDs") to NSArray(listOf(BPAsciiString("iMessage;-;$senderID"))),
                )),
            ))

            val bulletin = Bulletin(
                title = sender,
                messageTitle = text,
                threadID = senderID,
                sectionID = "com.apple.MobileSMS",
                sectionDisplayName = "Messages",
                universalSectionID = "com.apple.MobileSMS",
                categoryID = "MessageExtension-Madrid",
                teamID = "0000000000",
                recordID = pubUUID,
                publisherBulletinId = pubUUID,
                replyToken = UUID.randomUUID().toString().uppercase(),
                feed = 59,
                sectionSubtype = 2,
                attachmentType = 0,
                soundAlertType = 2,
                soundAudioVolume = 1.0,
                soundMaximumDuration = 1.0,
                soundToneIdentifier = "",
                soundAccountIdentifier = "",
                date = now,
                publicationDate = now,
                includesSound = true,
                loading = false,
                turnsOnDisplay = true,
                ignoresQuietMode = false,
                soundShouldRepeat = false,
                soundShouldIgnoreRingerSwitch = false,
                hasCriticalIcon = false,
                preemptsPresentedAlert = false,
                alertSuppressionContexts = BPArray(emptyList()),
                context = KeyedArchiveEncoder().encode(context)
            )
            return BulletinRequest(bulletin, true, now, 0, false)
        }

        fun mimicSignal(sender: String, message: String, uuid: UUID = UUID.randomUUID()) : BulletinRequest {
            val now = Date()
            val pubUUID = uuid.toString().uppercase()
            val threadUUID = UUID.randomUUID().toString().uppercase()

            val actions = listOf(Action(
                identifier = "Signal.AppNotifications.Action.reply",
                launchURL = null,
                activationMode = 1,
                behavior = 1,
                appearance = Appearance("Reply", null, false),
                behaviorParameters = BPDict(mapOf(
                    BPAsciiString("UNNotificationActionTextInputPlaceholder") to BPAsciiString(""),
                    BPAsciiString("UNNotificationActionTextInputButtonTitle") to BPAsciiString("Send")
                )).renderAsTopLevelObject(),
                behaviorParametersNull = null
            ))

            val context = NSDict(mapOf(
                BPAsciiString("launchImage") to BPAsciiString(""),
                BPAsciiString("recordDate") to NSDate(now),
                BPAsciiString("shouldIgnoreDoNotDisturb") to BPFalse,
                BPAsciiString("userInfo") to NSDict(mapOf(
                    BPAsciiString("Signal.AppNotificationsUserInfoKey.messageId") to BPAsciiString(UUID.randomUUID().toString()),
                    BPAsciiString("Signal.AppNotificationsUserInfoKey.threadId") to BPAsciiString(threadUUID)
                ))
            ))

            val bulletin = Bulletin(
                bulletinID = UUID.randomUUID().toString().uppercase(),
                sectionID = "org.whispersystems.signal",
                sectionDisplayName = "Signal",
                title = sender,
                messageTitle = message,
                recordID = pubUUID,
                publisherBulletinId = pubUUID,
                soundAccountIdentifier = "",
                soundToneIdentifier = "",
                categoryID = "Signal.AppNotificationCategory.incomingMessageWithActions",
                threadID = threadUUID,
                replyToken = UUID.randomUUID().toString().uppercase(),
                teamID = "U68MSDN6DR",
                feed = 59,
                sectionSubtype = 2,
                attachmentType = 0,
                soundAlertType = 17,
                soundAudioVolume = 1.0,
                soundMaximumDuration = 0.0,
                date = now,
                publicationDate = now,
                includesSound = true,
                loading = false,
                turnsOnDisplay = true,
                ignoresQuietMode = false,
                soundShouldRepeat = false,
                soundShouldIgnoreRingerSwitch = false,
                hasCriticalIcon = false,
                preemptsPresentedAlert = false,
                actions = actions,
                context = KeyedArchiveEncoder().encode(context),
                alertSuppressionContexts = BPArray(emptyList())
            )

            return BulletinRequest(bulletin, true, now, 0, false)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(bulletin != null)
            fields[1] = listOf(ProtoLen(bulletin.renderProtobuf()))
        if(shouldPlaySoundsAndSirens != null)
            fields[2] = listOf(ProtoVarInt(shouldPlaySoundsAndSirens))
        if(date != null)
            fields[3] = listOf(ProtoI64(date.toCanonicalTimestamp()))
        if(updateType != null)
            fields[4] = listOf(ProtoVarInt(updateType.toLong()))
        if(trafficRestricted != null)
            fields[5] = listOf(ProtoVarInt(if(trafficRestricted) 1 else 0))

        return ProtoBuf(fields).renderStandalone()
    }
}