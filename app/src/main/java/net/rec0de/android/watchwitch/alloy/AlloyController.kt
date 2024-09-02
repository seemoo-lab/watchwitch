package net.rec0de.android.watchwitch.alloy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.rec0de.android.watchwitch.IdsLogger
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.WatchState
import net.rec0de.android.watchwitch.nwsc.NWSCManager
import net.rec0de.android.watchwitch.servicehandlers.CoreDuet
import net.rec0de.android.watchwitch.servicehandlers.FindMyLocalDevice
import net.rec0de.android.watchwitch.servicehandlers.PreferencesSync
import net.rec0de.android.watchwitch.servicehandlers.Screenshotter
import net.rec0de.android.watchwitch.servicehandlers.health.HealthSync
import net.rec0de.android.watchwitch.servicehandlers.messaging.BulletinDistributorService
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

object AlloyController {

    private var output: DataOutputStream? = null
    private val instanceID = UUID.randomUUID()

    private val remoteAnnouncedChannels = mutableSetOf<String>()
    private val requestingChannels = mutableSetOf<String>()
    private val establishedChannels = mutableSetOf<String>()
    private val handlers = mutableMapOf<String,AlloyHandler>()

    private val serviceNameToLocalUUID = mutableMapOf<String, UUID>()
    private val streamIdAssociations = mutableMapOf<Int, String>()
    private val reverseStreamIdAssociations = mutableMapOf<String, Int>()

    val nextSenderSequence: AtomicInteger = AtomicInteger(0)

    val services: Map<String, AlloyService> = listOf(PreferencesSync, HealthSync, FindMyLocalDevice, BulletinDistributorService, Screenshotter, CoreDuet).flatMap { service -> service.handlesTopics.map { Pair(it, service) } }.toMap()

    fun usingOutput(out: DataOutputStream): AlloyController {
        output = out
        return this
    }

    /**
     * When the ids-control-channel is initialized, both parties send their hellos immediately followed by a bunch of create channel messages
     * Then both parties attempt to open actual connections for (a subset of??) the requested channels
     */
    fun init() {
        WatchState.alloyConnected.set(false)
        val hello = Hello("5", "iPhone OS", "14.8", "18H17", "iPhone10,4", 0)
        hello.compatMinProtocolVersion = 15
        hello.compatMaxProtocolVersion = 16
        hello.serviceMinimumCompatibilityVersion = 10
        hello.capabilityFlags = 0x3ff
        // we re-generate this on every launch which i think causes the watch to treat us more nicely (including topics in message because we don't have mappings yet etc)
        hello.instanceID = instanceID
        hello.deviceID = LongTermStorage.getUTUNDeviceID() ?: UUID.randomUUID()

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
                    val handler = AlloyHandler(fullService, null)
                    NWSCManager.initiateChannelAndForward(fullService, targetPort, handler)
                }
            }
        }
    }

    fun receive(message: ByteArray) {
        IdsLogger.logControl(false, message)
        val msg = UTunControlMessage.parse(message)

        NWSCManager.markControlChannelInitialized()

        Logger.logUTUN("rcv $msg", 0)

        when (msg) {
            is SetupChannel -> setupChannel(msg)
            is CloseChannel -> closeChannel(msg)
            is SetupEncryptedChannel -> setupEncryptedChannel(msg)
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

    private fun setupEncryptedChannel(msg: SetupEncryptedChannel) {
        val fullService = "${msg.account}/${msg.service}/${msg.name}"
        remoteAnnouncedChannels.add(fullService)

        if(!requestingChannels.contains(fullService)) {
            Logger.logUTUN("got request for $fullService, which we did not request - replying", 1)
            val ourUUID = serviceNameToLocalUUID.computeIfAbsent(fullService) { UUID.randomUUID()}
            
            // we don't really know how encrypted channels work since we haven't observed them in use
            // but we CAN send an appropriate reply with random values for the key material
            // (encrypted channels seem to use SRTP in some way shape or form)
            val ssrc = Random.nextInt()
            val keymat = Random.nextBytes(60)
            val reply = SetupEncryptedChannel(msg.protocol, msg.receiverPort, msg.senderPort, ourUUID, msg.senderUUID, msg.account, msg.service, msg.name, ssrc, 0, keymat)
            
            Logger.logUTUN("UTUN snd $reply", 0)
            send(reply.toBytes())
        }
    }

    private fun closeChannel(msg: CloseChannel) {
        val fullService = "${msg.account}/${msg.service}/${msg.name}"
        registerChannelClose(fullService)
    }

    fun registerChannelClose(channel: String) {
        remoteAnnouncedChannels.remove(channel)
        serviceNameToLocalUUID.remove(channel)
        establishedChannels.remove(channel)
        handlers.remove(channel)
    }

    fun registerChannelCreation(channel: String, handler: AlloyHandler) {
        handlers[channel] = handler
        establishedChannels.add(channel)

        // we'll somewhat arbitrarily say that we consider the watch to be connected when we have 3 channels open
        if(establishedChannels.size >= 3 && !WatchState.alloyConnected.getAndSet(true)) {
            // notify service handlers that watch connected
            Thread {
                runBlocking { delay(4000) }
                services.values.toSet().forEach { it.onWatchConnect() }
            }.start()
        }
    }

    fun shouldAcceptConnection(service: String): Boolean {
        // technically there is some mechanism for which side should accept simultaneous connection requests
        // based on the connection UUIDs, but we'll just accept everything we can for now
        return remoteAnnouncedChannels.contains(service) && !establishedChannels.contains(service)
    }

    fun getHandlerForChannel(channel: String): AlloyHandler? {
        return handlers["idstest/localdelivery/$channel"]
    }

    // get handlers for any one of the listed channels in descending order of preference
    fun getHandlerForChannel(channels: List<String>): AlloyHandler? {
        return channels.map { handlers["idstest/localdelivery/$it"] }.firstOrNull { it != null }
    }

    fun getAnyHandler(): AlloyHandler? {
        return handlers.values.firstOrNull()
    }

    fun getFreshStream(topic: String): Int {
        val id = (streamIdAssociations.keys.maxOrNull() ?: 0) + 1
        streamIdAssociations[id] = topic
        reverseStreamIdAssociations[topic] = id
        return id
    }

    fun associateStreamWithTopic(streamID: Int, topic: String, allowOverride: Boolean = false) {
        if(streamIdAssociations.containsKey(streamID)) {
            if(streamIdAssociations[streamID] != topic && !allowOverride)
                throw Exception("Stream ID $streamID is associated with topic ${streamIdAssociations[streamID]} but trying to rebind with $topic")
        }
        streamIdAssociations[streamID] = topic
        reverseStreamIdAssociations[topic] = streamID
    }

    fun resolveStream(streamID: Int): String? {
        return streamIdAssociations[streamID]
    }

    fun resolveTopic(topic: String): Int? {
        return reverseStreamIdAssociations[topic]
    }

    private fun send(message: ByteArray) {
        IdsLogger.logControl(true, message)
        val toWatch = output!!
        toWatch.writeShort(message.size)
        toWatch.write(message)
        toWatch.flush()
    }

    fun close() {
        Logger.logUTUN("Handler closed for ids-control-channel", 1)
    }
}