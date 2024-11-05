package net.rec0de.android.watchwitch.alloy

interface AlloyService {

    // see the servicehandlers package for example service implementations

    // list of alloy topic strings this services wishes to receive
    // e.g. "com.apple.private.alloy.coreduet"
    val handlesTopics: List<String>

    // indicate whether your implementation is capable of handling certain alloy message types
    // only supported types will be received
    // e.g. DataMessage, ProtobufMessage
    fun acceptsMessageType(msg: AlloyMessage): Boolean

    // called whenever a supported message with a topic of interest is received
    // handler is the AlloyHandler of the channel where the message was received
    // you may send replies using handler.send(msg: AlloyMessage)
    // sequence numbers and stream identifiers may be obtained from the AlloyController object
    fun receiveMessage(msg: AlloyMessage, handler: AlloyHandler)

    // called when a watch is connected and at least one channel is open
    // you may perform setup and send initial messages here
    // use AlloyController.getHandlerForChannel or AlloyController.getAnyHandler to obtain a handler
    // that lets you send messages
    fun onWatchConnect() {}
}