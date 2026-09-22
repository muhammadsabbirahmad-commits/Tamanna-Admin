package com.tamanna.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        findViewById<Button>(R.id.btnPartners).setOnClickListener {
            startActivity(Intent(this, PartnerManagementActivity::class.java))
        }
        findViewById<Button>(R.id.btnBusiness).setOnClickListener {
            startActivity(Intent(this, BusinessOverviewActivity::class.java))
        }
        findViewById<Button>(R.id.btnFinance).setOnClickListener {
            startActivity(Intent(this, FinanceProfitActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            // Admin Settings will be added in the next step.
        }
    }
}
