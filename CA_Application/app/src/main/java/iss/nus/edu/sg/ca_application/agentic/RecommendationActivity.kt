package iss.nus.edu.sg.ca_application.agentic

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.model.AgentRecommendation
import iss.nus.edu.sg.ca_application.model.ToolTrace
import android.widget.Toast
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.AgentApi
import iss.nus.edu.sg.ca_application.network.AuthApi

/**
 * Agentic AI recommendation screen.
 *
 * Drives the user through three states: idle, loading, result.
 * The loading state shows a simulated step-by-step progress aligned
 * with the agent's empirical tool sequence; the actual agent call is
 * a single HTTP round-trip handled in Phase 8.3.5.D.
 *
 * Author: Cai Peilin
 */
class RecommendationActivity : AppCompatActivity() {

    // Views
    private lateinit var headlineText: TextView
    private lateinit var subheadText: TextView
    private lateinit var generateButton: MaterialButton
    private lateinit var viewHistoryButton: MaterialButton

    private lateinit var loadingContainer: LinearLayout
    private lateinit var loadingStatusText: TextView
    private lateinit var loadingStepText: TextView

    private lateinit var resultContainer: LinearLayout
    private lateinit var recommendationText: TextView
    private lateinit var evidenceToggleText: TextView
    private lateinit var evidenceContainer: LinearLayout
    private lateinit var generateAgainButton: MaterialButton

    private lateinit var viewHistoryInResultButton: MaterialButton
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tokenManager: TokenManager
    private var evidenceExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recommendation)

        tokenManager = TokenManager(this)
        bindViews()
        wireListeners()
    }

    private fun bindViews() {
        headlineText = findViewById(R.id.headlineText)
        subheadText = findViewById(R.id.subheadText)
        generateButton = findViewById(R.id.generateButton)
        viewHistoryButton = findViewById(R.id.viewHistoryButton)

        loadingContainer = findViewById(R.id.loadingContainer)
        loadingStatusText = findViewById(R.id.loadingStatusText)
        loadingStepText = findViewById(R.id.loadingStepText)

        resultContainer = findViewById(R.id.resultContainer)
        recommendationText = findViewById(R.id.recommendationText)
        evidenceToggleText = findViewById(R.id.evidenceToggleText)
        evidenceContainer = findViewById(R.id.evidenceContainer)
        generateAgainButton = findViewById(R.id.generateAgainButton)
        viewHistoryInResultButton = findViewById(R.id.viewHistoryInResultButton)
    }

    private fun wireListeners() {
        generateButton.setOnClickListener { startGenerate() }
        generateAgainButton.setOnClickListener { startGenerate() }
        evidenceToggleText.setOnClickListener { toggleEvidence() }

        val historyIntent = {
            startActivity(android.content.Intent(this, RecommendationHistoryActivity::class.java))
        }
        viewHistoryButton.setOnClickListener { historyIntent() }
        viewHistoryInResultButton.setOnClickListener { historyIntent() }
    }

    // ---------------------------------------------------------------------
    // State transitions: idle → loading → result
    // ---------------------------------------------------------------------
    private fun startGenerate() {
        showLoading()
        scheduleStep(1, 0L)
        scheduleStep(2, 1500L)
        scheduleStep(3, 3000L)
        scheduleStep(4, 4500L)

        // Real network call runs in parallel with the simulated progress.
        // The progress is purely UI feedback; the actual agent loop is one
        // round-trip whose duration aligns with the four-step empirical timing.
        Thread {
            try {
                val token = tokenManager.getToken() ?: AuthApi.loginAsDevUser().also {
                    tokenManager.saveToken(it)
                }
                val result = AgentApi.recommend(token)
                runOnUiThread { showResult(result) }
            } catch (e: Exception) {
                runOnUiThread { showError(e.message ?: getString(R.string.agent_error_generic)) }
            }
        }.start()
    }

    private fun showError(message: String) {
        loadingContainer.visibility = View.GONE
        headlineText.visibility = View.VISIBLE
        subheadText.visibility = View.VISIBLE
        generateButton.visibility = View.VISIBLE
        viewHistoryButton.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun scheduleStep(step: Int, delayMs: Long) {
        handler.postDelayed({
            val statusRes = when (step) {
                1 -> R.string.agent_loading_step_1
                2 -> R.string.agent_loading_step_2
                3 -> R.string.agent_loading_step_3
                else -> R.string.agent_loading_step_4
            }
            loadingStatusText.text = getString(statusRes)
            loadingStepText.text = getString(R.string.agent_loading_step_format, step)
        }, delayMs)
    }

    private fun showLoading() {
        headlineText.visibility = View.GONE
        subheadText.visibility = View.GONE
        generateButton.visibility = View.GONE
        viewHistoryButton.visibility = View.GONE
        resultContainer.visibility = View.GONE
        loadingContainer.visibility = View.VISIBLE
    }

    private fun showResult(rec: AgentRecommendation) {
        loadingContainer.visibility = View.GONE
        viewHistoryButton.visibility = View.GONE
        resultContainer.visibility = View.VISIBLE

        recommendationText.text = rec.recommendation
        renderEvidence(rec.evidence)
        evidenceExpanded = false
        updateEvidenceToggleText()
    }

    private fun renderEvidence(evidence: List<ToolTrace>) {
        evidenceContainer.removeAllViews()
        evidence.forEachIndexed { index, item ->
            val row = TextView(this).apply {
                text = getString(R.string.agent_evidence_row, index + 1, item.summary)
                setTextAppearance(R.style.Body)
                setLineSpacing(6f, 1.0f)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) params.topMargin = (8 * resources.displayMetrics.density).toInt()
            row.layoutParams = params
            evidenceContainer.addView(row)
        }
    }

    private fun toggleEvidence() {
        evidenceExpanded = !evidenceExpanded
        evidenceContainer.visibility = if (evidenceExpanded) View.VISIBLE else View.GONE
        updateEvidenceToggleText()
    }

    private fun updateEvidenceToggleText() {
        val arrow = if (evidenceExpanded) "▴" else "▾"
        evidenceToggleText.text = getString(R.string.agent_evidence_section) + "  " + arrow
    }

}