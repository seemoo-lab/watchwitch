package net.rec0de.android.watchwitch.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.voice.VoiceInteractionSession.VisibleActivityCallback
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.FilesActivity
import net.rec0de.android.watchwitch.R
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt


class FilesAdapter(private val activity: FilesActivity, private val paths: MutableList<FilesActivity.FileItem>) : RecyclerView.Adapter<FilesAdapter.ItemViewHolder>() {

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val filename: TextView = view.findViewById(R.id.labelFilename)
        val filesize: TextView = view.findViewById(R.id.labelFileSize)
        val time: TextView = view.findViewById(R.id.labelTime)
        val preview: ImageView = view.findViewById(R.id.imgPreview)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteFile)
        val btnSave: ImageButton = view.findViewById(R.id.btnSaveFile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.filelist_item, parent, false)
        return ItemViewHolder(adapterLayout)
    }

    override fun getItemCount() = paths.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = paths[position]
        holder.filename.text = item.filename
        holder.filesize.text = renderFileSize(item.size)
        holder.time.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(item.timestamp)

        holder.btnDelete.setOnClickListener {
            File(item.fullPath).delete()
            paths.removeAt(position)
            notifyItemRemoved(position)
        }

        holder.btnSave.setOnClickListener {
            activity.sourceFilePath = item.fullPath
            activity.savePrompt.launch(item.filename)
        }

        if(item.bitmap != null){
            holder.preview.setImageBitmap(item.bitmap)
            holder.preview.visibility = VISIBLE
        }
        else
            holder.preview.visibility = GONE
    }

    private fun renderFileSize(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1000000 -> "${round(bytes.toDouble()/100)/10} kB"
            else -> "${round(bytes.toDouble()/100000)/10} MB"
        }
    }
}