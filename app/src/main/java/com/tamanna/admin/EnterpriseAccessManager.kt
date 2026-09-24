package com.tamanna.admin

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

data class EnterpriseAccessRequest(val uid:String,val email:String,val status:String,val startAt:Long,val expiresAt:Long,val notificationsEnabled:Boolean)

object EnterpriseAccessManager {
    private const val COLLECTION = "accessRequests"
    private const val CLIENT = "241311598063-d6pqnutv6598pj4lmsikqs7bsfs0m0bs.apps.googleusercontent.com"

    @Volatile
    private var interactiveSignInInProgress = false

    const val PENDING = "PENDING"
    const val ACTIVE = "ACTIVE"
    const val BLOCKED = "BLOCKED"
    const val REJECTED = "REJECTED"
    const val EXPIRED = "EXPIRED"

    fun buildEnterpriseGoogleIntent(context: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(CLIENT)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    fun finishEnterpriseGoogleSignIn(
        context: Context,
        data: Intent?,
        onReady: (FirebaseFirestore) -> Unit,
        onError: (String) -> Unit
    ) {
        interactiveSignInInProgress = false
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val token = account.idToken.orEmpty()
            val email = account.email.orEmpty().trim()

            if (token.isBlank() || email.isBlank()) {
                onError("Admin Gmail-এর Enterprise authorization token পাওয়া যায়নি।")
                return
            }
            auth(context, token, email, onReady, onError)
        } catch (e: ApiException) {
            onError("Enterprise Google Sign-In ব্যর্থ হয়েছে.\nGoogle status code: ${e.statusCode}")
        } catch (e: Exception) {
            onError("Enterprise Google Sign-In ব্যর্থ হয়েছে.\n${e.localizedMessage.orEmpty()}")
        }
    }

    fun withEnterpriseAdmin(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        onReady: (FirebaseFirestore) -> Unit,
        onError: (String) -> Unit
    ) {
        if (interactiveSignInInProgress) return

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(CLIENT)
            .requestEmail()
            .build()

        GoogleSignIn.getClient(context, options)
            .silentSignIn()
            .addOnSuccessListener { account ->
                val token = account.idToken.orEmpty()
                val email = account.email.orEmpty().trim()
                if (token.isBlank() || email.isBlank()) {
                    if (!interactiveSignInInProgress) {
                        interactiveSignInInProgress = true
                        launcher.launch(buildEnterpriseGoogleIntent(context))
                    }
                } else {
                    auth(context, token, email, onReady, onError)
                }
            }
            .addOnFailureListener {
                if (!interactiveSignInInProgress) {
                    interactiveSignInInProgress = true
                    launcher.launch(buildEnterpriseGoogleIntent(context))
                }
            }
    }

    private fun auth(
        context: Context,
        token: String,
        email: String,
        onReady: (FirebaseFirestore) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val auth = FirebaseAuth.getInstance(app)

            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                .addOnSuccessListener {
                    val user = auth.currentUser
                    if (user == null || !user.email.orEmpty().equals(email, true)) {
                        onError("Enterprise Admin Gmail verification ব্যর্থ হয়েছে।")
                        return@addOnSuccessListener
                    }

                    val db = FirebaseFirestore.getInstance(app)
                    db.collection("appConfig").document("admin")
                        .get(Source.SERVER)
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                val ok = document.getString("uid").orEmpty() == user.uid &&
                                    document.getString("email").orEmpty().equals(email, true) &&
                                    document.getString("role").orEmpty().uppercase() == "ADMIN"

                                if (ok) {
                                    onReady(db)
                                } else {
                                    onError("Enterprise Server-এ এই Admin Gmail-এর অনুমোদন পাওয়া যায়নি।")
                                }
                            } else {
                                db.collection("accessUsers").document(user.uid)
                                    .get(Source.SERVER)
                                    .addOnSuccessListener { legacy ->
                                        val ok = legacy.exists() &&
                                            legacy.getString("uid").orEmpty() == user.uid &&
                                            legacy.getString("email").orEmpty().equals(email, true) &&
                                            legacy.getString("role").orEmpty().uppercase() == "ADMIN" &&
                                            legacy.getBoolean("approved") == true &&
                                            legacy.getBoolean("blocked") != true

                                        if (ok) {
                                            onReady(db)
                                        } else {
                                            onError("Enterprise Server-এ এই Admin Gmail-এর অনুমোদন পাওয়া যায়নি।")
                                        }
                                    }
                                    .addOnFailureListener {
                                        onError("Enterprise Admin verification ব্যর্থ হয়েছে।")
                                    }
                            }
                        }
                        .addOnFailureListener {
                            onError("Enterprise Admin verification ব্যর্থ হয়েছে।")
                        }
                }
                .addOnFailureListener {
                    onError("Enterprise Firebase Admin login ব্যর্থ হয়েছে।")
                }
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Enterprise Admin connection ব্যর্থ হয়েছে।")
        }
    }

    fun loadRequests(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        onResult: (List<EnterpriseAccessRequest>) -> Unit,
        onError: (String) -> Unit
    ) {
        withEnterpriseAdmin(context, launcher, { db ->
            db.collection(COLLECTION)
                .get(Source.SERVER)
                .addOnSuccessListener { snapshot ->
                    val now = System.currentTimeMillis()
                    onResult(
                        snapshot.documents
                            .mapNotNull { document ->
                                val uid = document.getString("uid").orEmpty()
                                val email = document.getString("email").orEmpty()
                                if (uid.isBlank() || email.isBlank()) {
                                    null
                                } else {
                                    val expiresAt = document.getLong("expiresAt") ?: 0L
                                    var status = document.getString("status").orEmpty().ifBlank { PENDING }
                                    if (status == ACTIVE && expiresAt > 0 && expiresAt <= now) {
                                        status = EXPIRED
                                    }
                                    EnterpriseAccessRequest(
                                        uid = uid,
                                        email = email,
                                        status = status,
                                        startAt = document.getLong("startAt") ?: 0L,
                                        expiresAt = expiresAt,
                                        notificationsEnabled = document.getBoolean("notificationsEnabled") == true
                                    )
                                }
                            }
                            .sortedWith(compareBy({ it.status != PENDING }, { it.email.lowercase() }))
                    )
                }
                .addOnFailureListener {
                    onError("Access request list আনা যায়নি।")
                }
        }, onError)
    }

    fun updateRequest(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        request: EnterpriseAccessRequest,
        status: String,
        startAt: Long,
        expiresAt: Long,
        notificationsEnabled: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        withEnterpriseAdmin(context, launcher, { db ->
            val data = hashMapOf<String, Any>(
                "uid" to request.uid,
                "email" to request.email,
                "status" to status,
                "startAt" to startAt,
                "expiresAt" to expiresAt,
                "notificationsEnabled" to notificationsEnabled,
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection(COLLECTION)
                .document(request.uid)
                .set(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError("Access update ব্যর্থ হয়েছে।") }
        }, onError)
    }
}
