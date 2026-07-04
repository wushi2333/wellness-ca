// Author: Xia Zihang
package iss.nus.edu.sg.ca_application

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.ApiErrorHandler
import iss.nus.edu.sg.ca_application.network.ApiException
import iss.nus.edu.sg.ca_application.network.ProfileApi

class ChangePasswordActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(SettingsActivity.wrapContextForLocale(newBase))
    }

    private lateinit var etOldPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvError: TextView
    private lateinit var btnSave: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.background)
        setContentView(R.layout.activity_change_password)

        etOldPassword = findViewById(R.id.etOldPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        tvError = findViewById(R.id.tvError)
        btnSave = findViewById(R.id.btnSave)

        (findViewById<View>(R.id.btnBack).parent as View).applyTopInset()
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        btnSave.setOnClickListener { attemptChangePassword() }
    }

    private fun attemptChangePassword() {
        val oldPw = etOldPassword.text.toString().trim()
        val newPw = etNewPassword.text.toString().trim()
        val confirmPw = etConfirmPassword.text.toString().trim()

        // Client-side validation
        if (oldPw.isEmpty() || newPw.isEmpty() || confirmPw.isEmpty()) {
            showError(getString(R.string.field_required))
            return
        }
        if (newPw.length < 8) {
            showError(getString(R.string.password_weak))
            return
        }
        var hasDigit = false
        var hasLetter = false
        for (c in newPw) {
            if (c.isDigit()) hasDigit = true
            if (c.isLetter()) hasLetter = true
        }
        if (!hasDigit || !hasLetter) {
            showError(getString(R.string.password_weak))
            return
        }
        if (newPw != confirmPw) {
            showError(getString(R.string.password_mismatch))
            return
        }

        tvError.visibility = View.GONE
        btnSave.isEnabled = false

        val token = TokenManager.getToken(this)
        Thread {
            try {
                ProfileApi.changePassword(token, oldPw, newPw)
                runOnUiThread {
                    Toast.makeText(this, R.string.password_changed, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    btnSave.isEnabled = true
                    if (!ApiErrorHandler.handle(this, e)) {
                        try {
                            val errBody = org.json.JSONObject(e.message ?: "")
                            showError(errBody.optString("detail", e.message ?: getString(R.string.network_error)))
                        } catch (_: Exception) {
                            showError(e.message ?: getString(R.string.network_error))
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnSave.isEnabled = true
                    ApiErrorHandler.handleGeneric(this, e)
                }
            }
        }.start()
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }
}
