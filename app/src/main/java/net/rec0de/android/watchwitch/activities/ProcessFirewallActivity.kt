package net.rec0de.android.watchwitch.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.adapter.FirewallProcessAdapter
import net.rec0de.android.watchwitch.shoes.SHOES_MSG_FIREWALL_PROCESS_RULE
import net.rec0de.android.watchwitch.shoes.SHOES_MSG_STATS
import net.rec0de.android.watchwitch.shoes.ShoesService
import net.rec0de.android.watchwitch.shoes.StatsEntry

class ProcessFirewallActivity : AppCompatActivity(), ServiceConnection {

    private var isBound = false
    private var serverMessenger: Messenger? = null
    private var clientMessenger: Messenger? = null
    private val shoesResponseHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            // receive reply with networks stats from SHOES process
            val bundle = msg.data
            val json = bundle.getString("statsJson")!!

            val decoded = Json.decodeFromString<Pair<Map<String, StatsEntry>, Map<String, Boolean?>>>(json)

            val processes = decoded.second.toMutableMap()

            // gather process / bundle ID names that do not have firewall rules yet from the generic stats
            val undefinedProcesses = decoded.first.flatMap { it.value.bundleIDs }.toSet().minus(processes.keys)
            processes.putAll(undefinedProcesses.map { Pair(it, null) })

            val recyclerView = findViewById<RecyclerView>(R.id.hostList)

            val adapter = recyclerView.adapter as FirewallProcessAdapter
            adapter.stats.clear()
            adapter.stats.addAll(processes.toList().sortedBy { it.first })
            adapter.notifyDataSetChanged()

            val listEmptyLabel = findViewById<TextView>(R.id.emptyLabel)
            listEmptyLabel.visibility = if(processes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_process_firewall)

        val recyclerView = findViewById<RecyclerView>(R.id.hostList)
        recyclerView.adapter = FirewallProcessAdapter(mutableListOf(), this)
        recyclerView.setHasFixedSize(true)
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

    fun setProcessRule(process: String, allow: Boolean?) {
        if (!isBound) return

        val bundle = Bundle()
        bundle.putString("process", process)
        if(allow != null)
            bundle.putBoolean("allow", allow)

        val message = Message.obtain(null, SHOES_MSG_FIREWALL_PROCESS_RULE)
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