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
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import net.rec0de.android.watchwitch.servicehandlers.health.db.HealthSyncHelper
import net.rec0de.android.watchwitch.servicehandlers.health.db.HealthSyncSecureHelper
import net.rec0de.android.watchwitch.shoes.ShoesProxyHandler
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean


// adapted from https://perihanmirkelam.medium.com/socket-programming-on-android-tcp-server-example-e4552a957c08
class TcpServerService : Service() {
    private val idsA = TcpServer(61314)
    private val idsB = TcpServer(61315)

    private val runnable = Runnable {
        //val terminus = TcpServer(62742)
        //terminus.start()
        idsA.start()
        idsB.start()

        idsA.join()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        startMeForeground()
        Thread(runnable).start()
        DatabaseWrangler.initDbHelper(HealthSyncSecureHelper(baseContext), HealthSyncHelper(baseContext))
    }

    override fun onDestroy() {
        idsA.kill()
        idsB.kill()
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
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("WatchWitch is running in the background")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(2, notification)
    }
}

class TcpServer(private val port: Int) : Thread() {

    private lateinit var serverSocket: ServerSocket
    private var shouldRun = true

    fun kill() {
        serverSocket.close()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun run() {
        var socket: Socket? = null
        try {
            serverSocket = ServerSocket(port)
            while (shouldRun) {
                socket = serverSocket.accept()
                val dataInputStream = DataInputStream(socket.getInputStream())
                val dataOutputStream = DataOutputStream(socket.getOutputStream())

                // Use global coroutines for each client to communicate with them simultaneously
                if(port == 62742)
                    GlobalScope.launch { ShoesProxyHandler.handleConnection(dataInputStream, dataOutputStream) }
                else {
                    Logger.logIDS("IDS connect on $port: src port ${socket.port}", 3)
                    GlobalScope.launch{ NWSCManager.readFromSocket(dataInputStream, dataOutputStream, port) }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            if(e is BindException) {
                Logger.setError("couldn't bind Alloy port $port")
            }
            try {
                serverSocket.close()
                socket?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }

        Logger.logIDS("Alloy server exited", 0)
    }
}

