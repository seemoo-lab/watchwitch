package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.WatchState
import net.rec0de.android.watchwitch.alloy.AlloyController
import net.rec0de.android.watchwitch.alloy.AlloyHandler
import net.rec0de.android.watchwitch.alloy.AlloyMessage
import net.rec0de.android.watchwitch.alloy.AlloyService
import net.rec0de.android.watchwitch.alloy.ProtobufMessage
import net.rec0de.android.watchwitch.decoders.bplist.BPArray
import net.rec0de.android.watchwitch.decoders.bplist.BPAsciiString
import net.rec0de.android.watchwitch.decoders.bplist.BPDate
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPInt
import net.rec0de.android.watchwitch.decoders.bplist.BPListObject
import net.rec0de.android.watchwitch.decoders.bplist.BPString
import net.rec0de.android.watchwitch.decoders.bplist.BPTrue
import net.rec0de.android.watchwitch.decoders.bplist.CodableBPListObject
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBPList
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.toAppleTimestamp
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

    override fun onWatchConnect() {
        super.onWatchConnect()
        enableScreenshots()
    }

    private fun enableScreenshots() {
        // try to force-enable screenshots when the watch connects
        val enableScreenshotsMsg = UserDefaultsMessage(
            Date(),
            "com.apple.Carousel",
            listOf(UserDefaultsMsgKey("CSLScreenshotEnabled", BPTrue, true, Date()))
        )

        Logger.logIDS("[nps] trying to enable screenshots: $enableScreenshotsMsg", 0)
        val handler = AlloyController.getHandlerForChannel(listOf("UTunDelivery-Default-Sync-C", "UTunDelivery-Default-Sync-D", "UTunDelivery-Default-Default-C", "UTunDelivery-Default-Default-D", "UTunDelivery-Default-Urgent-C", "UTunDelivery-Default-Urgent-D", "UTunDelivery-Default-DefaultCloud-C", "UTunDelivery-Default-DefaultCloud-D", "UTunDelivery-Default-UrgentCloud-C", "UTunDelivery-Default-UrgentCloud-D"))
        handler?.sendProtobuf(enableScreenshotsMsg.renderProtobuf(), "com.apple.private.alloy.preferencessync", 0, isResponse = false)
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
        val domain: String?, // this is a guess
        val entries: List<FileBackupEntry>
    ) {
        companion object : PBParsable<FileBackupMessage>() {
            override fun fromSafePB(pb: ProtoBuf): FileBackupMessage {
                val domain = pb.readOptString(2)
                val entries = pb.readMulti(3).map { FileBackupEntry.fromSafePB(it as ProtoBuf) }
                return FileBackupMessage(domain, entries)
            }
        }

        override fun toString() = "FileBackupMessage(domain: $domain, $entries)"
    }
    class FileBackupEntry(
        val fileUrl: String,
        val fileData: BPListObject?
    ) {
        companion object : PBParsable<FileBackupEntry>() {
            override fun fromSafePB(pb: ProtoBuf): FileBackupEntry {
                val fileUrl = pb.readOptString(1)!!
                val fileData = (pb.readOptionalSinglet(2) as ProtoBPList?)?.parsed

                return FileBackupEntry(fileUrl, fileData)
            }
        }

        override fun toString() = "FileBackupEntry(url: $fileUrl, data: $fileData)"
    }

    // from NPSServer::handleUserDefaultsBackupMsgData:backupFile:idsGuid: in nanoprefsyncd
    class UserDefaultsBackupMessage(
        val container: String?,
        val key: String?,
        val value: List<UserDefaultsBackupMsgKey>
    ) {
        companion object : PBParsable<UserDefaultsBackupMessage>() {
            override fun fromSafePB(pb: ProtoBuf): UserDefaultsBackupMessage {
                val container = pb.readOptString(1)
                val domain = pb.readOptString(2)
                val keys = pb.readMulti(3).map { UserDefaultsBackupMsgKey.fromSafePB(it as ProtoBuf) }

                return UserDefaultsBackupMessage(container, domain, keys)
            }
        }

        fun renderProtobuf(): ByteArray {
            val fields = mutableMapOf<Int,List<ProtoValue>>()

            if(container != null)
                fields[1] = listOf(ProtoString(container))

            if(key != null)
                fields[2] = listOf(ProtoString(key))

            fields[3] = value.map { ProtoLen(it.renderProtobuf()) }

            return ProtoBuf(fields).renderStandalone()
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

        fun renderProtobuf(): ByteArray {
            val fields = mutableMapOf<Int,List<ProtoValue>>()

            fields[1] = listOf(ProtoI64(timestamp.toAppleTimestamp()))
            fields[2] = listOf(ProtoString(domain))
            fields[3] = keys.map { ProtoLen(it.renderProtobuf()) }

            return ProtoBuf(fields).renderStandalone()
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

        fun renderProtobuf(): ByteArray {
            val fields = mutableMapOf<Int,List<ProtoValue>>()

            fields[1] = listOf(ProtoString(key))

            if(value != null)
                fields[2] = listOf(ProtoLen((value as CodableBPListObject).renderAsTopLevelObject()))
            if(twoWaySync != null)
                fields[3] = listOf(ProtoVarInt(twoWaySync))
            if(timestamp != null)
                fields[4] = listOf(ProtoI64(timestamp.toAppleTimestamp()))

            return ProtoBuf(fields).renderStandalone()
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

        fun renderProtobuf(): ByteArray {
            val fields = mutableMapOf<Int,List<ProtoValue>>()

            fields[1] = listOf(ProtoString(key))

            if(value != null)
                fields[2] = listOf(ProtoLen((value as CodableBPListObject).renderAsTopLevelObject()))

            return ProtoBuf(fields).renderStandalone()
        }

        override fun toString() = "BKey($key: $value)"
    }
}