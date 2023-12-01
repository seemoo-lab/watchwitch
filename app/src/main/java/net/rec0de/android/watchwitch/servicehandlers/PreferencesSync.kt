package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.WatchState
import net.rec0de.android.watchwitch.decoders.bplist.BPArray
import net.rec0de.android.watchwitch.decoders.bplist.BPAsciiString
import net.rec0de.android.watchwitch.decoders.bplist.BPDate
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPInt
import net.rec0de.android.watchwitch.decoders.bplist.BPListObject
import net.rec0de.android.watchwitch.decoders.bplist.BPString
import net.rec0de.android.watchwitch.decoders.bplist.BPTrue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBPList
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.servicehandlers.health.PBParsable
import net.rec0de.android.watchwitch.utun.ProtobufMessage
import net.rec0de.android.watchwitch.utun.AlloyHandler
import net.rec0de.android.watchwitch.utun.AlloyMessage
import java.util.Date

object PreferencesSync : AlloyService {
    override val handlesTopics = listOf("com.apple.private.alloy.preferencessync", "com.apple.private.alloy.preferencessync.pairedsync")

    override fun acceptsMessageType(msg: AlloyMessage) = msg is ProtobufMessage

    override fun receiveMessage(msg: AlloyMessage, handler: AlloyHandler) {
        if(msg !is ProtobufMessage)
            throw Exception("PreferencesSync expects ProtobufMessage but got $msg")

        val parsed = ProtobufParser().parse(msg.payload)

        // based on NPSServer::initCore: in nanoprefsyncd
        when(msg.type) {
            0 -> handleUserDefaultMessage(parsed, handler)
            2 -> handleUserDefaultBackupMessage(parsed, handler)
            3 -> handleFileBackupMessage(parsed, handler)
            else -> Logger.logIDS("[nps] unknown pref sync message type ${msg.type}", 0)
        }
    }

    private fun handleUserDefaultMessage(pb: ProtoBuf, handler: AlloyHandler) {
        val msg = UserDefaultsMessage.fromSafePB(pb)
        Logger.logIDS("[nps] received $msg", 1)
    }

    private fun handleUserDefaultBackupMessage(pb: ProtoBuf, handler: AlloyHandler) {
        val msg = UserDefaultsBackupMessage.fromSafePB(pb)

        when(msg.key) {
            "com.apple.Carousel" -> handleCarouselUpdate(msg.value)
            "com.apple.mobiletimerd" -> handleMobileTimerUpdate(msg.value)
            else -> Logger.logIDS("[nps] received $msg", 1)
        }
    }

    private fun handleFileBackupMessage(pb: ProtoBuf, handler: AlloyHandler) {
        val msg = FileBackupMessage.fromSafePB(pb)
        Logger.logIDS("[nps] received $msg", 1)
    }


    private fun handleCarouselUpdate(changes: List<UserDefaultsBackupMsgKey>) {
        changes.forEach {
            when(it.key) {
                "MRUFullScreenApplications" -> {
                    val openApps = (it.value as BPArray).values.map { app -> (app as BPAsciiString).value }
                    WatchState.openApps.postValue(openApps)
                    Logger.logIDS("[nps] open apps: $openApps", 0)
                }
                "SBRingerMuted" -> {
                    val muted = it.value is BPTrue
                    WatchState.ringerMuted.postValue(if(muted) WatchState.TriState.TRUE else WatchState.TriState.FALSE)
                    Logger.logIDS("[nps] muted: $muted", 0)
                }
                else -> Logger.logIDS("[nps] received carousel: $it", 0)
            }
        }
    }

    private fun handleMobileTimerUpdate(changes: List<UserDefaultsBackupMsgKey>) {
        changes.forEach {
            when(it.key) {
                "MTAlarmModifiedDate" -> {
                    val modified = (it.value as BPDate).asDate()
                    Logger.logIDS("[nps] alarm modified: $modified", 0)
                }
                "MTAlarms" -> {
                    val alarmsArray = (it.value as BPDict).values[BPAsciiString("MTAlarms")]!! as BPArray
                    val alarms = alarmsArray.values.map { alarm -> (alarm as BPDict).values[BPAsciiString("\$MTAlarm")]!! as BPDict }

                    alarms.forEach { alarm ->
                        val values = alarm.values
                        val hour = (values[BPAsciiString("MTAlarmHour")] as BPInt).value.toInt()
                        val minute = (values[BPAsciiString("MTAlarmMinute")] as BPInt).value.toInt()
                        val enabled = values[BPAsciiString("MTAlarmEnabled")] is BPTrue
                        val id = (values[BPAsciiString("MTAlarmID")] as BPString).value
                        val title = (values[BPAsciiString("MTAlarmTitle")] as BPString?)?.value

                        WatchState.setAlarm(id, WatchState.Alarm(hour, minute, enabled, title))
                        Logger.logIDS("[nps] alarm at $hour:$minute, enabled? $enabled $title", 0)
                    }
                }
                else -> Logger.logIDS("[nps] received mobiletimerd: $it", 0)
            }
        }
    }

