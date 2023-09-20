package net.rec0de.android.watchwitch.adapter

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.MapActivity
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt


class HealthLogAdapter(
    private val dataset: List<DatabaseWrangler.HealthLogDisplayItem>
) : RecyclerView.Adapter<HealthLogAdapter.ItemViewHolder>() {

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.labelTitle)
        val time: TextView = view.findViewById(R.id.labelTime)
        val stats: TextView = view.findViewById(R.id.labelStats)
        val metadata: TextView = view.findViewById(R.id.labelMetadata)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.healthlog_item, parent, false)

        return ItemViewHolder(adapterLayout)
    }

    override fun getItemCount() = dataset.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = dataset[dataset.size-1-position]

        if(item is DatabaseWrangler.DisplaySample) {
            // Title & main value
            holder.title.text = "${item.dataType}: ${roundPretty(item.value)}${item.unit}"

            // Data Series Statistics
            if(item.max != null && item.min != null) {
                holder.stats.text = holder.stats.context.getString(R.string.healthlog_min_max, roundPretty(item.min), roundPretty(item.max))
                holder.stats.visibility = VISIBLE
            }
            else {
                holder.stats.visibility = GONE
            }

            // Associated metadata entries
            if(item.metadata.isNotEmpty()) {
                holder.metadata.text = item.metadata.map { "${it.key}: ${it.value}" }.joinToString(", ")
                holder.metadata.visibility = VISIBLE
            }
            else
                holder.metadata.visibility = GONE

            // clear any listener that we may have set before recycling
            holder.view.setOnClickListener {  }
        }
        else if(item is DatabaseWrangler.DisplayLocationSeries) {
            holder.title.text = holder.title.context.getString(R.string.healthlog_locationseries)
            holder.stats.text = holder.stats.context.getString(R.string.healthlog_gps_points, item.points.size)
            holder.stats.visibility = VISIBLE
            holder.metadata.visibility = GONE

            holder.view.setOnClickListener {
                val netLog = Intent(it.context, MapActivity::class.java)
                val b = Bundle()
                b.putInt("seriesId", item.seriesId)
                netLog.putExtras(b)
                it.context.startActivity(netLog)
            }
        }

        // Timestamp
        val pattern = "dd/MM/yyyy HH:mm:ss"
        val df: DateFormat = SimpleDateFormat(pattern, Locale.US)
        if(item.startDate == item.endDate) {
            holder.time.text = df.format(item.startDate)
        }
        else {
            holder.time.text = "${df.format(item.startDate)} until ${df.format(item.endDate)}"
        }
    }

    private fun roundPretty(v: Double): String {
        return ((v*100).roundToInt().toDouble()/100).toString()
    }
}