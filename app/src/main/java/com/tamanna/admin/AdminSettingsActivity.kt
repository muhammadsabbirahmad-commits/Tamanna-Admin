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

class AdminSettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "admin_identity"
        private const val ADMIN_EMAIL = "admin_email"
        private const val ADMIN_CONNECTED = "admin_connected"
        private const val FIREBASE_AUTHENTICATED = "firebase_authenticated"
    }

    private lateinit var tvEmail: TextView
    private lateinit var tvStatus: TextView
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

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
                    val uid = authResult.user?.uid.orEmpty()

                    if (verifiedEmail.isBlank() || uid.isBlank()) {
                        firebaseAuth.signOut()
                        Toast.makeText(this, "Firebase account verification ব্যর্থ হয়েছে।", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    authorizeAdminOnServer(uid, verifiedEmail)
                        .addOnSuccessListener { isAdmin ->
                            if (isAdmin) {
                                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putBoolean(FIREBASE_AUTHENTICATED, true)
                                    .putString(ADMIN_EMAIL, verifiedEmail)
                                    .putBoolean(ADMIN_CONNECTED, true)
                                    .apply()

                                refreshAdminIdentity()

                                Toast.makeText(
                                    this,
                                    "Admin Gmail ও Firestore Server Authorization সফল হয়েছে।",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                firebaseAuth.signOut()
                                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putBoolean(FIREBASE_AUTHENTICATED, false)
                                    .remove(ADMIN_EMAIL)
                                    .putBoolean(ADMIN_CONNECTED, false)
                                    .apply()

                                tvEmail.text = "Admin Gmail: Not connected"
                                tvStatus.text = "Admin Status: NOT CONNECTED"

                                Toast.makeText(
                                    this,
                                    "এই Gmail Admin নয়। Server-এর স্থায়ী Admin Gmail পরিবর্তন করা যাবে না।",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            firebaseAuth.signOut()
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putBoolean(FIREBASE_AUTHENTICATED, false)
                                .apply()

                            Toast.makeText(
                                this,
                                "Firestore Server Authorization ব্যর্থ: ${e.message ?: "Rules/Internet পরীক্ষা করুন।"}",
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

    override fun onResume() {
        super.onResume()
        AdminSecurityGuard.requireUnlocked(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)

        tvEmail = findViewById(R.id.tvAdminEmail)
        tvStatus = findViewById(R.id.tvAdminStatus)
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        findViewById<Button>(R.id.btnConnectAdminGmail).setOnClickListener {
            connectAdminGmail()
        }

        findViewById<Button>(R.id.btnRefreshSettings).setOnClickListener {
            refreshAdminIdentity()
        }

        findViewById<Button>(R.id.btnAdminLogout).setOnClickListener {
            // Logout means locking the local Admin session.
            // Keep the permanent Server Admin Gmail/Firebase session intact so
            // the approved Admin account does not need to be selected again.
            getSharedPreferences("admin_security", MODE_PRIVATE)
                .edit()
                .putBoolean("admin_unlocked", false)
                .apply()

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        refreshAdminIdentity()
    }

    private fun connectAdminGmail() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val existing = prefs.getString(ADMIN_EMAIL, null)

        if (!existing.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Admin Gmail ইতিমধ্যে সংযুক্ত। Server থেকে স্থায়ী Admin যাচাই করা হচ্ছে।",
                Toast.LENGTH_SHORT
            ).show()
            refreshAdminIdentity()
            return
        }

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(this, options)
        googleLauncher.launch(client.signInIntent)
    }

    private fun refreshAdminIdentity() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val user = firebaseAuth.currentUser

        if (user == null) {
            tvEmail.text = "Admin Gmail: Not connected"
            tvStatus.text = "Admin Status: NOT CONNECTED"
            return
        }

        tvStatus.text = "Admin Status: CHECKING SERVER..."

        firestore.collection("admin_registry").document("primary")
            .get(Source.SERVER)
            .addOnSuccessListener { doc ->
                val serverUid = doc.getString("uid").orEmpty()
                val serverEmail = doc.getString("email").orEmpty()
                val serverRole = doc.getString("role").orEmpty()

                if (doc.exists() &&
                    serverUid == user.uid &&
                    serverEmail.isNotBlank() &&
                    serverRole == "admin"
                ) {
                    prefs.edit()
                        .putBoolean(FIREBASE_AUTHENTICATED, true)
                        .putString(ADMIN_EMAIL, serverEmail)
                        .putBoolean(ADMIN_CONNECTED, true)
                        .apply()

                    tvEmail.text = "Admin Gmail: $serverEmail"
                    tvStatus.text = "Admin Status: ACTIVE"
                } else {
                    firebaseAuth.signOut()

                    prefs.edit()
                        .putBoolean(FIREBASE_AUTHENTICATED, false)
                        .remove(ADMIN_EMAIL)
                        .putBoolean(ADMIN_CONNECTED, false)
                        .apply()

                    tvEmail.text = "Admin Gmail: Not connected"
                    tvStatus.text = "Admin Status: NOT CONNECTED"
                }
            }
            .addOnFailureListener { e ->
                tvEmail.text = "Admin Gmail: Not connected"
                tvStatus.text = "Admin Status: SERVER CHECK FAILED"

                Toast.makeText(
                    this,
                    "Server Admin যাচাই করা যায়নি: ${e.message ?: "Internet/Firestore Rules পরীক্ষা করুন।"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun authorizeAdminOnServer(
        uid: String,
        email: String
    ): com.google.android.gms.tasks.Task<Boolean> {
        val ref = firestore.collection("admin_registry").document("primary")

        return ref.get(Source.SERVER).continueWithTask { getTask ->
            if (getTask.isSuccessful) {
                val doc = getTask.result

                if (doc.exists()) {
                    val serverUid = doc.getString("uid").orEmpty()
                    val serverRole = doc.getString("role").orEmpty()

                    return@continueWithTask com.google.android.gms.tasks.Tasks.forResult(
                        serverUid == uid && serverRole == "admin"
                    )
                }
            }

            // No visible primary Admin exists: first authenticated account attempts to claim it.
            val adminData = hashMapOf(
                "uid" to uid,
                "email" to email,
                "role" to "admin"
            )

            ref.set(adminData).continueWithTask { setTask ->
                com.google.android.gms.tasks.Tasks.forResult(setTask.isSuccessful)
            }
        }
    }
}
