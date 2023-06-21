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
    private val establishedChannels = mutableSetOf<String>()
    private val requestingChannels = mutableSetOf<String>()

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

        deliveryChannels.forEachIndexed { i, it ->
            val fullService = "idstest/localdelivery/$it"
            val uuid = UUID.randomUUID()
            serviceNameToLocalUUID[fullService] = uuid

            // code in IDSUTunController::startDataChannelWithDevice:genericConnection:serviceConnectorService:endpoint: suggests 61315 is used for cloud-enabled channels
            val targetPort = if(it.contains("Cloud")) 61315 else 61314

            val setupChan = SetupChannel(6, targetPort, 51314+portIdx, uuid, null, "idstest", "localdelivery", it)
            portIdx += 1
            Logger.logUTUN("snd $setupChan", 1)
            send(setupChan.toBytes())
            requestingChannels.add(fullService)

            // attempt to open an actual connection
            GlobalScope.launch {
                delay((200 + 30*i).toLong())
                // we may have received and accepted an incoming request for this channel in the meantime
                if(!establishedChannels.contains(fullService)) {
                    val handler = UTunHandler(it, null)
                    NWSCManager.initiateChannelAndForward(fullService, targetPort, handler)
                }
            }
        }
    }

    override fun receive(message: ByteArray) {
        val msg = UTunControlMessage.parse(message)

        Logger.logUTUN("rcv $msg", 0)

        when (msg) {
            is SetupChannel -> setupChannel(msg)
            is CloseChannel -> closeChannel(msg)
        }
    }

    private fun setupChannel(msg: SetupChannel) {
        val fullService = "${msg.account}/${msg.service}/${msg.name}"
        remoteAnnouncedChannels.add(fullService)

        if(!requestingChannels.contains(fullService)) {
            Logger.logUTUN("got request for $fullService, which we did not request - replying", 1)
            val ourUUID = serviceNameToLocalUUID.computeIfAbsent(fullService) { UUID.randomUUID()}
            val reply = SetupChannel(msg.protocol, msg.receiverPort, msg.senderPort, ourUUID, msg.senderUUID, msg.account, msg.service, msg.name)
            Logger.logUTUN("UTUN snd $reply", 0)
            send(reply.toBytes())
        }
    }

    private fun closeChannel(msg: CloseChannel) {
        val fullService = "${msg.account}/${msg.service}/${msg.name}"
        remoteAnnouncedChannels.remove(fullService)
        serviceNameToLocalUUID.remove(fullService)
        establishedChannels.remove(fullService)
    }

    fun registerChannelCreation(service: String) {
        establishedChannels.add(service)
    }

    fun shouldAcceptConnection(service: String): Boolean {
        // technically there is some mechanism for which side should accept simultaneous connection requests
        // based on the connection UUIDs, but we'll just accept everything we can for now
        return remoteAnnouncedChannels.contains(service) && !establishedChannels.contains(service)
    }
}