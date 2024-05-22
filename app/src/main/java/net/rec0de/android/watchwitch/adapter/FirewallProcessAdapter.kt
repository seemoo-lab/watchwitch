package net.rec0de.android.watchwitch.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.activities.ProcessFirewallActivity

class FirewallProcessAdapter(var stats: MutableList<Pair<String, Boolean?>>, private val firewallActivity: ProcessFirewallActivity) : RecyclerView.Adapter<FirewallProcessAdapter.ItemViewHolder>() {

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val process: TextView = view.findViewById(R.id.labelProcessName)
        val radioUnspecified: RadioButton = view.findViewById(R.id.radioUnspecified)
        val radioAllow: RadioButton = view.findViewById(R.id.radioAllow)
        val radioDeny: RadioButton = view.findViewById(R.id.radioDeny)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.firewall_process_item, parent, false)

        return ItemViewHolder(adapterLayout)
    }

    override fun getItemCount() = stats.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = stats[position]
        holder.process.text = item.first

        val state = item.second
        if(state == null)
            holder.radioUnspecified.isChecked = true
        else if(state)
            holder.radioAllow.isChecked = true
        else
            holder.radioDeny.isChecked = true

        holder.radioUnspecified.setOnCheckedChangeListener { _, b ->
            if(b)
                firewallActivity.setProcessRule(item.first, null)
        }

        holder.radioAllow.setOnCheckedChangeListener { _, b ->
            if(b)
                firewallActivity.setProcessRule(item.first, true)
        }

        holder.radioDeny.setOnCheckedChangeListener { _, b ->
            if(b)
                firewallActivity.setProcessRule(item.first, false)
        }

    }
}