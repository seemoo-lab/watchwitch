package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.bplist.NSDict
import net.rec0de.android.watchwitch.alloy.DataMessage
import net.rec0de.android.watchwitch.alloy.AlloyHandler
import net.rec0de.android.watchwitch.alloy.AlloyMessage
import net.rec0de.android.watchwitch.alloy.AlloyService

object CoreDuet : AlloyService {
    override val handlesTopics = listOf("com.apple.private.alloy.coreduet")

    override fun acceptsMessageType(msg: AlloyMessage) = msg is DataMessage

    override fun receiveMessage(msg: AlloyMessage, handler: AlloyHandler) {
        if(msg !is DataMessage)
            throw Exception("CoreDuet expects DataMessage but got $msg")

        val map = BPListParser().parse(msg.payload) as NSDict
        //val version = map.values[BPInt(0)]
        println(map)
    }

    class CoreDuetMessage(val version: Int, val type: CDMessageType, val maxVersion: Int, val minVersion: Int, val payload: NSDict)

    enum class CDMessageType {
        SYSTEM_DATA, ADMISSION_DATA, FORECAST_DATA, STATISTIC_DATA, DATA_VALUE_REQUEST, ACK, SYNC_REQUEST
    }
}