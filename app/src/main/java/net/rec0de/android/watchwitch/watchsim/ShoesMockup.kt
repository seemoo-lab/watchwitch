package net.rec0de.android.watchwitch.watchsim

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.rec0de.android.watchwitch.bitmage.ByteOrder
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.fromHex
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.bitmage.toBytes
import net.rec0de.android.watchwitch.shoes.ShoesHostnameRequest
import net.rec0de.android.watchwitch.shoes.ShoesReply
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import kotlin.random.Random

// based on https://stackoverflow.com/questions/38162775/really-simple-tcp-client
class ShoesMockup() : Thread() {

    private var toPhone: DataOutputStream? = null
    private var fromPhone: DataInputStream? = null

    private val mockedBundles = listOf("com.watchwitch.weather.watchapp", "com.watchwitch.spyware", "com.watchwitch.logd", "com.watchwitch.sysdiagnose", "com.watchwitch.NanoMaps")
    private val mockedHosts = listOf("example.com", "example.org", "example.edu", "example.net")

    private fun sendMessage(message: ByteArray) {
        val runnable = Runnable {
            if (toPhone != null) {
                try {
                    toPhone!!.write(message)
                    toPhone!!.flush()
                }
                catch (e: Exception) {
                    Log.e("TCP", "C: Error", e)
                }
            }
        }
        val thread = Thread(runnable)
        thread.start()
    }


    fun stopClient() {
        if (toPhone != null) {
            toPhone!!.flush()
            toPhone!!.close()
        }
        fromPhone?.close()
        fromPhone = null
        toPhone = null
    }

    override fun run() {
        try {
            while (true) {
                // make a SHOES request every couple seconds
                runBlocking { delay(Random.nextLong(5000, 10000)) }

                val serverAddr = InetAddress.getByName("127.0.0.1")
                Log.d("SimShoes", "Connecting to Shoes port 62742...")

                //create a socket to make the connection with the server
                val socket = Socket(serverAddr, 62742)
                try {
                    toPhone = DataOutputStream(socket.getOutputStream())
                    fromPhone = DataInputStream(socket.getInputStream())



                    val host = mockedHosts.random()
                    val request = ShoesHostnameRequest(443, host)
                    request.bundleId = mockedBundles.random()

                    Log.d("SimShoes", "Sending connection request: $request")
                    val requestBytes = request.render()
                    val lengthPrefixedRequest = requestBytes
                    Log.d("SimShoes", "Sending connection request: ${lengthPrefixedRequest.hex()}")
                    sendMessage(lengthPrefixedRequest)

                    val headerLen = fromPhone!!.readUnsignedShort()
                    val response = ByteArray(headerLen)
                    fromPhone!!.readFully(response, 0, response.size)
                    val responseObj = ShoesReply.parse(response)
                    Log.d("SimShoes", "Got connection response: $responseObj")

                    if(responseObj.denied) {
                        Log.d("SimShoes", "Connection to $host denied!")
                        socket.close()
                        continue
                    }

                    Log.d("SimShoes", "Sending TLS Client Hello...")
                    sendMessage(tlsHelloForHost(host))

                    val recordHeader = ByteArray(5)
                    fromPhone!!.readFully(recordHeader)
                    val len = Int.fromBytes(recordHeader.sliceArray(3 until 5), ByteOrder.BIG)
                    val packet = ByteArray(5+len)
                    recordHeader.copyInto(packet)
                    fromPhone!!.readFully(packet, 5, len)

                    Log.d("SimShoes", "Got remote response: ${packet.hex()}")

                    runBlocking { delay(1000) }

                } catch (e: Exception) {
                    Log.e("TCP", "S: Error", e)
                } finally {
                    //the socket must be closed. It is not possible to reconnect to this socket
                    // after it is closed, which means a new socket instance has to be created.
                    socket.close()
                }
            }

        } catch (e: Exception) {
            Log.e("TCP", "C: Error", e)
        }
    }


    // create a very basic TLS client hello with SNI
    // see https://tls12.xargs.org/#client-hello/annotated
    private fun tlsHelloForHost(host: String): ByteArray {
        val clientRandom = Random.Default.nextBytes(32)
        val cipherSuitesEtc = "000020cca8cca9c02fc030c02bc02cc013c009c014c00a009c009d002f0035c012000a0100".fromHex()

        val hostBytes = host.encodeToByteArray()
        val sni = "0000".fromHex() + (hostBytes.size+5).toUShort().toBytes(ByteOrder.BIG) + (hostBytes.size+3).toUShort().toBytes(ByteOrder.BIG) + "00".fromHex() + hostBytes.size.toUShort().toBytes(ByteOrder.BIG) + hostBytes
        val extensions = sni + "000500050100000000000a000a0008001d001700180019000b00020100000d0012001004010403050105030601060302010203ff0100010000120000".fromHex()

        val payload = clientRandom + cipherSuitesEtc + extensions.size.toUShort().toBytes(ByteOrder.BIG) + extensions

        val handshake = "0100".fromHex() + (payload.size+2).toUShort().toBytes(ByteOrder.BIG) + "0303".fromHex() + payload

        return "160301".fromHex() + handshake.size.toUShort().toBytes(ByteOrder.BIG) + handshake
    }
}