package net.rec0de.android.watchwitch

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import net.rec0de.android.watchwitch.shoes.NetworkStats


class MainActivity : AppCompatActivity() {
    private lateinit var statusLabel: TextView
    private lateinit var packetLog: TextView
    private val localIP = Utils.getLocalIP()
    private var udpHandler: UDPHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusLabel = findViewById(R.id.tvState)
        packetLog = findViewById(R.id.tvPacketLog)

        Logger.setMainActivity(this)

        val serverToggle: Switch = findViewById(R.id.swToggleServer)
        serverToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                statusLabel.text = "starting..."
                udpHandler = UDPHandler(this, 5000)
                udpHandler!!.start()
            } else {
                statusLabel.text = "stopping..."
                udpHandler?.kill()
            }
        }

        val networkLogButton: Button = findViewById(R.id.btnLogNetwork)
        networkLogButton.setOnClickListener {
            val log = NetworkStats.print()
            Logger.log(log, 0)
        }

        val healthLogButton: Button = findViewById(R.id.btnHealthLog)
        healthLogButton.setOnClickListener {
            val healthLog = Intent(this@MainActivity, HealthLogActivity::class.java)
            this@MainActivity.startActivity(healthLog)
        }

        LongTermStorage.context = applicationContext

        KeyReceiver(this).start()
        AddressAllocator().start()
        RoutingManager.startup(this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)

        startForegroundService(Intent(applicationContext, TcpServerService::class.java))
    }

    fun logData(data: String) {
        packetLog.append(data.removeSuffix("\n") + "\n")
    }

    fun statusListening(port: Int) {
        statusLabel.text = "listening on $localIP:$port"
    }

    fun statusIdle() {
        statusLabel.text = "not running"
    }

    fun setError(msg: String) {
        statusLabel.text = "Error: $msg"
    }
}