    // from NPSFileBackupMsg::readFrom: in nanoprefsyncd
    class FileBackupMessage(
        val fileUrl: BPListObject,
        val fileData: BPListObject
    ) {
        companion object : PBParsable<FileBackupMessage>() {
            override fun fromSafePB(pb: ProtoBuf): FileBackupMessage {
                val fileUrl = (pb.readAssertedSinglet(1) as ProtoBPList).parsed // NSSet of NSUrl
                val fileData = (pb.readAssertedSinglet(2) as ProtoBPList).parsed

                return FileBackupMessage(fileUrl, fileData)
            }
        }

        override fun toString() = "FileBackupMsg(urls: $fileUrl, data: $fileData)"
    }

    // from NPSServer::handleUserDefaultsBackupMsgData:backupFile:idsGuid: in nanoprefsyncd
    class UserDefaultsBackupMessage(
        val container: String?,
        val key: String,
        val value: List<UserDefaultsBackupMsgKey>
    ) {
        companion object : PBParsable<UserDefaultsBackupMessage>() {
            override fun fromSafePB(pb: ProtoBuf): UserDefaultsBackupMessage {
                val container = pb.readOptString(1)
                val domain = pb.readOptString(2)!!
                val keys = pb.readMulti(3).map { UserDefaultsBackupMsgKey.fromSafePB(it as ProtoBuf) }

                return UserDefaultsBackupMessage(container, domain, keys)
            }
        }

        override fun toString() = "UserDefaultsBackupMsg(container: $container, $key: $value)"
    }

    // from NPSServer::handleUserDefaultsMsgData:backupFile:idsGuid: in nanoprefsyncd
    class UserDefaultsMessage(
        val timestamp: Date,
        val domain: String,
        val keys: List<UserDefaultsMsgKey>
    ) {
        companion object : PBParsable<UserDefaultsMessage>() {
            override fun fromSafePB(pb: ProtoBuf): UserDefaultsMessage {
                val timestamp = pb.readOptDate(1)!!
                val domain = pb.readOptString(2)!!
                val keys = pb.readMulti(3).map { UserDefaultsMsgKey.fromSafePB(it as ProtoBuf) }

                return UserDefaultsMessage(timestamp, domain, keys)
            }
        }

        override fun toString() = "UserDefaultsMsg($timestamp $domain: $keys)"
    }

    class UserDefaultsMsgKey(
        val key: String,
        val value: BPListObject?,
        val twoWaySync: Boolean?,
        val timestamp: Date?
    ) {
        companion object : PBParsable<UserDefaultsMsgKey>() {
            override fun fromSafePB(pb: ProtoBuf): UserDefaultsMsgKey {
                val key = pb.readOptString(1)!!
                val value = (pb.readOptionalSinglet(2) as ProtoBPList?)?.parsed
                val twoWaySync = pb.readOptBool(3)
                val timestamp = pb.readOptDate(4)
                return UserDefaultsMsgKey(key, value, twoWaySync, timestamp)
            }
        }

        override fun toString() = "Key($timestamp, tws? $twoWaySync, $key: $value)"
    }

    class UserDefaultsBackupMsgKey(
        val key: String,
        val value: BPListObject?
    ) {
        companion object : PBParsable<UserDefaultsBackupMsgKey>() {
            override fun fromSafePB(pb: ProtoBuf): UserDefaultsBackupMsgKey {
                val key = pb.readOptString(1)!!
                val value = (pb.readOptionalSinglet(2) as ProtoBPList?)?.parsed
                return UserDefaultsBackupMsgKey(key, value)
            }
        }

        override fun toString() = "BKey($key: $value)"
    }
}