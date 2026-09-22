package com.tamanna.admin

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        findViewById<Button>(R.id.btnPartners).setOnClickListener {
            // Partner Management will be added in the next step.
        }
        findViewById<Button>(R.id.btnBusiness).setOnClickListener {
            // Business Overview will be added in the next step.
        }
        findViewById<Button>(R.id.btnFinance).setOnClickListener {
            // Finance & Profit will be added in the next step.
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            // Admin Settings will be added in the next step.
        }
    }
}
