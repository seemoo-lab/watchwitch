package net.rec0de.android.watchwitch.activities

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.adapter.HealthLogAdapter
import net.rec0de.android.watchwitch.servicehandlers.health.HealthSync
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler


class HealthLogActivity : AppCompatActivity() {

    private val ignoreBoring = true
    private val boringTypes = listOf("BasalEnergyBurned", "ActiveEnergyBurned", "WristEvent", "HeartRate", "DistanceWalkingRunning")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_log)

        val categoryData = DatabaseWrangler.getCategorySamples()
        val quantityData = DatabaseWrangler.getQuantitySamples()
        val locationSeries = DatabaseWrangler.getLocationSeries()
        val workouts = DatabaseWrangler.getWorkouts()
        val ecg = DatabaseWrangler.getEcgSamples()

        var data = (categoryData + quantityData + workouts + ecg + locationSeries).sortedBy { it.startDate }

        if(ignoreBoring)
            data = data.filterNot { it is DatabaseWrangler.DisplaySample && it.dataType in boringTypes }

        val recyclerView = findViewById<RecyclerView>(R.id.hostList)
        recyclerView.adapter = HealthLogAdapter(data)
        recyclerView.setHasFixedSize(false)

        val resetButton = findViewById<Button>(R.id.btnResetSyncStatus)
        resetButton.setOnClickListener {
            Thread {
                HealthSync.resetSyncStatus()
            }.start()
        }

        val unlockEcgButton = findViewById<Button>(R.id.btnEcgUnlock)
        unlockEcgButton.setOnClickListener {
            Thread {
                HealthSync.enableECG()
                HealthSync.enableCycleTracking()
            }.start()
        }
    }
}