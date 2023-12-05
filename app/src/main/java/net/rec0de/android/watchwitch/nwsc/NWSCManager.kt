package net.rec0de.android.watchwitch.nwsc

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.hex
import net.rec0de.android.watchwitch.alloy.AlloyController
import net.rec0de.android.watchwitch.alloy.AlloyHandler
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object NWSCManager {

    private var requestedPubkey = AtomicBoolean(false)
    private var gotPubkey = false
    private var requestedIdsChannel = AtomicBoolean(false)
    private var gotIdsChannel = false
    private var idsHandlerJob: Job? = null

    private val localPrivateKey: Ed25519PrivateKeyParameters
    val localPublicKey: Ed25519PublicKeyParameters
    private lateinit var remotePubKey: ByteArray

    private var base: Long = microsecondsSinceEpoch()
    private var counter: AtomicInteger = AtomicInteger(0)

    private val waitingForPubkeyQueue = mutableListOf<Triple<NWSCRequest, DataInputStream, DataOutputStream>>()
    private val queueLock = Mutex()
    private val ccInitLock = Mutex(locked = true)
    private var ccInitialized = false

    init {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = gen.generateKeyPair()

        localPrivateKey = keyPair.private as Ed25519PrivateKeyParameters
        localPublicKey = keyPair.public as Ed25519PublicKeyParameters
    }

    fun reset() {
        gotIdsChannel = false
        requestedIdsChannel.set(false)
        requestedPubkey.set(false)
        gotPubkey = false

        if(idsHandlerJob != null) {
            idsHandlerJob!!.cancel("resetting connection")
            idsHandlerJob = null
        }

        base = microsecondsSinceEpoch()
        counter.set(0)

        ccInitialized = false
    }

    @Synchronized
    fun markControlChannelInitialized() {
        if(!ccInitialized) {
            ccInitialized = true
            ccInitLock.unlock()
        }
    }

    suspend fun readFromSocket(fromWatch: DataInputStream, toWatch: DataOutputStream, port: Int) {
        // receive NWSC start request
        // see libnetwork.dylib, _nw_service_connector_create_initial_payload_for_request
        val reqLen = withContext(Dispatchers.IO) {
            fromWatch.readUnsignedShort()
        }
        Logger.logIDS("NWSC rcv trying to read $reqLen bytes", 10)

        val request = ByteArray(reqLen)
        withContext(Dispatchers.IO) {
            fromWatch.readFully(request, 0, request.size)
        }

        Logger.logIDS("NWSC rcv req ${request.hex()}", 3)

        val packet = NWSCPacket.parseRequest(request)

        if(packet.port != port)
            throw Exception("Got NWSC request for port ${packet.port} on port $port")

        handleRequest(packet, fromWatch, toWatch)
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun handleRequest(request: NWSCRequest, fromWatch: DataInputStream, toWatch: DataOutputStream) {
        // alright, here's what we do:
        // 1. if the request is a pubkey request, we immediately answer
        // 2. if we do not have the remote pubkey, we request it
        // 3. if we have the key, we check the signature
        // 4. if it is a control channel request, we accept
        if(request is NWSCPubkeyRequest) {
            handlePubkeyRequest(toWatch)
            close(fromWatch, toWatch)
        }
        else if(!gotPubkey) {
            if(!requestedPubkey.getAndSet(true)) // if requestedPubkey is already true, we re-set to true and do not enter the branch. if it is false, we set to true and request pubkey
                GlobalScope.launch{ requestPubkey() } // using GlobalScope to request pubkey in a thread-like way without waiting for completion
            Logger.logIDS("NWSC rcv $request before pubkey ready, enqueueing", 2)
            queueLock.lock()
            waitingForPubkeyQueue.add(Triple(request, fromWatch, toWatch))
            queueLock.unlock()
        }
        else {
            val verified = request.verify(remotePubKey)
            if(!verified)
                throw Exception("Signature verification failed for NWSC request $request")
            handleServiceRequest(request as NWSCServiceRequest, fromWatch, toWatch)
        }
    }

    fun tryRequestIdscc() {
        if(!gotIdsChannel && !requestedIdsChannel.getAndSet(true))
            GlobalScope.launch { requestIdsChannel() }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun handleServiceRequest(request: NWSCServiceRequest, fromWatch: DataInputStream, toWatch: DataOutputStream) {
        Logger.logIDS("NWSC rcv $request", 1)
        if(request.service == "ids-control-channel") {
            accept(toWatch)
            Logger.logIDS("register IDS control", 0)
            registerIdsControlChannel(fromWatch, toWatch)
        }
        else if(!gotIdsChannel) {
            Logger.logIDS("no control channel, rejecting", 1)
            reject(toWatch)
            close(fromWatch, toWatch)
            tryRequestIdscc()
        }
        else {
            // ensure that initialization of the control channel is complete
            ccInitLock.lock()
            ccInitLock.unlock()

            if(AlloyController.shouldAcceptConnection(request.service)) {
                Logger.logIDS("Accepting $request", 1)
                accept(toWatch)
                GlobalScope.launch { createHandlerAndForward(fromWatch, toWatch, request.service, false) }
            }
            else {
                Logger.logIDS("Rejecting $request, unexpected channel or unwanted DPC", 1)
                reject(toWatch, false)
                close(fromWatch, toWatch)
            }

        }
    }

    private fun handlePubkeyRequest(toWatch: DataOutputStream) {
        Logger.logIDS("NWSC rcv pubkey req", 3)

        // send feedback not accepting connection but including our pubkey
        val payload = NWSCFeedback.fresh(0x00u).toByteArray()
        Logger.logIDS("NWSC snd pubkey", 3)

        toWatch.write(payload)
        toWatch.flush()
    }

    private suspend fun processQueued() {
        queueLock.lock()
        waitingForPubkeyQueue.forEach {
            handleRequest(it.first, it.second, it.third)
        }
        waitingForPubkeyQueue.clear()
        queueLock.unlock()
    }

    private fun requestIdsChannel() {
        Logger.logIDS("initiating control channel", 1)
        val port = 61315
        val socket = Socket(
            LongTermStorage.getAddress(LongTermStorage.REMOTE_ADDRESS_CLASS_C),
            port
        )

        val toWatch = DataOutputStream(socket.getOutputStream())
        val fromWatch = DataInputStream(socket.getInputStream())

        val req = NWSCServiceRequest.fresh(port, "ids-control-channel")
        req.sign(localPrivateKey)
        val payload = req.toByteArray()

        Logger.logIDS("NWSC snd idscc request", 2)

        // send request and receive response buffer
        toWatch.write(payload)
        toWatch.flush()
        val ackLen = fromWatch.readUnsignedShort()
        val ack = ByteArray(ackLen)
        fromWatch.readFully(ack, 0, ack.size)

        // parse response feedback
        val feedback = NWSCPacket.parseFeedback(ack)

        // if our channel was accepted
        if(feedback.flags.toUInt() == 0x8000u) {
            Logger.logIDS("register IDS control", 0)
            registerIdsControlChannel(fromWatch, toWatch)
            Logger.logIDS("NWSC rcv idscc request accepted", 2)
        }
        else {
            requestedIdsChannel.set(false)
            Logger.logIDS("NWSC rcv idscc request rejected", 2)
            close(fromWatch, toWatch)
        }
    }

    private suspend fun requestPubkey() {
        val port = 61315

        val streams = withContext(Dispatchers.IO) {
            val socket = Socket(
                LongTermStorage.getAddress(LongTermStorage.REMOTE_ADDRESS_CLASS_C), // does it matter which address we use here?
                port
            )
            val toWatch = DataOutputStream(socket.getOutputStream())
            val fromWatch = DataInputStream(socket.getInputStream())
            Pair(toWatch, fromWatch)
        }

        val toWatch = streams.first
        val fromWatch = streams.second

        val req = NWSCPubkeyRequest.fresh(port)
        req.sign(localPrivateKey)
        val payload = req.toByteArray()

        Logger.logIDS("NWSC snd pubkey req", 2)

        // send request and receive response buffer
        val ackLen = withContext(Dispatchers.IO) {
            toWatch.write(payload)
            toWatch.flush()
            fromWatch.readUnsignedShort()
        }

        val ack = ByteArray(ackLen)
        withContext(Dispatchers.IO) {
            fromWatch.readFully(ack, 0, ack.size)
        }

        // parse response feedback
        val feedback = NWSCPacket.parseFeedback(ack)
        remotePubKey = feedback.pubkey
        gotPubkey = true

        Logger.logIDS("NWSC rcv pubkey ${feedback.pubkey.hex()}", 2)
        close(fromWatch, toWatch)

        processQueued()
    }

    private fun reject(toWatch: DataOutputStream, basedOnPolicy: Boolean = false) {
        // send feedback rejecting connection (flag 0x40, reject based on policy)
        val flags = if(basedOnPolicy) 0x4000u else 0x0000u
        val ack = NWSCFeedback.fresh(flags.toUShort())
        val payload = ack.toByteArray()
        Logger.logIDS("NWSC snd reject with flags ${flags.toString(16)}", 3)

        toWatch.write(payload)
        toWatch.flush()
    }

    private fun accept(toWatch: DataOutputStream) {
        val ack = NWSCFeedback.fresh(0x8000u)
        val payload = ack.toByteArray()
        Logger.logIDS("NWSC snd accept", 3)

        toWatch.write(payload)
        toWatch.flush()
    }

    private fun close(fromWatch: DataInputStream, toWatch: DataOutputStream) {
        toWatch.close()
        fromWatch.close()
    }

    private fun registerIdsControlChannel(fromWatch: DataInputStream, toWatch: DataOutputStream) {
        gotIdsChannel = true
        if(idsHandlerJob != null)
            idsHandlerJob!!.cancel("got new ids control channel, canceling old handler")
        idsHandlerJob = GlobalScope.launch { forwardToUTunController(fromWatch, toWatch) }
    }

    private fun forwardToUTunController(fromWatch: DataInputStream, toWatch: DataOutputStream) {
        AlloyController.usingOutput(toWatch)
        AlloyController.init()
        try {
            while(true) {
                val length = fromWatch.readUnsignedShort() // read length of incoming message
                if (length > 0) {
                    val message = ByteArray(length)
                    fromWatch.readFully(message, 0, message.size)
                    AlloyController.receive(message)
                }
            }
        }
        catch (e: EOFException) {
            AlloyController.close()
        }
    }

    private fun createHandlerAndForward(fromWatch: DataInputStream, toWatch: DataOutputStream, service: String, weInitiated: Boolean) {
        val handler = AlloyHandler(service, toWatch)
        handler.init(weInitiated)
        forwardToHandlerForever(fromWatch, handler)
    }

    fun initiateChannelAndForward(service: String, port: Int, handler: AlloyHandler) {
        Logger.logIDS("initiating channel for $service", 1)
        val socket = Socket(
            LongTermStorage.getAddress(LongTermStorage.REMOTE_ADDRESS_CLASS_C),
            port
        )

        val toWatch = DataOutputStream(socket.getOutputStream())
        val fromWatch = DataInputStream(socket.getInputStream())

        val req = NWSCServiceRequest.fresh(port, service)
        req.sign(localPrivateKey)
        val payload = req.toByteArray()

        Logger.logIDS("NWSC snd $req", 1)

        // send request and receive response buffer
        toWatch.write(payload)
        toWatch.flush()
        val ackLen = fromWatch.readUnsignedShort()
        val ack = ByteArray(ackLen)
        fromWatch.readFully(ack, 0, ack.size)

        // parse response feedback
        val feedback = NWSCPacket.parseFeedback(ack)

        // if our channel was accepted
        if(feedback.flags.toUInt() == 0x8000u) {
            Logger.logIDS("NWSC rcv channel $req accepted", 1)
            handler.output = toWatch
            handler.init(true)
            forwardToHandlerForever(fromWatch, handler)
        }
        else {
            Logger.logIDS("NWSC rcv channel $req rejected with flag ${feedback.flags.toString(16)}", 1)
            close(fromWatch, toWatch)
        }
    }

    private fun forwardToHandlerForever(fromWatch: DataInputStream, handler: AlloyHandler) {
        try {
            while(true) {
                val type = fromWatch.readByte()
                val length = fromWatch.readInt() // read length of incoming message
                if (length > 0) {
                    val message = ByteArray(length+5)
                    val buf = ByteBuffer.wrap(message, 0, 5)
                    buf.put(type)
                    buf.putInt(length)
                    fromWatch.readFully(message, 5, message.size-5)
                    handler.receive(message)
                }
            }
        }
        catch (e: EOFException) {
            handler.close()
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun freshSequenceNumber(): Long {
        return base + counter.getAndIncrement()
    }

    private fun microsecondsSinceEpoch(): Long {
        val instant = Instant.now()
        val microsecondsSinceSecond = instant.nano / 1000
        val microsecondsSinceEpoch = instant.epochSecond * 1000000
        return microsecondsSinceEpoch + microsecondsSinceSecond
    }

}