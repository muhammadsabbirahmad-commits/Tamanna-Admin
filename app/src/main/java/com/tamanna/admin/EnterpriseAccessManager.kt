package com.tamanna.admin

import android.content.Context
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

    fun withEnterpriseAdmin(context: Context, onReady: (FirebaseFirestore) -> Unit, onError: (String) -> Unit) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            val token = account?.idToken.orEmpty()
            val email = account?.email.orEmpty().trim()
            if (token.isBlank() || email.isBlank()) {
                onError("Admin Gmail session পাওয়া যায়নি। Admin Settings থেকে Gmail সংযুক্ত করুন।")
                return
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
        } catch (e: Exception) { onError(e.localizedMessage ?: "Enterprise Admin connection ব্যর্থ হয়েছে।") }
    }

    fun loadRequests(context: Context, onResult: (List<EnterpriseAccessRequest>) -> Unit, onError: (String) -> Unit) {
        withEnterpriseAdmin(context, { db ->
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

    fun updateRequest(context: Context, request: EnterpriseAccessRequest, status: String,
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
