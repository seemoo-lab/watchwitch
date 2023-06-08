package net.rec0de.android.watchwitch

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var statusLabel: TextView
    private lateinit var packetLog: TextView
    private val localIP = Utils.getLocalIP()
    private var udpHandler: UDPHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Logger.setMainActivity(this)

        statusLabel = findViewById(R.id.tvState)
        packetLog = findViewById(R.id.tvPacketLog)

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

        LongTermStorage.context = applicationContext

        KeyReceiver(this).start()
        AddressAllocator().start()
        RoutingManager.startup(this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)

        /*RoutingManager.createTunnel(
            "192.168.133.25",
            LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_C)!!,
            LongTermKeys.getAddress(LongTermKeys.REMOTE_ADDRESS_CLASS_C)!!,
            "c0c0da7e".hexBytes(),
            "cafeda7e".hexBytes(),
            "288cc9beaacff22d53668df8bba37bd1f905b229a8b9204c8311eabb749abe6bc0c0c0c0".hexBytes(),
            "288cc9beaacff22d53668df8bba37bd1f905b229a8b9204c8311eabb749abe6bcafecafe".hexBytes()
        )*/

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
}