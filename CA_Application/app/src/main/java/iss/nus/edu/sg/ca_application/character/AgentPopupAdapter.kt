// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.character

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R

/** Simple message for agent popup (no ID, emotion, timestamp needed). */
data class PopupMsg(
    val role: String,
    val content: String,
    val tools: List<String>? = null
)

class AgentPopupAdapter : RecyclerView.Adapter<AgentPopupAdapter.VH>() {

    private val items = mutableListOf<PopupMsg>()
    private val expandedTools = mutableSetOf<Int>()
    var loading = false
        private set

    val lastIndex: Int get() = items.size - 1

    fun addUserMsg(text: String) {
        items.add(PopupMsg("user", text))
        notifyItemInserted(items.size - 1)
    }

    /** Directly add a complete assistant message (e.g. greeting). */
    fun addAssistantReply(reply: String, tools: List<String>? = null) {
        items.add(PopupMsg("assistant", reply, tools))
        notifyItemInserted(items.size - 1)
    }

    fun addAssistantPlaceholder() {
        loading = true
        items.add(PopupMsg("assistant", ""))
        notifyItemInserted(items.size - 1)
    }

    fun updateAssistantReply(reply: String, tools: List<String>? = null) {
        loading = false
        if (items.isNotEmpty()) {
            val idx = items.size - 1
            items[idx] = PopupMsg("assistant", reply, tools)
            notifyItemChanged(idx)
        }
    }

    fun clear() { items.clear(); expandedTools.clear(); loading = false; notifyDataSetChanged() }

    override fun getItemViewType(pos: Int) = if (items[pos].role == "user") 0 else 1

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 0) R.layout.item_character_message_user
        else R.layout.item_character_message_ai
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val msg = items[pos]
        if (msg.role == "user") {
            holder.content?.text = msg.content
            holder.toolsToggle?.visibility = View.GONE
            holder.toolsList?.visibility = View.GONE
        } else {
            val isLoading = pos == items.size - 1 && loading
            if (isLoading) {
                holder.content?.visibility = View.GONE
                holder.progress?.visibility = View.VISIBLE
                holder.emotion?.visibility = View.GONE
                holder.toolsToggle?.visibility = View.GONE
                holder.toolsList?.visibility = View.GONE
            } else {
                holder.progress?.visibility = View.GONE
                holder.content?.visibility = View.VISIBLE
                holder.content?.text = msg.content
                holder.emotion?.visibility = View.GONE // no emotion in popup

                if (msg.tools != null && msg.tools.isNotEmpty()) {
                    holder.toolsToggle?.visibility = View.VISIBLE
                    val expanded = expandedTools.contains(pos)
                    holder.toolsToggle?.text = if (expanded) "tools▾" else "tools▸"
                    holder.toolsToggle?.setOnClickListener {
                        if (expandedTools.contains(pos)) expandedTools.remove(pos)
                        else expandedTools.add(pos)
                        notifyItemChanged(pos)
                    }
                    holder.toolsList?.visibility = if (expanded) View.VISIBLE else View.GONE
                    if (expanded) {
                        holder.toolsList?.removeAllViews()
                        for (tool in msg.tools) {
                            val tv = TextView(holder.itemView.context).apply {
                                text = tool
                                setTextAppearance(iss.nus.edu.sg.ca_application.R.style.Caption)
                                setTextColor(holder.itemView.context.resources.getColor(
                                    iss.nus.edu.sg.ca_application.R.color.text_hint, null))
                                textSize = 11f
                                setPadding(0, 1, 0, 1)
                                setTypeface(typeface, Typeface.ITALIC)
                            }
                            holder.toolsList?.addView(tv)
                        }
                    }
                } else {
                    holder.toolsToggle?.visibility = View.GONE
                    holder.toolsList?.visibility = View.GONE
                }
            }
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView? = view.findViewById(R.id.tvContent)
        val emotion: TextView? = view.findViewById(R.id.tvEmotion)
        val progress: android.widget.ProgressBar? = view.findViewById(R.id.progressLoading)
        val toolsToggle: TextView? = view.findViewById(R.id.tvToolsToggle)
        val toolsList: LinearLayout? = view.findViewById(R.id.layoutToolsList)
    }
}
