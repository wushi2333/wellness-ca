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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.ApiException

/** Lists wellness records with swipe-to-delete and FAB to add new. */
class WellnessListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WellnessAdapter
    private lateinit var btnAddRecord: Button
    private lateinit var tvEmptyState: TextView
    private lateinit var progressBar: ProgressBar

    private val currentToken: String
        get() = TokenManager.getToken(this)

    private val entryActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadRecords()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wellness_list)

        recyclerView = findViewById(R.id.recyclerViewWellness)
        recyclerView.layoutManager = LinearLayoutManager(this)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        progressBar = findViewById(R.id.progressBar)

        adapter = WellnessAdapter(
            records = emptyList(),
            onItemClick = { record ->
                val intent = Intent(this, WellnessDetailActivity::class.java).apply {
                    putExtra("recordId", record.id)
                    putExtra("sleepHours", record.sleepHours)
                    putExtra("exerciseActivity", record.exerciseActivity)
                    putExtra("exerciseDuration", record.exerciseDuration)
                    putExtra("recordDate", record.recordDate)
                    putExtra("notes", record.notes)
                }
                entryActivityLauncher.launch(intent)
            },
            onItemLongClick = { record ->
                confirmDelete(record.id)
            }
        )
        recyclerView.adapter = adapter

        btnAddRecord = findViewById(R.id.btnAddRecord)
        btnAddRecord.setOnClickListener {
            val intent = Intent(this, WellnessEntryActivity::class.java)
            entryActivityLauncher.launch(intent)
        }

        loadRecords()
    }

    private fun confirmDelete(recordId: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_message))
            .setPositiveButton(getString(R.string.delete_confirm)) { _, _ -> deleteRecord(recordId) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteRecord(recordId: Int) {
        showLoading(true)
        Thread {
            try {
                ApiClient.deleteRecord(currentToken, recordId)
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
                    loadRecords()
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    showLoading(false)
                    when (e.code) {
                        401 -> Toast.makeText(this, getString(R.string.session_expired), Toast.LENGTH_SHORT).show()
                        403 -> Toast.makeText(this, getString(R.string.api_forbidden), Toast.LENGTH_SHORT).show()
                        404 -> Toast.makeText(this, getString(R.string.record_not_found), Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(this, "Failed to delete record: ${e.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun loadRecords() {
        showLoading(true)
        Thread {
            try {
                val records = ApiClient.getRecords(currentToken)
                runOnUiThread {
                    showLoading(false)
                    adapter.updateData(records)
                    tvEmptyState.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    showLoading(false)
                    when (e.code) {
                        401 -> Toast.makeText(this, getString(R.string.session_expired), Toast.LENGTH_SHORT).show()
                        403 -> Toast.makeText(this, getString(R.string.api_forbidden), Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(this, "Request failed: ${e.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            recyclerView.visibility = View.GONE
            tvEmptyState.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
        }
    }
}
