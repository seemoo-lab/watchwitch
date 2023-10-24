package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.utun.UTunHandler
import net.rec0de.android.watchwitch.utun.UTunMessage

interface UTunService {
    val handlesTopics: List<String>

    fun acceptsMessageType(msg: UTunMessage): Boolean

    fun receiveMessage(msg: UTunMessage, handler: UTunHandler)
}