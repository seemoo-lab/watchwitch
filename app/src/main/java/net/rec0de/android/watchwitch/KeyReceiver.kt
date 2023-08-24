package net.rec0de.android.watchwitch

import android.util.Base64
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.rec0de.android.watchwitch.decoders.aoverc.MPKeys


class KeyReceiver(private val main: MainActivity) : Thread() {
    private val serverPort = 0x7777
    private val maxDatagramSize = 10000
    private var bKeepRunning = true

    override fun run() {
        val lmessage = ByteArray(maxDatagramSize)
        val packet = DatagramPacket(lmessage, lmessage.size)
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(serverPort)
            Logger.log("listening for keys on :$serverPort", 1)
            while (bKeepRunning) {
                socket.receive(packet)
                val trimmed = packet.data.sliceArray(0 until packet.length)

                val key = "witchinthewatch-'#s[MZu!Xv*UZjbt".encodeToByteArray()
                val nonce = trimmed.sliceArray(0 until 12)
                val ciphertext = trimmed.fromIndex(12)
                val json = Utils.chachaPolyDecrypt(key, nonce, byteArrayOf(), ciphertext).decodeToString()

                val map = Json.decodeFromString<Map<String, String>>(json)

                // store local private keys
                if(map.containsKey("al"))
                    LongTermStorage.setKey(LongTermStorage.PRIVATE_CLASS_A, Base64.decode(map["al"]!!, Base64.DEFAULT))
                if(map.containsKey("cl"))
                    LongTermStorage.setKey(LongTermStorage.PRIVATE_CLASS_C, Base64.decode(map["cl"]!!, Base64.DEFAULT))
                if(map.containsKey("dl"))
                    LongTermStorage.setKey(LongTermStorage.PRIVATE_CLASS_D, Base64.decode(map["dl"]!!, Base64.DEFAULT))

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
                }

                RoutingManager.registerAddresses()
                Logger.log("got keys! $map", 0)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        finally {
            socket?.close()
            main.runOnUiThread { main.statusIdle() }
        }
    }

    fun kill() {
        bKeepRunning = false
    }
}