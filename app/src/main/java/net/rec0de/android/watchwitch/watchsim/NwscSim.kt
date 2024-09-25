package net.rec0de.android.watchwitch.watchsim

import net.rec0de.android.watchwitch.nwsc.NWSCManager
import net.rec0de.android.watchwitch.nwsc.NWSCServiceRequest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object NwscSim {
    private val localPrivateKey: Ed25519PrivateKeyParameters
    val localPublicKey: Ed25519PublicKeyParameters
    private lateinit var remotePubKey: ByteArray

    private var base: Long = microsecondsSinceEpoch()
    private var counter: AtomicInteger = AtomicInteger(0)

    init {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = gen.generateKeyPair()

        localPrivateKey = keyPair.private as Ed25519PrivateKeyParameters
        localPublicKey = keyPair.public as Ed25519PublicKeyParameters

        // if the NWSC manager does not know our public key, it will attempt to send a request to itself (since we are on the same device)
        NWSCManager.simulatorSetRemotePubkey(localPublicKey.encoded)
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

    fun buildServiceRequest(port: Int, service: String): NWSCServiceRequest {
        val req = NWSCServiceRequest(port, 0L, UUID.randomUUID(), service, null)
        req.sign(localPrivateKey)
        return req
    }
}