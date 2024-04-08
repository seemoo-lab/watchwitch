package net.rec0de.android.watchwitch.nwsc

import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.bitmage.ByteOrder
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.fromIndex
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.ByteBuffer
import java.util.UUID

abstract class NWSCPacket(val seq: Long) {
    companion object {
        fun parseRequest(bytes: ByteArray): NWSCRequest {
            return if(bytes.size == 0x4f) parsePubkeyRequest(bytes) else parseServiceRequest(bytes)
        }

        // assuming 2 byte length prefix has already been consumed
        fun parseFeedback(bytes: ByteArray): NWSCFeedback {
            // 2 bytes accept flag
            val acceptFlag = UInt.fromBytes(bytes.sliceArray(0 until 2), ByteOrder.BIG).toUShort()
            // 8 byte sequence number
            val seq = Long.fromBytes(bytes.sliceArray(2 until 10), ByteOrder.BIG)
            // 32 byte pubkey
            val pubkey = bytes.fromIndex(10)

            return NWSCFeedback(seq, acceptFlag, pubkey)
        }

        // assuming 2 byte length prefix has already been consumed
        private fun parseServiceRequest(bytes: ByteArray): NWSCServiceRequest {
            // 2 bytes target port
            val targetPort = Int.fromBytes(bytes.sliceArray(0 until 2), ByteOrder.BIG)
            // 8 byte sequence number
            val seq = Long.fromBytes(bytes.sliceArray(2 until 10), ByteOrder.BIG)
            // 16 byte UUID
            val uuid = Utils.uuidFromBytes(bytes.sliceArray(10 until 26))
            // 1 byte service name len
            val serviceNameLen = bytes[26].toUByte().toInt()
            // var length service name
            val serviceName = bytes.sliceArray(27 until 27+serviceNameLen).toString(Charsets.UTF_8)
            // 64 byte ed25519 signature
            val signature = bytes.fromIndex(27+serviceNameLen)

            return NWSCServiceRequest(targetPort, seq, uuid, serviceName, signature)
        }

        // assuming 2 byte length prefix has already been consumed
        private fun parsePubkeyRequest(bytes: ByteArray): NWSCPubkeyRequest {
            // 2 bytes target port
            val targetPort = Int.fromBytes(bytes.sliceArray(0 until 2), ByteOrder.BIG)
            // 8 byte sequence number
            val seq = Long.fromBytes(bytes.sliceArray(2 until 10), ByteOrder.BIG)
            // 4 zero bytes
            val zero = bytes.sliceArray(10 until 14)
            // 64 byte ed25519 signature
            val signature = bytes.sliceArray(14 until 78)
            // 1 zero byte
            val zero2 = bytes[78]

            return NWSCPubkeyRequest(targetPort, seq, signature)
        }
    }

    abstract fun toByteArray(): ByteArray
}

/**
 * Flag documentation:
 * 0x8000 is accept
 * 0x0000 looks to be NWSC-internal reject (due to failed verification, already established service, etc)
 * 0x4000 looks to be higher-level (client) reject (higher-level client timed out, sent active reject)
 */
class NWSCFeedback(seq: Long, val flags: UShort, val pubkey: ByteArray): NWSCPacket(seq) {
    companion object {
        fun fresh(flags: UShort) = NWSCFeedback(NWSCManager.freshSequenceNumber(), flags, NWSCManager.localPublicKey.encoded)
    }

    override fun toByteArray(): ByteArray {
        val buf = ByteBuffer.allocate(0x2c)
        buf.putShort(0x2a) // message length
        buf.putShort(flags.toShort()) // connection accept flag
        buf.putLong(seq) // sequence number
        buf.put(pubkey) // ed25519 pubkey
        return buf.array()
    }

    override fun toString() = "NWSCFeedback(${flags.toString(16)})"
}

abstract class NWSCRequest(val port: Int, seq: Long, var signature: ByteArray?): NWSCPacket(seq) {
    fun sign(privkey: Ed25519PrivateKeyParameters) {
        if(signature != null)
            return

        val bytes = toByteArray()
        val signer = Ed25519Signer()
        signer.init(true, privkey)
        signer.update(bytes, 0, bytes.size)
        signature = signer.generateSignature()
    }

    fun verify(pubkey: ByteArray): Boolean {
        val sig = signature

        signature = null
        val signedBytes = toByteArray()
        signature = sig

        val key = Ed25519PublicKeyParameters(pubkey)

        val verifier = Ed25519Signer()
        verifier.init(false, key)
        verifier.update(signedBytes, 0, signedBytes.size)
        return verifier.verifySignature(sig)
    }
}

class NWSCServiceRequest(port: Int, seq: Long, val uuid: UUID, val service: String, signature: ByteArray?): NWSCRequest(port, seq, signature) {
    companion object {
        fun fresh(port: Int, service: String) = NWSCServiceRequest(port, NWSCManager.freshSequenceNumber(), UUID.randomUUID(), service, null)
    }

    override fun toByteArray(): ByteArray {
        // 2 byte port, 8 byte seq, 16 byte uuid, 1 byte sname len, service name, 64 byte signature
        val reqLen = 2 + 8 + 16 + (service.length + 1) + 64
        val req = ByteBuffer.allocate(reqLen + 2)
        req.putShort(reqLen.toShort())
        req.putShort(port.toShort())
        req.putLong(seq)

        req.putLong(uuid.mostSignificantBits)
        req.putLong(uuid.leastSignificantBits)

        req.put(service.length.toByte())
        req.put(service.toByteArray(Charsets.US_ASCII))

        if(signature != null)
            req.put(signature!!)

        return req.array()
    }

    override fun toString() = "NWSCServiceRequest($service, $uuid)"
}

class NWSCPubkeyRequest(port: Int, seq: Long, signature: ByteArray?): NWSCRequest(port, seq, signature) {
    companion object {
        fun fresh(port: Int) = NWSCPubkeyRequest(port, NWSCManager.freshSequenceNumber(), null)
    }

    override fun toByteArray(): ByteArray {
        val reqLen = 0x4f
        val req = ByteBuffer.allocate(reqLen + 2)
        req.putShort(reqLen.toShort())
        req.putShort(port.toShort())
        req.putLong(seq)
        req.putInt(0) // 4 zero bytes
        if(signature != null)
            req.put(signature!!)
        req.put(0)

        return req.array()
    }

    override fun toString() = "NWSCPubkeyRequest"
}