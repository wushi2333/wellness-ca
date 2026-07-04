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
import iss.nus.edu.sg.ca_application.util.ExerciseTypeMap

class AddExerciseSheet : BottomSheetDialogFragment() {

    private var isSaving = false
    private var editRecordId: Long = 0
    private var prefillActivity: String = ""
    private var prefillDuration: Int = 0
    private var prefillDate: String = ""
    private var prefillNotes: String = ""

    companion object {
        fun newEdit(recordId: Long, exerciseActivity: String, exerciseDuration: Int,
                    recordDate: String, notes: String): AddExerciseSheet {
            return AddExerciseSheet().apply {
                editRecordId = recordId
                prefillActivity = exerciseActivity
                prefillDuration = exerciseDuration
                prefillDate = recordDate
                prefillNotes = notes
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_add_exercise, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isEdit = editRecordId > 0
        val spinner = view.findViewById<Spinner>(R.id.spExerciseType)
        val etDuration = view.findViewById<EditText>(R.id.etExDuration)
        val etNotes = view.findViewById<EditText>(R.id.etExNotes)
        val types = resources.getStringArray(R.array.exercise_types)
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val etDate = view.findViewById<EditText>(R.id.etExDate)
        val btnSave = view.findViewById<Button>(R.id.btnSaveEx)
        if (isEdit) {
            etDate.setText(prefillDate)
            etDuration.setText(prefillDuration.toString())
            etNotes.setText(prefillNotes)
            // Select the prefill activity in spinner
            val types = resources.getStringArray(R.array.exercise_types)
            val idx = types.indexOfFirst { it.equals(prefillActivity, ignoreCase = true) || it == prefillActivity }
            if (idx >= 0) spinner.setSelection(idx)
            btnSave.text = "Update"
        } else {
            etDate.setText(java.time.LocalDate.now().toString())
        }
        etDate.setOnClickListener { showDatePicker(etDate) }
        etDate.isFocusable = false
        etDate.isClickable = true

        view.findViewById<Button>(R.id.btnSaveEx).setOnClickListener {
            if (isSaving) return@setOnClickListener
            isSaving = true
            val dateStr = etDate.text.toString().trim()
            Thread {
                val token = TokenManager.getToken(requireContext())
                val act = ExerciseTypeMap.toKey(spinner.selectedItem.toString())
                val duration = etDuration.text.toString().toIntOrNull() ?: 0
                val dateStr2 = if (dateStr.isNotEmpty() && dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) dateStr else java.time.LocalDate.now().toString()
                try {
                    if (isEdit) {
                        ApiClient.updateExerciseRecord(token, editRecordId, act, duration,
                            dateStr, etNotes.text.toString().trim())
                    } else {
                        ApiClient.createExerciseRecord(token, act, duration, dateStr2,
                            etNotes.text.toString().trim())
                    }
                    isSaving = false
                    activity?.runOnUiThread {
                        parentFragmentManager.setFragmentResult("record_updated", Bundle())
                        Toast.makeText(requireContext(), if (isEdit) "Exercise updated" else "Exercise record saved", Toast.LENGTH_SHORT).show()
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
