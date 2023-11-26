package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.utun.AlloyHandler
import net.rec0de.android.watchwitch.utun.UTunMessage

interface AlloyService {
    val handlesTopics: List<String>

    fun acceptsMessageType(msg: UTunMessage): Boolean

    fun receiveMessage(msg: UTunMessage, handler: AlloyHandler)
}