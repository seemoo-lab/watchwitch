package net.rec0de.android.watchwitch.shoes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.R
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket

const val SHOES_MSG_CONNECTIVITY = 1
const val SHOES_MSG_STATS = 2
const val SHOES_MSG_STATS_RESPONSE = 3
const val SHOES_MSG_FIREWALL_DEFAULT = 4
const val SHOES_MSG_FIREWALL_RULE = 5
const val SHOES_MSG_FIREWALL_PROCESS_RULE = 6

class ShoesService : Service() {

    private lateinit var serverSocket: ServerSocket
    private lateinit var mMessenger: Messenger

    // Running this in a separate process helps with keeping network performance snappy on the watch
    // but comes at the cost of some annoying IPC
    internal inner class IncomingHandler : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                // update our connection flags based on info from the main thread
                SHOES_MSG_CONNECTIVITY -> {
                    val bundle = msg.data
                    val flags = bundle.getInt("netFlags")
                    Logger.logShoes("SHOES received connectivity flags $flags", 1)
                    ShoesProxyHandler.connectionCellular = (flags and 0x01) != 0
                    ShoesProxyHandler.connectionWiFi = (flags and 0x02) != 0
                    ShoesProxyHandler.connectionExpensive = (flags and 0x04) != 0
                }
                SHOES_MSG_STATS -> {
                    // Send message to the client. The message contains server info
                    val message = Message.obtain(this@IncomingHandler, SHOES_MSG_STATS_RESPONSE)
                    val bundle = Bundle()
                    bundle.putString("statsJson", NetworkStats.json())
                    message.data = bundle
                    msg.replyTo.send(message)
                }
                SHOES_MSG_FIREWALL_DEFAULT -> {
                    val bundle = msg.data
                    NetworkStats.allowByDefault = bundle.getBoolean("allowByDefault")
                    Logger.logShoes("SHOES Firewall set default: allow? ${NetworkStats.allowByDefault}", 1)
                    this@ShoesService.firewallStateJson = NetworkStats.json()
                }
                SHOES_MSG_FIREWALL_RULE -> {
                    val bundle = msg.data
                    val host = bundle.getString("host")!!
                    val allow = bundle.getBoolean("allow")
                    NetworkStats.setRule(host, allow)
                    this@ShoesService.firewallStateJson = NetworkStats.json()
                }
                SHOES_MSG_FIREWALL_PROCESS_RULE -> {
                    val bundle = msg.data
                    val host = bundle.getString("process")!!
                    val allow = bundle.getBoolean("allow")
                    NetworkStats.setProcessRule(host, allow)
                    this@ShoesService.firewallStateJson = NetworkStats.json()
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        mMessenger = Messenger(IncomingHandler())
        return mMessenger.binder
    }

    var firewallStateJson: String?
        get() {
            val value = baseContext.getSharedPreferences("${LongTermStorage.appID}.prefs", Context.MODE_PRIVATE)
                .getString(
                    "netstats.state", null
                )
            Logger.log(value ?: "null", 1)
            return value
        }
        set(value) = with (baseContext.getSharedPreferences("${LongTermStorage.appID}.prefs", Context.MODE_PRIVATE).edit()) {
            Logger.log(value!!, 1)
            putString("netstats.state", value)
            apply()
        }

    private val runnable = Runnable {
        var socket: Socket? = null
        try {
            serverSocket = ServerSocket(62742)
            Logger.logShoes("started shoes server on port 62742", 1)
            while (true) {
                socket = serverSocket.accept()
                Logger.logShoes("got shoes connection", 5)
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

    override fun onCreate() {
        startMeForeground()
        val firewallState = firewallStateJson
        if(firewallState != null)
            NetworkStats.fromJson(firewallState)
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
            .setSmallIcon(R.drawable.witch_monochrome_lowdpi)
            .setContentTitle("WatchWitch is running in the background")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(2, notification)
    }
}