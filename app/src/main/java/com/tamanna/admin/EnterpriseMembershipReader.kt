package com.tamanna.admin

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

data class EnterpriseMembership(
    val uid: String,
    val email: String,
    val role: String,
    val approved: Boolean,
    val blocked: Boolean
)

object EnterpriseMembershipReader {
    fun load(context: Context, onResult: (Boolean, EnterpriseMembership?, String) -> Unit) {
        try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val user = FirebaseAuth.getInstance(app).currentUser
                ?: return onResult(false, null, "Enterprise Firebase-এ Admin Gmail সংযুক্ত নেই।")
            val businessId = AdminBusinessContext.getBusinessId(context).trim()
            if (businessId.isBlank() || businessId == AdminBusinessContext.DEFAULT_ID) {
                return onResult(false, null, "Settings থেকে আসল Business ID সংরক্ষণ করুন।")
            }
            FirebaseFirestore.getInstance(app)
                .collection("businesses").document(businessId)
                .collection("members").document(user.uid)
                .get(Source.SERVER)
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        onResult(false, null, "এই Admin Gmail Business membership-এ পাওয়া যায়নি।")
                        return@addOnSuccessListener
                    }
                    val membership = EnterpriseMembership(
                        uid = user.uid,
                        email = doc.getString("email").orEmpty().ifBlank { user.email.orEmpty() },
                        role = doc.getString("role").orEmpty().ifBlank { "UNKNOWN" },
                        approved = doc.getBoolean("approved") == true,
                        blocked = doc.getBoolean("blocked") == true
                    )
                    val state = when {
                        membership.blocked -> "BLOCKED"
                        membership.approved -> "APPROVED"
                        else -> "PENDING"
                    }
                    onResult(true, membership, "Enterprise Access: $state • Role: ${membership.role} • Read-only")
                }
                .addOnFailureListener {
                    onResult(false, null, "Enterprise membership পড়া ব্যর্থ: ${it.message ?: "Firestore Rules/Internet পরীক্ষা করুন।"}")
                }
        } catch (e: Exception) {
            onResult(false, null, "Enterprise membership সংযোগ ব্যর্থ: ${e.message ?: "আবার চেষ্টা করুন।"}")
        }
    }
}