// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.ui.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.applyTopInset
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.model.DailyWellness
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.ApiErrorHandler
import iss.nus.edu.sg.ca_application.network.ApiException
import iss.nus.edu.sg.ca_application.network.CacheManager
import iss.nus.edu.sg.ca_application.ui.add.AddExerciseSheet
import iss.nus.edu.sg.ca_application.ui.add.AddSleepSheet
import iss.nus.edu.sg.ca_application.ui.chart.WeekUtils
import iss.nus.edu.sg.ca_application.util.ExerciseTypeMap

class RecordManageFragment : Fragment() {

    companion object {
        const val TYPE_SLEEP = "sleep"
        const val TYPE_EXERCISE = "exercise"
    }

    private var type: String = TYPE_SLEEP
    private val adapter = RecordAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        type = arguments?.getString("type", TYPE_SLEEP) ?: TYPE_SLEEP
        return inflater.inflate(R.layout.fragment_record_manage, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Reload when a record is updated from the edit sheet
        parentFragmentManager.setFragmentResultListener("record_updated", this) { _, _ -> loadData() }

        view.findViewById<TextView>(R.id.tvManageTitle).text = getString(
            if (type == TYPE_SLEEP) R.string.manage_sleep_title else R.string.manage_exercise_title)

        view.findViewById<View>(R.id.topBar).applyTopInset()
        view.findViewById<View>(R.id.btnManageBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvManageRecords)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        adapter.onEdit = { item -> showEditSheet(item) }
        adapter.onDelete = { item -> confirmDelete(item) }

        loadData()
    }

    private fun loadData() {
        val token = TokenManager.getToken(requireContext())
        if (token.isEmpty()) return
        Thread {
            try {
                val (dailies, _) = ApiClient.getDailyRecords(token, 0, 200)
                // Filter: only dates with relevant data
                val filtered = if (type == TYPE_SLEEP) {
                    dailies.filter { it.sleep != null }
                } else {
                    dailies.filter { it.exercises.isNotEmpty() }
                }
                // Group by week
                val grouped = filtered.groupBy { d ->
                    try {
                        val date = java.time.LocalDate.parse(d.recordDate)
                        WeekUtils.weekMonday(date)
                    } catch (_: Exception) { WeekUtils.currentWeekMonday() }
                }.toList().sortedByDescending { (monday, _) -> monday }

                val sections = grouped.map { (monday, dailies) ->
                    val sorted = dailies.sortedByDescending { it.recordDate }
                    val recordItems = if (type == TYPE_SLEEP) {
                        sorted.map { d -> RecordItem(d.recordDate, d.sleep!!, null) }
                    } else {
                        sorted.flatMap { d -> d.exercises.map { e -> RecordItem(d.recordDate, null, e) } }
                    }
                    RecordSection(WeekUtils.weekLabel(monday), recordItems, monday)
                }
                activity?.runOnUiThread { adapter.setSections(sections) }
            } catch (e: ApiException) {
                activity?.runOnUiThread { activity?.let { ApiErrorHandler.handle(it, e) } }
            } catch (_: Exception) {}
        }.start()
    }

    private fun showEditSheet(item: RecordItem) {
        if (type == TYPE_SLEEP && item.sleep != null) {
            val s = item.sleep
            val sheet = AddSleepSheet.newEdit(s.id, s.sleepHours, s.sleepTime, s.wakeTime,
                s.moodScore, item.date, s.notes)
            sheet.show(parentFragmentManager, "edit_sleep")
        } else if (type == TYPE_EXERCISE && item.exercise != null) {
            val e = item.exercise
            val sheet = AddExerciseSheet.newEdit(e.id, e.exerciseActivity,
                e.exerciseDuration, item.date, e.notes)
            sheet.show(parentFragmentManager, "edit_exercise")
        }
    }

    private fun confirmDelete(item: RecordItem) {
        val msg = if (type == TYPE_SLEEP)
            getString(R.string.confirm_delete_msg_sleep, item.date)
        else
            getString(R.string.confirm_delete_msg_exercise, item.exercise?.exerciseActivity ?: "", item.date)
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.delete_record)) { _, _ -> deleteRecord(item) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteRecord(item: RecordItem) {
        val token = TokenManager.getToken(requireContext())
        Thread {
            try {
                if (type == TYPE_SLEEP && item.sleep != null) {
                    ApiClient.deleteSleepRecord(token, item.sleep.id)
                } else if (type == TYPE_EXERCISE && item.exercise != null) {
                    ApiClient.deleteExerciseRecord(token, item.exercise.id)
                }
                activity?.runOnUiThread {
                    CacheManager.invalidate("records")
                    Toast.makeText(requireContext(), getString(R.string.toast_deleted), Toast.LENGTH_SHORT).show()
                    loadData()
                }
            } catch (e: ApiException) {
                activity?.runOnUiThread { activity?.let { ApiErrorHandler.handle(it, e) } }
            } catch (_: Exception) {}
        }.start()
    }
}

