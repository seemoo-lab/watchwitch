package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.decoders.bplist.BPListObject
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBPList
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.utun.ProtobufMessage
import net.rec0de.android.watchwitch.utun.UTunMessage
import java.util.Date

object PreferencesSync : UTunService {
    override val name = "com.apple.private.alloy.preferencessync"

    override fun acceptsMessageType(msg: UTunMessage) = msg is ProtobufMessage

    override fun receiveMessage(msg: UTunMessage) {
        if(msg !is ProtobufMessage)
            throw Exception("PreferencesSync expects ProtobufMessage but got $msg")

        val fields = ProtobufParser().parse(msg.payload).value
        println(fields)
        val timestamp = (fields[1]?.first() as ProtoI64?)?.asDate()
        val bundleID = (fields[2]!!.first() as ProtoString).value
        val preferences = fields[3]!!

        val records = preferences.map {
            it as ProtoBuf
            val entries = it.value
            val key = (entries[1]!!.first() as ProtoString).value
            val date = if(entries.containsKey(4)) (entries[4]!!.first() as ProtoI64).asDate() else null
            val protobuf = entries[3]?.first() as ProtoBuf?
            val bplist = (entries[2]?.first() as ProtoBPList?)?.parsed
            PreferenceRecord(key, date, bplist, protobuf)
        }

        println("rcv preferencessync: $timestamp, $bundleID, $records")
    }

    class PreferenceRecord(val key: String, val time: Date?, val bplist: BPListObject?, val protobuf: ProtoBuf?) {
        override fun toString() = "$key: $protobuf / $bplist @ $time"
    }
}