package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.decoders.bplist.BPInt
import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.bplist.NSDict
import net.rec0de.android.watchwitch.utun.DataMessage
import net.rec0de.android.watchwitch.utun.UTunHandler
import net.rec0de.android.watchwitch.utun.UTunMessage

object CoreDuet : UTunService {
    override val handlesTopics = listOf("com.apple.private.alloy.coreduet")

    override fun acceptsMessageType(msg: UTunMessage) = msg is DataMessage

    override fun receiveMessage(msg: UTunMessage, handler: UTunHandler) {
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