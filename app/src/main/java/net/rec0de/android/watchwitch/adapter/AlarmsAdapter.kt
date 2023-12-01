package net.rec0de.android.watchwitch.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.WatchState

class AlarmsAdapter(alarms: Map<String,WatchState.Alarm>) : RecyclerView.Adapter<AlarmsAdapter.ItemViewHolder>() {

    private val items = alarms.toList()

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.labelAlarmTime)
        val iconEnabled: ImageView = view.findViewById(R.id.iconAlarmEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.alarms_item, parent, false)
        return ItemViewHolder(adapterLayout)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        val hourString = item.second.hour.toString().padStart(2, '0')
        val minuteString = item.second.minute.toString().padStart(2, '0')

        if(item.second.title == null)
            holder.time.text = "$hourString:$minuteString"
        else
            holder.time.text = "$hourString:$minuteString - ${item.second.title}"

        val ico = if(item.second.enabled) R.drawable.icon_alarm_enabled else R.drawable.icon_alarm_disabled

        holder.iconEnabled.setImageDrawable(ResourcesCompat.getDrawable(holder.itemView.resources, ico, null))
    }
}