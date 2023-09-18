package net.rec0de.android.watchwitch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import net.rec0de.android.watchwitch.shoes.NetworkStats
import net.rec0de.android.watchwitch.shoes.SHOES_MSG_CONNECTIVITY
import net.rec0de.android.watchwitch.shoes.ShoesService


class MainActivity : AppCompatActivity() {
    private lateinit var statusLabel: TextView
    private lateinit var packetLog: TextView
    private val localIP = Utils.getLocalIP()
    private var udpHandler: UDPHandler? = null

    private val keyReceiver = KeyReceiver()

    // setup for IPC with the SHOES process, see https://developer.android.com/guide/components/bound-services
    private var mService: Messenger? = null
    private var bound: Boolean = false
    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mService = Messenger(service)
            bound = true
        }
        override fun onServiceDisconnected(className: ComponentName) {
            mService = null
            bound = false
        }
    }


    fun updateNetworkFlags() {
        if (!bound ) return
        RoutingManager.updateNetworkFlags(mService!!)
    }

    override fun onStart() {
        super.onStart()
        // Bind to the service.
        Intent(this, ShoesService::class.java).also { intent ->
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from the service.
        if (bound) {
            unbindService(mConnection)
            bound = false
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusLabel = findViewById(R.id.tvState)
        packetLog = findViewById(R.id.tvPacketLog)

        Logger.setMainActivity(this)

        val serverToggle: SwitchMaterial = findViewById(R.id.swToggleServer)
        serverToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                udpHandler?.kill() // kill existing instances if weird UI state
                statusLabel.text = getString(R.string.status_starting)
                udpHandler = UDPHandler(this, 5000)
                udpHandler!!.start()
            } else {
                statusLabel.text = getString(R.string.status_stopping)
                udpHandler?.kill()
            }
        }

        val networkLogButton: Button = findViewById(R.id.btnLogNetwork)
        networkLogButton.setOnClickListener {
            updateNetworkFlags()
            val netLog = Intent(this@MainActivity, NetworkLogActivity::class.java)
            this@MainActivity.startActivity(netLog)
        }

        val healthLogButton: Button = findViewById(R.id.btnHealthLog)
        healthLogButton.setOnClickListener {
            val healthLog = Intent(this@MainActivity, HealthLogActivity::class.java)
            this@MainActivity.startActivity(healthLog)
        }

        LongTermStorage.context = applicationContext

        keyReceiver.start()
        AddressAllocator().start()
        RoutingManager.startup(this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)

        startForegroundService(Intent(applicationContext, TcpServerService::class.java))
        startForegroundService(Intent(applicationContext, ShoesService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(applicationContext, ShoesService::class.java))
        stopService(Intent(applicationContext, TcpServerService::class.java))
        keyReceiver.socket?.close()
        udpHandler?.kill()
    }

    fun logData(data: String) {
        packetLog.append(data.removeSuffix("\n") + "\n")
    }

    fun statusListening(port: Int) {
        statusLabel.text = getString(R.string.status_listening, localIP, port)
    }

    fun statusIdle() {
        statusLabel.text = getString(R.string.status_not_running)
    }

    fun setError(msg: String) {
        statusLabel.text = getString(R.string.status_error, msg)
    }
}