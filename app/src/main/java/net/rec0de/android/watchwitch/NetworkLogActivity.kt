package net.rec0de.android.watchwitch

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.adapter.HealthLogAdapter
import net.rec0de.android.watchwitch.adapter.NetworkStatsAdapter
import net.rec0de.android.watchwitch.servicehandlers.health.HealthSync
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import net.rec0de.android.watchwitch.shoes.NetworkStats

class NetworkLogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_log)

        val recyclerView = findViewById<RecyclerView>(R.id.hostList)
        recyclerView.adapter = NetworkStatsAdapter(NetworkStats.stats().toList())
        recyclerView.setHasFixedSize(true)
    }
}