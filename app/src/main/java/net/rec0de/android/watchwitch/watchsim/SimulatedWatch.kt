package net.rec0de.android.watchwitch.watchsim

import android.util.Log
import net.rec0de.android.watchwitch.KeyStoreHelper
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.bitmage.fromHex
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.aoverc.MPKeys
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom


class SimulatedWatch {

    fun start() {
        val controller = AlloyControlClient()
        controller.start()

        //val watchState = WatchStateMockup(controller)
        val shoes = ShoesMockup()
        //shoes.start()

        installKeys()
    }

    fun installKeys() {

        // "Watch" ECDSA keys
        val ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1")
        val generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        generator.initialize(ecSpec, SecureRandom())
        val watchEcdsaKeys: KeyPair = generator.generateKeyPair()
        val watchEcdsaPublicBytes = ecdsaGetPublicBytes(watchEcdsaKeys.public as ECPublicKey)

        // "Watch" RSA keys
        val rsaGen = KeyPairGenerator.getInstance("RSA")
        rsaGen.initialize(1280)
        val watchRsaKeys = rsaGen.generateKeyPair()
        val watchRsaPublicBytes = watchRsaKeys.public.encoded

        // Phone ECDSA keys
        val phoneEcdsaKeys: KeyPair = generator.generateKeyPair()
        // the private key we get is actually the public key (65B) followed by the private key (32B)
        val phoneEcdsaPrivateBytes = ecdsaGetPublicBytes(phoneEcdsaKeys.public as ECPublicKey) + (phoneEcdsaKeys.private as ECPrivateKey).d.toByteArray()

        // Phone RSA keys
        val phoneRsaKeys = rsaGen.generateKeyPair()
        val privBytes = phoneRsaKeys.private.encoded
        val pkInfo = PrivateKeyInfo.getInstance(privBytes)
        val encodable = pkInfo.parsePrivateKey()
        val primitive = encodable.toASN1Primitive()
        val phoneRsaPrivateBytes = primitive.getEncoded()

        Log.d("SimCrypto", "Ecdsa public: ${watchEcdsaPublicBytes.hex()}")
        Log.d("SimCrypto", "Ecdsa private: ${phoneEcdsaPrivateBytes.hex()}")
        Log.d("SimCrypto", "RSA public: ${watchRsaPublicBytes.hex()}")
        Log.d("SimCrypto", "RSA private: ${phoneRsaPrivateBytes.hex()}")

        val keys = MPKeys(watchEcdsaPublicBytes, watchRsaPublicBytes, phoneEcdsaPrivateBytes, phoneRsaPrivateBytes)
        LongTermStorage.storeMPKeysForClass("A", keys)
        KeyStoreHelper.enrollAovercEcdsaPrivateKey(keys.friendlyEcdsaPrivateKey())
        KeyStoreHelper.enrollAovercRsaPrivateKey(keys.friendlyRsaPrivateKey())
    }

    private fun ecdsaGetPublicBytes(ecPublicKey: ECPublicKey): ByteArray {
        val ecPoint = ecPublicKey.q
        return "04".fromHex() + ecPoint.affineXCoord.encoded + ecPoint.affineYCoord.encoded
    }
}