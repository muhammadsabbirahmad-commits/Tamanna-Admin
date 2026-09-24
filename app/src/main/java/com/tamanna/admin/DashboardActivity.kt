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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class DashboardActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var status: TextView
    private var busy = false

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
        
        // এখানে Enterprise কানেকশন ব্যবহার করা হয়েছে
        db = EnterpriseFirebaseConnection.getFirestore(this)
        auth = FirebaseAuth.getInstance(db.app)
        
        status = findViewById(R.id.tvAdminStatus)

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
        status.text = if (p.getBoolean("admin_connected", false) && email.isNotBlank())
            "Admin Gmail: $email\nStatus: CONNECTED"
        else "Admin Gmail: Not connected\nStatus: অপেক্ষমাণ"
    }

    private fun connect() {
        if (busy) return
        busy = true
        // এখানে Enterprise Client ID ব্যবহার করা হয়েছে
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
        db.collection("admin_registry").document("primary").get(Source.SERVER)
            .addOnSuccessListener { doc ->
                if (doc.exists() &&
                    doc.getString("uid") == uid &&
                    doc.getString("role") == "admin") {
                    getSharedPreferences("admin_identity", MODE_PRIVATE).edit()
                        .putBoolean("firebase_authenticated", true)
                        .putString("admin_email", email)
                        .putBoolean("admin_connected", true)
                        .apply()
                    status.text = "Admin Gmail: $email\nStatus: CONNECTED"
                    Toast.makeText(this, "Admin Gmail Connected হয়েছে।", Toast.LENGTH_LONG).show()
                } else {
                    auth.signOut()
                    getSharedPreferences("admin_identity", MODE_PRIVATE).edit()
                        .putBoolean("firebase_authenticated", false)
                        .remove("admin_email")
                        .putBoolean("admin_connected", false)
                        .apply()
                    show("এই Gmail Admin নয়। Server Admin Gmail পরিবর্তন করা যাবে না।")
                }
            }
            .addOnFailureListener { e ->
                show("Firestore verification error: " + (e.message ?: "Permission denied"))
            }
    }

    private fun show(message: String) {
        status.text = message
    }
}
