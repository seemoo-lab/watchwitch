package net.rec0de.android.watchwitch.decoders.bplist

import net.rec0de.android.watchwitch.servicehandlers.health.StatisticsQuantityInfo

object ActivityStatisticsCoder {
    fun decode(data: NSArray): List<StatisticsQuantityInfo> {
        val entries = data.values
        return entries.map { decodeEntry(it as NSDict) }
    }

    private fun decodeEntry(entry: NSDict): StatisticsQuantityInfo {
        val quantityValue = entry.values[BPAsciiString("quantityValue")]!! as BPDict
        val unitKey = quantityValue.values[BPAsciiString("UnitKey")]!! as BPDict
        val unitString = (unitKey.values[BPAsciiString("HKUnitStringKey")] as BPAsciiString).value
        val value = (quantityValue.values[BPAsciiString("ValueKey")]!! as BPReal).value

        val startDate = entry.values[BPAsciiString("startDate")]!! as NSDate
        val endDate = entry.values[BPAsciiString("endDate")]!! as NSDate

        return StatisticsQuantityInfo(startDate.value, endDate.value, unitString, value)
    }
}