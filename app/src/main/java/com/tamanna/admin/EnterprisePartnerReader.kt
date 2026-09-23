package com.tamanna.admin

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray

data class EnterprisePartner(val id: Long, val name: String, val investment: Double, val percentage: Double, val username: String, val active: Boolean)

object EnterprisePartnerReader {
    private const val PARTNERS = "tamanna_enterprise_partners"

    fun load(context: Context, onResult: (Boolean, List<EnterprisePartner>, String) -> Unit) {
        try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val user = FirebaseAuth.getInstance(app).currentUser
                ?: return onResult(false, emptyList(), "Enterprise Firebase-এ Admin Gmail সংযুক্ত নেই।")
            val businessId = AdminBusinessContext.getBusinessId(context).trim()
            if (businessId.isBlank() || businessId == AdminBusinessContext.DEFAULT_ID)
                return onResult(false, emptyList(), "Settings থেকে আসল Business ID সংরক্ষণ করুন।")
            val db = FirebaseFirestore.getInstance(app)
            db.collection("businesses").document(businessId).collection("members").document(user.uid)
                .get(Source.SERVER).addOnSuccessListener { member ->
                    val approved = member.exists() && member.getBoolean("approved") == true && member.getBoolean("blocked") != true
                    if (!approved) {
                        onResult(false, emptyList(), "Admin Gmail এই Business-এর approved member নয়।")
                        return@addOnSuccessListener
                    }
                    db.collection("businesses").document(businessId).collection("data").document(PARTNERS)
                        .get(Source.SERVER).addOnSuccessListener { doc ->
                            val values = doc.get("values") as? Map<*, *>
                            val raw = values?.get("partners") as? String ?: "[]"
                            val list = parse(raw)
                            onResult(true, list, "${list.size} জন Enterprise Partner পড়া হয়েছে। Read-only.")
                        }.addOnFailureListener {
                            onResult(false, emptyList(), "Enterprise Partner data পড়া ব্যর্থ: ${it.message ?: "Firestore Rules পরীক্ষা করুন।"}")
                        }
                }.addOnFailureListener {
                    onResult(false, emptyList(), "Business membership যাচাই ব্যর্থ: ${it.message ?: "Internet/Firestore Rules পরীক্ষা করুন।"}")
                }
        } catch (e: Exception) {
            onResult(false, emptyList(), "Enterprise Firebase সংযোগ ব্যর্থ: ${e.message ?: "আবার চেষ্টা করুন।"}")
        }
    }

    private fun parse(raw: String): List<EnterprisePartner> {
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(EnterprisePartner(o.optLong("id"), o.optString("name"), o.optDouble("investment", 0.0), o.optDouble("percentage", 0.0), o.optString("username"), o.optBoolean("active", true)))
            }
        }.sortedBy { it.name.lowercase() }
    }
}