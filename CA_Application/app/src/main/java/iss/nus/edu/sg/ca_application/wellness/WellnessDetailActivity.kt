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
        tvDetailSleep.text = getString(R.string.sleep_format, sleepHours)
        tvDetailExercise.text = getString(R.string.exercise_format, exerciseActivity, exerciseDuration)
        tvDetailNotes.text = if (notes.isNotEmpty()) notes else getString(R.string.no_notes)
    }

    /**
     * Shows a confirmation dialog before deleting the record.
     */
    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_message))
            .setPositiveButton(getString(R.string.delete_confirm)) { _, _ ->
                deleteRecord()
            }
            .setNegativeButton(getString(R.string.cancel), null)
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
                        getString(R.string.delete_success),
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
                            getString(R.string.session_expired),
                            Toast.LENGTH_SHORT
                        ).show()

                        403 -> Toast.makeText(
                            this,
                            getString(R.string.api_forbidden),
                            Toast.LENGTH_SHORT
                        ).show()

                        404 -> Toast.makeText(
                            this,
                            getString(R.string.record_not_found),
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
                        getString(R.string.network_error),
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