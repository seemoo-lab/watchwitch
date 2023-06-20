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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.rec0de.android.watchwitch.nwsc.NWSCManager
import net.rec0de.android.watchwitch.shoes.ShoesProxyHandler
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
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
    @OptIn(DelicateCoroutinesApi::class)
    override fun run() {
        var socket: Socket? = null
        try {
            val serverSocket = ServerSocket(port)
            while (true) {
                socket = serverSocket.accept()
                Logger.logIDS("IDS connect on $port: src port ${socket.port}", 2)
                val dataInputStream = DataInputStream(socket.getInputStream())
                val dataOutputStream = DataOutputStream(socket.getOutputStream())

                // Use threads for each client to communicate with them simultaneously
                if(port == 62742)
                    ShoesProxyHandler(dataInputStream, dataOutputStream).start()
                else {
                    GlobalScope.launch{ NWSCManager.readFromSocket(dataInputStream, dataOutputStream, port) }
                }
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

