package net.rec0de.android.watchwitch.watchsim

import net.rec0de.android.watchwitch.KeyStoreHelper
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.alloy.AlloyController
import net.rec0de.android.watchwitch.bitmage.fromHex
import net.rec0de.android.watchwitch.decoders.aoverc.MPKeys
import net.rec0de.android.watchwitch.servicehandlers.health.HealthSync
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom


class SimulatedWatch {

    private lateinit var watchKeys: MPKeys
    private lateinit var phoneKeys: MPKeys

    fun start() {

        AlloyController.simulateNeverInitiate()
        installKeys()

        val controller = AlloyControlClient()
        controller.start()

        WatchStateMockup(controller)
        val shoes = ShoesMockup()
        shoes.start()

        HealthDataMockup(watchKeys, controller)
    }

    fun installKeys() {

        // "Watch" ECDSA keys
        val ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1")
        val generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        generator.initialize(ecSpec, SecureRandom())
        val watchEcdsaKeys: KeyPair = generator.generateKeyPair()
        val watchEcdsaPublicBytes = ecdsaGetPublicBytes(watchEcdsaKeys.public as ECPublicKey)
        val watchEcdsaPrivateBytes = ecdsaGetPublicBytes(watchEcdsaKeys.public as ECPublicKey) + (watchEcdsaKeys.private as ECPrivateKey).d.toByteArray()

        // "Watch" RSA keys
        val rsaGen = KeyPairGenerator.getInstance("RSA")
        rsaGen.initialize(1280)
        val watchRsaKeys = rsaGen.generateKeyPair()
        // java can be incredibly annoying about key encodings
        // these are the magic words to get it to output PKCS#1
        val watchRsaPublicBytes = SubjectPublicKeyInfo.getInstance(watchRsaKeys.public.encoded).parsePublicKey().encoded
        val watchRsaPrivateBytes = PrivateKeyInfo.getInstance(watchRsaKeys.private.encoded).parsePrivateKey().toASN1Primitive().encoded

        // Phone ECDSA keys
        val phoneEcdsaKeys: KeyPair = generator.generateKeyPair()
        // the private key we get is actually the public key (65B) followed by the private key (32B)
        val phoneEcdsaPrivateBytes = ecdsaGetPublicBytes(phoneEcdsaKeys.public as ECPublicKey) + (phoneEcdsaKeys.private as ECPrivateKey).d.toByteArray()
        val phoneEcdsaPublicBytes = ecdsaGetPublicBytes(phoneEcdsaKeys.public as ECPublicKey)

        // Phone RSA keys
        val phoneRsaKeys = rsaGen.generateKeyPair()
        val privBytes = phoneRsaKeys.private.encoded
        val phoneRsaPrivateBytes = PrivateKeyInfo.getInstance(privBytes).parsePrivateKey().toASN1Primitive().encoded
        val phoneRsaPublicBytes = SubjectPublicKeyInfo.getInstance(phoneRsaKeys.public.encoded).parsePublicKey().encoded

        val keys = MPKeys(watchEcdsaPublicBytes, phoneRsaPrivateBytes, phoneEcdsaPrivateBytes, watchRsaPublicBytes)
        phoneKeys = keys
        LongTermStorage.storeMPKeysForClass("A", keys)
        KeyStoreHelper.enrollAovercEcdsaPrivateKey(keys.friendlyEcdsaPrivateKey())
        KeyStoreHelper.enrollAovercRsaPrivateKey(keys.friendlyRsaPrivateKey())

        HealthSync.reloadKeys()

        watchKeys = MPKeys(phoneEcdsaPublicBytes, watchRsaPrivateBytes, watchEcdsaPrivateBytes, phoneRsaPublicBytes)
    }

    private fun ecdsaGetPublicBytes(ecPublicKey: ECPublicKey): ByteArray {
        val ecPoint = ecPublicKey.q
        return "04".fromHex() + ecPoint.affineXCoord.encoded + ecPoint.affineYCoord.encoded
    }
}