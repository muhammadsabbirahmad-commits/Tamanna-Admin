package com.tamanna.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AdminSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)

        findViewById<Button>(R.id.btnRefreshSettings).setOnClickListener {
            findViewById<TextView>(R.id.tvSettingsStatus).text = "Admin settings ready"
        }

        findViewById<Button>(R.id.btnAdminLogout).setOnClickListener {
            getSharedPreferences("admin_security", MODE_PRIVATE)
                .edit()
                .putBoolean("admin_unlocked", false)
                .apply()

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }
}
