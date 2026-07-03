package iss.nus.edu.sg.ca_application.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Author: Huang Qianer
 * Adapter for the Chat Sessions Sidebar with Delete and Management support.
 */
class HistoryAdapter(
    private val sessions: List<ChatSession>,
    private val onItemClick: (ChatSession) -> Unit,
    private val onDeleteClick: (ChatSession) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    data class ChatSession(val id: String, val firstMessage: String, val timestamp: Long)

    private var isManageMode: Boolean = false

    fun setManageMode(enabled: Boolean) {
        isManageMode = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_session, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val session = sessions[position]
        holder.bind(session, isManageMode)
        holder.itemView.setOnClickListener { onItemClick(session) }
        holder.btnDelete.setOnClickListener { onDeleteClick(session) }
    }

    override fun getItemCount(): Int = sessions.size

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.textViewSessionTitle)
        private val date: TextView = view.findViewById(R.id.textViewSessionDate)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteSession)
        private val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        fun bind(session: ChatSession, manageMode: Boolean) {
            title.text = session.firstMessage
            date.text = sdf.format(Date(session.timestamp))
            btnDelete.visibility = if (manageMode) View.VISIBLE else View.GONE
        }
    }
}
