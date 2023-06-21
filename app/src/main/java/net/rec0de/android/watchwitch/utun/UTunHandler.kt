package net.rec0de.android.watchwitch.utun

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.hex
import java.io.DataOutputStream

open class UTunHandler(val channel: String, var output: DataOutputStream?) {

    open fun init() {
        UTunController.registerChannelCreation(channel)
        Logger.logUTUN("Creating handler for $channel", 1)
    }

    open fun receive(message: ByteArray) {
        Logger.logUTUN("UTUN rcv for $channel: ${message.hex()}", 1)
    }

    open fun close() {
        Logger.logUTUN("Handler closed for $channel", 1)
    }

    protected fun send(message: ByteArray) {
        //Logger.logUTUN("snd raw ${message.hex()}", 3)
        val toWatch = output!!
        toWatch.writeShort(message.size)
        toWatch.write(message)
        toWatch.flush()
    }
}