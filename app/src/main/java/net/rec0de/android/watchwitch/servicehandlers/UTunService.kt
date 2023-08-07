package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.utun.UTunMessage

interface UTunService {
    val name: String

    fun acceptsMessageType(msg: UTunMessage): Boolean

    fun receiveMessage(msg: UTunMessage)
}