// Author: Xia Zihang
package iss.nus.edu.sg.ca_application

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.auth.LoginActivity
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.ApiErrorHandler
import iss.nus.edu.sg.ca_application.network.ApiException
import iss.nus.edu.sg.ca_application.network.ProfileApi
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var btnEnglish: TextView
    private lateinit var btnChinese: TextView

    companion object {
        private const val PREFS_NAME = "settings_prefs"
        private const val KEY_LANGUAGE = "app_language"

        fun getSavedLocale(context: Context): Locale? {
            val lang = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_LANGUAGE, null) ?: return null
            return when (lang) {
                "zh" -> Locale.SIMPLIFIED_CHINESE
                "en" -> Locale.ENGLISH
                else -> null
            }
        }

        fun wrapContextForLocale(base: Context): Context {
            val locale = getSavedLocale(base) ?: return base
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapContextForLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.background)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Back button
        (findViewById<View>(R.id.btnSettingsBack).parent as View).applyTopInset()
        findViewById<View>(R.id.btnSettingsBack).setOnClickListener { finish() }

        // Language toggle buttons
        btnEnglish = findViewById(R.id.btnEnglish)
        btnChinese = findViewById(R.id.btnChinese)

        val savedLang = prefs.getString(KEY_LANGUAGE, null)
        updateLanguageButtons(savedLang)

        btnEnglish.setOnClickListener { setLanguage("en") }
        btnChinese.setOnClickListener { setLanguage("zh") }

        // Profile row → ProfileActivity
        findViewById<View>(R.id.rowProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Change Password row → ChangePasswordActivity (Google users blocked)
        findViewById<View>(R.id.rowChangePassword).setOnClickListener {
            if (TokenManager.getProvider(this) == "GOOGLE") {
                Toast.makeText(this, R.string.google_no_password, Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, ChangePasswordActivity::class.java))
            }
        }

        // Logout row → confirm dialog → clear token → LoginActivity
        findViewById<View>(R.id.rowLogout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.logout) { _, _ -> performLogout() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Delete Account row → double confirm with username input → DELETE /auth/account
        findViewById<View>(R.id.rowDeleteAccount).setOnClickListener {
            val username = TokenManager.getUsername(this)
            val input = android.widget.EditText(this).apply {
                hint = getString(R.string.username)
                setPadding(48, 48, 48, 48)
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_confirm_title)
                .setMessage(getString(R.string.delete_account_confirm_message))
                .setView(input)
                .setPositiveButton(R.string.delete_account) { _, _ ->
                    if (input.text.toString().trim() == username) {
                        performDeleteAccount()
                    } else {
                        Toast.makeText(this, "Username does not match", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun updateLanguageButtons(selected: String?) {
        val isEnglish = selected == "en"
        val isChinese = selected == "zh"
        btnEnglish.isSelected = isEnglish
        btnEnglish.isActivated = isEnglish
        btnEnglish.setTextColor(if (isEnglish) getColor(R.color.on_primary) else getColor(R.color.text_primary))
        btnChinese.isSelected = isChinese
        btnChinese.isActivated = isChinese
        btnChinese.setTextColor(if (isChinese) getColor(R.color.on_primary) else getColor(R.color.text_primary))
    }

    private fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        Locale.setDefault(
            when (lang) {
                "zh" -> Locale.SIMPLIFIED_CHINESE
                else -> Locale.ENGLISH
            }
        )
        updateLanguageButtons(lang)
        Toast.makeText(this, if (lang == "zh") "语言已切换" else "Language changed", Toast.LENGTH_SHORT).show()

        // Restart app to apply everywhere
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun performLogout() {
        TokenManager.clearToken(this)
        Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun performDeleteAccount() {
        val token = TokenManager.getToken(this)
        Thread {
            try {
                ProfileApi.deleteAccount(token)
                runOnUiThread {
                    Toast.makeText(this, R.string.delete_account_success, Toast.LENGTH_SHORT).show()
                    performLogout()
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    if (!ApiErrorHandler.handle(this, e)) {
                        Toast.makeText(this, R.string.delete_account_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { ApiErrorHandler.handleGeneric(this, e) }
            }
        }.start()
    }
}
