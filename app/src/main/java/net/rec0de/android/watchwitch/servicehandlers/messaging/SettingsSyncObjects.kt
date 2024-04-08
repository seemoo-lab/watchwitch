package net.rec0de.android.watchwitch.servicehandlers.messaging

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import java.util.Date

class SetSectionInfoRequest(val sectionInfo: SectionInfo?) {
    override fun toString(): String {
        return "SetSectionInfoRequest($sectionInfo)"
    }

    companion object : PBParsable<SetSectionInfoRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SetSectionInfoRequest {
            val sectionInfo = SectionInfo.fromPB(pb.readOptPB(1))
            return SetSectionInfoRequest(sectionInfo)
        }
    }
}

class RemoveSectionRequest(val sectionID: String?) {
    override fun toString(): String {
        return "RemoveSectionRequest($sectionID)"
    }

    companion object : PBParsable<RemoveSectionRequest>() {
        override fun fromSafePB(pb: ProtoBuf): RemoveSectionRequest {
            val sectionID = pb.readOptString(1)
            return RemoveSectionRequest(sectionID)
        }
    }
}

class SetNotificationsAlertLevelRequest(val level: Int?, val sectionID: String?, val mirror: Boolean?) {
    override fun toString(): String {
        return "SetNotificationsAlertLevelRequest(section $sectionID, level $level, mirror? $mirror)"
    }

    companion object : PBParsable<SetNotificationsAlertLevelRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SetNotificationsAlertLevelRequest {
            val level = pb.readOptShortVarInt(1)
            val sectionID = pb.readOptString(2)
            val mirror = pb.readOptBool(3)
            return SetNotificationsAlertLevelRequest(level, sectionID, mirror)
        }
    }
}

class SetNotificationsGroupingRequest(val grouping: Int?, val sectionID: String?) {
    override fun toString(): String {
        return "SetNotificationsGroupingRequest($grouping, $sectionID)"
    }

    companion object : PBParsable<SetNotificationsGroupingRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SetNotificationsGroupingRequest {
            val level = pb.readOptShortVarInt(1)
            val sectionID = pb.readOptString(2)
            return SetNotificationsGroupingRequest(level, sectionID)
        }
    }
}

class SetNotificationsSoundRequest(val sectionID: String?, val sound: Int?) {
    override fun toString(): String {
        return "SetNotificationsSoundRequest($sectionID, sound $sound)"
    }

    companion object : PBParsable<SetNotificationsSoundRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SetNotificationsSoundRequest {
            val sectionID = pb.readOptString(1)
            val sound = pb.readOptShortVarInt(2)
            return SetNotificationsSoundRequest(sectionID, sound)
        }
    }
}

class SetRemoteGlobalSpokenSettingEnabledRequest(val settingEnabled: Boolean?, val settingDate: Date?) {
    override fun toString(): String {
        return "SetRemoteGlobalSpokenSettingEnabledRequest(enabled? $settingEnabled @ $settingDate)"
    }

    companion object : PBParsable<SetRemoteGlobalSpokenSettingEnabledRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SetRemoteGlobalSpokenSettingEnabledRequest {
            val settingEnabled = pb.readOptBool(1)
            val settingDate = pb.readOptDate(2)
            return SetRemoteGlobalSpokenSettingEnabledRequest(settingEnabled, settingDate)
        }
    }
}

class SetNotificationsCriticalAlertRequest(val criticalAlertSetting: Int?, val sectionID: String?) {
    override fun toString(): String {
        return "SetNotificationsCriticalAlertRequest($criticalAlertSetting, $sectionID)"
    }

    companion object : PBParsable<SetNotificationsCriticalAlertRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SetNotificationsCriticalAlertRequest {
            val criticalAlertSetting = pb.readOptShortVarInt(1)
            val sectionID = pb.readOptString(2)
            return SetNotificationsCriticalAlertRequest(criticalAlertSetting, sectionID)
        }
    }
}

class SetSectionSubtypeParametersIconRequest(val sectionID: String?, val subtypeID: Int?, val defaultSubtype: Boolean?, val icon: SectionIcon?) {
    override fun toString(): String {
        return "SetSectionSubtypeParametersIconRequest(section $sectionID, subtype $subtypeID, default? $defaultSubtype, icon $icon)"
    }

