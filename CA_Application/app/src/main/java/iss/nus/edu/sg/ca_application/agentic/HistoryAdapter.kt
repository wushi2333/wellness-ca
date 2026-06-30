package iss.nus.edu.sg.ca_application.agentic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.model.AgentHistoryItem

/**
 * RecyclerView adapter for the history list.
 *
 * Each row shows: short date, recommendation preview, and a collapsible
 * evidence trace. The evidence container is dynamically populated from
 * the item's `evidence` list.
 *
 * Author: Cai Peilin
 */
class HistoryAdapter(
    private val items: List<AgentHistoryItem>
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation_history, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.itemDateText)
        private val contentText: TextView = itemView.findViewById(R.id.itemContentText)
        private val toggleText: TextView = itemView.findViewById(R.id.itemToggleText)
        private val evidenceContainer: LinearLayout = itemView.findViewById(R.id.itemEvidenceContainer)

        private var expanded = false

        fun bind(item: AgentHistoryItem) {
            dateText.text = formatDate(item.createdAt)
            contentText.text = item.content

            // Reset evidence visibility for view recycling.
            expanded = false
            evidenceContainer.visibility = View.GONE
            evidenceContainer.removeAllViews()

            item.evidence.forEachIndexed { index, trace ->
                val row = TextView(itemView.context).apply {
                    text = itemView.context.getString(
                        R.string.agent_evidence_row,
                        index + 1,
                        trace.summary
                    )
                    setTextAppearance(R.style.Body)
                    setLineSpacing(6f, 1.0f)
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (index > 0) {
                    params.topMargin = (8 * itemView.resources.displayMetrics.density).toInt()
                }
                row.layoutParams = params
                evidenceContainer.addView(row)
            }

            toggleText.setOnClickListener {
                expanded = !expanded
                evidenceContainer.visibility = if (expanded) View.VISIBLE else View.GONE
            }
        }

        private fun formatDate(iso: String): String {
            // ISO format: "2026-06-30T14:22:31" → display "2026-06-30 14:22"
            return iso.replace("T", " ").substringBefore(":")
                .let { it.padEnd(13, '0') } + ":" +
                    iso.substringAfter("T").substringAfter(":").substringBefore(":")
        }
    }
}