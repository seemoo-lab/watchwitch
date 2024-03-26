package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.decoders.bplist.BPListObject
import net.rec0de.android.watchwitch.decoders.bplist.CodableBPListObject
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBPList
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.hex
import net.rec0de.android.watchwitch.toCanonicalTimestamp
import java.util.Date

class Bulletin(
    val bulletinID: String? = null,
    val sectionID: String? = null,
    val sectionDisplayName: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val messageTitle: String? = null,
    val date: Date? = null,
    val attachment: ByteArray? = null,
    val actions: List<Action> = listOf(),
    val feed: Int? = null,
    val snoozeAction: Action? = null,
    val recordID: String? = null,
    val publisherBulletinId: String? = null,
    val dismissAction: Action? = null,
    val sectionSubtype: Int? = null,
    val sockPuppetAppBundleID: String? = null,
    val category: String? = null,
    val publicationDate: Date? = null,
    val includesSound: Boolean? = null,
    val teamID: String? = null,
    val context: BPListObject? = null,
    val universalSectionID: String? = null,
    val alertSuppressionContexts: BPListObject? = null,
    val soundAlertType: Int? = null,
    val soundAccountIdentifier: String? = null,
    val soundToneIdentifier: String? = null,
    val attachmentType: Int? = null,
    val containsUpdatedAttachment: Boolean? = null,
    val loading: Boolean? = null,
    val turnsOnDisplay: Boolean? = null,
    val subsectionIDs: List<String> = emptyList(),
    val dismissalID: String? = null,
    val attachmentURL: String? = null,
    val peopleIDs: List<String> = emptyList(),
    val ignoresQuietMode: Boolean? = null,
    val categoryID: String? = null,
    val contextNulls: ByteArray? = null,
    val alertSuppressionContextNulls: ByteArray? = null,
    val threadID: String? = null,
    val attachmentID: String? = null,
    val additionalAttachments: List<BulletinAttachment> = emptyList(),
    val requiredExpirationDate: Date? = null,
    val replyToken: String? = null,
    val soundMaximumDuration: Double? = null,
    val soundShouldRepeat: Boolean? = null,
    val soundShouldIgnoreRingerSwitch: Boolean? = null,
    val hasCriticalIcon: Boolean? = null,
    val soundAudioVolume: Double? = null,
    val preemptsPresentedAlert: Boolean? = null,
    val suppressDelayForwardBulletins: Boolean? = null,
    val icon: SectionIcon? = null,
    val containsUpdateIcon: Boolean? = null
) {
    override fun toString(): String {
        var str = "Bulletin("

        if(bulletinID != null)
            str += " bID: \"$bulletinID\""
        if(sectionID != null)
            str += " sID: \"$sectionID\""
        if(sectionDisplayName != null)
            str += " sDN: \"$sectionDisplayName\""
        if(title != null)
            str += " title: \"$title\""
        if(subtitle != null)
            str += " subtitle: \"$subtitle\""
        if(messageTitle != null)
            str += " msgTitle: \"$messageTitle\""
        if(recordID != null)
            str += " rID: \"$recordID\""
        if(publisherBulletinId != null)
            str += " pubID: \"$publisherBulletinId\""
        if(sockPuppetAppBundleID != null)
            str += " sockPuppetBundle: \"$sockPuppetAppBundleID\""
        if(category != null)
            str += " category: \"$category\""
        if(teamID != null)
            str += " teamID: \"$teamID\""
        if(universalSectionID != null)
            str += " usID: \"$universalSectionID\""
        if(soundAccountIdentifier != null)
            str += " soundAccount: \"$soundAccountIdentifier\""
        if(soundToneIdentifier != null)
            str += " soundTone: \"$soundToneIdentifier\""
        if(dismissalID != null)
            str += " dID: \"$dismissalID\""
        if(attachmentURL != null)
            str += " aURL: \"$attachmentURL\""
        if(categoryID != null)
            str += " cID: \"$categoryID\""
        if(threadID != null)
            str += " thID: \"$threadID\""
        if(attachmentID != null)
            str += " aID: \"$attachmentID\""
        if(replyToken != null)
            str += " replyToken: \"$replyToken\""

        if(feed != null)
            str += " feed: $feed"
        if(sectionSubtype != null)
            str += " sectionSubtype: $sectionSubtype"
        if(attachmentType != null)
            str += " attachmentType: $attachmentType"
        if(soundAlertType != null)
            str += " alertType: $soundAlertType"

        if(soundAudioVolume != null)
            str += " volume: $soundAudioVolume"
        if(soundMaximumDuration != null)
            str += " maxDuration: $soundAudioVolume"

        if(date != null)
            str += " date: $date"
        if(publicationDate != null)
            str += " pubDate: $publicationDate"
        if(requiredExpirationDate != null)
            str += " expDate: $requiredExpirationDate"

        if(includesSound != null)
            str += " includesSound: $includesSound"
        if(containsUpdatedAttachment != null)
            str += " containsUpdatedAttachment: $containsUpdatedAttachment"
        if(loading != null)
            str += " loading: $loading"
        if(turnsOnDisplay != null)
            str += " turnsOnDisplay: $turnsOnDisplay"
        if(ignoresQuietMode != null)
            str += " ignoresQuietMode: $ignoresQuietMode"
        if(soundShouldRepeat != null)
            str += " soundShouldRepeat: $soundShouldRepeat"
        if(soundShouldIgnoreRingerSwitch != null)
            str += " soundShouldIgnoreRingerSwitch: $soundShouldIgnoreRingerSwitch"
        if(hasCriticalIcon != null)
            str += " hasCriticalIcon: $hasCriticalIcon"
        if(preemptsPresentedAlert != null)
            str += " preemptsPresentedAlert: $preemptsPresentedAlert"
        if(suppressDelayForwardBulletins != null)
            str += " suppressDelayForwardBulletins: $suppressDelayForwardBulletins"
        if(containsUpdateIcon != null)
            str += " containsUpdateIcon: $containsUpdateIcon"

        if(actions.isNotEmpty())
            str += " actions: $actions"
        if(dismissAction != null)
            str += " dismissAction: $dismissAction"
        if(snoozeAction != null)
            str += " snoozeAction: $snoozeAction"
        if(icon != null)
            str += " icon: $icon"

        if(attachment != null)
            str += " attachment: \"${attachment.hex()}\""
        if(context != null)
            str += " context: $context"
        if(alertSuppressionContexts != null)
            str += " alertSuppressionContexts: $alertSuppressionContexts"
        if(contextNulls != null)
            str += " contextNulls: \"${contextNulls.hex()}\""
        if(alertSuppressionContextNulls != null)
            str += " alertSuppressionContextNulls: \"${alertSuppressionContextNulls.hex()}\""

        if(subsectionIDs.isNotEmpty())
            str += " subsectionIDs: ${subsectionIDs.joinToString(", ")}"
        if(peopleIDs.isNotEmpty())
            str += " peopleIDs: ${peopleIDs.joinToString(", ")}"
        if(additionalAttachments.isNotEmpty())
            str += " additionalAttachments: ${additionalAttachments.joinToString(", ")}"

        str += ")"
        return str
    }

    companion object : PBParsable<Bulletin>() {
        // based on _BLTPBBulletinReadFrom in BulletinDistributorCompanion binary
        override fun fromSafePB(pb: ProtoBuf): Bulletin {
            // welp these are a lot of fields
            val bulletinID = pb.readOptString(1)
            val sectionID = pb.readOptString(2)
            val sectionDisplayName = pb.readOptString(3)
            val title = pb.readOptString(4)
            val subtitle = pb.readOptString(5)
            val messageTitle = pb.readOptString(6)
            val date = pb.readOptDate(7, appleEpoch = false)
            val attachment = pb.readOptionalSinglet(8) as ProtoLen?
            val actions = pb.readMulti(9).map { Action.fromSafePB(it as ProtoBuf) }
            val feed = pb.readOptShortVarInt(0x0a)
            val snoozeAction = Action.fromPB(pb.readOptPB(0x0b))
            val recordID = pb.readOptString(0x0c)
            val publisherBulletinId = pb.readOptString(0x0d)
            val dismissAction = Action.fromPB(pb.readOptPB(0x0e))
            val sectionSubtype = pb.readOptShortVarInt(0x0f)
            val sockPuppetAppBundleID = pb.readOptString(0x10)
            val category = pb.readOptString(0x11)
            val publicationDate = pb.readOptDate(0x12, appleEpoch = false)
            val includesSound = pb.readOptBool(0x13)
            val teamID = pb.readOptString(0x14)
            val context = (pb.readOptionalSinglet(0x15) as ProtoBPList?)?.parsed
            val universalSectionID = pb.readOptString(0x16)
            val alertSuppressionContexts = (pb.readOptionalSinglet(0x17) as ProtoBPList?)?.parsed
            val soundAlertType = pb.readOptShortVarInt(0x18)
            val soundAccountIdentifier = pb.readOptString(0x19)
            val soundToneIdentifier = pb.readOptString(0x1a)
            val attachmentType = pb.readOptShortVarInt(0x1b)
            val containsUpdatedAttachment = pb.readOptBool(0x1c)
            val loading = pb.readOptBool(0x1d)
            val turnsOnDisplay = pb.readOptBool(0x1e)
            val subsectionIDs = pb.readMulti(0x1f).map { (it as ProtoString).stringValue }
            val dismissalID = pb.readOptString(0x20)
            val attachmentURL = pb.readOptString(0x21)
            val peopleIDs = pb.readMulti(0x22).map { (it as ProtoString).stringValue }
            val ignoresQuietMode = pb.readOptBool(0x23)
            val categoryID = pb.readOptString(0x24)
            val contextNulls = pb.readOptionalSinglet(0x25) as ProtoLen?
            val alertSuppressionContextNulls = pb.readOptionalSinglet(0x26) as ProtoLen?
            val threadID = pb.readOptString(0x27)
            val attachmentID = pb.readOptString(0x28)
            val additionalAttachments = pb.readMulti(0x29).map { BulletinAttachment.fromSafePB(it as ProtoBuf) }
            val requiredExpirationDate = pb.readOptDate(0x2a, appleEpoch = false)
            val replyToken = pb.readOptString(0x2b)
            val soundMaximumDuration = pb.readOptDouble(0x2c)
            val soundShouldRepeat = pb.readOptBool(0x2d)
            val soundShouldIgnoreRingerSwitch = pb.readOptBool(0x2e)
            val hasCriticalIcon = pb.readOptBool(0x2f)
            val soundAudioVolume = pb.readOptDouble(0x30)
            val preemptsPresentedAlert = pb.readOptBool(0x31)
            val suppressDelayForwardBulletins = pb.readOptBool(0x32)
            val icon = SectionIcon.fromPB(pb.readOptPB(0x33))
            val containsUpdateIcon = pb.readOptBool(0x34)


            // oh dear
            return Bulletin(
                bulletinID, sectionID, sectionDisplayName, title, subtitle, messageTitle, date,
                attachment?.value, actions, feed, snoozeAction, recordID, publisherBulletinId,
                dismissAction, sectionSubtype, sockPuppetAppBundleID, category, publicationDate,
                includesSound, teamID, context, universalSectionID, alertSuppressionContexts,
                soundAlertType, soundAccountIdentifier, soundToneIdentifier, attachmentType,
                containsUpdatedAttachment, loading, turnsOnDisplay, subsectionIDs, dismissalID,
                attachmentURL, peopleIDs, ignoresQuietMode, categoryID, contextNulls?.value,
                alertSuppressionContextNulls?.value, threadID, attachmentID, additionalAttachments,
                requiredExpirationDate, replyToken, soundMaximumDuration, soundShouldRepeat,
                soundShouldIgnoreRingerSwitch, hasCriticalIcon, soundAudioVolume, preemptsPresentedAlert,
                suppressDelayForwardBulletins, icon, containsUpdateIcon
            )
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        if(bulletinID != null)
            fields[0x02] = listOf(ProtoString(bulletinID))
        if(sectionID != null)
            fields[0x02] = listOf(ProtoString(sectionID))
        if(sectionDisplayName != null)
            fields[0x03] = listOf(ProtoString(sectionDisplayName))
        if(title != null)
            fields[0x04] = listOf(ProtoString(title))
        if(subtitle != null)
            fields[0x05] = listOf(ProtoString(subtitle))
        if(messageTitle != null)
            fields[0x06] = listOf(ProtoString(messageTitle))
        if(date != null)
            fields[0x07] = listOf(ProtoI64(date.toCanonicalTimestamp()))
        if(attachment != null)
            fields[0x08] = listOf(ProtoLen(attachment))
        if(actions.isNotEmpty())
            fields[0x09] = actions.map { ProtoLen(it.renderProtobuf()) }
        if(feed != null)
            fields[0x0a] = listOf(ProtoVarInt(feed))
        if(snoozeAction != null)
            fields[0x0b] = listOf(ProtoLen(snoozeAction.renderProtobuf()))
        if(recordID != null)
            fields[0x0c] = listOf(ProtoString(recordID))
        if(publisherBulletinId != null)
            fields[0x0d] = listOf(ProtoString(publisherBulletinId))
        if(dismissAction != null)
            fields[0x0e] = listOf(ProtoLen(dismissAction.renderProtobuf()))
        if(sectionSubtype != null)
            fields[0x0f] = listOf(ProtoVarInt(sectionSubtype))
        if(sockPuppetAppBundleID != null)
            fields[0x10] = listOf(ProtoString(sockPuppetAppBundleID))
        if(category != null)
            fields[0x11] = listOf(ProtoString(category))
        if(publicationDate != null)
            fields[0x12] = listOf(ProtoI64(publicationDate.toCanonicalTimestamp()))
        if(includesSound != null)
            fields[0x13] = listOf(ProtoVarInt(includesSound))
        if(teamID != null)
            fields[0x14] = listOf(ProtoString(teamID))
        if(context != null)
            fields[0x15] = listOf(ProtoBPList((context as CodableBPListObject).renderAsTopLevelObject()))
        if(universalSectionID != null)
            fields[0x16] = listOf(ProtoString(universalSectionID))
        if(alertSuppressionContexts != null)
            fields[0x17] = listOf(ProtoBPList((alertSuppressionContexts as CodableBPListObject).renderAsTopLevelObject()))
        if(soundAlertType != null)
            fields[0x18] = listOf(ProtoVarInt(soundAlertType))
        if(soundAccountIdentifier != null)
            fields[0x19] = listOf(ProtoString(soundAccountIdentifier))
        if(soundToneIdentifier != null)
            fields[0x1a] = listOf(ProtoString(soundToneIdentifier))
        if(attachmentType != null)
            fields[0x1b] = listOf(ProtoVarInt(attachmentType))
        if(containsUpdatedAttachment != null)
            fields[0x1c] = listOf(ProtoVarInt(containsUpdatedAttachment))
        if(loading != null)
            fields[0x1d] = listOf(ProtoVarInt(loading))
        if(turnsOnDisplay != null)
            fields[0x1e] = listOf(ProtoVarInt(turnsOnDisplay))

        fields[0x1f] = subsectionIDs.map { ProtoString(it) }

        if(dismissalID != null)
            fields[0x20] = listOf(ProtoString(dismissalID))
        if(attachmentURL != null)
            fields[0x21] = listOf(ProtoString(attachmentURL))

        fields[0x22] = peopleIDs.map { ProtoString(it) }

        if(ignoresQuietMode != null)
            fields[0x23] = listOf(ProtoVarInt(ignoresQuietMode))
        if(categoryID != null)
            fields[0x24] = listOf(ProtoString(categoryID))
        if(contextNulls != null)
            fields[0x25] = listOf(ProtoLen(contextNulls))
        if(alertSuppressionContextNulls != null)
            fields[0x26] = listOf(ProtoLen(alertSuppressionContextNulls))
        if(threadID != null)
            fields[0x27] = listOf(ProtoString(threadID))
        if(attachmentID != null)
            fields[0x28] = listOf(ProtoString(attachmentID))

        fields[0x29] = additionalAttachments.map { ProtoLen(it.renderProtobuf()) }

        if(requiredExpirationDate != null)
            fields[0x2a] = listOf(ProtoI64(requiredExpirationDate.toCanonicalTimestamp()))
        if(replyToken != null)
            fields[0x2b] = listOf(ProtoString(replyToken))
        if(soundMaximumDuration != null)
            fields[0x2c] = listOf(ProtoI64(soundMaximumDuration))
        if(soundShouldRepeat != null)
            fields[0x2d] = listOf(ProtoVarInt(soundShouldRepeat))
        if(soundShouldIgnoreRingerSwitch != null)
            fields[0x2e] = listOf(ProtoVarInt(soundShouldIgnoreRingerSwitch))
        if(hasCriticalIcon != null)
            fields[0x2f] = listOf(ProtoVarInt(hasCriticalIcon))
        if(soundAudioVolume != null)
            fields[0x30] = listOf(ProtoI64(soundAudioVolume))
        if(preemptsPresentedAlert != null)
            fields[0x31] = listOf(ProtoVarInt(preemptsPresentedAlert))
        if(suppressDelayForwardBulletins != null)
            fields[0x32] = listOf(ProtoVarInt(suppressDelayForwardBulletins))
        if(icon != null)
            fields[0x33] = listOf(ProtoLen(icon.renderProtobuf()))
        if(containsUpdateIcon != null)
            fields[0x34] = listOf(ProtoVarInt(containsUpdateIcon))

        return ProtoBuf(fields).renderStandalone()
    }
}
