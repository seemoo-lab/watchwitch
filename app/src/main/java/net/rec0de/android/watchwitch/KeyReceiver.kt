package net.rec0de.android.watchwitch

import android.util.Base64
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json


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
            main.runOnUiThread { main.logData("listening for keys on :$serverPort") }
            while (bKeepRunning) {
                socket.receive(packet)
                val trimmed = packet.data.sliceArray(0 until packet.length)

                val key = "witchinthewatch-'#s[MZu!Xv*UZjbt".encodeToByteArray()
                val nonce = trimmed.sliceArray(0 until 12)
                val ciphertext = trimmed.fromIndex(12)
                val json = Utils.chachaPolyDecrypt(key, nonce, byteArrayOf(), ciphertext).decodeToString()

                val map = Json.decodeFromString<Map<String, String>>(json).mapValues { Base64.decode(it.value, Base64.DEFAULT) }

                // store local private keys
                if(map.containsKey("al"))
                    LongTermKeys.setKey(LongTermKeys.PRIVATE_CLASS_A, map["al"]!!)
                if(map.containsKey("cl"))
                    LongTermKeys.setKey(LongTermKeys.PRIVATE_CLASS_C, map["cl"]!!)
                if(map.containsKey("dl"))
                    LongTermKeys.setKey(LongTermKeys.PRIVATE_CLASS_D, map["dl"]!!)

                // store remote public keys
                if(map.containsKey("ar"))
                    LongTermKeys.setKey(LongTermKeys.PUBLIC_CLASS_A, map["ar"]!!)
                if(map.containsKey("cr"))
                    LongTermKeys.setKey(LongTermKeys.PUBLIC_CLASS_C, map["cr"]!!)
                if(map.containsKey("dr"))
                    LongTermKeys.setKey(LongTermKeys.PUBLIC_CLASS_D, map["dr"]!!)

                main.runOnUiThread { main.logData("got keys! $map") }
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