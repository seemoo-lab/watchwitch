package net.rec0de.android.watchwitch.activities

import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.WatchState
import net.rec0de.android.watchwitch.adapter.AlarmsAdapter
import net.rec0de.android.watchwitch.adapter.OpenAppsAdapter

class WatchStateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_state)

        val alarmList = findViewById<RecyclerView>(R.id.listFiles)
        val alarmListEmpty = findViewById<TextView>(R.id.labelFilesEmpty)
        WatchState.alarms.observe(this) { alarms ->
            alarmList.swapAdapter(AlarmsAdapter(alarms), true)
            if(alarms.isEmpty()) {
                alarmList.visibility = GONE
                alarmListEmpty.visibility = VISIBLE
            }
            else {
                alarmList.visibility = VISIBLE
                alarmListEmpty.visibility = GONE
            }
        }

        val appList = findViewById<RecyclerView>(R.id.listOpenApps)
        val appListEmpty = findViewById<TextView>(R.id.labelAppsEmpty)
        WatchState.openApps.observe(this){ apps ->
            appList.swapAdapter(OpenAppsAdapter(apps), true)
            if(apps.isEmpty()) {
                appList.visibility = GONE
                appListEmpty.visibility = VISIBLE
            }
            else {
                appList.visibility = VISIBLE
                appListEmpty.visibility = GONE
            }
        }

        val ringer = findViewById<ImageView>(R.id.iconRinger)
        WatchState.ringerMuted.observe(this) {
            val ico = when(it) {
                WatchState.TriState.TRUE -> R.drawable.icon_bell_mute
                WatchState.TriState.FALSE -> R.drawable.icon_bell_ringing
                else -> R.drawable.icon_bell_unknown
            }

            ringer.setImageDrawable(ResourcesCompat.getDrawable(resources, ico, null))
        }
    }
}