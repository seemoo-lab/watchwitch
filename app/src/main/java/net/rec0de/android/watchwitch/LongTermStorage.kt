package net.rec0de.android.watchwitch

import android.annotation.SuppressLint
import android.content.Context
import net.rec0de.android.watchwitch.decoders.aoverc.MPKeys
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

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

    private const val MP_KEY_PREFIX = "mp."

    private val addresstypes = listOf(LOCAL_ADDRESS_CLASS_C, LOCAL_ADDRESS_CLASS_D, REMOTE_ADDRESS_CLASS_C, REMOTE_ADDRESS_CLASS_D)

    fun getMPKeysForService(service: String): MPKeys? {
        val ecdsa = getKey("$MP_KEY_PREFIX$service.ecdsa.public")
        val rsa = getKey("$MP_KEY_PREFIX$service.rsa.private")
        return if(ecdsa != null && rsa != null) MPKeys(ecdsa, rsa) else null
    }

    fun getEd25519RemotePublicKey(type: String): Ed25519PublicKeyParameters {
        if (type != PUBLIC_CLASS_A && type != PUBLIC_CLASS_C && type != PUBLIC_CLASS_D)
            throw Exception("trying to get public key for invalid type $type")
        return Ed25519PublicKeyParameters(getKey(type))
    }

    fun getEd25519LocalPrivateKey(type: String): Ed25519PrivateKeyParameters {
        if (type != PRIVATE_CLASS_A && type != PRIVATE_CLASS_C && type != PRIVATE_CLASS_D)
            throw Exception("trying to get private key for invalid type $type")
        return Ed25519PrivateKeyParameters(getKey(type))
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

    fun getKey(type: String): ByteArray? {
        return context.getSharedPreferences("$appID.prefs", Context.MODE_PRIVATE).getString(type, null)?.hexBytes()
    }

    fun setKey(type: String, value: ByteArray) {
        with (context.getSharedPreferences("$appID.prefs", Context.MODE_PRIVATE).edit()) {
            putString(type, value.hex())
            apply()
        }
    }
}