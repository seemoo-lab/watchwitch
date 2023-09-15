package net.rec0de.android.watchwitch.shoes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.TcpServer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket

class ShoesService : Service() {

    private lateinit var serverSocket: ServerSocket

    private val runnable = Runnable {
        var socket: Socket? = null
        try {
            serverSocket = ServerSocket(62742)
            while (true) {
                socket = serverSocket.accept()
                val dataInputStream = DataInputStream(socket.getInputStream())
                val dataOutputStream = DataOutputStream(socket.getOutputStream())
                GlobalScope.launch { ShoesProxyHandler.handleConnection(dataInputStream, dataOutputStream) }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            if(e is BindException) {
                Logger.setError("couldn't bind SHOES port 62742")
            }
            try {
                serverSocket.close()
                socket?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        startMeForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logShoes("Shoes service started", 0)
        Thread(runnable).start()
        return START_STICKY
    }

    override fun onDestroy() {
        Logger.logShoes("Shoes service destroyed", 0)
        serverSocket.close()
    }

    private fun startMeForeground() {
        val channelId = packageName
        val channelName = "WatchWitch SHOES proxy service"
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
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("WatchWitch is running in the background")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(2, notification)
    }
}