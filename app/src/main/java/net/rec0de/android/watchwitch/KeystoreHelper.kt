package net.rec0de.android.watchwitch

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECPrivateKeySpec
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec


// adapted from https://github.com/signalapp/Signal-Android/blob/main/app/src/main/java/org/thoughtcrime/securesms/crypto/KeyStoreHelper.java

object KeyStoreHelper {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val INTERNAL_KEY_ALIAS = "WatchWitchSecret"
    private const val AOVERC_ECDSA_ALIAS = "WW.aoverc.ecdsa.private"
    private const val AOVERC_RSA_ALIAS = "WW.aoverc.rsa.private"

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
        return Signature.getInstance("SHA1withECDSA").run {
            initSign(aovercEcdsaPrivateKey.privateKey)
            update(message)
            sign()
        }
    }

    fun aovercRsaDecrypt(ciphertext: ByteArray): ByteArray {
        val decryptCipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-1ANDMGF1PADDING")
        val oaepParams = OAEPParameterSpec("SHA1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        decryptCipher.init(Cipher.DECRYPT_MODE, aovercRsaPrivateKey.privateKey, oaepParams)
        return decryptCipher.doFinal(ciphertext)
    }

    private val sealingSecretKey: SecretKey
        get() = if (hasKeyStoreEntry()) (keyStore.getEntry(INTERNAL_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey else createKeyStoreEntry()

    private val aovercEcdsaPrivateKey: PrivateKeyEntry
        get() = keyStore.getEntry(AOVERC_ECDSA_ALIAS, null) as PrivateKeyEntry

    fun enrollAovercEcdsaPrivateKey(key: ECPrivateKeySpec) {
        val entry = KeyStore.SecretKeyEntry(key as SecretKeySpec)
        keyStore.setEntry(
            AOVERC_ECDSA_ALIAS, entry,
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build()
        )
    }

    private val aovercRsaPrivateKey: PrivateKeyEntry
        get() = keyStore.getEntry(AOVERC_RSA_ALIAS, null) as PrivateKeyEntry

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