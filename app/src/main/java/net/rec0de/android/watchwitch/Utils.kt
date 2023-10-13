package net.rec0de.android.watchwitch

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
        val int = UInt.fromBytesBig(bytes.sliceArray(parseOffset until parseOffset +size)).toInt()
        parseOffset += size
        return int
    }
}

fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
fun ByteArray.fromIndex(i: Int) = sliceArray(i until size)
fun String.hexBytes(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun UInt.Companion.fromBytesLittle(bytes: ByteArray): UInt {
    return bytes.mapIndexed { index, byte ->  byte.toUByte().toUInt() shl (index * 8)}.sum()
}
fun UInt.Companion.fromBytesBig(bytes: ByteArray): UInt {
    return bytes.reversed().mapIndexed { index, byte ->  byte.toUByte().toUInt() shl (index * 8)}.sum()
}
fun ULong.Companion.fromBytesLittle(bytes: ByteArray): ULong {
    return bytes.mapIndexed { index, byte ->  byte.toUByte().toULong() shl (index * 8)}.sum()
}
fun ULong.Companion.fromBytesBig(bytes: ByteArray): ULong {
    return bytes.reversed().mapIndexed { index, byte ->  byte.toUByte().toULong() shl (index * 8)}.sum()
}

fun Int.toBytesBig(): ByteArray {
    return byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), (this shr 0).toByte())
}

fun Long.doubleFromLongBytes(): Double {
    val b = ByteBuffer.allocate(8)
    b.putLong(this)
    return b.getDouble(0)
}

fun Int.floatFromIntBytes(): Float {
    val b = ByteBuffer.allocate(4)
    b.putInt(this)
    return b.getFloat(0)
}

fun Date.toAppleTimestamp(): Double {
    // NSDate timestamps encode time as seconds since Jan 01 2001 with millisecond precision as doubles
    val canonicalTimestamp = this.time
    return (canonicalTimestamp - 978307200000).toDouble() / 1000
}