package net.rec0de.android.watchwitch

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECPrivateKeySpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource


// adapted from https://github.com/signalapp/Signal-Android/blob/main/app/src/main/java/org/thoughtcrime/securesms/crypto/KeyStoreHelper.java

object KeyStoreHelper {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val INTERNAL_KEY_ALIAS = "WatchWitchSecret"

    fun seal(input: ByteArray): SealedData {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sealingSecretKey)
        val iv = cipher.iv
        val data = cipher.doFinal(input)
        return SealedData(iv, data)
    }

    fun unseal(sealedData: SealedData): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, sealingSecretKey, GCMParameterSpec(128, sealedData.iv))
        return cipher.doFinal(sealedData.data)
    }

    fun aovercEcdsaSign(message: ByteArray): ByteArray {
        // i hear that keystore operations can be slow, but we'll just try it
        // perhaps it makes sense to cache the unsealed private key on app start
        val keyBytes = unseal(SealedData.fromBytes(LongTermStorage.encryptedEcdsaPrivate!!))
        val spec = ECNamedCurveTable.getParameterSpec("secp256r1")
        val params = ECNamedCurveSpec("secp256r1", spec.curve, spec.g, spec.n)
        val key = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(keyBytes), params))

        return Signature.getInstance("SHA1withECDSA").run {
            initSign(key)
            update(message)
            sign()
        }
    }

    fun aovercRsaDecrypt(ciphertext: ByteArray): ByteArray {
        // i hear that keystore operations can be slow, but we'll just try it
        // perhaps it makes sense to cache the unsealed private key on app start
        val keyBytes = unseal(SealedData.fromBytes(LongTermStorage.encryptedRsaPrivate!!))
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))

        val decryptCipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-1ANDMGF1PADDING")
        val oaepParams = OAEPParameterSpec("SHA1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        decryptCipher.init(Cipher.DECRYPT_MODE, key, oaepParams)
        return decryptCipher.doFinal(ciphertext)
    }

    private val sealingSecretKey: SecretKey
        get() = if (hasKeyStoreEntry()) (keyStore.getEntry(INTERNAL_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey else createKeyStoreEntry()


    fun enrollAovercRsaPrivateKey(key: PrivateKey) {
        // Apple uses severely nonstandard RSA key sizes which are not supported by the Android keystore 🙄
        // we'll do our best by storing them client side as sealed data the same way we store health data
        val sealed = seal(key.encoded)
        LongTermStorage.encryptedRsaPrivate = sealed.toBytes()
        Logger.log("Enrolled RSA private key (sealed-at-rest)", 1)
    }

    fun enrollAovercEcdsaPrivateKey(key: ECPrivateKey) {
        // same here: we can't quite use the nice keystore interface to generate signatures in the secure hardware
        // because Apple uses SHA1 for their signatures and the keystore only supports SHA256
        val sealed = seal(key.s.toByteArray())
        LongTermStorage.encryptedEcdsaPrivate = sealed.toBytes()
        Logger.log("Enrolled ECDSA private key (sealed-at-rest)", 1)
    }

    // leaving this here because it WOULD work nicely if only Apple would use SHA256 instead of SHA1 for their signatures...
    /*
    private const val AOVERC_ECDSA_ALIAS = "WW.aoverc.ecdsa.private"

    private val aovercEcdsaPrivateKey: PrivateKeyEntry
        get() = keyStore.getEntry(AOVERC_ECDSA_ALIAS, null) as PrivateKeyEntry

    fun aovercEcdsaSign(message: ByteArray): ByteArray {
        return Signature.getInstance("SHA1withECDSA").run {
            initSign(aovercEcdsaPrivateKey.privateKey)
            update(message)
            sign()
        }
    }

    fun enrollAovercEcdsaPrivateKey(key: ECPrivateKey) {
        val entry = PrivateKeyEntry(key, arrayOf(certificateForEcdsaKey(key)))
        keyStore.setEntry(
            AOVERC_ECDSA_ALIAS, entry,
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build()
        )
    }

    // for some reason, we need a certificate accompanying the private key to store it in the keystore
    // ... so we'll just make something up
    private fun certificateForEcdsaKey(key: ECPrivateKey): X509Certificate {
        val spec = ECNamedCurveTable.getParameterSpec("secp256r1")
        val kf = KeyFactory.getInstance("EC", BouncyCastleProvider())
        val params = ECNamedCurveSpec("secp256r1", spec.curve, spec.g, spec.n)

        // we need the public key for the certificate we don't need
        // so we have to reconstruct it from the private key
        val q = spec.g.multiply(key.s)
        // i have no idea what i am doing here and it scares me
        // (but q should be public so....)
        val qn = q.normalize()
        val qs = ECPoint(qn.affineXCoord.toBigInteger(), qn.affineYCoord.toBigInteger())
        val pubSpec = ECPublicKeySpec(qs, params)
        val pub = kf.generatePublic(pubSpec) as PublicKey

        val x500Name =
            X500Name("CN=WatchWitch, OU=AoverC, O=WatchWitch")
        val pubKeyInfo = SubjectPublicKeyInfo.getInstance(pub.encoded)

        val start = Date()
        val until = Date()
        val certificateBuilder = X509v3CertificateBuilder(
            x500Name,
            BigInteger(10, SecureRandom()), start, until, x500Name, pubKeyInfo
        )
        val contentSigner = JcaContentSignerBuilder("SHA256WithECDSA").build(key)

        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider())
            .getCertificate(certificateBuilder.build(contentSigner))
    }

     */

    private fun createKeyStoreEntry(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            INTERNAL_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private val keyStore: KeyStore
        get() {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            return keyStore
        }

    private fun hasKeyStoreEntry(): Boolean {
        val ks = keyStore
        return ks.containsAlias(INTERNAL_KEY_ALIAS) && ks.entryInstanceOf(
            INTERNAL_KEY_ALIAS,
            KeyStore.SecretKeyEntry::class.java
        )
    }

    class SealedData(val iv: ByteArray, val data: ByteArray) {
        companion object {
            fun fromBytes(bytes: ByteArray): SealedData {
                val ivLen = bytes[0].toInt()
                val remaining = bytes.fromIndex(1)
                val iv = remaining.sliceArray(0 until ivLen)
                val data = remaining.fromIndex(ivLen)
                return SealedData(iv, data)
            }
        }

        fun toBytes(): ByteArray {
            val buf = ByteBuffer.allocate(iv.size+data.size+1) // store IV with 1 byte length prefix
            buf.put(iv.size.toByte())
            buf.put(iv)
            buf.put(data)
            return buf.array()
        }
    }
}