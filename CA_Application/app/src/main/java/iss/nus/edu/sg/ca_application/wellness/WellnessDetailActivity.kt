package iss.nus.edu.sg.ca_application.wellness

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.ApiException

/**
 * Author: Wang Songyu
 *
 * Displays the details of a wellness record.
 * Users can edit or delete the selected record.
 */
class WellnessDetailActivity : AppCompatActivity() {

    private lateinit var tvDetailDate: TextView
    private lateinit var tvDetailSleep: TextView
    private lateinit var tvDetailExercise: TextView
    private lateinit var tvDetailNotes: TextView
    private lateinit var btnEdit: Button
    private lateinit var btnDelete: Button
    private lateinit var progressBar: ProgressBar

    private val currentToken: String
        get() = TokenManager.getToken(this)

    private var recordId: Int = -1
    private var sleepHours: Double = 0.0
    private var exerciseActivity: String = ""
    private var exerciseDuration: Int = 0
    private var recordDate: String = ""
    private var notes: String = ""

    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wellness_detail)

        tvDetailDate = findViewById(R.id.tvDetailDate)
        tvDetailSleep = findViewById(R.id.tvDetailSleep)
        tvDetailExercise = findViewById(R.id.tvDetailExercise)
        tvDetailNotes = findViewById(R.id.tvDetailNotes)
        btnEdit = findViewById(R.id.btnEdit)
        btnDelete = findViewById(R.id.btnDelete)
        progressBar = findViewById(R.id.progressBar)

        recordId = intent.getIntExtra("recordId", -1)
        sleepHours = intent.getDoubleExtra("sleepHours", 0.0)
        exerciseActivity = intent.getStringExtra("exerciseActivity") ?: ""
        exerciseDuration = intent.getIntExtra("exerciseDuration", 0)
        recordDate = intent.getStringExtra("recordDate") ?: ""
        notes = intent.getStringExtra("notes") ?: ""

        displayRecord()

        btnEdit.setOnClickListener {
            val intent = Intent(this, WellnessEntryActivity::class.java).apply {
                putExtra("recordId", recordId)
                putExtra("sleepHours", sleepHours)
                putExtra("exerciseActivity", exerciseActivity)
                putExtra("exerciseDuration", exerciseDuration)
                putExtra("recordDate", recordDate)
                putExtra("notes", notes)
            }
            editLauncher.launch(intent)
        }

        btnDelete.setOnClickListener {
            confirmDelete()
        }
    }

    /**
     * Displays the selected wellness record.
     */
    private fun displayRecord() {
        tvDetailDate.text = recordDate
        tvDetailSleep.text = "Sleep: ${sleepHours}h"
        tvDetailExercise.text = "$exerciseActivity (${exerciseDuration} min)"
        tvDetailNotes.text = if (notes.isNotEmpty()) notes else "No notes"
    }

    /**
     * Shows a confirmation dialog before deleting the record.
     */
    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to delete this wellness record?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRecord()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Deletes the current wellness record through the backend API.
     */
    private fun deleteRecord() {
        showLoading(true)

        Thread {
            try {
                ApiClient.deleteRecord(currentToken, recordId)

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Record deleted successfully.",
                        Toast.LENGTH_SHORT
                    ).show()

                    setResult(Activity.RESULT_OK)
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
                            "Delete failed (${e.code}).",
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
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnEdit.isEnabled = !loading
        btnDelete.isEnabled = !loading
    }
}