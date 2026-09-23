package com.tamanna.admin

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

data class EnterpriseAccessRequest(
    val uid: String, val email: String, val status: String,
    val startAt: Long, val expiresAt: Long, val notificationsEnabled: Boolean
)

object EnterpriseAccessManager {
    private const val COLLECTION = "accessRequests"
    const val PENDING = "PENDING"
    const val ACTIVE = "ACTIVE"
    const val BLOCKED = "BLOCKED"
    const val REJECTED = "REJECTED"
    const val EXPIRED = "EXPIRED"

    fun buildEnterpriseGoogleIntent(context: Context): Intent {\n        val options = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(\n            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN\n        ).requestIdToken("241311598063-d6pqnutv6598pj4lmsikqs7bsfs0m0bs.apps.googleusercontent.com").requestEmail().build()\n        return GoogleSignIn.getClient(context, options).signInIntent\n    }\n\n    fun finishEnterpriseGoogleSignIn(context: Context, data: Intent?, onReady: (FirebaseFirestore) -> Unit, onError: (String) -> Unit) {\n        try {\n            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(com.google.android.gms.common.api.ApiException::class.java)\n            val token = account.idToken.orEmpty()\n            val email = account.email.orEmpty().trim()\n            if (token.isBlank() || email.isBlank()) { onError("Admin Gmail-এর Enterprise authorization token পাওয়া যায়নি।"); return }\n            authenticateEnterprise(context, token, email, onReady, onError)\n        } catch (e: Exception) { onError("Enterprise Google Sign-In ব্যর্থ হয়েছে।") }\n    }\n\n    private fun authenticateEnterprise(context: Context, token: String, email: String, onReady: (FirebaseFirestore) -> Unit, onError: (String) -> Unit) {
        try {
            // Enterprise Firebase requires an ID token whose audience belongs to
            // the Enterprise project's Web OAuth client, not the Admin project's client.
            val enterpriseWebClientId =
                "241311598063-d6pqnutv6598pj4lmsikqs7bsfs0m0bs.apps.googleusercontent.com"

            val options = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken(enterpriseWebClientId)
                .requestEmail()
                .build()

            val googleClient = GoogleSignIn.getClient(context, options)
            googleClient.silentSignIn()
                .addOnSuccessListener { account ->
                    val token = account.idToken.orEmpty()
                    val email = account.email.orEmpty().trim()
                    if (token.isBlank() || email.isBlank()) {
                        onError("Admin Gmail-এর Enterprise authorization token পাওয়া যায়নি।")
                        return@addOnSuccessListener
                    }

                    val app = EnterpriseFirebaseConnection.getFirestore(context).app
                    val auth = FirebaseAuth.getInstance(app)
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                .addOnSuccessListener {
                    val user = auth.currentUser
                    if (user == null || !user.email.orEmpty().equals(email, true)) {
                        onError("Enterprise Admin Gmail verification ব্যর্থ হয়েছে."); return@addOnSuccessListener
                    }
                    val db = FirebaseFirestore.getInstance(app)
                    db.collection("appConfig").document("admin").get(Source.SERVER)
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                val valid = doc.getString("uid").orEmpty() == user.uid &&
                                    doc.getString("email").orEmpty().equals(email, true) &&
                                    doc.getString("role").orEmpty().uppercase() == "ADMIN"
                                if (valid) onReady(db)
                                else onError("Enterprise Server-এ এই Admin Gmail-এর অনুমোদন পাওয়া যায়নি।")
                            } else {
                                db.collection("accessUsers").document(user.uid).get(Source.SERVER)
                                    .addOnSuccessListener { legacy ->
                                        val valid = legacy.exists() &&
                                            legacy.getString("uid").orEmpty() == user.uid &&
                                            legacy.getString("email").orEmpty().equals(email, true) &&
                                            legacy.getString("role").orEmpty().uppercase() == "ADMIN" &&
                                            legacy.getBoolean("approved") == true &&
                                            legacy.getBoolean("blocked") != true
                                        if (valid) onReady(db)
                                        else onError("Enterprise Server-এ এই Admin Gmail-এর অনুমোদন পাওয়া যায়নি।")
                                    }
                                    .addOnFailureListener { onError("Enterprise Admin verification ব্যর্থ হয়েছে।") }
                            }
                        }
                        .addOnFailureListener { onError("Enterprise Admin verification ব্যর্থ হয়েছে।") }
                }
                    .addOnFailureListener { onError("Enterprise Firebase Admin login ব্যর্থ: ${it.localizedMessage ?: "আবার চেষ্টা করুন।"}") }
                }
                .addOnFailureListener { onError("Admin Gmail-এর Enterprise Google token পাওয়া যায়নি। Admin Gmail একবার পুনরায় সংযুক্ত করুন।") }        } catch (e: Exception) { onError(e.localizedMessage ?: "Enterprise Admin connection ব্যর্থ হয়েছে।") }
    }

    fun withEnterpriseAdmin(context: Context, launcher: ActivityResultLauncher<Intent>, onReady: (FirebaseFirestore) -> Unit, onError: (String) -> Unit) {\n        try {\n            val options = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)\n                .requestIdToken("241311598063-d6pqnutv6598pj4lmsikqs7bsfs0m0bs.apps.googleusercontent.com").requestEmail().build()\n            GoogleSignIn.getClient(context, options).silentSignIn()\n                .addOnSuccessListener { account ->\n                    val token = account.idToken.orEmpty(); val email = account.email.orEmpty().trim()\n                    if (token.isBlank() || email.isBlank()) launcher.launch(buildEnterpriseGoogleIntent(context))\n                    else authenticateEnterprise(context, token, email, onReady, onError)\n                }\n                .addOnFailureListener { launcher.launch(buildEnterpriseGoogleIntent(context)) }\n        } catch (e: Exception) { onError(e.localizedMessage ?: "Enterprise Admin connection ব্যর্থ হয়েছে।") }\n    }\n\n    fun loadRequests(context: Context, launcher: ActivityResultLauncher<Intent>, onResult: (List<EnterpriseAccessRequest>) -> Unit, onError: (String) -> Unit) {
        withEnterpriseAdmin(context, launcher, { db ->
            db.collection(COLLECTION).get(Source.SERVER)
                .addOnSuccessListener { snapshot ->
                    val now = System.currentTimeMillis()
                    val list = snapshot.documents.mapNotNull { doc ->
                        val uid = doc.getString("uid").orEmpty()
                        val email = doc.getString("email").orEmpty()
                        if (uid.isBlank() || email.isBlank()) return@mapNotNull null
                        val expires = doc.getLong("expiresAt") ?: 0L
                        var status = doc.getString("status").orEmpty().ifBlank { PENDING }
                        if (status == ACTIVE && expires > 0L && expires <= now) status = EXPIRED
                        EnterpriseAccessRequest(uid, email, status, doc.getLong("startAt") ?: 0L, expires,
                            doc.getBoolean("notificationsEnabled") == true)
                    }.sortedWith(compareBy({ it.status != PENDING }, { it.email.lowercase() }))
                    onResult(list)
                }
                .addOnFailureListener { onError("Access request list আনা যায়নি: ${it.localizedMessage ?: "আবার চেষ্টা করুন।"}") }
        }, onError)
    }

    fun updateRequest(context: Context, launcher: ActivityResultLauncher<Intent>, request: EnterpriseAccessRequest, status: String,
                      startAt: Long, expiresAt: Long, notificationsEnabled: Boolean,
                      onSuccess: () -> Unit, onError: (String) -> Unit) {
        withEnterpriseAdmin(context, { db ->
            val data = hashMapOf<String, Any>(
                "uid" to request.uid, "email" to request.email, "status" to status,
                "startAt" to startAt, "expiresAt" to expiresAt,
                "notificationsEnabled" to notificationsEnabled, "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION).document(request.uid).set(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError("Access update ব্যর্থ: ${it.localizedMessage ?: "আবার চেষ্টা করুন।"}") }
        }, onError)
    }
}
