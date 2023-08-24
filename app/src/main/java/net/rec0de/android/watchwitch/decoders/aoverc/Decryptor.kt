package net.rec0de.android.watchwitch.decoders.aoverc

import net.rec0de.android.watchwitch.decoders.bplist.BPAsciiString
import net.rec0de.android.watchwitch.decoders.bplist.BPData
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPListObject
import net.rec0de.android.watchwitch.decoders.bplist.CodableBPListObject
import net.rec0de.android.watchwitch.fromBytesBig
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hexBytes
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.ECPointUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec


class Decryptor(keys: MPKeys) {

    companion object {
        fun isEncryptedMessage(msg: BPListObject) = msg is BPDict && msg.values.containsKey(BPAsciiString("ekd")) && msg.values.containsKey(BPAsciiString("sed"))
    }

    private val rsaRemotePublicKey: PublicKey
    private val ecdsaRemotePublicKey: ECPublicKey

    private val rsaLocalPrivateKey: PrivateKey
    private val ecdsaLocalPrivateKey: ECPrivateKey

    private val random = SecureRandom()

    init {
        Security.addProvider(BouncyCastleProvider())

        rsaLocalPrivateKey = keys.friendlyRsaPrivateKey()
        rsaRemotePublicKey = keys.friendlyRsaPublicKey()

        ecdsaLocalPrivateKey = keys.friendlyEcdsaPrivateKey()
        ecdsaRemotePublicKey = keys.friendlyEcdsaPublicKey()
    }

    fun decrypt(msg: BPDict): ByteArray? {
        val ekd = (msg.values[BPAsciiString("ekd")] as BPData).value
        val sed = (msg.values[BPAsciiString("sed")] as BPData).value

        val symKey = decapsulateEkd(ekd)

        return if(symKey == null) {
            println("could not decap ekd")
            null
        } else {
            // CCCryptor is initialized with zero IV in AES-CBC mode (potential issue: first block effectively ECB, IND-CPA break)
            val iv = IvParameterSpec("00000000000000000000000000000000".hexBytes())
            val cryptorKey = SecretKeySpec(symKey, "AES/CBC/PKCS5Padding")
            val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
            c.init(Cipher.DECRYPT_MODE, cryptorKey, iv)

            val plain = c.update(sed) + c.doFinal()
            plain
        }
    }

    fun encrypt(plaintext: ByteArray): BPDict {
        val symKey = ByteArray(16)
        random.nextBytes(symKey)

        val iv = IvParameterSpec("00000000000000000000000000000000".hexBytes())
        val cryptorKey = SecretKeySpec(symKey, "AES/CBC/PKCS5Padding")
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, cryptorKey, iv)

        val ciphertext = c.update(plaintext) + c.doFinal()
        val sed = BPData(ciphertext)
        val ekd = encapsulateKey(symKey)

        val dict = mapOf(
            (BPAsciiString("sed") as CodableBPListObject) to (sed as CodableBPListObject),
            (BPAsciiString("ekd") as CodableBPListObject) to (sed as CodableBPListObject)  // todo ekd
        )
        return BPDict(dict)
    }

    private fun encapsulateKey(key: ByteArray): ByteArray {
        val payload = encryptEkd(key)
        val signature = signEkd(payload)

        val capsule = ByteBuffer.allocate(3 + payload.size + 1 + signature.size)
        capsule.put(0x02) // version
        capsule.putShort(payload.size.toShort())
        capsule.put(payload)
        capsule.put(signature.size.toByte())
        capsule.put(signature)
        return capsule.array()
    }

    private fun decapsulateEkd(ekd: ByteArray): ByteArray? {
        if (ekd[0].toInt() != 0x02)
            throw Exception("Unsupported version in ekd field of AoverC encrypted message: ${ekd[0]}, expected 2")
        val payloadLen = UInt.fromBytesBig(ekd.sliceArray(1 until 3)).toInt()
        val payload = ekd.sliceArray(3 until 3 + payloadLen)
        val rest = ekd.fromIndex(3 + payloadLen)

        val signatureLen = rest[0].toInt()
        val signature = rest.fromIndex(1)

        if(signature.size != signatureLen)
            throw Exception("Expected signature length $signatureLen but got ${signature.size}")

        val verified = verifyEkd(payload, signature)

        //println("AoverC: ekd data len $payloadLen sig len $signatureLen verified? $verified")

        return if(verified) decryptEkd(payload) else null
    }

    private fun verifyEkd(message: ByteArray, signature: ByteArray): Boolean {
        val ecdsaVerify: Signature = Signature.getInstance("SHA1withECDSA", BouncyCastleProvider())
        ecdsaVerify.initVerify(ecdsaRemotePublicKey)
        ecdsaVerify.update(message)
        return ecdsaVerify.verify(signature)
    }

    private fun signEkd(message: ByteArray): ByteArray {
        val ecdsaSign: Signature = Signature.getInstance("SHA1withECDSA", BouncyCastleProvider())
        ecdsaSign.initSign(ecdsaLocalPrivateKey)
        ecdsaSign.update(message)
        return ecdsaSign.sign()
    }

    private fun decryptEkd(ciphertext: ByteArray): ByteArray {
        // EKD is RSA-OAEP encrypted 32-byte payload
        val decryptCipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-1ANDMGF1PADDING")
        val oaepParams = OAEPParameterSpec("SHA1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        decryptCipher.init(Cipher.DECRYPT_MODE, rsaLocalPrivateKey, oaepParams)
        val plaintext = decryptCipher.doFinal(ciphertext)

        // decrypted EKD contains an AES key in the first 16 bytes (128bit) and a CTR encrypted block in the second 16 bytes
        // NOTE: is this an all-or-nothing transform bolted on to RSA?
        val key = plaintext.sliceArray(0 until 16)
        val payload = plaintext.fromIndex(16)

        //println("key: ${key.hex()}")
        //println("pld: ${payload.hex()}")

        // encryption is "one-shot", always starts with 1-value counter and no nonce (bit of a questionable choice, why not ECB at this point?)
        val iv = IvParameterSpec("00000000000000000000000000000001".hexBytes())
        val ctrKey = SecretKeySpec(key, "AES/CTR/NoPadding")
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.DECRYPT_MODE, ctrKey, iv)

        return c.update(payload) + c.doFinal()
    }

    private fun encryptEkd(plaintext: ByteArray): ByteArray {
        // generate a random AES key
        val key = ByteArray(16)
        random.nextBytes(key)

        // encrypt the plaintext using the random key
        val iv = IvParameterSpec("00000000000000000000000000000001".hexBytes())
        val ctrKey = SecretKeySpec(key, "AES/CTR/NoPadding")
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, ctrKey, iv)
        val ciphertext = c.update(plaintext) + c.doFinal()

        // now encrypt the key and ciphertext again using RSA
        val rsaPayload = key + ciphertext

        val cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-1ANDMGF1PADDING")
        val oaepParams = OAEPParameterSpec("SHA1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        cipher.init(Cipher.ENCRYPT_MODE, rsaRemotePublicKey, oaepParams)
        return cipher.doFinal(rsaPayload)
    }
}