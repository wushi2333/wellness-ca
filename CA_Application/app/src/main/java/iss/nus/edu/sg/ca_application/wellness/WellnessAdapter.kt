// Author: Wang Songyu, Xia Zihang
package iss.nus.edu.sg.ca_application.wellness

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.model.WellnessRecord

/**
 * Author: Wang Songyu
 *
 * RecyclerView adapter for displaying wellness records.
 * Uses ListAdapter with DiffUtil for efficient incremental updates.
 */
class WellnessAdapter(
    private val onItemClick: (WellnessRecord) -> Unit,
    private val onItemLongClick: (WellnessRecord) -> Unit
) : ListAdapter<WellnessRecord, WellnessAdapter.WellnessViewHolder>(DiffCallback) {

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<WellnessRecord>() {
            override fun areItemsTheSame(oldItem: WellnessRecord, newItem: WellnessRecord): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: WellnessRecord, newItem: WellnessRecord): Boolean =
                oldItem == newItem
        }
    }

    /**
     * Holds references to the views of a single wellness record item.
     */
    class WellnessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvRecordDate)
        val tvSleep: TextView = itemView.findViewById(R.id.tvSleepHours)
        val tvExercise: TextView = itemView.findViewById(R.id.tvExercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WellnessViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wellness_record, parent, false)
        return WellnessViewHolder(view)
    }

    override fun onBindViewHolder(holder: WellnessViewHolder, position: Int) {
        val record = getItem(position)

        val context = holder.itemView.context
        holder.tvDate.text = record.recordDate
        holder.tvSleep.text = context.getString(R.string.sleep_format, record.sleepHours)
        holder.tvExercise.text =
            context.getString(R.string.exercise_format, record.exerciseActivity, record.exerciseDuration)

        holder.itemView.setOnClickListener {
            onItemClick(record)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(record)
            true
        }
    }
}