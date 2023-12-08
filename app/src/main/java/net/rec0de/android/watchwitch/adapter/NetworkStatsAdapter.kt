package net.rec0de.android.watchwitch.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.shoes.StatsEntry
import kotlin.math.roundToInt

class NetworkStatsAdapter(private val stats: List<Pair<String, StatsEntry>>) : RecyclerView.Adapter<NetworkStatsAdapter.ItemViewHolder>() {

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val host: TextView = view.findViewById(R.id.labelFilename)
        val bundle: TextView = view.findViewById(R.id.labelBundle)
        val packets: TextView = view.findViewById(R.id.labelPackets)
        val upload: TextView = view.findViewById(R.id.labelUpload)
        val download: TextView = view.findViewById(R.id.labelDownload)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.netstats_item, parent, false)

        return ItemViewHolder(adapterLayout)
    }

    override fun getItemCount() = stats.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = stats[position]

        holder.host.text = item.first
        holder.bundle.text = "from: " + item.second.bundleIDs.joinToString(", ")
        holder.packets.text = item.second.packets.toString()
        holder.upload.text = renderHumanReadableBytes(item.second.bytesSent)
        holder.download.text = renderHumanReadableBytes(item.second.bytesReceived)

    }

    private fun renderHumanReadableBytes(bytes: Int): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1000000 -> "${(bytes.toDouble()/100).roundToInt().toDouble()/10} kB"
            else -> "${(bytes.toDouble()/100000).roundToInt().toDouble()/10} MB"
        }
    }
}