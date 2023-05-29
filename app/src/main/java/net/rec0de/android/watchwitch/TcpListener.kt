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
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
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
                println("new client on port $port: $socket")
                //val dataInputStream = DataInputStream(socket.getInputStream())
                val dataInputStream = BufferedReader(InputStreamReader(socket.getInputStream()))
                val dataOutputStream = DataOutputStream(socket.getOutputStream())

                // Use threads for each client to communicate with them simultaneously
                val t: Thread = TcpClientHandler(dataInputStream, dataOutputStream)
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

class TcpClientHandler(private val dataInputStream: BufferedReader, private val dataOutputStream: DataOutputStream) : Thread() {
    override fun run() {
        while (true) {
            try {
                val line = dataInputStream.readLine()
                if(line != null)
                    println("Received: " + dataInputStream.readLine())
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
}