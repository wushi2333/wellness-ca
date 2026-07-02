package iss.nus.edu.sg.ca_application

// Author: Wang Songyu, Liu Yu

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.agentic.RecommendationActivity
import iss.nus.edu.sg.ca_application.auth.LoginActivity
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.chat.ChatActivity
import iss.nus.edu.sg.ca_application.wellness.WellnessEntryActivity
import iss.nus.edu.sg.ca_application.wellness.WellnessListActivity

/**
 * Main dashboard — shown after login.
 *
 * Provides navigation to all app features:
 *   - View wellness records
 *   - Add a new wellness record
 *   - AI wellness insights
 *   - Logout
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnViewRecords).setOnClickListener {
            startActivity(Intent(this, WellnessListActivity::class.java))
        }

        findViewById<Button>(R.id.btnAddRecord).setOnClickListener {
            // Open WellnessEntryActivity in "create" mode (no editingRecordId extra)
            startActivity(Intent(this, WellnessEntryActivity::class.java))
        }

        findViewById<Button>(R.id.btnWellnessInsights).setOnClickListener {
            startActivity(Intent(this, RecommendationActivity::class.java))
        }

        findViewById<Button>(R.id.btnChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            TokenManager.clearToken(this)
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
