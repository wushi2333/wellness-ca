// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.character

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.R

class CharacterLoadingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.overlay_loading)

        findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, CharacterChatActivity::class.java))
            finish()
            overridePendingTransition(0, 0)
        }, 800)
    }
}
