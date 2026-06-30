package iss.nus.edu.sg.ca_application.wellness

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.model.WellnessEntry
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.ApiException
import java.util.Calendar

/**
 * Author: Wang Songyu
 *
 * Screen for creating or updating a wellness record.
 */
class WellnessEntryActivity : AppCompatActivity() {

    private lateinit var etSleepHours: EditText
    private lateinit var etExerciseActivity: EditText
    private lateinit var etExerciseDuration: EditText
    private lateinit var etRecordDate: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    private val currentToken: String
        get() = TokenManager.getToken(this)

    private var editingRecordId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wellness_entry)

        etSleepHours = findViewById(R.id.etSleepHours)
        etExerciseActivity = findViewById(R.id.etExerciseActivity)
        etExerciseDuration = findViewById(R.id.etExerciseDuration)
        etRecordDate = findViewById(R.id.etRecordDate)
        etNotes = findViewById(R.id.etNotes)
        btnSave = findViewById(R.id.btnSave)
        progressBar = findViewById(R.id.progressBar)

        editingRecordId = intent.getIntExtra("record_id", -1)

        if (editingRecordId != -1) {
            etSleepHours.setText(intent.getDoubleExtra("sleep_hours", 0.0).toString())
            etExerciseActivity.setText(intent.getStringExtra("exercise_activity") ?: "")
            etExerciseDuration.setText(intent.getIntExtra("exercise_duration", 0).toString())
            etRecordDate.setText(intent.getStringExtra("record_date") ?: "")
            etNotes.setText(intent.getStringExtra("notes") ?: "")
            btnSave.text = "Update"
        }

        etRecordDate.setOnClickListener {
            showDatePicker()
        }

        btnSave.setOnClickListener {
            saveRecord()
        }
    }

    /**
     * Displays a date picker for selecting the record date.
     */
    private fun showDatePicker() {

        val calendar = Calendar.getInstance()

        val existing = etRecordDate.text.toString()

        if (existing.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val parts = existing.split("-")
            calendar.set(
                parts[0].toInt(),
                parts[1].toInt() - 1,
                parts[2].toInt()
            )
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->

                val formatted = String.format(
                    "%04d-%02d-%02d",
                    selectedYear,
                    selectedMonth + 1,
                    selectedDay
                )

                etRecordDate.setText(formatted)

            },
            year,
            month,
            day
        ).show()
    }

    /**
     * Validates user input and submits the record to the backend.
     */
    private fun saveRecord() {

        val sleepHoursText = etSleepHours.text.toString().trim()
        val exerciseActivity = etExerciseActivity.text.toString().trim()
        val exerciseDurationText = etExerciseDuration.text.toString().trim()
        val recordDate = etRecordDate.text.toString().trim()
        val notes = etNotes.text.toString().trim()

        if (
            sleepHoursText.isEmpty() ||
            exerciseActivity.isEmpty() ||
            exerciseDurationText.isEmpty() ||
            recordDate.isEmpty()
        ) {
            Toast.makeText(
                this,
                "Please complete all required fields.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val sleepHours = sleepHoursText.toDoubleOrNull()
        val exerciseDuration = exerciseDurationText.toIntOrNull()

        if (sleepHours == null || exerciseDuration == null) {
            Toast.makeText(
                this,
                "Invalid numeric input.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (sleepHours < 0 || sleepHours > 24) {
            Toast.makeText(
                this,
                "Sleep hours must be between 0 and 24.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (exerciseDuration < 0 || exerciseDuration > 1440) {
            Toast.makeText(
                this,
                "Exercise duration must be between 0 and 1440 minutes.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!recordDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            Toast.makeText(
                this,
                "Please select a valid date.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val entry = WellnessEntry(
            sleepHours = sleepHours,
            exerciseActivity = exerciseActivity,
            exerciseDuration = exerciseDuration,
            recordDate = recordDate,
            notes = notes
        )

        showLoading(true)

        Thread {

            try {

                if (editingRecordId == -1) {
                    ApiClient.createRecord(currentToken, entry)
                } else {
                    ApiClient.updateRecord(currentToken, editingRecordId, entry)
                }

                runOnUiThread {

                    Toast.makeText(
                        this,
                        "Record saved successfully.",
                        Toast.LENGTH_SHORT
                    ).show()

                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: ApiException) {

                runOnUiThread {

                    showLoading(false)

                    when (e.code) {

                        401 -> Toast.makeText(
                            this,
                            "Session expired. Please log in again.",
                            Toast.LENGTH_SHORT
                        ).show()

                        403 -> Toast.makeText(
                            this,
                            "API gateway authentication failed.",
                            Toast.LENGTH_SHORT
                        ).show()

                        404 -> Toast.makeText(
                            this,
                            "Record not found.",
                            Toast.LENGTH_SHORT
                        ).show()

                        else -> Toast.makeText(
                            this,
                            "Save failed (${e.code}).",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    showLoading(false)

                    Toast.makeText(
                        this,
                        "Network error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }.start()
    }

    /**
     * Shows or hides the loading indicator.
     */
    private fun showLoading(loading: Boolean) {
        progressBar.visibility =
            if (loading) View.VISIBLE else View.GONE

        btnSave.isEnabled = !loading
    }
}