package iss.nus.edu.sg.ca_application.network

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.auth.LoginActivity
import iss.nus.edu.sg.ca_application.auth.TokenManager
import org.json.JSONObject

/**
 * Shared API error handler. Centralizes the repeated 401/403/404/generic catch blocks
 * used across WellnessListActivity, WellnessDetailActivity, WellnessEntryActivity,
 * MainActivity, and CharacterChatActivity.
 */
object ApiErrorHandler {

    /** Handle an ApiException. Returns true if the error was fully handled. */
    fun handle(activity: Activity, e: ApiException): Boolean {
        when (e.code) {
            401 -> {
                Toast.makeText(activity, activity.getString(R.string.session_expired), Toast.LENGTH_LONG).show()
                TokenManager.clearToken(activity)
                val intent = Intent(activity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                activity.startActivity(intent)
                activity.finish()
                return true
            }
            403 -> {
                Toast.makeText(activity, activity.getString(R.string.api_forbidden), Toast.LENGTH_SHORT).show()
                return true
            }
            404 -> {
                Toast.makeText(activity, activity.getString(R.string.record_not_found), Toast.LENGTH_SHORT).show()
                return true
            }
            409 -> {
                // Parse backend's message, fallback to raw body
                val msg = try { JSONObject(e.body).optString("error", e.body) } catch (_: Exception) { e.body }
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                return true
            }
        }
        return false
    }

    /** Handle a generic Exception (network errors, etc). */
    fun handleGeneric(activity: Activity, e: Exception) {
        Toast.makeText(activity, activity.getString(R.string.network_error), Toast.LENGTH_SHORT).show()
    }
}
