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

                ensureAdminMembership(context, auth, user.uid, email)
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

    private fun ensureAdminMembership(
        context: Context,
        auth: FirebaseAuth,
        uid: String,
        email: String
    ): Task<Boolean> {
        val firestore = FirebaseFirestore.getInstance(auth.app)
        val businessId = AdminBusinessContext.getBusinessId(context).trim()

        if (businessId.isBlank() || businessId == AdminBusinessContext.DEFAULT_ID) {
            return Tasks.forResult(false)
        }

        val memberRef = firestore.collection("businesses")
            .document(businessId)
            .collection("members")
            .document(uid)

        return memberRef.get(Source.SERVER).continueWithTask { memberTask ->
            if (!memberTask.isSuccessful) {
                return@continueWithTask Tasks.forResult(false)
            }

            val member = memberTask.result
            if (!member.exists()) {
                return@continueWithTask Tasks.forResult(false)
            }

            val memberUid = member.getString("uid").orEmpty()
            val memberEmail = member.getString("email").orEmpty()
            val role = member.getString("role").orEmpty()
            val approved = member.getBoolean("approved") == true
            val blocked = member.getBoolean("blocked") == true

            Tasks.forResult(
                memberUid == uid &&
                    memberEmail.equals(email, ignoreCase = true) &&
                    role == "OWNER" &&
                    approved &&
                    !blocked
            )
        }
    }
}
