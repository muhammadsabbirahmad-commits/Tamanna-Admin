package com.tamanna.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class DashboardActivity : AppCompatActivity() {
    private lateinit var partnerCount: TextView
    private lateinit var pendingCount: TextView
    private lateinit var approvedCount: TextView
    private lateinit var suspendedCount: TextView
    private lateinit var enterpriseConnectionStatus: TextView
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var googleSignInInProgress = false

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        googleSignInInProgress = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken.orEmpty()
            if (idToken.isBlank()) {
                Toast.makeText(this, "Google ID token পাওয়া যায়নি। আবার Gmail দিয়ে চেষ্টা করুন।", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    val signedUser = authResult.user
                    if (signedUser == null) {
                        Toast.makeText(this, "Firebase Google Login ব্যর্থ হয়েছে।", Toast.LENGTH_LONG).show()
                    } else {
                        verifyAdminUser(signedUser)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Firebase Google Login ব্যর্থ হয়েছে: " + (e.message ?: "আবার চেষ্টা করুন।"), Toast.LENGTH_LONG).show()
                }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google Sign-In বাতিল/ব্যর্থ হয়েছে। Error code: " + e.statusCode, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Google account সংযোগ ব্যর্থ হয়েছে।", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminSecurityGuard.requireUnlocked(this)) return
        setContentView(R.layout.activity_dashboard)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        partnerCount = findViewById(R.id.tvPartnerCount)
        pendingCount = findViewById(R.id.tvPendingCount)
        approvedCount = findViewById(R.id.tvApprovedCount)
        suspendedCount = findViewById(R.id.tvSuspendedCount)
        enterpriseConnectionStatus = findViewById(R.id.tvEnterpriseConnectionStatus)

        findViewById<Button>(R.id.btnUserApproval).setOnClickListener {
            startActivity(Intent(this, EnterpriseUserApprovalActivity::class.java))
        }
        findViewById<Button>(R.id.btnPartners).setOnClickListener {
            startActivity(Intent(this, PartnerManagementActivity::class.java))
        }
        findViewById<Button>(R.id.btnProducts).setOnClickListener {
            startActivity(Intent(this, EnterpriseProductsActivity::class.java))
        }
        findViewById<Button>(R.id.btnSalesPurchase).setOnClickListener {
            startActivity(Intent(this, EnterpriseSalesPurchaseActivity::class.java))
        }
        findViewById<Button>(R.id.btnReports).setOnClickListener {
            startActivity(Intent(this, EnterpriseDateRangeReportActivity::class.java))
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
        findViewById<Button>(R.id.btnAudit).setOnClickListener {
            startActivity(Intent(this, AdminAuditActivity::class.java))
        }

        verifyAdminSession()
    }

    override fun onResume() {
        super.onResume()
        if (!AdminSecurityGuard.isUnlocked(this)) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun verifyAdminSession() {
        // Always select the Admin Gmail after Master PIN; never trust a stale Firebase session.
        firebaseAuth.signOut()
        GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        ).signOut().addOnCompleteListener {
            openAdminGmailSetup()
        }
    }

    private fun verifyAdminUser(user: FirebaseUser) {
        enterpriseConnectionStatus.text =
            "Admin Gmail যাচাই করা হচ্ছে…\nUID: ${user.uid}"

        // Force-refresh the Firebase ID token before the Firestore request.
        // This prevents a stale/expired authentication token from causing
        // a false PERMISSION_DENIED immediately after Google sign-in.
        user.getIdToken(true)
            .addOnSuccessListener {
                val currentUser = firebaseAuth.currentUser

                if (currentUser == null || currentUser.uid != user.uid) {
                    val detail = "Firebase Auth session পাওয়া যায়নি।"
                    Toast.makeText(this, detail, Toast.LENGTH_LONG).show()
                    enterpriseConnectionStatus.text = detail
                    return@addOnSuccessListener
                }

                firestore.collection("admin_registry").document("primary").get(Source.SERVER)
                    .addOnSuccessListener { doc ->
                        val serverUid = doc.getString("uid").orEmpty()
                        val serverRole = doc.getString("role").orEmpty()

                        if (doc.exists() && serverUid == user.uid && serverRole == "admin") {
                            refreshPartnerSummary()
                            enterpriseConnectionStatus.text =
                                "Tamanna Enterprise: Authorization ready\nEnterprise User Approval খুলে Admin authorization সম্পন্ন করুন।"
                        } else {
                            val detail =
                                "Server Admin record মিলেনি।\nLogin UID: ${user.uid}\nServer UID: $serverUid\nRole: $serverRole"
                            Toast.makeText(this, detail, Toast.LENGTH_LONG).show()
                            enterpriseConnectionStatus.text = detail
                        }
                    }
                    .addOnFailureListener { e ->
                        val detail = e.message?.takeIf { it.isNotBlank() }
                            ?: "অজানা Firestore error"
                        val diagnostic =
                            "Firestore verification error: $detail\nFirebase UID: ${currentUser.uid}"
                        Toast.makeText(this, diagnostic, Toast.LENGTH_LONG).show()
                        enterpriseConnectionStatus.text = diagnostic
                    }
            }
            .addOnFailureListener { e ->
                val detail = e.message?.takeIf { it.isNotBlank() }
                    ?: "Firebase ID token refresh ব্যর্থ হয়েছে।"
                Toast.makeText(
                    this,
                    "Firebase authentication token যাচাই ব্যর্থ: $detail",
                    Toast.LENGTH_LONG
                ).show()
                enterpriseConnectionStatus.text = "Authentication error: $detail"
            }
    }

    private fun openAdminGmailSetup() {
        enterpriseConnectionStatus.text =
            "Tamanna Admin: Admin Gmail সংযুক্ত নেই\nGoogle Sign-In দিয়ে অনুমোদিত Admin Gmail নির্বাচন করুন।"
        launchAdminGoogleSignIn()
    }

    private fun launchAdminGoogleSignIn() {
        if (googleSignInInProgress) return
        googleSignInInProgress = true
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleLauncher.launch(GoogleSignIn.getClient(this, options).signInIntent)
    }

    private fun forceReauthentication(message: String?, launchGoogle: Boolean = false) {
        firebaseAuth.signOut()
        if (launchGoogle) {
            GoogleSignIn.getClient(this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()).signOut()
        }
        getSharedPreferences("admin_identity", MODE_PRIVATE).edit()
            .putBoolean("firebase_authenticated", false)
            .remove("admin_email")
            .putBoolean("admin_connected", false)
            .apply()
        getSharedPreferences("admin_security", MODE_PRIVATE).edit()
            .putBoolean("admin_unlocked", false)
            .apply()

        if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
        if (launchGoogle) {
            window.decorView.postDelayed({ launchAdminGoogleSignIn() }, 350)
        }
    }

    private fun refreshPartnerSummary() {
        firestore.collection("admin_data").document("partners").collection("items")
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

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
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener

                partnerCount.text = "Partners\n—"
                pendingCount.text = "Pending\n—"
                approvedCount.text = "Approved\n—"
                suspendedCount.text = "Suspended\n—"
            }
    }

    override fun onDestroy() {
        authStateListener?.let { firebaseAuth.removeAuthStateListener(it) }
        authStateListener = null
        super.onDestroy()
    }
}