    companion object : PBParsable<SetSectionSubtypeParametersIconRequest>() {
        override fun fromSafePB(pb: ProtoBuf): SetSectionSubtypeParametersIconRequest {
            val sectionID = pb.readOptString(1)
            val subtypeID = pb.readOptShortVarInt(2)
            val defaultSubtype = pb.readOptBool(3)
            val icon = SectionIcon.fromPB(pb.readOptPB(4))
            return SetSectionSubtypeParametersIconRequest(sectionID, subtypeID, defaultSubtype, icon)
        }
    }
}

class AckInitialSequenceNumberRequest(val assert: Boolean?, val sessionIdentifier: ByteArray?, val sessionState: Int?) {
    override fun toString(): String {
        return "AckInitialSequenceNumberRequest(sessionID ${sessionIdentifier?.hex()}, state $sessionState, assert? $assert)"
    }

    companion object : PBParsable<AckInitialSequenceNumberRequest>() {
        override fun fromSafePB(pb: ProtoBuf): AckInitialSequenceNumberRequest {
            val assert = pb.readOptBool(1)
            val sessionIdentifier = (pb.readOptionalSinglet(2) as ProtoLen?)?.value
            val sessionState = pb.readOptShortVarInt(3)
            return AckInitialSequenceNumberRequest(assert, sessionIdentifier, sessionState)
        }
    }

    fun renderProtobuf(): ByteArray{
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(assert != null)
            fields[1] = listOf(ProtoVarInt(assert))
        if(sessionIdentifier != null)
            fields[2] = listOf(ProtoLen(sessionIdentifier))
        if(sessionState != null)
            fields[3] = listOf(ProtoVarInt(sessionState))
        return ProtoBuf(fields).renderStandalone()
    }
}

