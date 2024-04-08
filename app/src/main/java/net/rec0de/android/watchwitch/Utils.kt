package net.rec0de.android.watchwitch

import net.rec0de.android.watchwitch.bitmage.ByteOrder
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.util.Date
import java.util.UUID


object Utils {
    fun getLocalIP(): String = RoutingManager.getLocalIPv4Address().hostAddress!!

    fun HMAC(k: ByteArray, v: ByteArray): ByteArray {
        val hmac = org.bouncycastle.crypto.macs.HMac(SHA512Digest())
        hmac.init(KeyParameter(k))
        val digest = ByteArray(hmac.macSize)
        hmac.update(v, 0, v.size)
        hmac.doFinal(digest, 0)
        return digest
    }

    fun PRFplus(key: ByteArray, data: ByteArray, length: Int): ByteArray {
        var keystream = byteArrayOf()
        var counter = 1

        var lastblock = HMAC(key, data + byteArrayOf(counter.toByte()))
        keystream += lastblock
        counter += 1

        while(keystream.size < length) {
            lastblock = HMAC(key, lastblock + data + byteArrayOf(counter.toByte()))
            keystream += lastblock
            counter += 1
        }

        return keystream
    }

    fun chachaPolyDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val chacha = ChaCha20Poly1305()
        val params = AEADParameters(KeyParameter(key), 16*8, nonce, aad)
        chacha.init(false, params)

        val plaintext = ByteArray(chacha.getOutputSize(ciphertext.size))
        val finalOffset = chacha.processBytes(ciphertext, 0, ciphertext.size, plaintext, 0)
        chacha.doFinal(plaintext, finalOffset)

        return plaintext
    }

    fun chachaPolyEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val chacha = ChaCha20Poly1305()
        val params = AEADParameters(KeyParameter(key), 16*8, nonce, aad)
        chacha.init(true, params)

        val ciphertext = ByteArray(chacha.getOutputSize(plaintext.size))
        val finalOffset = chacha.processBytes(plaintext, 0, plaintext.size, ciphertext, 0)
        chacha.doFinal(ciphertext, finalOffset)

        return ciphertext
    }

    fun uuidFromBytes(bytes: ByteArray): UUID {
        if(bytes.size != 16)
            throw Exception("Trying to build UUID from ${bytes.size} bytes, expected 16")

        val a = bytes.sliceArray(0 until 4).hex()
        val b = bytes.sliceArray(4 until 6).hex()
        val c = bytes.sliceArray(6 until 8).hex()
        val d = bytes.sliceArray(8 until 10).hex()
        val e = bytes.sliceArray(10 until 16).hex()
        return UUID.fromString("$a-$b-$c-$d-$e")
    }

    fun uuidToBytes(uuid: UUID): ByteArray {
        val buf = ByteBuffer.allocate(16)
        buf.putLong(uuid.mostSignificantBits)
        buf.putLong(uuid.leastSignificantBits)
        return buf.array()
    }

    fun dateFromAppleTimestamp(timestamp: Double): Date {
        // NSDate timestamps encode time as seconds since Jan 01 2001 with millisecond precision as doubles
        return Date((timestamp*1000).toLong() + 978307200000)
    }
}

open class ParseCompanion {
    protected var parseOffset = 0

    protected fun readBytes(bytes: ByteArray, length: Int): ByteArray {
        val sliced = bytes.sliceArray(parseOffset until parseOffset + length)
        parseOffset += length
        return sliced
    }

    protected fun readLengthPrefixedString(bytes: ByteArray, sizePrefixLen: Int): String? {
        val len = readInt(bytes, sizePrefixLen)
        return if(len == 0) null else readString(bytes, len)
    }

    protected fun readString(bytes: ByteArray, size: Int): String {
        val str = bytes.sliceArray(parseOffset until parseOffset +size).toString(Charsets.UTF_8)
        parseOffset += size
        return str
    }

    protected fun readInt(bytes: ByteArray, size: Int): Int {
        val int = UInt.fromBytes(bytes.sliceArray(parseOffset until parseOffset +size), ByteOrder.BIG).toInt()
        parseOffset += size
        return int
    }
}

abstract class PBParsable<TargetClass> {
    abstract fun fromSafePB(pb: ProtoBuf): TargetClass
    fun fromPB(pb: ProtoBuf?): TargetClass? {
        if(pb == null)
            return null
        return fromSafePB(pb)
    }
}

object Poetry {
    private val v = mapOf(
        "Circe Aiaie" to listOf("When I was born, the name for what I was did not exist", "I turned into myself in the woods, where the god's blood soaked the earth", "I pluck flowers for the deathless, a white bloom with a root of black", "And I become pharmakis, become witch, become mortal and myself"),
        "Baba Yaga" to listOf("Come sit with me, in this pit of mud", "Where we are neither one thing nor another", "We, the untethered, the free, the chicken-housed", "watching the world burn, grinding candy between iron teeth"),
        "Medea" to listOf("I should have seen it, the way you flinched at me", "And yet, to throw me to the crows, after everything I've done?", "All I ask of you is this:", "Remember that my cuts are precise, that my hands don't shake, and my mind never wavers.", "Farewell, my love."),
        "Morana" to listOf("Burn me with your fragrant herbs, drown me in the river", "But don't look back at what you've done, don't ever look back", "Forget that I am goddess and you are not", "Forget that what you drown is just a body, not the soul, not me"),
        "Hecate Chthonia" to listOf("Behind this mask I hide my body, pallid and decaying", "I let you bask in my phosphorus glow", "I let you call me candle", "And I let you burn up in my flame")
    )
    fun witch() = v.entries.random()
}

fun Date.toAppleTimestamp(): Double {
    // NSDate timestamps encode time as seconds since Jan 01 2001 with millisecond precision as doubles
    val canonicalTimestamp = this.time
    return (canonicalTimestamp - 978307200000).toDouble() / 1000
}

fun Date.toCanonicalTimestamp(): Double {
    return this.time.toDouble() / 1000
}