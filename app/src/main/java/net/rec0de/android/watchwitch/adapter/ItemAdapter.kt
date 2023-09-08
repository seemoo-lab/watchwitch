package net.rec0de.android.watchwitch.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.servicehandlers.health.CategorySample
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ItemAdapter(
    private val context: Context,
    private val dataset: List<DatabaseWrangler.DisplaySample>
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.labelTitle)
        val time: TextView = view.findViewById(R.id.labelTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item, parent, false)

        return ItemViewHolder(adapterLayout)
    }

    override fun getItemCount() = dataset.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = dataset[dataset.size-1-position]

        holder.title.text = "${item.dataType}: ${(item.value*100).roundToInt().toDouble()/100}${item.unit}"

        val pattern = "dd/MM/yyyy HH:mm:ss"
        val df: DateFormat = SimpleDateFormat(pattern, Locale.US)

        if(item.startDate == item.endDate) {
            holder.time.text = df.format(item.startDate)
        }
        else {
            holder.time.text = "${df.format(item.startDate)} until ${df.format(item.endDate)}"
        }
    }
}