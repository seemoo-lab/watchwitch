package net.rec0de.android.watchwitch

import android.os.Bundle
import android.provider.ContactsContract.Data
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.adapter.HealthLogAdapter
import net.rec0de.android.watchwitch.servicehandlers.health.HealthSync
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date


class HealthLogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_log)

        val categoryData = DatabaseWrangler.getCategorySamples()
        val quantityData = DatabaseWrangler.getQuantitySamples()
        val locationSeries = DatabaseWrangler.getLocationSeries()


        val data = (categoryData + quantityData + locationSeries).sortedBy { it.startDate }

        val recyclerView = findViewById<RecyclerView>(R.id.hostList)
        recyclerView.adapter = HealthLogAdapter(data)
        recyclerView.setHasFixedSize(true)

        val resetButton = findViewById<Button>(R.id.btnResetSyncStatus)
        resetButton.setOnClickListener {
            HealthSync.resetSyncStatus()
        }
    }
}