// ── Data classes ──────────────────────────────────────────────────────

data class RecordSection(
    val label: String,
    val items: List<RecordItem>,
    val monday: java.time.LocalDate? = null
)

data class RecordItem(
    val date: String,
    val sleep: iss.nus.edu.sg.ca_application.model.SleepRecord?,
    val exercise: iss.nus.edu.sg.ca_application.model.ExerciseRecord?
)

// ── Adapter ───────────────────────────────────────────────────────────

class RecordAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val sections = mutableListOf<RecordSection>()
    private val collapsed = mutableSetOf<Int>()
    var onEdit: ((RecordItem) -> Unit)? = null
    var onDelete: ((RecordItem) -> Unit)? = null

    fun setSections(list: List<RecordSection>) {
        sections.clear(); collapsed.clear()
        // Only expand the first (most recent) section by default
        sections.addAll(list)
        if (sections.size > 1) (1 until sections.size).forEach { collapsed.add(it) }
        notifyDataSetChanged()
    }

    override fun getItemViewType(pos: Int): Int {
        var idx = pos
        for (i in sections.indices) {
            if (idx == 0) return i * 2      // section header
            idx--
            if (!collapsed.contains(i)) {
                val itemCount = sections[i].items.size
                if (idx < itemCount) return i * 2 + 1  // record item
                idx -= itemCount
            }
        }
        throw IndexOutOfBoundsException()
    }

    override fun getItemCount(): Int {
        var count = 0
        for (i in sections.indices) {
            count++ // header
            if (!collapsed.contains(i)) count += sections[i].items.size
        }
        return count
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType % 2 == 0) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_record_section, parent, false)
            SectionHolder(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_record_row, parent, false)
            RecordHolder(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        val (sectionIdx, isHeader, itemIdx) = resolvePosition(pos)
        val section = sections[sectionIdx]
        if (isHeader) {
            val h = holder as SectionHolder
            h.label.text = section.label
            h.arrow.text = if (collapsed.contains(sectionIdx)) "▶" else "▼"
            h.itemView.setOnClickListener {
                if (collapsed.contains(sectionIdx)) collapsed.remove(sectionIdx)
                else collapsed.add(sectionIdx)
                notifyDataSetChanged()
            }
        } else {
            val h = holder as RecordHolder
            val item = section.items[itemIdx]
            h.date.text = item.date
            h.detail.text = if (item.sleep != null) {
                val s = item.sleep
                val moodEmoji = when { s.moodScore >= 4 -> "😊"; s.moodScore >= 3 -> "😐"; s.moodScore > 0 -> "😟"; else -> "" }
                if (s.sleepTime.isNotEmpty())
                    "💤 %.1fh  %s-%s  %s".format(s.sleepHours, s.sleepTime, s.wakeTime, moodEmoji)
                else
                    "💤 %.1fh  %s".format(s.sleepHours, moodEmoji)
            } else if (item.exercise != null) {
                "🏃 %s  %dmin".format(ExerciseTypeMap.toDisplay(h.itemView.context, item.exercise.exerciseActivity), item.exercise.exerciseDuration)
            } else ""
            h.btnEdit.setOnClickListener { onEdit?.invoke(item) }
            h.btnDelete.setOnClickListener { onDelete?.invoke(item) }
        }
    }

    private fun resolvePosition(pos: Int): Triple<Int, Boolean, Int> {
        var idx = pos
        for (i in sections.indices) {
            if (idx == 0) return Triple(i, true, -1)
            idx--
            if (!collapsed.contains(i)) {
                val itemCount = sections[i].items.size
                if (idx < itemCount) return Triple(i, false, idx)
                idx -= itemCount
            }
        }
        throw IndexOutOfBoundsException()
    }

    class SectionHolder(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.tvSectionLabel)
        val arrow: TextView = v.findViewById(R.id.tvSectionArrow)
    }

    class RecordHolder(v: View) : RecyclerView.ViewHolder(v) {
        val date: TextView = v.findViewById(R.id.tvRecordDate)
        val detail: TextView = v.findViewById(R.id.tvRecordDetail)
        val btnEdit: View = v.findViewById(R.id.btnRecordEdit)
        val btnDelete: View = v.findViewById(R.id.btnRecordDelete)
    }
}
