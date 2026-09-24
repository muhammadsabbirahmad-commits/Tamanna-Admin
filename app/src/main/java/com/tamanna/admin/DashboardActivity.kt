package com.tamanna.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class DashboardActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var status: TextView
    private var busy = false

    // Dashboard Stats Views
    private lateinit var statsContainer: View
    private lateinit var tvActiveCount: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvBlockedCount: TextView

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        busy = false
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val token = account.idToken.orEmpty()
            if (token.isBlank()) {
                show("Google token পাওয়া যায়নি।")
                return@registerForActivityResult
            }
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                .addOnSuccessListener {
                    val user = auth.currentUser
                    if (user == null) {
                        show("Admin login সম্পন্ন হয়নি।")
                    } else {
                        user.getIdToken(true)
                            .addOnSuccessListener {
                                verify(user.uid, user.email.orEmpty())
                            }
                            .addOnFailureListener { e ->
                                show("Firebase token refresh ব্যর্থ: " + (e.message ?: "Unknown error"))
                            }
                    }
                }
                .addOnFailureListener { show("Gmail login ব্যর্থ।") }
        } catch (_: Exception) {
            show("Google Sign-In বাতিল বা ব্যর্থ হয়েছে।")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminSecurityGuard.requireUnlocked(this)) return
        setContentView(R.layout.activity_dashboard)
        
        db = EnterpriseFirebaseConnection.getFirestore(this)
        auth = FirebaseAuth.getInstance(db.app)
        
        status = findViewById(R.id.tvAdminStatus)
        statsContainer = findViewById(R.id.statsContainer)
        tvActiveCount = findViewById(R.id.tvActiveCount)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvBlockedCount = findViewById(R.id.tvBlockedCount)

        findViewById<Button>(R.id.btnConnectAdmin).setOnClickListener { connect() }
        findViewById<Button>(R.id.btnUserApproval).setOnClickListener {
            startActivity(Intent(this, EnterpriseUserApprovalActivity::class.java))
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (!AdminSecurityGuard.isUnlocked(this)) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } else refresh()
    }

    private fun refresh() {
        val p = getSharedPreferences("admin_identity", MODE_PRIVATE)
        val email = p.getString("admin_email", "").orEmpty()
        val isConnected = p.getBoolean("admin_connected", false) && email.isNotBlank()
        
        if (isConnected) {
            status.text = "Admin Gmail: $email\nStatus: CONNECTED"
            statsContainer.visibility = View.VISIBLE
            loadDashboardStats()
        } else {
            status.text = "Admin Gmail: Not connected\nStatus: অপেক্ষমাণ"
            statsContainer.visibility = View.GONE
        }
    }

    private fun connect() {
        if (busy) return
        busy = true
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("241311598063-d6pqnutv6598pj4lmsikqs7bsfs0m0bs.apps.googleusercontent.com")
            .requestEmail()
            .build()
            
        GoogleSignIn.getClient(this, options).signOut().addOnCompleteListener {
            launcher.launch(GoogleSignIn.getClient(this, options).signInIntent)
        }
    }

    private fun verify(uid: String, email: String) {
        status.text = "Server Admin যাচাই হচ্ছে…"
        
        db.collection("appConfig").document("admin").get(Source.SERVER)
            .addOnSuccessListener { doc ->
                if (doc.exists() &&
                    doc.getString("uid") == uid &&
                    doc.getString("role")?.uppercase() == "ADMIN") {
                    
                    getSharedPreferences("admin_identity", MODE_PRIVATE).edit()
                        .putBoolean("firebase_authenticated", true)
                        .putString("admin_email", email)
                        .putBoolean("admin_connected", true)
                        .apply()
                        
                    status.text = "Admin Gmail: $email\nStatus: CONNECTED"
                    statsContainer.visibility = View.VISIBLE
                    Toast.makeText(this, "Admin Gmail Connected হয়েছে।", Toast.LENGTH_SHORT).show()
                    loadDashboardStats()
                    
                } else {
                    auth.signOut()
                    getSharedPreferences("admin_identity", MODE_PRIVATE).edit()
                        .putBoolean("firebase_authenticated", false)
                        .remove("admin_email")
                        .putBoolean("admin_connected", false)
                        .apply()
                    show("এই Gmail-এর Enterprise Admin অনুমোদন নেই।")
                    statsContainer.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                show("Firestore verification error: " + (e.message ?: "Permission denied"))
            }
    }

    private fun loadDashboardStats() {
        // ফায়ারবেস থেকে রিয়েল-টাইম ডাটা কাউন্ট করা হচ্ছে
        db.collection("accessRequests").get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                var activeCount = 0
                var pendingCount = 0
                var blockedCount = 0
                val now = System.currentTimeMillis()

                for (document in snapshot.documents) {
                    val reqStatus = document.getString("status") ?: "PENDING"
                    val expiresAt = document.getLong("expiresAt") ?: 0L

                    // EnterpriseAccessManager এর লজিক অনুযায়ী স্ট্যাটাস ফিল্টার
                    if (reqStatus == "ACTIVE" && expiresAt > now) {
                        activeCount++
                    } else if (reqStatus == "PENDING" || reqStatus == "") {
                        pendingCount++
                    } else if (reqStatus == "BLOCKED") {
                        blockedCount++
                    }
                }

                // UI তে ডেটা বসানো
                tvActiveCount.text = activeCount.toString()
                tvPendingCount.text = pendingCount.toString()
                tvBlockedCount.text = blockedCount.toString()
            }
            .addOnFailureListener {
                // এরর হলে ০ দেখাবে
                tvActiveCount.text = "0"
                tvPendingCount.text = "0"
                tvBlockedCount.text = "0"
            }
    }

    private fun show(message: String) {
        status.text = message
    }
}
