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

class AdminSettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "admin_identity"
        private const val ADMIN_EMAIL = "admin_email"
        private const val ADMIN_CONNECTED = "admin_connected"
    }

    private lateinit var tvEmail: TextView
    private lateinit var tvStatus: TextView

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)
            val email = account.email?.trim().orEmpty()

            if (email.isBlank()) {
                Toast.makeText(this, "Google account-এর email পাওয়া যায়নি।", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val existing = prefs.getString(ADMIN_EMAIL, null)

            if (existing.isNullOrBlank()) {
                prefs.edit()
                    .putString(ADMIN_EMAIL, email)
                    .putBoolean(ADMIN_CONNECTED, true)
                    .apply()
                refreshAdminIdentity()
                Toast.makeText(this, "Admin Gmail সফলভাবে স্থায়ীভাবে সংযুক্ত হয়েছে।", Toast.LENGTH_LONG).show()
            } else if (existing.equals(email, ignoreCase = true)) {
                prefs.edit().putBoolean(ADMIN_CONNECTED, true).apply()
                refreshAdminIdentity()
                Toast.makeText(this, "এই Gmail-ই বর্তমান Admin Gmail।", Toast.LENGTH_SHORT).show()
            } else {
                refreshAdminIdentity()
                Toast.makeText(
                    this,
                    "অন্য Gmail অনুমোদিত নয়। বর্তমান Admin Gmail পরিবর্তন করা যাবে না।",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Google account সংযোগ ব্যর্থ হয়েছে। আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)

        tvEmail = findViewById(R.id.tvAdminEmail)
        tvStatus = findViewById(R.id.tvAdminStatus)

        findViewById<Button>(R.id.btnConnectAdminGmail).setOnClickListener {
            connectAdminGmail()
        }

        findViewById<Button>(R.id.btnRefreshSettings).setOnClickListener {
            refreshAdminIdentity()
        }

        findViewById<Button>(R.id.btnAdminLogout).setOnClickListener {
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
