package net.rec0de.android.watchwitch.activities

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.IBinder
import android.os.Messenger
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.LinearLayout.GONE
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.rec0de.android.watchwitch.AddressAllocator
import net.rec0de.android.watchwitch.KeyReceiver
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.NRLPoverUDPhandler
import net.rec0de.android.watchwitch.Poetry
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.RoutingManager
import net.rec0de.android.watchwitch.TcpServerService
import net.rec0de.android.watchwitch.UDPHandler
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.WatchState
import net.rec0de.android.watchwitch.nwsc.NWSCManager
import net.rec0de.android.watchwitch.servicehandlers.messaging.BulletinDistributorService
import net.rec0de.android.watchwitch.shoes.ShoesService
import net.rec0de.android.watchwitch.watchsim.SimulatedWatch


class MainActivity : AppCompatActivity() {
    private lateinit var statusLabel: TextView
    private lateinit var packetLog: TextView
    private val localIP = Utils.getLocalIP()
    private var nrlpHandler: NRLPoverUDPhandler? = null
    private var udpHandler: UDPHandler? = null
    private var altUdpHandler: UDPHandler? = null

    private val keyReceiver = KeyReceiver()

    // setup for IPC with the SHOES process, see https://developer.android.com/guide/components/bound-services
    private var bound: Boolean = false
    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            RoutingManager.shoesServiceMessenger = Messenger(service)
            bound = true
        }
        override fun onServiceDisconnected(className: ComponentName) {
            RoutingManager.shoesServiceMessenger = null
            bound = false
        }
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
        LongTermStorage.context = applicationContext
        System.loadLibrary("sqlcipher")

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
                nrlpHandler = NRLPoverUDPhandler(this, 5757)
                nrlpHandler!!.start()
                //altUdpHandler = UDPHandler(this, 62743)
                //altUdpHandler!!.start()
            } else {
                statusLabel.text = getString(R.string.status_stopping)
                udpHandler?.kill()
                altUdpHandler?.kill()
                NWSCManager.reset()
                WatchState.resetConnectionState()
            }
        }

        val watchStateButton: Button = findViewById(R.id.btnWatchState)
        watchStateButton.setOnClickListener {
            val watchState = Intent(this@MainActivity, WatchStateActivity::class.java)
            this@MainActivity.startActivity(watchState)
        }

        val networkLogButton: Button = findViewById(R.id.btnLogNetwork)
        networkLogButton.setOnClickListener {
            val netLog = Intent(this@MainActivity, NetworkLogActivity::class.java)
            this@MainActivity.startActivity(netLog)
        }

        val healthLogButton: Button = findViewById(R.id.btnHealthLog)
        healthLogButton.setOnClickListener {
            val healthLog = Intent(this@MainActivity, HealthLogActivity::class.java)
            this@MainActivity.startActivity(healthLog)
        }

        val fileViewButton: Button = findViewById(R.id.btnFiles)
        fileViewButton.setOnClickListener {
            val healthLog = Intent(this@MainActivity, FilesActivity::class.java)
            this@MainActivity.startActivity(healthLog)
        }

        val chatButton: Button = findViewById(R.id.btnChitchat)
        chatButton.setOnClickListener {
            val healthLog = Intent(this@MainActivity, ChatActivity::class.java)
            this@MainActivity.startActivity(healthLog)
        }

        val simButton: Button = findViewById(R.id.btnSimWatch)
        simButton.setOnClickListener {
            Thread {
                val simulator = SimulatedWatch()
                simulator.start()
            }.start()
            val buttonContainer: LinearLayout = findViewById(R.id.layoutSimulateButton)
            buttonContainer.visibility = GONE
        }

        val transitKeyButton: Button = findViewById(R.id.btnSetTransitKey)
        transitKeyButton.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("Set Key Transit Secret")

            val label = TextView(this)
            label.text = getString(R.string.transit_key_explanation)

            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_TEXT

            val container = LinearLayout(this)
            container.orientation = VERTICAL
            container.setPadding(16, 16, 16, 16)
            container.addView(label)
            container.addView(input)

            builder.setView(container)
            builder.setPositiveButton("Save") { _, _ -> LongTermStorage.keyTransitSecret = input.text.toString().encodeToByteArray() }
            builder.setNegativeButton("Reset") { _, _ -> LongTermStorage.resetKeyTransitSecret() }
            builder.show()
        }

        val logo: TextView = findViewById(R.id.witch)
        logo.setOnClickListener {
            val thread = Thread {
                val channeledDeity = Poetry.witch()
                val name = channeledDeity.key
                val lines = channeledDeity.value

                lines.forEach {
                    BulletinDistributorService.sendBulletin(name, it)
                    runBlocking { delay(20) }
                }

            }
            thread.start()
        }

        keyReceiver.start()
        AddressAllocator().start()
        RoutingManager.startup(this.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)

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

    fun hideWatchSimButton() {
        runOnUiThread {
            val buttonContainer: LinearLayout = findViewById(R.id.layoutSimulateButton)
            buttonContainer.visibility = GONE
        }
    }

    fun statusIdle() {
        statusLabel.text = getString(R.string.status_not_running)
        NWSCManager.reset()
        WatchState.resetConnectionState()
        val serverToggle: SwitchMaterial = findViewById(R.id.swToggleServer)
        serverToggle.isChecked = false

    }

    fun setError(msg: String) {
        statusLabel.text = getString(R.string.status_error, msg)
    }
}