package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.decoders.bplist.BPListObject
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBPList
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.utun.ProtobufMessage
import net.rec0de.android.watchwitch.utun.UTunHandler
import net.rec0de.android.watchwitch.utun.UTunMessage
import java.util.Date

object PreferencesSync : UTunService {
    override val name = "com.apple.private.alloy.preferencessync"

    override fun acceptsMessageType(msg: UTunMessage) = msg is ProtobufMessage

    override fun receiveMessage(msg: UTunMessage, handler: UTunHandler) {
        if(msg !is ProtobufMessage)
            throw Exception("PreferencesSync expects ProtobufMessage but got $msg")

        val parsed = ProtobufParser().parse(msg.payload)
        println(parsed)
        val timestamp = parsed.readOptDate(1)
        val bundleID = parsed.readOptString(2)!!
        val preferences = parsed.readMulti(3).map { (it as ProtoLen).asProtoBuf() }

        val records = preferences.map {
            val key = it.readOptString(1)!!
            val date = it.readOptDate(4)
            val protobuf = it.readOptionalSinglet(3)
            val bplist = (it.readOptionalSinglet(2) as ProtoBPList?)?.parsed
            PreferenceRecord(key, date, bplist, protobuf)
        }

        println("rcv preferencessync: $timestamp, $bundleID, $records")
    }

    class PreferenceRecord(val key: String, val time: Date?, val bplist: BPListObject?, val protobuf: ProtoValue?) {
        override fun toString() = "$key: $protobuf / $bplist @ $time"
    }
}