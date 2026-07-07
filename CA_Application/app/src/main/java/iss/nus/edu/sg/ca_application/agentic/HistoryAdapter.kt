// Author: Cai Peilin, Xia Zihang
package iss.nus.edu.sg.ca_application.agentic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.model.AgentHistoryItem
import java.text.SimpleDateFormat
import java.util.Locale

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
    private val items: List<AgentHistoryItem>,
    private val onItemClick: ((AgentHistoryItem, Int, Float) -> Unit)? = null
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    companion object {
        private const val EVIDENCE_LINE_SPACING = 6f
        private const val EVIDENCE_LINE_SPACING_MULTIPLIER = 1.0f
        private const val EVIDENCE_ITEM_MARGIN_DP = 8
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation_history, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position, onItemClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.itemDateText)
        private val contentText: TextView = itemView.findViewById(R.id.itemContentText)
        private val toggleText: TextView = itemView.findViewById(R.id.itemToggleText)
        private val evidenceContainer: LinearLayout = itemView.findViewById(R.id.itemEvidenceContainer)

        private var expanded = false

        fun bind(item: AgentHistoryItem, position: Int, onItemClick: ((AgentHistoryItem, Int, Float) -> Unit)?) {
            itemView.setOnTouchListener { v, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    onItemClick?.invoke(item, position, event.x)
                    v.performClick()
                }
                true
            }
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
                    setLineSpacing(EVIDENCE_LINE_SPACING, EVIDENCE_LINE_SPACING_MULTIPLIER)
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (index > 0) {
                    params.topMargin = (EVIDENCE_ITEM_MARGIN_DP * itemView.resources.displayMetrics.density).toInt()
                }
                row.layoutParams = params
                evidenceContainer.addView(row)
            }

            toggleText.setOnClickListener {
                expanded = !expanded
                evidenceContainer.visibility = if (expanded) View.VISIBLE else View.GONE
            }

            itemView.setOnLongClickListener(null)
            // Toggle evidence
        }

        private fun formatDate(iso: String): String {
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                formatter.format(parser.parse(iso)!!)
            } catch (e: Exception) {
                iso.substringBefore("T")
            }
        }
    }
}