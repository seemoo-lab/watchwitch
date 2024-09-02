package net.rec0de.android.watchwitch.decoders.aoverc

import net.rec0de.android.watchwitch.KeyStoreHelper
import net.rec0de.android.watchwitch.bitmage.fromHex
import net.rec0de.android.watchwitch.bitmage.fromIndex
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class KeystoreBackedDecryptor(keys: MPKeys) : Decryptor(keys) {

    override fun signEkd(message: ByteArray): ByteArray {
        //println("Signing msg using keystore keys")
        return KeyStoreHelper.aovercEcdsaSign(message)
    }

    override fun decryptEkd(ciphertext: ByteArray): ByteArray {
        // EKD is RSA-OAEP encrypted 32-byte payload
        val plaintext = KeyStoreHelper.aovercRsaDecrypt(ciphertext)

        // decrypted EKD contains an AES key in the first 16 bytes (128bit) and a CTR encrypted block in the second 16 bytes
        // NOTE: is this an all-or-nothing transform bolted on to RSA?
        val key = plaintext.sliceArray(0 until 16)
        val payload = plaintext.fromIndex(16)

        // encryption is "one-shot", always starts with 1-value counter and no nonce (bit of a questionable choice, why not ECB at this point?)
        val iv = IvParameterSpec("00000000000000000000000000000001".fromHex())
        val ctrKey = SecretKeySpec(key, "AES/CTR/NoPadding")
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.DECRYPT_MODE, ctrKey, iv)

        return c.update(payload) + c.doFinal()
    }
}