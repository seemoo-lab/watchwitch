package net.rec0de.android.watchwitch

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

        statusLabel = findViewById(R.id.tvState)
        packetLog = findViewById(R.id.tvPacketLog)

        val serverToggle: Switch = findViewById(R.id.swToggleServer)

        serverToggle.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                statusLabel.text = "starting..."
                udpHandler = UDPHandler(this, 5000)
                udpHandler!!.start()
            }
            else {
                statusLabel.text = "stopping..."
                udpHandler?.kill()
            }
        }

        LongTermKeys.context = applicationContext

        val keyReceiver = KeyReceiver(this)
        keyReceiver.start()


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