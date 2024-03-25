package net.rec0de.android.watchwitch.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.adapter.NetworkStatsAdapter
import net.rec0de.android.watchwitch.shoes.SHOES_MSG_CONNECTIVITY
import net.rec0de.android.watchwitch.shoes.SHOES_MSG_FIREWALL_DEFAULT
import net.rec0de.android.watchwitch.shoes.SHOES_MSG_FIREWALL_RULE
import net.rec0de.android.watchwitch.shoes.SHOES_MSG_STATS
import net.rec0de.android.watchwitch.shoes.ShoesService
import net.rec0de.android.watchwitch.shoes.StatsEntry

class NetworkLogActivity : AppCompatActivity(), ServiceConnection {

    var firewallIsDefaultAllow: Boolean = true
    private var isBound = false
    private var serverMessenger: Messenger? = null
    private var clientMessenger: Messenger? = null
    private val shoesResponseHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            // receive reply with networks stats from SHOES process
            val bundle = msg.data
            val json = bundle.getString("statsJson")!!

            // received stats contain synthetic default entry
            val statsWithDefault = Json.decodeFromString<Map<String, StatsEntry>>(json)
            val stats = statsWithDefault.filterKeys { it != "default" }
            firewallIsDefaultAllow = statsWithDefault["default"]!!.allow!!

            val recyclerView = findViewById<RecyclerView>(R.id.hostList)

            val adapter = recyclerView.adapter as NetworkStatsAdapter
            adapter.stats.clear()
            adapter.stats.addAll(stats.toList().sortedBy { it.second.packets }.reversed())
            adapter.notifyDataSetChanged()

            val listEmptyLabel = findViewById<TextView>(R.id.emptyLabel)
            listEmptyLabel.visibility = if(stats.isEmpty()) VISIBLE else GONE

            val defaultSwitch = findViewById<SwitchMaterial>(R.id.swFirewallDefault)
            defaultSwitch.setOnCheckedChangeListener(null)
            defaultSwitch.isChecked = firewallIsDefaultAllow
            defaultSwitch.setOnCheckedChangeListener { _, allowed ->
                firewallIsDefaultAllow = allowed
                setFirewallDefault(allowed)
                recyclerView.adapter?.notifyDataSetChanged()
            }

            Logger.logShoes("NetworkLog view refreshed", 2)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_log)

        val recyclerView = findViewById<RecyclerView>(R.id.hostList)
        recyclerView.adapter = NetworkStatsAdapter(mutableListOf(), this)
        recyclerView.setHasFixedSize(true)

        val refreshBtn = findViewById<ImageButton>(R.id.btnRefresh)
        refreshBtn.setOnClickListener {
            getStats()
        }
    }

    override fun onStart() {
        super.onStart()
        doBindService()
    }

    override fun onStop() {
        super.onStop()
        doUnbindService()
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        serverMessenger = Messenger(service)
        // Ready to send messages to remote service
        getStats()
    }

    override fun onServiceDisconnected(className: ComponentName) {
        serverMessenger = null
    }

    private fun doBindService() {
        clientMessenger = Messenger(shoesResponseHandler)
        Intent(this, ShoesService::class.java).also { intent ->
            applicationContext.bindService(intent, this, Context.BIND_AUTO_CREATE)
        }
        isBound = true
    }

    private fun doUnbindService() {
        if (isBound) {
            applicationContext.unbindService(this)
            isBound = false
        }
    }

    private fun setFirewallDefault(value: Boolean) {
        if (!isBound) return

        val bundle = Bundle()
        bundle.putBoolean("allowByDefault", value)
        val message = Message.obtain(null, SHOES_MSG_FIREWALL_DEFAULT)
        message.data = bundle

        try {
            serverMessenger?.send(message)
        } catch (e: RemoteException) {
            e.printStackTrace()
        } finally {
            message.recycle()
        }
    }

    fun setFirewallRule(host: String, allow: Boolean) {
        if (!isBound) return

        val bundle = Bundle()
        bundle.putString("host", host)
        bundle.putBoolean("allow", allow)

        val message = Message.obtain(null, SHOES_MSG_FIREWALL_RULE)
        message.data = bundle

        try {
            serverMessenger?.send(message)
        } catch (e: RemoteException) {
            e.printStackTrace()
        } finally {
            message.recycle()
        }
    }

    private fun getStats() {
        if (!isBound) return
        val message = Message.obtain(shoesResponseHandler, SHOES_MSG_STATS)
        message.replyTo = clientMessenger
        try {
            serverMessenger?.send(message)
        } catch (e: RemoteException) {
            e.printStackTrace()
        } finally {
            message.recycle()
        }
    }
}