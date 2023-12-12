package net.rec0de.android.watchwitch.adapter

import android.app.PendingIntent.getActivity
import android.drm.DrmStore.RightsStatus
import android.view.Gravity
import android.view.Gravity.LEFT
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.WatchState

class ChatbubbleAdapter(val msgs: MutableList<Pair<Boolean,String>>) : RecyclerView.Adapter<ChatbubbleAdapter.ItemViewHolder>() {

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.msgContent)
        val layout: LinearLayout = view.findViewById(R.id.layoutBubble)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.chatbubble, parent, false)
        return ItemViewHolder(adapterLayout)
    }

    override fun getItemCount() = msgs.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = msgs[position]
        holder.content.text = item.second

        val isIncoming = item.first
        if(isIncoming) {
            holder.layout.setHorizontalGravity(Gravity.START)
            holder.content.background = ContextCompat.getDrawable(holder.view.context, R.drawable.chatbubble_incoming)
        }
    }
}