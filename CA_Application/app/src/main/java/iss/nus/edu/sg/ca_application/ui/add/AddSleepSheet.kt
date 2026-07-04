// Author: Wang Songyu
package iss.nus.edu.sg.ca_application.ui.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.ApiErrorHandler
import iss.nus.edu.sg.ca_application.network.ApiException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AddSleepSheet : BottomSheetDialogFragment() {

    private var isSaving = false
    private var editRecordId: Long = 0
    private var prefillSleepHours: Double = 0.0
    private var prefillSleepTime: String = ""
    private var prefillWakeTime: String = ""
    private var prefillMood: Int = 3
    private var prefillDate: String = ""
    private var prefillNotes: String = ""
    private var selectedMood = 3

    companion object {
        fun newEdit(recordId: Long, sleepHours: Double, sleepTime: String, wakeTime: String,
                    moodScore: Int, recordDate: String, notes: String): AddSleepSheet {
            return AddSleepSheet().apply {
                editRecordId = recordId
                prefillSleepHours = sleepHours
                prefillSleepTime = sleepTime
                prefillWakeTime = wakeTime
                prefillMood = moodScore
                prefillDate = recordDate
                prefillNotes = notes
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_add_sleep, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isEdit = editRecordId > 0
        val etDate = view.findViewById<EditText>(R.id.etSleepDate)
        val etSleep = view.findViewById<EditText>(R.id.etSleepTime)
        val etWake = view.findViewById<EditText>(R.id.etWakeTime)
        val tvDuration = view.findViewById<TextView>(R.id.tvCalcDuration)
        val etNotes = view.findViewById<EditText>(R.id.etSleepNotes)

        val btnSave = view.findViewById<Button>(R.id.btnSaveSleep)
        if (isEdit) {
            etDate.setText(prefillDate)
            etSleep.setText(prefillSleepTime)
            etWake.setText(prefillWakeTime)
            selectedMood = prefillMood
            etNotes.setText(prefillNotes)
            btnSave.text = "Update"
        } else {
            etDate.setText(java.time.LocalDate.now().toString())
        }
        etDate.setOnClickListener { showDatePicker(etDate) }
        etDate.isFocusable = false
        etDate.isClickable = true

        val durationWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                try {
                    val sleep = LocalTime.parse(etSleep.text.toString().trim(), DateTimeFormatter.ofPattern("H:mm"))
                    val wake = LocalTime.parse(etWake.text.toString().trim(), DateTimeFormatter.ofPattern("H:mm"))
                    var hours = java.time.Duration.between(sleep, wake).toMinutes() / 60.0
                    if (hours < 0) hours += 24
                    tvDuration.text = "Duration: %.1f hours".format(hours)
                } catch (_: Exception) { tvDuration.text = "Duration: --" }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etSleep.addTextChangedListener(durationWatcher)
        etWake.addTextChangedListener(durationWatcher)

        // Mood selector
        val moods = listOf(1, 2, 3, 4, 5)
        val moodViews = listOf(
            view.findViewById<TextView>(R.id.mood1), view.findViewById<TextView>(R.id.mood2),
            view.findViewById<TextView>(R.id.mood3), view.findViewById<TextView>(R.id.mood4),
            view.findViewById<TextView>(R.id.mood5)
        )
        selectedMood = if (isEdit) prefillMood else 3
        moodViews.forEachIndexed { i, tv ->
            tv.setOnClickListener {
                selectedMood = moods[i]
                moodViews.forEachIndexed { j, t -> t.alpha = if (j == i) 1f else 0.3f }
            }
        }
        if (isEdit) moodViews[prefillMood - 1].alpha = 1f else moodViews[2].alpha = 1f

        view.findViewById<Button>(R.id.btnSaveSleep).setOnClickListener {
            if (isSaving) return@setOnClickListener
            isSaving = true
            val dateStr = etDate.text.toString().trim()
            val sleepTimeStr = etSleep.text.toString().trim()
            val wakeTimeStr = etWake.text.toString().trim()

            val timePattern = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")
            if (sleepTimeStr.isEmpty() || wakeTimeStr.isEmpty()) {
                Toast.makeText(requireContext(), "Bedtime and wake time are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!sleepTimeStr.matches(timePattern) || !wakeTimeStr.matches(timePattern)) {
                Toast.makeText(requireContext(), "Time must be in HH:MM format (e.g. 23:00, 07:30)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Thread {
                val token = TokenManager.getToken(requireContext())
                val sleepHours = try {
                    val sleep = LocalTime.parse(sleepTimeStr, DateTimeFormatter.ofPattern("H:mm"))
                    val wake = LocalTime.parse(wakeTimeStr, DateTimeFormatter.ofPattern("H:mm"))
                    var h = java.time.Duration.between(sleep, wake).toMinutes() / 60.0
                    if (h < 0) h += 24; h
                } catch (_: Exception) { 0.0 }
                val dateStr2 = if (dateStr.isNotEmpty() && dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) dateStr else java.time.LocalDate.now().toString()
                try {
                    if (isEdit) {
                        ApiClient.updateSleepRecord(token, editRecordId, sleepHours,
                            sleepTimeStr, wakeTimeStr, selectedMood, dateStr, etNotes.text.toString().trim())
                    } else {
                        ApiClient.createSleepRecord(token, sleepHours, sleepTimeStr, wakeTimeStr,
                            selectedMood, dateStr2, etNotes.text.toString().trim())
                    }
                    isSaving = false
                    activity?.runOnUiThread {
                        parentFragmentManager.setFragmentResult("record_updated", Bundle())
                        Toast.makeText(requireContext(), if (isEdit) "Sleep updated" else "Sleep record saved", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                } catch (e: ApiException) {
                    isSaving = false
                    activity?.runOnUiThread {
                        if (activity != null) ApiErrorHandler.handle(requireActivity(), e)
                    }
                } catch (e: Exception) {
                    isSaving = false
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun showDatePicker(etDate: EditText) {
        val now = java.time.LocalDate.now()
        val dpd = android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
            etDate.setText(String.format("%04d-%02d-%02d", y, m + 1, d))
        }, now.year, now.monthValue - 1, now.dayOfMonth)
        dpd.datePicker.maxDate = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        dpd.datePicker.minDate = now.minusYears(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        dpd.show()
    }
}
