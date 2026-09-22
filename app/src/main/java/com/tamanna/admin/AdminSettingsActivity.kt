package com.tamanna.admin

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
    }
}
