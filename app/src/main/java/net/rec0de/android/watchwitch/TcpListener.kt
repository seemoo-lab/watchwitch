package net.rec0de.android.watchwitch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean


// adapted from https://perihanmirkelam.medium.com/socket-programming-on-android-tcp-server-example-e4552a957c08
class TcpServerService : Service() {
    private val working = AtomicBoolean(true)

    private val runnable = Runnable {
        val terminus = TcpServer(62742)
        val idsA = TcpServer(61314)
        val idsB = TcpServer(61315)
        val lockdown = TcpServer(62078)

        terminus.start()
        idsA.start()
        idsB.start()
        lockdown.start()

        lockdown.join()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        startMeForeground()
        Thread(runnable).start()
    }

    override fun onDestroy() {
        working.set(false)
    }

    private fun startMeForeground() {
        val channelId = packageName
        val channelName = "WatchWitch TCP Servers"
        val chan = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(false)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("WatchWitch is running in the background")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(2, notification)
    }
}

class TcpServer(private val port: Int) : Thread() {
    override fun run() {
        var socket: Socket? = null
        try {
            val serverSocket = ServerSocket(port)
            while (true) {
                socket = serverSocket.accept()
                Logger.logIDS("IDS connect on $port: src port ${socket.port}", 1)
                val dataInputStream = DataInputStream(socket.getInputStream())
                val dataOutputStream = DataOutputStream(socket.getOutputStream())

                // Use threads for each client to communicate with them simultaneously
                val t: Thread = if(port == 62742)
                        ShoesProxyHandler(dataInputStream, dataOutputStream)
                    else
                        NWServiceConnectorHandler(dataInputStream, dataOutputStream, socket.localPort)

                t.start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            try {
                socket?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
}

class TcpClientHandler(private val dataInputStream: DataInputStream, private val dataOutputStream: DataOutputStream, private val socket: Socket) : Thread() {
    override fun run() {
        try {
            while(true) {
                val length = dataInputStream.readUnsignedShort() // read length of incoming message
                Logger.logIDS("IDS rcv on ${socket.localPort}, trying to read $length bytes", 0)

                if (length > 0) {
                    val message = ByteArray(length)
                    dataInputStream.readFully(message, 0, message.size)
                    Logger.logIDS("rcv IDS on ${socket.localPort}: ${message.hex()}", 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                dataInputStream.close()
                dataOutputStream.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
}

class NWServiceConnectorHandler(private val fromWatch: DataInputStream, private val toWatch: DataOutputStream, private val port: Int) : Thread() {
    override fun run() {
        try {
            // receive NWSC start request
            // see libnetwork.dylib, _nw_service_connector_create_initial_payload_for_request
            val reqLen = fromWatch.readUnsignedShort()

            Logger.logIDS("NWSC rcv trying to read $reqLen bytes", 1)

            val request = ByteArray(reqLen)
            fromWatch.readFully(request, 0, request.size)

            Logger.logIDS("NWSC rcv req ${request.hex()}", 1)

            // 2 bytes target port
            val targetPort = UInt.fromBytesBig(request.sliceArray(0 until 2)).toInt()
            // 8 byte sequence number (why so long???)
            val seq = request.sliceArray(2 until 10)
            // 16 byte UUID
            val uuid = request.sliceArray(10 until 26)
            // 1 byte service name len
            val serviceNameLen = request[26].toUByte().toInt()
            // var length service name
            val serviceName = request.sliceArray(27 until 27+serviceNameLen).toString(Charsets.UTF_8)
            // 64 byte ed25519 signature
            val signature = request.fromIndex(27+serviceNameLen)

            if(targetPort != port)
                throw Exception("Got NWSC request for port $targetPort on port $port")

            Logger.logIDS("NWSC req for $serviceName on $port", 0)

            // send a very crude acknowledge, roughly like _nwsc_send_feedback but likely with garbage data
            val ack = ByteBuffer.allocate(0x2c)
            ack.putShort(0x2a) // message length
            ack.putShort(0x80) // connection accept flag
            ack.putLong(NWSCState.freshSequenceNumber()) // sequence number
            ack.put(LongTermStorage.getKey(LongTermStorage.PUBLIC_CLASS_C)!!) // ed25519 pubkey - should be a different key than the terminus IKE keys, but we'll just put this for now

            val payload = ack.array()
            Logger.logIDS("NWSC snd accept ${payload.hex()}", 2)
            toWatch.write(payload)
            toWatch.flush()

            NWServiceConnectorInitiator(port, serviceName).start()

            while(true) {
                val length = fromWatch.readUnsignedShort() // read length of incoming message
                Logger.logIDS("IDS rcv on ${port}, trying to read $length bytes", 0)

                if (length > 0) {
                    val message = ByteArray(length)
                    fromWatch.readFully(message, 0, message.size)
                    Logger.logIDS("rcv IDS on ${port}: ${message.hex()}", 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                fromWatch.close()
                toWatch.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
}

class NWServiceConnectorInitiator(private val port: Int, private val serviceName: String) : Thread() {

    override fun run() {
        val socket = Socket(LongTermStorage.getAddress(LongTermStorage.REMOTE_ADDRESS_CLASS_C), port) // does it matter which address we use here?
        val toWatch = socket.getOutputStream()
        val fromWatch = DataInputStream(socket.getInputStream())

        try {
            // send NWSC start request

            // 2 byte port, 8 byte seq, 16 byte uuid, 1 byte sname len, service name, 64 byte signature
            val reqLen = 2 + 8 + 16 + (serviceName.length + 1) + 64
            val req = ByteBuffer.allocate(reqLen + 2)
            req.putShort(reqLen.toShort())
            req.putLong(NWSCState.freshSequenceNumber())

            val uuid = UUID.randomUUID()
            req.putLong(uuid.mostSignificantBits)
            req.putLong(uuid.leastSignificantBits)

            req.put(serviceName.length.toByte())
            req.put(serviceName.toByteArray(Charsets.US_ASCII))

            val signer = Ed25519Signer()
            signer.init(true, LongTermStorage.getEd25519PrivateKey(LongTermStorage.PRIVATE_CLASS_C))
            signer.update(req.array(), 0, reqLen+2)
            val sig = signer.generateSignature()
            req.put(sig)

            val payload = req.array()
            Logger.logIDS("NWSC snd req ${payload.hex()}", 1)
            toWatch.write(payload)
            toWatch.flush()

            while(true) {
                val length = fromWatch.readUnsignedShort() // read length of incoming message
                Logger.logIDS("IDS rcv on ${port}, trying to read $length bytes", 0)

                if (length > 0) {
                    val message = ByteArray(length)
                    fromWatch.readFully(message, 0, message.size)
                    Logger.logIDS("rcv IDS on ${port}: ${message.hex()}", 1)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            try {
                fromWatch.close()
                toWatch.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
}