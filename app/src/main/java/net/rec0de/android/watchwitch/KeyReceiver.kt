package net.rec0de.android.watchwitch

import android.util.Base64
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.rec0de.android.watchwitch.bitmage.fromIndex
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.aoverc.MPKeys
import java.security.MessageDigest


class KeyReceiver : Thread() {
    private val serverPort = 0x7777
    private val maxDatagramSize = 10000

    var socket: DatagramSocket? = null

    override fun run() {
        val lmessage = ByteArray(maxDatagramSize)
        val packet = DatagramPacket(lmessage, lmessage.size)
        try {
            socket = DatagramSocket(serverPort)
            Logger.log("listening for keys on :$serverPort", 1)
            while (true) {
                socket!!.receive(packet)

                if (currentThread().isInterrupted)
                    break

                val trimmed = packet.data.sliceArray(0 until packet.length)

                val plainKey = LongTermStorage.keyTransitSecret
                val md = MessageDigest.getInstance("SHA-256");
                md.update(plainKey)
                val keyBytes = md.digest()

                val nonce = trimmed.sliceArray(0 until 12)
                val ciphertext = trimmed.fromIndex(12)

                val json = try {
                    Utils.chachaPolyDecrypt(keyBytes, nonce, byteArrayOf(), ciphertext).decodeToString()
                } catch (e: Exception) {
                    Logger.log("failed to decrypt keys, do you have the right key?", 0)
                    continue
                }

                val map = Json.decodeFromString<Map<String, String>>(json)

                // store local private keys
                // (we cannot properly import them into the keystore since the keymaster does not support ed25519 signatures, but we'll at least seal them to be protected at rest)
                if(map.containsKey("al"))
                    LongTermStorage.setKey(LongTermStorage.PRIVATE_CLASS_A, KeyStoreHelper.seal(Base64.decode(map["al"]!!, Base64.DEFAULT)).toBytes())
                if(map.containsKey("cl"))
                    LongTermStorage.setKey(LongTermStorage.PRIVATE_CLASS_C, KeyStoreHelper.seal(Base64.decode(map["cl"]!!, Base64.DEFAULT)).toBytes())
                if(map.containsKey("dl"))
                    LongTermStorage.setKey(LongTermStorage.PRIVATE_CLASS_D, KeyStoreHelper.seal(Base64.decode(map["dl"]!!, Base64.DEFAULT)).toBytes())

                // store remote public keys
                if(map.containsKey("ar"))
                    LongTermStorage.setKey(LongTermStorage.PUBLIC_CLASS_A, Base64.decode(map["ar"]!!, Base64.DEFAULT))
                if(map.containsKey("cr"))
                    LongTermStorage.setKey(LongTermStorage.PUBLIC_CLASS_C, Base64.decode(map["cr"]!!, Base64.DEFAULT))
                if(map.containsKey("dr"))
                    LongTermStorage.setKey(LongTermStorage.PUBLIC_CLASS_D, Base64.decode(map["dr"]!!, Base64.DEFAULT))

                // store inner IPv6 addresses
                if(map.containsKey("lac"))
                    LongTermStorage.setAddress(LongTermStorage.LOCAL_ADDRESS_CLASS_C, map["lac"]!!)
                if(map.containsKey("lad"))
                    LongTermStorage.setAddress(LongTermStorage.LOCAL_ADDRESS_CLASS_D, map["lad"]!!)
                if(map.containsKey("rac"))
                    LongTermStorage.setAddress(LongTermStorage.REMOTE_ADDRESS_CLASS_C, map["rac"]!!)
                if(map.containsKey("rad"))
                    LongTermStorage.setAddress(LongTermStorage.REMOTE_ADDRESS_CLASS_D, map["rad"]!!)

                // store IDS keys and UUID
                if(map.containsKey("idsLocalUUID"))
                    LongTermStorage.setUTUNDeviceID(map["idsLocalUUID"]!!)
                if(map.containsKey("idsLocalClassARsa") && map.containsKey("idsLocalClassAEcdsa") && map.containsKey("idsRemoteClassA")) {
                    val privateEcdsaBytes = Base64.decode(map["idsLocalClassAEcdsa"]!!, Base64.DEFAULT)
                    val privateRsaBytes = Base64.decode(map["idsLocalClassARsa"]!!, Base64.DEFAULT)
                    val publicDerBytes = Base64.decode(map["idsRemoteClassA"]!!, Base64.DEFAULT)

                    val keys = MPKeys.fromSentKeys(publicDerBytes, privateEcdsaBytes, privateRsaBytes)
                    LongTermStorage.storeMPKeysForClass("A", keys)
                    KeyStoreHelper.enrollAovercEcdsaPrivateKey(keys.friendlyEcdsaPrivateKey())
                    KeyStoreHelper.enrollAovercRsaPrivateKey(keys.friendlyRsaPrivateKey())
                }

                RoutingManager.registerAddresses()
                Logger.log("got keys!", 0)
                Logger.log("remote public (C/D):", 1)
                Logger.log(Base64.decode(map["cr"]!!, Base64.DEFAULT).hex(), 1)
                Logger.log(Base64.decode(map["dr"]!!, Base64.DEFAULT).hex(), 1)
            }
        }
        catch (e: Throwable) {
            e.printStackTrace()
        }
        finally {
            socket?.close()
            Logger.log("KeyReceiver exited", 0)
        }
    }
}