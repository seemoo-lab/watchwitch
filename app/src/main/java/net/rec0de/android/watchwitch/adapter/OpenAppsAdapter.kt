package net.rec0de.android.watchwitch.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.R

class OpenAppsAdapter(private val apps: List<String>) : RecyclerView.Adapter<OpenAppsAdapter.ItemViewHolder>() {

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.labelAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.apps_item, parent, false)
        return ItemViewHolder(adapterLayout)
    }

    override fun getItemCount() = apps.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = apps[position]
        holder.name.text = item
    }
}