package net.rec0de.android.watchwitch.utun

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.nwsc.NWSCManager
import java.io.DataOutputStream
import java.util.UUID

object UTunController : UTunHandler("ids-control-channel", null) {

    private val remoteAnnouncedChannels = mutableSetOf<String>()

    private val serviceNameToLocalUUID = mutableMapOf<String, UUID>()

    fun usingOutput(out: DataOutputStream): UTunController {
        output = out
        return this
    }

    /**
     * When the ids-control-channel is initialized, both parties send their hellos immediately followed by a bunch of create channel messages
     * Then both parties attempt to open actual connections for (a subset of??) the requested channels
     */
    override fun init() {
        val hello = Hello("5", "iPhone OS", "14.8", "18H17", "iPhone10,4", 0)
        hello.compatMinProtocolVersion = 15
        hello.compatMaxProtocolVersion = 16
        hello.serviceMinimumCompatibilityVersion = 10
        hello.capabilityFlags = 0x3ff
        hello.instanceID = UUID.fromString("dc7a651b-0230-4430-95da-bcd0b7e45737")
        hello.deviceID = UUID.fromString("54767e20-5a60-4e84-9d81-130568f258ce")

        Logger.logUTUN("snd $hello", 1)
        send(hello.toBytes())

        val deliveryChannels = listOf("UTunDelivery-Default-Default-C", "UTunDelivery-Default-Default-D", "UTunDelivery-Default-DefaultCloud-C", "UTunDelivery-Default-DefaultCloud-D", "UTunDelivery-Default-Sync-C", "UTunDelivery-Default-Sync-D", "UTunDelivery-Default-Urgent-C", "UTunDelivery-Default-Urgent-D", "UTunDelivery-Default-UrgentCloud-C", "UTunDelivery-Default-UrgentCloud-D")
        var portIdx = 0
        val targetPort = 61314 // there's no obvious pattern for when 61314 is used vs 61315 - we'll just try 61314 for now
        deliveryChannels.forEach {
            val uuid = UUID.randomUUID()
            serviceNameToLocalUUID["idstest/localdelivery/$it"] = uuid
            val setupChan = SetupChannel(6, targetPort, 51314+portIdx, uuid, null, "idstest", "localdelivery", it)
            portIdx += 1
            Logger.logUTUN("snd $setupChan", 1)
            send(setupChan.toBytes())
        }

        deliveryChannels.forEachIndexed { i, service ->
            // attempt to open an actual connection
            GlobalScope.launch {
                delay((200 + 30*i).toLong())
                val handler = UTunHandler(service, null)
                NWSCManager.initiateChannelAndForward("idstest/localdelivery/$service", 61314, handler)
            }
        }
    }

    override fun receive(message: ByteArray) {
        val msg = UTunControlMessage.parse(message)

        Logger.logUTUN("rcv $msg", 0)

        when (msg) {
            is SetupChannel -> setupChannel(msg)
        }
    }

    private fun setupChannel(msg: SetupChannel) {
        val fullService = "${msg.account}/${msg.service}/${msg.name}"
        remoteAnnouncedChannels.add(fullService)

        /*val ourUUID = serviceNameToLocalUUID.computeIfAbsent("${msg.account}/${msg.service}/${msg.name}") { UUID.randomUUID()}
        val reply = SetupChannel(msg.proto, msg.receiverPort, msg.senderPort, ourUUID, msg.senderUUID, msg.account, msg.service, msg.name)

        Logger.logUTUN("snd $reply", 0)
        send(reply.toBytes())*/
    }

    fun shouldAcceptConnection(service: String): Boolean {
        // we're pretending to be an unlocked iPhone, in which case we reject (most?) requests for lower data protection class channels
        return remoteAnnouncedChannels.contains(service) && !service.endsWith("-D")
    }
}