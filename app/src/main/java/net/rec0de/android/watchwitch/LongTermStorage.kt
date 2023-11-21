package net.rec0de.android.watchwitch

import android.annotation.SuppressLint
import android.content.Context
import net.rec0de.android.watchwitch.decoders.aoverc.MPKeys
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.util.UUID

@SuppressLint("StaticFieldLeak")
object LongTermStorage {
    lateinit var context: Context
    private const val appID = "net.rec0de.android.watchwitch"

    const val PUBLIC_CLASS_A = "remote.a.public"
    const val PUBLIC_CLASS_C = "remote.c.public"
    const val PUBLIC_CLASS_D = "remote.d.public"

    const val PRIVATE_CLASS_A = "local.a.private"
    const val PRIVATE_CLASS_C = "local.c.private"
    const val PRIVATE_CLASS_D = "local.d.private"

    const val LOCAL_ADDRESS_CLASS_C = "local.c.address"
    const val LOCAL_ADDRESS_CLASS_D = "local.d.address"
    const val REMOTE_ADDRESS_CLASS_C = "remote.c.address"
    const val REMOTE_ADDRESS_CLASS_D = "remote.d.address"

    const val KEY_TRANSIT_SECRET = "keyreceiver.sharedsecret"

    private const val MP_KEY_PREFIX = "mp."

    private val addresstypes = listOf(LOCAL_ADDRESS_CLASS_C, LOCAL_ADDRESS_CLASS_D, REMOTE_ADDRESS_CLASS_C, REMOTE_ADDRESS_CLASS_D)

    fun getMPKeysForClass(protectionClass: String): MPKeys? {
        val ecdsaRemotePub = getKey("$MP_KEY_PREFIX$protectionClass.ecdsa.remote.public")
        val rsaLocalPriv = getKey("$MP_KEY_PREFIX$protectionClass.rsa.local.private")
        val ecdsaLocalPriv = getKey("$MP_KEY_PREFIX$protectionClass.ecdsa.local.private")
        val rsaRemotePub = getKey("$MP_KEY_PREFIX$protectionClass.rsa.remote.public")
        return if(ecdsaRemotePub != null && rsaLocalPriv != null && ecdsaLocalPriv != null && rsaRemotePub != null)
                MPKeys(ecdsaRemotePub, rsaLocalPriv, ecdsaLocalPriv, rsaRemotePub)
            else
                null
    }

    fun storeMPKeysForClass(dataProtectionClass: String, keys: MPKeys) {
        setKey("$MP_KEY_PREFIX$dataProtectionClass.ecdsa.remote.public", keys.ecdsaRemotePublic)
        setKey("$MP_KEY_PREFIX$dataProtectionClass.rsa.remote.public", keys.rsaRemotePublic)
        setKey("$MP_KEY_PREFIX$dataProtectionClass.ecdsa.local.private", keys.ecdsaLocalPrivate)
        setKey("$MP_KEY_PREFIX$dataProtectionClass.rsa.local.private", keys.rsaLocalPrivate)
    }

    fun getUTUNDeviceID(): UUID? {
        val str = context.getSharedPreferences("$appID.prefs", Context.MODE_PRIVATE).getString("misc.utun.localuuid", null)
        return if(str == null) null else UUID.fromString(str)
    }

    fun setUTUNDeviceID(uuid: String) {
        with (context.getSharedPreferences("$appID.prefs", Context.MODE_PRIVATE).edit()) {
            putString("misc.utun.localuuid", uuid)
            apply()
        }
    }

    fun getEd25519RemotePublicKey(type: String): Ed25519PublicKeyParameters {
        if (type != PUBLIC_CLASS_A && type != PUBLIC_CLASS_C && type != PUBLIC_CLASS_D)
            throw Exception("trying to get public key for invalid type $type")
        val bytes = getKey(type)
        if(bytes == null) {
            Logger.setError("uninitialized keys")
            throw Exception("Trying to get uninitialized key: $type")
        }
        return Ed25519PublicKeyParameters(bytes)
    }

    fun getEd25519LocalPrivateKey(type: String): Ed25519PrivateKeyParameters {
        if (type != PRIVATE_CLASS_A && type != PRIVATE_CLASS_C && type != PRIVATE_CLASS_D)
            throw Exception("trying to get private key for invalid type $type")
        val bytes = getKey(type)
        if(bytes == null) {
            Logger.setError("uninitialized keys")
            throw Exception("Trying to get uninitialized key: $type")
        }
        return Ed25519PrivateKeyParameters(bytes)
    }

    fun getAddress(type: String): String? {
        if (!addresstypes.contains(type))
            throw Exception("trying to get address for invalid type $type")
        return context.getSharedPreferences("$appID.prefs", Context.MODE_PRIVATE).getString(type, null)
    }

    fun setAddress(type: String, value: String) {
        if (!addresstypes.contains(type))
            throw Exception("trying to set address for invalid type $type")
        with (context.getSharedPreferences("$appID.prefs", Context.MODE_PRIVATE).edit()) {
            putString(type, value)
            apply()
        }
    }

    fun getKeyTransitSecret(): ByteArray {
        return getKey(KEY_TRANSIT_SECRET) ?: "witchinthewatch-'#s[MZu!Xv*UZjbt".encodeToByteArray()
    }

    fun setKeyTransitSecret(secret: String) {
        setKey(KEY_TRANSIT_SECRET, secret.encodeToByteArray())
    }

    fun resetKeyTransitSecret() {
        with (context.getSharedPreferences("$appID.prefs", Context.MODE_PRIVATE).edit()) {
            remove(KEY_TRANSIT_SECRET)
            apply()
        }
    }

    private fun getKey(type: String): ByteArray? {
        return context.getSharedPreferences("$appID.prefs", Context.MODE_PRIVATE).getString(type, null)?.hexBytes()
    }

    fun setKey(type: String, value: ByteArray) {
        with (context.getSharedPreferences("$appID.prefs", Context.MODE_PRIVATE).edit()) {
            putString(type, value.hex())
            apply()
        }
    }
}