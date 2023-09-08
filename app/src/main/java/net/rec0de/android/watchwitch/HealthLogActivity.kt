package net.rec0de.android.watchwitch

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.ContactsContract.Data
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.adapter.ItemAdapter
import net.rec0de.android.watchwitch.servicehandlers.health.HealthSync
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler

class HealthLogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_log)

        val categoryData = DatabaseWrangler.getCategorySamples()
        val quantityData = DatabaseWrangler.getQuantitySamples()

        val data = (categoryData + quantityData).sortedBy { it.startDate }

        val recyclerView = findViewById<RecyclerView>(R.id.sampleList)
        recyclerView.adapter = ItemAdapter(this, data)
        recyclerView.setHasFixedSize(true)

        val resetButton = findViewById<Button>(R.id.btnResetSyncStatus)
        resetButton.setOnClickListener {
            HealthSync.resetSyncStatus()
        }
    }


}