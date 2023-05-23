package net.rec0de.android.watchwitch

import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.nio.ByteBuffer

abstract class IKEPayload {

    abstract val payload: ByteArray
    abstract val typeByte: Int

    @OptIn(ExperimentalUnsignedTypes::class)
    open fun render(nextHeader: Int): ByteArray {
        val length = payload.size + 4
        val buf = ByteBuffer.allocate(length)
        buf.put(nextHeader.toByte())
        buf.put(0)
        buf.putShort(length.toShort())
        buf.put(payload)

        return buf.array()
    }
}

abstract class SAPayload : IKEPayload() {
    override val typeByte = 33

    protected fun transformStruct(isLast: Boolean, transformType: Int, transformId: Int, data: ByteArray = byteArrayOf()): ByteArray {
        val length = 8 + data.size
        val lastSubstruct = if (isLast) 0x00 else 0x03
        val buf = ByteBuffer.allocate(length)
        buf.put(lastSubstruct.toByte()) // last substruct?
        buf.put(0) // reserved
        buf.putShort(length.toShort()) // transform length
        buf.put(transformType.toByte()) // transform type
        buf.put(0) // reserved
        buf.putShort(transformId.toShort()) // transform id
        buf.put(data)
        return buf.array()
    }
}

class IKESAPayload : SAPayload() {
    override val payload: ByteArray

    init {
        // build a single proposal containing our supported algorithms
        val buf = ByteBuffer.allocate(32)
        buf.putShort(0) // last substruct, reserved
        buf.putShort(32) // length
        buf.put(1) // proposal num
        buf.put(1) // protocol IKE
        buf.put(0) // spi size
        buf.put(3) // number of transforms
        buf.put(transformStruct(false, 1, 0x1c)) // Encryption Algorithm: ENCR_CHACHA20_POLY1305
        buf.put(transformStruct(false, 2, 0x07)) // Pseudorandom Function: PRF_HMAC_SHA2_512
        buf.put(transformStruct(true, 4, 0x1f)) // Diffie-Hellman Group: Curve25519
        payload = buf.array()
    }
}

class ESPSAPayload(espSpi: ByteArray) : SAPayload() {
    override val payload: ByteArray

    init {
        val length = 28 + 4 // 4 extra bytes for AES keylen
        val buf = ByteBuffer.allocate(length)
        buf.putShort(0) // last substruct, reserved
        buf.putShort(length.toShort()) // length
        buf.put(1) // proposal num
        buf.put(3) // protocol ESP
        buf.put(4) // spi size (4 bytes)
        buf.put(2) // number of transforms
        buf.put(espSpi.sliceArray(0 until 4)) // ESP SPI should always be 4 bytes but let's be safe

        // Note: In the wild, watch and phone negotiate ENCR_CHACHA20_POLY1305_IIV, but android has no support for implicit IVs
        // so if we want to have any hope of speaking with the watch, we have to negotiate something else
        //buf.put(transformStruct(false, 1, 0x1f)) // Encryption Algorithm: ENCR_CHACHA20_POLY1305_IIV (note implicit IV!)
        //buf.put(transformStruct(false, 1, 0x1c)) // Encryption Algorithm: ENCR_CHACHA20_POLY1305
        buf.put(transformStruct(false, 1, 0x14, "800e0100".hexBytes())) // ENCR_AES_GCM_16 with 256bit keys

        buf.put(transformStruct(true, 5, 0x00)) // Extended Sequence Numbers: disabled
        payload = buf.array()
    }
}

class KExPayload(pubkey: X25519PublicKeyParameters) : IKEPayload() {
    override val payload: ByteArray
    override val typeByte = 34

    init {
        val pubkeyBytes = pubkey.encoded
        val length = 4 + pubkeyBytes.size
        val x25519id = 0x1f
        val buf = ByteBuffer.allocate(length)
        buf.putShort(x25519id.toShort()) // DH group id
        buf.putShort(0) // reserved
        buf.put(pubkeyBytes) // pubkey
        payload = buf.array()
    }
}

class AuthPayload(sig: ByteArray) : IKEPayload() {
    override val payload: ByteArray
    override val typeByte = 39

    init {
        // signature type (0x0e, digital signature), reserved bytes, ASN1 algorithm identifier length (0x04) and identifier (0x01036570, ed25519?)
        val header = byteArrayOf(0x0e, 0x00, 0x00, 0x00, 0x04, 0x01, 0x03, 0x65, 0x70)
        payload = header + sig
    }
}

class EncryptedPayload(private val firstContainedPayload: Byte, private val containedLength: Int, private val iv: ByteArray) : IKEPayload() {
    override val typeByte = 46
    override val payload: ByteArray = byteArrayOf()
    private lateinit var ciphertext: ByteArray

    // 4 byte generic payload header, 8 byte IV, encrypted payload, 1 byte pad length, 16 byte tag
    fun size() = 4 + 8 + containedLength + 1 + 16

    fun header(): ByteArray {
        val buf = ByteBuffer.allocate(4)
        buf.put(firstContainedPayload)
        buf.put(0)
        buf.putShort(size().toShort())
        return buf.array()
    }

    fun setCiphertext(ctext: ByteArray) {
        if(ctext.size != containedLength + 1 + 16)
            throw Exception("Unexpected ciphertext length, expected ${containedLength+17} got ${ctext.size}")
        ciphertext = ctext
    }

    override fun render(nextHeader: Int): ByteArray {
        val length = size()
        val buf = ByteBuffer.allocate(length)
        buf.put(firstContainedPayload)
        buf.put(0)
        buf.putShort(length.toShort())
        buf.put(iv)
        buf.put(ciphertext)

        return buf.array()
    }

}

class NoncePayload(override val payload: ByteArray) : IKEPayload() {
    override val typeByte = 40
}

class IdPayload(override val payload: ByteArray) : IKEPayload() {
    override val typeByte = 36
}

class TSiPayload(override val payload: ByteArray) : IKEPayload() {
    override val typeByte = 44
}

class TSrPayload(override val payload: ByteArray) : IKEPayload() {
    override val typeByte = 45
}

class NotifyPayload(notifyType: Int, data: ByteArray = byteArrayOf()) : IKEPayload() {
    override val payload: ByteArray
    override val typeByte = 41

    init {
        val buf = ByteBuffer.allocate(4 + data.size)
        buf.putShort(0)
        buf.putShort(notifyType.toShort())
        buf.put(data)
        payload = buf.array()
    }
}

class NATDetectionPayload(spii: ByteArray, spir: ByteArray, ip: Int, port: Int, isSource: Boolean) : IKEPayload() {
    override val typeByte = 41
    override val payload: ByteArray
    private val notifyType = if(isSource) 16388 else 16389

    init {
        val hashedBytes = ByteBuffer.allocate(8 + 8 + 4 + 2) // SPIi, SPIr, IP, port
        hashedBytes.put(spii)
        hashedBytes.put(spir)
        hashedBytes.putInt(ip)
        hashedBytes.putShort(port.toShort())
        val hb = hashedBytes.array()

        val digest = SHA1Digest()
        digest.update(hb, 0, hb.size)
        val hash = ByteArray(digest.digestSize)
        digest.doFinal(hash, 0)

        val buf = ByteBuffer.allocate(4 + 20) // sha1 digest is 20 bytes
        buf.putShort(0)
        buf.putShort(notifyType.toShort())
        buf.put(hash)
        payload = buf.array()
    }
}