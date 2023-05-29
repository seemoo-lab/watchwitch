package net.rec0de.android.watchwitch

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface


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
}

fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
fun ByteArray.fromIndex(i: Int) = sliceArray(i until size)
fun String.hexBytes(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun UInt.Companion.fromBytesSmall(bytes: ByteArray): UInt {
    return bytes.mapIndexed { index, byte ->  byte.toUByte().toUInt() shl (index * 8)}.sum()
}
fun UInt.Companion.fromBytesBig(bytes: ByteArray): UInt {
    return bytes.reversed().mapIndexed { index, byte ->  byte.toUByte().toUInt() shl (index * 8)}.sum()
}