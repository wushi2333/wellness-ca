package iss.nus.edu.sg.ca_application.wellness

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.model.WellnessRecord

/**
 * Author: Wang Songyu
 *
 * RecyclerView adapter for displaying wellness records.
 */
class WellnessAdapter(
    private var records: List<WellnessRecord>,
    private val onItemClick: (WellnessRecord) -> Unit,
    private val onItemLongClick: (WellnessRecord) -> Unit
) : RecyclerView.Adapter<WellnessAdapter.WellnessViewHolder>() {

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
        val record = records[position]

        holder.tvDate.text = record.recordDate
        holder.tvSleep.text = "Sleep: ${record.sleepHours}h"
        holder.tvExercise.text =
            "${record.exerciseActivity} (${record.exerciseDuration} min)"

        holder.itemView.setOnClickListener {
            onItemClick(record)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(record)
            true
        }
    }

    override fun getItemCount(): Int = records.size

    /**
     * Updates the adapter data and refreshes the RecyclerView.
     */
    fun updateData(newRecords: List<WellnessRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }
}