package com.tamanna.admin

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BusinessOverviewActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    override fun onResume() {
        super.onResume()
        AdminSecurityGuard.requireUnlocked(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_overview)

        tvStatus = findViewById(R.id.tvOverviewStatus)

        findViewById<Button>(R.id.btnRefreshOverview).setOnClickListener {
            tvStatus.text = "Overview refreshed"
        }
    }
}
