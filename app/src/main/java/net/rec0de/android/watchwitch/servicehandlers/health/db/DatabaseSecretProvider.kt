package net.rec0de.android.watchwitch.servicehandlers.health.db


import android.content.Context
import net.rec0de.android.watchwitch.KeyStoreHelper.SealedData
import net.rec0de.android.watchwitch.KeyStoreHelper.seal
import net.rec0de.android.watchwitch.KeyStoreHelper.unseal
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.bitmage.fromHex
import net.rec0de.android.watchwitch.bitmage.hex
import java.security.SecureRandom
import kotlin.concurrent.Volatile

/*
    based on Signal Android Sources
    https://github.com/signalapp/Signal-Android/blob/6754fef164dffb7f21d40c55ea63c45f10841464/app/src/main/java/org/thoughtcrime/securesms/crypto/DatabaseSecretProvider.java
    https://github.com/signalapp/Signal-Android/blob/6754fef164dffb7f21d40c55ea63c45f10841464/app/src/main/java/org/thoughtcrime/securesms/crypto/DatabaseSecret.java
 */

object DatabaseSecretProvider {
    @Volatile
    private lateinit var instance: DatabaseSecret

    // cached access to database secret
    fun getOrCreateDatabaseSecret(context: Context): DatabaseSecret {
        if (!DatabaseSecretProvider::instance.isInitialized) {
            synchronized(DatabaseSecretProvider::class.java) {
                if (!DatabaseSecretProvider::instance.isInitialized) {
                    instance = getOrCreate()
                }
            }
        }
        return instance
    }

    private fun getOrCreate(): DatabaseSecret {
        val encryptedSecret = LongTermStorage.encryptedDatabaseSecret
        return if (encryptedSecret != null)
                getEncryptedDatabaseSecret(encryptedSecret)
            else
                createAndStoreDatabaseSecret()
    }

    private fun getEncryptedDatabaseSecret(encryptedSecret: ByteArray): DatabaseSecret {
        val sealed: SealedData = SealedData.fromBytes(encryptedSecret)
        return DatabaseSecret(unseal(sealed))
    }

    private fun createAndStoreDatabaseSecret(): DatabaseSecret {
        val random = SecureRandom()
        val secret = ByteArray(32)
        random.nextBytes(secret)
        val databaseSecret = DatabaseSecret(secret)
        val encryptedSecret = seal(databaseSecret.asBytes())
        LongTermStorage.encryptedDatabaseSecret = encryptedSecret.toBytes()
        return databaseSecret
    }
}

class DatabaseSecret {
    private val key: ByteArray
    private val encoded: String

    constructor(key: ByteArray) {
        this.key = key
        encoded = key.hex()
    }

    constructor(encoded: String) {
        key = encoded.fromHex()
        this.encoded = encoded
    }

    fun asString(): String {
        return encoded
    }

    fun asBytes(): ByteArray {
        return key
    }
}