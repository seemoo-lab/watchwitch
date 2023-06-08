package net.rec0de.android.watchwitch

import java.time.Instant

object NWSCState {

    private val base: Long = microsecondsSinceEpoch()
    private var counter: Int = 0

    fun freshSequenceNumber(): Long {
        val seq = base + counter
        counter += 1
        return seq
    }

    private fun microsecondsSinceEpoch(): Long {
        val instant = Instant.now()
        val microsecondsSinceSecond = instant.nano / 1000
        val microsecondsSinceEpoch = instant.epochSecond * 1000000
        return microsecondsSinceEpoch + microsecondsSinceSecond
    }
}