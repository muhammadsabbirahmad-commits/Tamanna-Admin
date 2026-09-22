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

class AdminSettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "admin_identity"
        private const val ADMIN_EMAIL = "admin_email"
        private const val ADMIN_CONNECTED = "admin_connected"
    }

    private lateinit var tvEmail: TextView
    private lateinit var tvStatus: TextView
    private lateinit var firebaseAuth: FirebaseAuth

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account.email?.trim().orEmpty()
            val idToken = account.idToken.orEmpty()

            if (email.isBlank() || idToken.isBlank()) {
                Toast.makeText(this, "Google account-এর email বা ID token পাওয়া যায়নি।", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    val verifiedEmail = authResult.user?.email?.trim().orEmpty()
                    if (verifiedEmail.isBlank()) {
                        Toast.makeText(this, "Firebase-এ Google account যাচাই করা যায়নি।", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                    val existing = prefs.getString(ADMIN_EMAIL, null)

                    if (existing.isNullOrBlank()) {
                        prefs.edit()
                            .putString(ADMIN_EMAIL, verifiedEmail)
                            .putBoolean(ADMIN_CONNECTED, true)
                            .apply()
                        refreshAdminIdentity()
                        Toast.makeText(this, "Admin Gmail সফলভাবে Firebase-এর মাধ্যমে সংযুক্ত হয়েছে।", Toast.LENGTH_LONG).show()
                    } else if (existing.equals(verifiedEmail, ignoreCase = true)) {
                        prefs.edit().putBoolean(ADMIN_CONNECTED, true).apply()
                        refreshAdminIdentity()
                        Toast.makeText(this, "এই Gmail-ই বর্তমান Admin Gmail।", Toast.LENGTH_SHORT).show()
                    } else {
                        firebaseAuth.signOut()
                        refreshAdminIdentity()
                        Toast.makeText(
                            this,
                            "অন্য Gmail অনুমোদিত নয়। বর্তমান Admin Gmail পরিবর্তন করা যাবে না।",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Firebase Google Login ব্যর্থ হয়েছে: ${e.message ?: "আবার চেষ্টা করুন।"}",
                        Toast.LENGTH_LONG
                    ).show()
                }

        } catch (e: ApiException) {
            Toast.makeText(
                this,
                "Google Sign-In ব্যর্থ হয়েছে। Error code: ${e.statusCode}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Google account সংযোগ ব্যর্থ হয়েছে। ${e.message ?: "আবার চেষ্টা করুন।"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)

        tvEmail = findViewById(R.id.tvAdminEmail)
        tvStatus = findViewById(R.id.tvAdminStatus)
        firebaseAuth = FirebaseAuth.getInstance()

        findViewById<Button>(R.id.btnConnectAdminGmail).setOnClickListener {
            connectAdminGmail()
        }

        findViewById<Button>(R.id.btnRefreshSettings).setOnClickListener {
            refreshAdminIdentity()
        }

        findViewById<Button>(R.id.btnAdminLogout).setOnClickListener {
            firebaseAuth.signOut()
            getSharedPreferences("admin_security", MODE_PRIVATE)
                .edit()
                .putBoolean("admin_unlocked", false)
                .apply()

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }

        refreshAdminIdentity()
    }

    private fun connectAdminGmail() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val existing = prefs.getString(ADMIN_EMAIL, null)

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(this, options)

        if (!existing.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Admin Gmail ইতিমধ্যে সংযুক্ত। অন্য Gmail দিয়ে পরিবর্তন করা যাবে না।",
                Toast.LENGTH_LONG
            ).show()
        }

        googleLauncher.launch(client.signInIntent)
    }

    private fun refreshAdminIdentity() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val email = prefs.getString(ADMIN_EMAIL, null)

        if (email.isNullOrBlank()) {
            tvEmail.text = "Admin Gmail: Not connected"
            tvStatus.text = "Admin Status: NOT CONNECTED"
        } else {
            tvEmail.text = "Admin Gmail: $email"
            tvStatus.text = "Admin Status: ACTIVE"
        }
    }
}
