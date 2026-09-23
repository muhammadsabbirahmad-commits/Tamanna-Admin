package com.tamanna.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class DashboardActivity : AppCompatActivity() {
    private lateinit var partnerCount: TextView
    private lateinit var pendingCount: TextView
    private lateinit var approvedCount: TextView
    private lateinit var suspendedCount: TextView
    private lateinit var enterpriseConnectionStatus: TextView
    private lateinit var enterpriseDataStatus: TextView
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        partnerCount = findViewById(R.id.tvPartnerCount)
        pendingCount = findViewById(R.id.tvPendingCount)
        approvedCount = findViewById(R.id.tvApprovedCount)
        suspendedCount = findViewById(R.id.tvSuspendedCount)
        enterpriseConnectionStatus = findViewById(R.id.tvEnterpriseConnectionStatus)
        enterpriseDataStatus = findViewById(R.id.tvEnterpriseDataStatus)
        findViewById<Button>(R.id.btnPartners).setOnClickListener { startActivity(Intent(this, PartnerManagementActivity::class.java)) }
        findViewById<Button>(R.id.btnProducts).setOnClickListener { startActivity(Intent(this, EnterpriseProductsActivity::class.java)) }
        findViewById<Button>(R.id.btnSalesPurchase).setOnClickListener { startActivity(Intent(this, EnterpriseSalesPurchaseActivity::class.java)) }
        findViewById<Button>(R.id.btnReports).setOnClickListener { startActivity(Intent(this, EnterpriseDateRangeReportActivity::class.java)) }
        findViewById<Button>(R.id.btnBusiness).setOnClickListener { startActivity(Intent(this, BusinessOverviewActivity::class.java)) }
        findViewById<Button>(R.id.btnFinance).setOnClickListener { startActivity(Intent(this, FinanceProfitActivity::class.java)) }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, AdminSettingsActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        val unlocked = getSharedPreferences("admin_security", MODE_PRIVATE)
            .getBoolean("admin_unlocked", false)
        if (!unlocked) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        verifyAdminSession()
    }

    private fun verifyAdminSession() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            forceReauthentication("Admin Gmail session পাওয়া যায়নি।")
            return
        }
        firestore.collection("admin_registry").document("primary").get(Source.SERVER)
            .addOnSuccessListener { doc ->
                val serverUid = doc.getString("uid").orEmpty()
                val serverRole = doc.getString("role").orEmpty()
                if (doc.exists() && serverUid == user.uid && serverRole == "admin") {
                    refreshPartnerSummary()
                    refreshEnterpriseConnection()
                    refreshEnterpriseDataAccess()
                } else {
                    forceReauthentication("এই Firebase account আর Server Admin হিসেবে অনুমোদিত নয়।")
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Server Admin verification ব্যর্থ হয়েছে। Dashboard খোলা থাকবে না।", Toast.LENGTH_LONG).show()
                forceReauthentication(null)
            }
    }

    private fun forceReauthentication(message: String?) {
        firebaseAuth.signOut()
        getSharedPreferences("admin_identity", MODE_PRIVATE).edit()
            .putBoolean("firebase_authenticated", false).remove("admin_email")
            .putBoolean("admin_connected", false).apply()
        getSharedPreferences("admin_security", MODE_PRIVATE).edit()
            .putBoolean("admin_unlocked", false).apply()
        if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun refreshEnterpriseConnection() {
        val businessId = AdminBusinessContext.getBusinessId(this)
        val businessName = AdminBusinessContext.getBusinessName(this)
        enterpriseConnectionStatus.text = "Tamanna Enterprise: যাচাই হচ্ছে..."
        if (businessId == AdminBusinessContext.DEFAULT_ID) {
            enterpriseConnectionStatus.text = "Tamanna Enterprise: Business ID সেট করা হয়নি"
            return
        }
        EnterpriseMembershipReader.load(this) { _, membership, message ->
            runOnUiThread {
                enterpriseConnectionStatus.text = "Business: $businessName\\nID: $businessId\\n$message"
            }
        }
    }

    private fun refreshEnterpriseDataAccess() {
        enterpriseDataStatus.text = "Enterprise Cloud Data: যাচাই হচ্ছে..."
        EnterpriseBusinessDataReader.loadOverview(this) { success, message ->
            runOnUiThread {
                enterpriseDataStatus.text = if (success) {
                    "Enterprise Cloud Data Access\n$message\nRead-only"
                } else {
                    "Enterprise Cloud Data Access\n$message"
                }
            }
        }
    }

    private fun refreshPartnerSummary() {
        firestore.collection("admin_data").document("partners").collection("items")
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                var pending = 0
                var approved = 0
                var suspended = 0
                snapshot.documents.forEach { doc ->
                    when (doc.getString("status").orEmpty()) {
                        "PENDING" -> pending++
                        "APPROVED" -> approved++
                        "SUSPENDED" -> suspended++
                    }
                }
                partnerCount.text = "Partners\n" + snapshot.size()
                pendingCount.text = "Pending\n" + pending
                approvedCount.text = "Approved\n" + approved
                suspendedCount.text = "Suspended\n" + suspended
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Server থেকে Partner summary আনা যায়নি: " + (error.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
                partnerCount.text = "Partners\n—"
                pendingCount.text = "Pending\n—"
                approvedCount.text = "Approved\n—"
                suspendedCount.text = "Suspended\n—"
            }
    }
}
