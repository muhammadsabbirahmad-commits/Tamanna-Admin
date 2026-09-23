package com.tamanna.admin

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

object EnterpriseBusinessDataReader {

    private val namespaces = listOf(
        "tamanna_enterprise_products",
        "tamanna_enterprise_sales",
        "tamanna_enterprise_purchases",
        "tamanna_enterprise_finance",
        "tamanna_enterprise_partners",
        "tamanna_enterprise_settings",
        "tamanna_customer_due",
        "tamanna_supplier_due",
        "tamanna_inventory_meta",
        "tamanna_enterprise_sale_transactions",
        "tamanna_enterprise_sale_returns",
        "tamanna_enterprise_activity_log"
    )

    fun loadOverview(context: Context, onResult: (Boolean, String) -> Unit) {
        try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val auth = FirebaseAuth.getInstance(app)
            val user = auth.currentUser
                ?: return onResult(false, "Enterprise Firebase-এ Admin Gmail সংযুক্ত নেই।")

            val businessId = AdminBusinessContext.getBusinessId(context).trim()
            if (businessId.isBlank() || businessId == AdminBusinessContext.DEFAULT_ID) {
                return onResult(false, "Settings থেকে আসল Business ID সংরক্ষণ করুন।")
            }

            val db = FirebaseFirestore.getInstance(app)
            val memberRef = db.collection("businesses")
                .document(businessId)
                .collection("members")
                .document(user.uid)

            memberRef.get(Source.SERVER)
                .addOnSuccessListener { member ->
                    val approved = member.exists() &&
                        member.getBoolean("approved") == true &&
                        member.getBoolean("blocked") != true

                    if (!approved) {
                        onResult(false, "এই Admin Gmail এই Business-এর approved member নয়।")
                        return@addOnSuccessListener
                    }

                    readBusinessData(db, businessId, onResult)
                }
                .addOnFailureListener {
                    onResult(false, "Business membership যাচাই ব্যর্থ: ${it.message ?: "Firestore Rules/Internet পরীক্ষা করুন।"}")
                }
        } catch (e: Exception) {
            onResult(false, "Enterprise Firebase সংযোগ ব্যর্থ: ${e.message ?: "আবার চেষ্টা করুন।"}")
        }
    }

    private fun readBusinessData(
        db: FirebaseFirestore,
        businessId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val refs = namespaces.map { namespace ->
            db.collection("businesses")
                .document(businessId)
                .collection("data")
                .document(namespace)
                .get(Source.SERVER)
        }

        Tasks.whenAllSuccess<DocumentSnapshot>(refs)
            .addOnSuccessListener { snapshots ->
                val found = snapshots.mapIndexedNotNull { index, snapshot ->
                    if (snapshot.exists()) namespaces[index] else null
                }

                val lines = mutableListOf<String>()
                lines += "Enterprise Business: $businessId"
                lines += "Approved Member: YES"
                lines += "Cloud Namespaces Found: ${found.size}/${namespaces.size}"

                if (found.isNotEmpty()) {
                    lines += ""
                    lines += "Available Cloud Data:"
                    found.forEach { lines += "• $it" }
                }

                onResult(true, lines.joinToString("\n"))
            }
            .addOnFailureListener {
                onResult(false, "Enterprise Business Data পড়া ব্যর্থ: ${it.message ?: "Firestore Rules পরীক্ষা করুন।"}")
            }
    }
}
