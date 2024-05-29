package net.rec0de.android.watchwitch.adapter

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.patrykandpatrick.vico.core.chart.edges.FadingEdges
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.views.chart.ChartView
import com.patrykandpatrick.vico.views.component.shape.shader.verticalGradient
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.activities.MapActivity
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt


class HealthLogAdapter(
    private val dataset: List<DatabaseWrangler.HealthLogDisplayItem>
) : RecyclerView.Adapter<HealthLogAdapter.ItemViewHolder>() {

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.labelFilename)
        val time: TextView = view.findViewById(R.id.labelTime)
        val stats: TextView = view.findViewById(R.id.labelStats)
        val metadata: TextView = view.findViewById(R.id.labelMetadata)
        val chart: ChartView = view.findViewById(R.id.chartEcg)
        val icon: ImageView = view.findViewById(R.id.iconEntryType)
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

        // clear any listener that we may have set before recycling
        holder.view.setOnClickListener {  }

        // hide chart
        holder.chart.visibility = GONE

        when(item) {
            is DatabaseWrangler.DisplaySample -> {
                // Title & main value
                holder.title.text = "${item.dataType}: ${roundPretty(item.value)}${item.unit}"

                val ico = when {
                    item.dataType.startsWith("Menstrual") -> R.drawable.icon_health_cycletracking
                    item.dataType.contains("Heart") -> R.drawable.icon_health_heart
                    item.dataType.contains("Energy") -> R.drawable.icon_health_energy
                    item.dataType.contains("Audio") -> R.drawable.icon_health_audio
                    item.dataType.contains("Walking") || item.dataType.contains("StepCount") || item.dataType.contains("Stair") -> R.drawable.icon_health_walk
                    else -> R.drawable.icon_health_unknown
                }
                holder.icon.setImageDrawable(ResourcesCompat.getDrawable(holder.view.resources, ico, null))

                // Data Series Statistics
                if(item.max != null && item.min != null) {
                    holder.stats.text = holder.stats.context.getString(R.string.healthlog_min_max, roundPretty(item.min), roundPretty(item.max))
                    holder.stats.visibility = VISIBLE
                }
                else {
                    holder.stats.visibility = GONE
                }

                if(item.series.isNotEmpty()) {
                    holder.chart.visibility = VISIBLE
                    // we're using the midpoint of the sampled interval as the x coordinate, starting at 0 = series start time, in minutes
                    val points = item.series.map{ v -> entryOf(((v.first.time/2 + v.second.time/2) - item.startDate.time).toDouble()/(1000*60), v.third)}
                    println(points)
                    val chartEntryModel = entryModelOf(points)

                    holder.chart.getXStep = { _ -> 0.5f }
                    holder.chart.setModel(chartEntryModel)
                    holder.chart.runInitialAnimation = false
                    holder.chart.fadingEdges = FadingEdges()

                    holder.chart.bottomAxis

                    val lineChart = holder.chart.chart!! as LineChart
                    //lineChart.axisValuesOverrider = AxisValuesOverrider.adaptiveYValues(2f, false)
                    val color = MaterialColors.getColor(holder.view.context, androidx.appcompat.R.attr.colorPrimary, Color.BLACK)
                    lineChart.lines = listOf(LineChart.LineSpec(
                        color,
                        lineBackgroundShader = DynamicShaders.verticalGradient(color, Color.TRANSPARENT)
                    ))
                }

                // Associated metadata entries
                setMetadata(item.metadata, holder)
            }
            is DatabaseWrangler.DisplayLocationSeries -> {
                holder.title.text = holder.title.context.getString(R.string.healthlog_locationseries)
                holder.stats.text = holder.stats.context.getString(R.string.healthlog_gps_points, item.points.size)
                holder.stats.visibility = VISIBLE
                holder.metadata.visibility = GONE

                val ico = ResourcesCompat.getDrawable(holder.view.resources, R.drawable.icon_health_gps, null)
                holder.icon.setImageDrawable(ico)

                holder.view.setOnClickListener {
                    val netLog = Intent(it.context, MapActivity::class.java)
                    val b = Bundle()
                    b.putInt("seriesId", item.seriesId)
                    netLog.putExtras(b)
                    it.context.startActivity(netLog)
                }
            }
            is DatabaseWrangler.DisplayWorkout -> {
                // Title & main value
                holder.title.text = "${item.type}, ${prettyDuration(item.duration)}"

                holder.stats.text = "${item.steps} steps, ${roundPretty(item.distance)}km, ${roundPretty(item.energy)} kcal"
                holder.stats.visibility = VISIBLE

                val ico = ResourcesCompat.getDrawable(holder.view.resources, R.drawable.icon_health_workout, null);
                holder.icon.setImageDrawable(ico)

                // Associated metadata entries
                setMetadata(item.metadata, holder)
            }
            is DatabaseWrangler.DisplayEcg -> {
                val sampleRate = 511.2
                holder.chart.visibility = VISIBLE
                holder.title.text = "Electrocardiogram"
                holder.stats.text = "${roundPretty(item.heartrate)} bpm, ${item.voltageSamples.size} samples"
                holder.stats.visibility = VISIBLE

                val ico = ResourcesCompat.getDrawable(holder.view.resources, R.drawable.icon_health_heart, null);
                holder.icon.setImageDrawable(ico)

                val chartEntryModel = entryModelOf(item.voltageSamples.mapIndexed{ i, v -> entryOf(i.toDouble() / sampleRate, v)})

                holder.chart.getXStep = { _ -> 0.5f }
                holder.chart.setModel(chartEntryModel)
                holder.chart.runInitialAnimation = false
                holder.chart.fadingEdges = FadingEdges()

                val lineChart = holder.chart.chart!! as LineChart
                //lineChart.axisValuesOverrider = AxisValuesOverrider.adaptiveYValues(2f, false)
                val color = MaterialColors.getColor(holder.view.context, androidx.appcompat.R.attr.colorPrimary, Color.BLACK)
                lineChart.lines = listOf(LineChart.LineSpec(
                    color,
                    lineBackgroundShader = DynamicShaders.verticalGradient(color, Color.TRANSPARENT)
                ))


                // Associated metadata entries
                setMetadata(item.metadata, holder)
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

    private fun setMetadata(metadata: Map<String, String>, holder: ItemViewHolder) {
        if(metadata.isNotEmpty()) {
            holder.metadata.text = metadata.map {
                val shortKey = it.key.replace("_HKPrivateMetadataKey", "")
                                     .replace("HKMetadataKey", "")
                                     .replace("_HKPrivate", "")
                                     .replace("HK", "")
                "$shortKey: ${it.value}"
            }.joinToString("\n")
            holder.metadata.visibility = VISIBLE
        }
        else
            holder.metadata.visibility = GONE
    }

    private fun roundPretty(v: Double): String {
        return ((v*100).roundToInt().toDouble()/100).toString()
    }

    private fun prettyDuration(seconds: Double): String {
        val hours = floor(seconds / (60*60))
        val minutes = round((seconds - hours*60*60)/60)

        val hourString = hours.toInt().toString().padStart(2, '0')
        val minuteString = minutes.toInt().toString().padStart(2, '0')

        return "$hourString:$minuteString"
    }
}