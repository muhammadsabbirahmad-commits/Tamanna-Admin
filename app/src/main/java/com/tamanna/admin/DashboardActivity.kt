package com.tamanna.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {
    private lateinit var partnerCount: TextView
    private lateinit var pendingCount: TextView
    private lateinit var approvedCount: TextView
    private lateinit var suspendedCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        partnerCount = findViewById(R.id.tvPartnerCount)
        pendingCount = findViewById(R.id.tvPendingCount)
        approvedCount = findViewById(R.id.tvApprovedCount)
        suspendedCount = findViewById(R.id.tvSuspendedCount)

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
            startActivity(Intent(this, AdminSettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPartnerSummary()
    }

    private fun refreshPartnerSummary() {
        val stored = getSharedPreferences("partner_management", MODE_PRIVATE)
            .getStringSet("partners", emptySet()) ?: emptySet()

        var pending = 0
        var approved = 0
        var suspended = 0

        stored.forEach { entry ->
            when (entry.substringBefore("|")) {
                "PENDING" -> pending++
                "APPROVED" -> approved++
                "SUSPENDED" -> suspended++
            }
        }

        partnerCount.text = "Partners\n" + stored.size
        pendingCount.text = "Pending\n" + pending
        approvedCount.text = "Approved\n" + approved
        suspendedCount.text = "Suspended\n" + suspended
    }
}
