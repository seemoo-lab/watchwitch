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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
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
                        TLSProxyHandler(dataInputStream, dataOutputStream)
                    else
                        TcpClientHandler(dataInputStream, dataOutputStream, socket)

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

class TLSProxyHandler(private val fromWatch: DataInputStream, private val toWatch: DataOutputStream) : Thread() {
    override fun run() {
        try {
            val headerLen = fromWatch.readUnsignedShort()

            Logger.logIDS("TLS rcv trying to read $headerLen header bytes", 1)

            val header = ByteArray(headerLen)
            fromWatch.readFully(header, 0, header.size)

            // connection request header starts with 0x0101bb (why?), then hostname length and hostname
            val headerMagic = header.slice(0 until 3)
            val hostnameLen = header[3].toUByte().toInt()
            val hostname = header.sliceArray(4 until 4+hostnameLen).toString(Charsets.UTF_8)

            Logger.logIDS("TLS req to $hostname", 0)

            // next up we have what looks like TLVs
            var offset = 4 + hostnameLen
            while(offset < headerLen) {
                val type = header[offset].toInt()
                val len = UInt.fromBytesBig(header.sliceArray(offset+1 until offset+3)).toInt()
                offset += 3
                val content = header.sliceArray(offset until offset+len)
                if(type == 0x03)
                    Logger.logIDS("from process ${content.toString(Charsets.UTF_8)}", 1)
                else
                    Logger.logIDS("TLS req unknown TLV: type $type, payload ${content.hex()}", 1)
                offset += len
            }

            val clientSocket = Socket(hostname, 443)
            val toRemote = clientSocket.getOutputStream()
            val fromRemote = DataInputStream(clientSocket.getInputStream())

            // send acknowledge, exact purpose unknown
            toWatch.write("0006000004000120".hexBytes())
            toWatch.flush()

            TLSProxyReplyForwarder(fromRemote, toWatch).start()

            while(true) {
                // we expect a TLS record header here, which is 1 byte record type, 2 bytes TLS version, 2 bytes length
                val recordHeader = ByteArray(5)
                fromWatch.readFully(recordHeader)
                val len = UInt.fromBytesBig(recordHeader.sliceArray(3 until 5)).toInt()
                val packet = ByteArray(5+len)
                recordHeader.copyInto(packet)
                fromWatch.readFully(packet, 5, len)

                Logger.logIDS("TLS to remote: ${packet.hex()}", 3)
                toRemote.write(packet)
                toRemote.flush()
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

class TLSProxyReplyForwarder(private val fromRemoteStream: DataInputStream, private val toLocalStream: OutputStream) : Thread() {
    override fun run() {
        try {
            while(true) {
                // we expect a TLS record header here, which is 1 byte record type, 2 bytes TLS version, 2 bytes length
                val recordHeader = ByteArray(5)
                fromRemoteStream.readFully(recordHeader)
                val len = UInt.fromBytesBig(recordHeader.sliceArray(3 until 5)).toInt()
                val packet = ByteArray(5+len)
                recordHeader.copyInto(packet)
                fromRemoteStream.readFully(packet, 5, len)

                Logger.logIDS("TLS from remote: ${packet.hex()}", 3)
                toLocalStream.write(packet)
                toLocalStream.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                fromRemoteStream.close()
                toLocalStream.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
}