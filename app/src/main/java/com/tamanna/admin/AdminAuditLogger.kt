package com.tamanna.admin

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

object AdminAuditLogger {
    fun log(context: Context, action: String, target: String = "", details: String = "") {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val eventId = UUID.randomUUID().toString()
        val data = hashMapOf(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "action" to action,
            "target" to target,
            "details" to details,
            "timestamp" to Timestamp.now(),
            "eventId" to eventId
        )
        FirebaseFirestore.getInstance().collection("admin_audit").document(eventId).set(data)
    }
}