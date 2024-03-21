package net.rec0de.android.watchwitch.decoders.aoverc

import net.rec0de.android.watchwitch.KeyStoreHelper
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


class KeystoreBackedDecryptor(keys: MPKeys) : Decryptor(keys) {

    override fun signEkd(message: ByteArray): ByteArray {
        println("Signing msg using keystore keys")
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
        val iv = IvParameterSpec("00000000000000000000000000000001".hexBytes())
        val ctrKey = SecretKeySpec(key, "AES/CTR/NoPadding")
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.DECRYPT_MODE, ctrKey, iv)

        return c.update(payload) + c.doFinal()
    }
}