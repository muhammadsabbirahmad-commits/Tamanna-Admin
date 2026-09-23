package com.tamanna.admin

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

object EnterpriseFirebaseAuth {

    fun authorizeAdmin(
        context: Context,
        idToken: String,
        email: String
    ): Task<Boolean> {
        return try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val auth = FirebaseAuth.getInstance(app)
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            auth.signInWithCredential(credential).continueWithTask { signInTask ->
                if (!signInTask.isSuccessful) {
                    return@continueWithTask Tasks.forResult(false)
                }

                val user = auth.currentUser
                    ?: return@continueWithTask Tasks.forResult(false)

                if (user.email?.trim()?.equals(email.trim(), ignoreCase = true) != true) {
                    auth.signOut()
                    return@continueWithTask Tasks.forResult(false)
                }

                verifyEnterpriseAdmin(context, auth, user.uid, email)
            }
        } catch (e: Exception) {
            Tasks.forResult(false)
        }
    }

    data class ResolvedBusiness(val businessId: String, val businessName: String)

    fun resolveBusinessFromLicense(context: Context, licenseCode: String, email: String): Task<ResolvedBusiness?> {
        return try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val auth = FirebaseAuth.getInstance(app)
            val user = auth.currentUser ?: return Tasks.forResult(null)
            if (user.email?.trim()?.equals(email.trim(), ignoreCase = true) != true) return Tasks.forResult(null)
            FirebaseFirestore.getInstance(app).collection("licenses").document(licenseCode.trim().uppercase())
                .get(Source.SERVER).continueWith { task ->
                    if (!task.isSuccessful) return@continueWith null
                    val doc = task.result
                    if (!doc.exists()) return@continueWith null
                    val id = doc.getString("businessId").orEmpty().trim()
                    if (id.isBlank() || id == AdminBusinessContext.DEFAULT_ID) null
                    else ResolvedBusiness(id, doc.getString("businessName").orEmpty().trim())
                }
        } catch (e: Exception) { Tasks.forResult(null) }
    }

    private fun verifyEnterpriseAdmin(
        context: Context,
        auth: FirebaseAuth,
        uid: String,
        email: String
    ): Task<Boolean> {
        val firestore = FirebaseFirestore.getInstance(auth.app)

        // New authorization record. This is independent of Business ID,
        // License Code, and the protected Tamanna Admin security system.
        return firestore.collection("appConfig").document("admin")
            .get(Source.SERVER)
            .continueWithTask { configTask ->
                if (!configTask.isSuccessful) {
                    return@continueWithTask Tasks.forResult(false)
                }

                val config = configTask.result
                if (config.exists()) {
                    val serverUid = config.getString("uid").orEmpty()
                    val serverEmail = config.getString("email").orEmpty()
                    val role = config.getString("role").orEmpty().uppercase()

                    return@continueWithTask Tasks.forResult(
                        serverUid == uid &&
                            serverEmail.equals(email, ignoreCase = true) &&
                            role == "ADMIN"
                    )
                }

                // Backward-compatible bridge: an already-authorized Enterprise
                // ADMIN account can continue to be recognized while the new
                // appConfig/admin record is introduced. No Business ID is needed.
                firestore.collection("accessUsers").document(uid)
                    .get(Source.SERVER)
                    .continueWith { legacyTask ->
                        if (!legacyTask.isSuccessful) return@continueWith false
                        val legacy = legacyTask.result
                        legacy.exists() &&
                            legacy.getString("uid").orEmpty() == uid &&
                            legacy.getString("email").orEmpty().equals(email, ignoreCase = true) &&
                            legacy.getString("role").orEmpty().uppercase() == "ADMIN" &&
                            legacy.getBoolean("approved") == true &&
                            legacy.getBoolean("blocked") != true
                    }
            }
    }
}