class SectionInfo(
    val sectionID: String?,
    val subsectionID: String?,
    val sectionType: Int?,
    val sectionCategory: Int?,
    val suppressFromSettings: Boolean?,
    val showsInNotificationCenter: Boolean?,
    val showsInLockScreen: Boolean?,
    val showsOnExternalDevices: Boolean?,
    val notificationCenterLimit: Int?,
    val pushSettings: Int?,
    val alertType: Int?,
    val showsMessagePreview: Boolean?,
    val allowsNotifications: Boolean?,
    val suppressedSettings: Int?,
    val displayName: String?,
    val displaysCriticalBulletinsLegacy: Boolean?,
    val subsections: List<SectionInfo>,
    val subsectionPriority: Int?,
    val version: Int?,
    val factorySectionID: String?,
    val universalSectionID: String?,
    val sectionIcon: SectionIcon?,
    val iconsStripped: Boolean?,
    val phoneAllowsNotifications: Boolean?,
    val criticalAlertSetting: Boolean?,
    val groupingSetting: Int?,
    val excludeFromBulletinBoard: Boolean?,
    val authorizationStatus: Int?,
    val phoneAuthorizationStatus: Int?,
    val lockScreenSetting: Int?,
    val notificationCenterSetting: Int?,
    val spokenNotificationSetting: Int?,
    val watchSectionID: String?
) {
    override fun toString(): String {
        var res = "SectionInfo("

        if(sectionID != null)
            res += " sectionID: $sectionID"
        if(subsectionID != null)
            res += " subsectionID: $subsectionID"
        if(sectionType != null)
            res += " sectionType: $sectionType"
        if(sectionCategory != null)
            res += " sectionCategory: $sectionCategory"
        if(suppressFromSettings != null)
            res += " suppressFromSettings: $suppressFromSettings"
        if(showsInNotificationCenter != null)
            res += " showsInNotificationCenter: $showsInNotificationCenter"
        if(showsInLockScreen != null)
            res += " showsInLockScreen: $showsInLockScreen"
        if(showsOnExternalDevices != null)
            res += " showsOnExternalDevices: $showsOnExternalDevices"
        if(notificationCenterLimit != null)
            res += " notificationCenterLimit: $notificationCenterLimit"
        if(pushSettings != null)
            res += " pushSettings: $pushSettings"
        if(alertType != null)
            res += " alertType: $alertType"
        if(showsMessagePreview != null)
            res += " showsMessagePreview: $showsMessagePreview"
        if(allowsNotifications != null)
            res += " allowsNotifications: $allowsNotifications"
        if(suppressedSettings != null)
            res += " suppressedSettings: $suppressedSettings"
        if(displayName != null)
            res += " displayName: $displayName"
        if(displaysCriticalBulletinsLegacy != null)
            res += " displaysCriticalBulletinsLegacy: $displaysCriticalBulletinsLegacy"
        if(subsections.isNotEmpty())
            res += "subsections: ${subsections.joinToString(", ")}"
        if(subsectionPriority != null)
            res += " subsectionPriority: $subsectionPriority"
        if(version != null)
            res += " version: $version"
        if(factorySectionID != null)
            res += " factorySectionID: $factorySectionID"
        if(universalSectionID != null)
            res += " universalSectionID: $universalSectionID"
        if(sectionIcon != null)
            res += " sectionIcon: $sectionIcon"
        if(iconsStripped != null)
            res += " iconsStripped: $iconsStripped"
        if(phoneAllowsNotifications != null)
            res += " phoneAllowsNotifications: $phoneAllowsNotifications"
        if(criticalAlertSetting != null)
            res += " criticalAlertSetting: $criticalAlertSetting"
        if(groupingSetting != null)
            res += " groupingSetting: $groupingSetting"
        if(excludeFromBulletinBoard != null)
            res += " excludeFromBulletinBoard: $excludeFromBulletinBoard"
        if(authorizationStatus != null)
            res += " authorizationStatus: $authorizationStatus"
        if(phoneAuthorizationStatus != null)
            res += " phoneAuthorizationStatus: $phoneAuthorizationStatus"
        if(lockScreenSetting != null)
            res += " lockScreenSetting: $lockScreenSetting"
        if(notificationCenterSetting != null)
            res += " notificationCenterSetting: $notificationCenterSetting"
        if(spokenNotificationSetting != null)
            res += " spokenNotificationSetting: $spokenNotificationSetting"
        if(watchSectionID != null)
            res += " watchSectionID: $watchSectionID"

        res += ")"
        return res
    }

    companion object : PBParsable<SectionInfo>() {
        override fun fromSafePB(pb: ProtoBuf): SectionInfo {
            val sectionID = pb.readOptString(1)
            val subsectionID = pb.readOptString(2)
            val sectionType = pb.readOptShortVarInt(3)
            val sectionCategory = pb.readOptShortVarInt(4)
            val suppressFromSettings = pb.readOptBool(5)
            val showsInNotificationCenter = pb.readOptBool(6)
            val showsInLockScreen = pb.readOptBool(7)
            val showsOnExternalDevices = pb.readOptBool(8)
            val notificationCenterLimit = pb.readOptShortVarInt(9)
            val pushSettings = pb.readOptShortVarInt(0x0a)
            val alertType = pb.readOptShortVarInt(0x0b)
            val showsMessagePreview = pb.readOptBool(0x0c)
            val allowsNotifications = pb.readOptBool(0x0d)
            val suppressedSettings = pb.readOptShortVarInt(0x0e)
            val displayName = pb.readOptString(0x0f)
            val displaysCriticalBulletinsLegacy = pb.readOptBool(0x10)
            val subsections = pb.readMulti(0x11).map { fromSafePB(it as ProtoBuf) }
            val subsectionPriority = pb.readOptShortVarInt(0x12)
            val version = pb.readOptShortVarInt(0x13)
            val factorySectionID = pb.readOptString(0x14)
            val universalSectionID = pb.readOptString(0x15)
            val sectionIcon = SectionIcon.fromPB(pb.readOptPB(0x16))
            val iconsStripped = pb.readOptBool(0x17)
            val phoneAllowsNotifications = pb.readOptBool(0x18)
            val criticalAlertSetting = pb.readOptBool(0x19)
            val groupingSetting = pb.readOptShortVarInt(0x1a)
            val excludeFromBulletinBoard = pb.readOptBool(0x1b)
            val authorizationStatus = pb.readOptShortVarInt(0x1c)
            val phoneAuthorizationStatus = pb.readOptShortVarInt(0x1d)
            val lockScreenSetting = pb.readOptShortVarInt(0x1e)
            val notificationCenterSetting = pb.readOptShortVarInt(0x1f)
            val spokenNotificationSetting = pb.readOptShortVarInt(0x20)
            val watchSectionID = pb.readOptString(0x21)

            return SectionInfo(sectionID, subsectionID, sectionType, sectionCategory, suppressFromSettings, showsInNotificationCenter, showsInLockScreen, showsOnExternalDevices, notificationCenterLimit, pushSettings, alertType, showsMessagePreview, allowsNotifications, suppressedSettings, displayName, displaysCriticalBulletinsLegacy, subsections, subsectionPriority, version, factorySectionID, universalSectionID, sectionIcon, iconsStripped, phoneAllowsNotifications, criticalAlertSetting, groupingSetting, excludeFromBulletinBoard, authorizationStatus, phoneAuthorizationStatus, lockScreenSetting, notificationCenterSetting, spokenNotificationSetting, watchSectionID)
        }
    }
}
