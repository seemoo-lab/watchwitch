package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.alloy.AlloyHandler
import net.rec0de.android.watchwitch.alloy.AlloyMessage

interface AlloyService {
    val handlesTopics: List<String>

    fun acceptsMessageType(msg: AlloyMessage): Boolean

    fun receiveMessage(msg: AlloyMessage, handler: AlloyHandler)
}