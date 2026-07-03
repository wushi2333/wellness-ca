// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.character

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.model.CharacterMessage

class CharacterChatAdapter : RecyclerView.Adapter<CharacterChatAdapter.VH>() {

    private val items = mutableListOf<CharacterMessage>()
    private var loading = false
    private val expandedTools = mutableSetOf<Int>()

    override fun getItemCount() = items.size
    val lastIndex: Int get() = items.size - 1

    fun setMessages(msgs: List<CharacterMessage>) {
        items.clear(); loading = false; expandedTools.clear(); items.addAll(msgs); notifyDataSetChanged()
    }

    fun addUserMessage(text: String) {
        items.add(CharacterMessage(0, "user", text, "", ""))
        notifyItemInserted(items.size - 1)
    }

    /** Add a complete assistant message (e.g. welcome, no ID, not saved to server). */
    fun addAssistantMessage(text: String, tools: List<String>?) {
        items.add(CharacterMessage(0, "assistant", text, "", "", tools))
        notifyItemInserted(items.size - 1)
    }

    fun showLoading() {
        if (!loading) {
            loading = true
            items.add(CharacterMessage(0, "assistant", "", "", ""))
            notifyItemInserted(items.size - 1)
        }
    }

    fun updateLoadingReply(reply: String, emotion: String, tools: List<String>? = null) {
        loading = false
        if (items.isNotEmpty()) {
            val idx = items.size - 1
            items[idx] = CharacterMessage(0, "assistant", reply, emotion, "", tools)
            notifyItemChanged(idx)
        }
    }

    override fun getItemViewType(pos: Int) = if (items[pos].role == "user") 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 0) R.layout.item_character_message_user
                     else R.layout.item_character_message_ai
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val msg = items[pos]
        if (msg.role == "user") {
            holder.content?.text = msg.content
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
                holder.emotion?.text = emojiFor(msg.emotion)
                holder.emotion?.visibility = if (msg.emotion.isNotEmpty()) View.VISIBLE else View.GONE

                // Tools display
                if (msg.tools != null && msg.tools.isNotEmpty()) {
                    holder.toolsToggle?.visibility = View.VISIBLE
                    val expanded = expandedTools.contains(pos)
                    holder.toolsToggle?.text = if (expanded) "tools▾" else "tools▸"
                    holder.toolsToggle?.setOnClickListener {
                        if (expandedTools.contains(pos)) {
                            expandedTools.remove(pos)
                        } else {
                            expandedTools.add(pos)
                        }
                        notifyItemChanged(pos)
                    }

                    // Build or update tools list
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

    private fun emojiFor(e: String) = when (e) {
        "happy" -> "Yui · 😊 Happy"; "listening" -> "Yui · 👂 Listening"
        "thinking" -> "Yui · 🤔 Thinking"; "surprised" -> "Yui · 😲 Surprised"
        "focused" -> "Yui · 🔍 Focused"; "confused" -> "Yui · 😕 Confused"
        else -> ""
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView? = view.findViewById(R.id.tvContent)
        val emotion: TextView? = view.findViewById(R.id.tvEmotion)
        val progress: ProgressBar? = view.findViewById(R.id.progressLoading)
        val toolsToggle: TextView? = view.findViewById(R.id.tvToolsToggle)
        val toolsList: LinearLayout? = view.findViewById(R.id.layoutToolsList)
    }
}
