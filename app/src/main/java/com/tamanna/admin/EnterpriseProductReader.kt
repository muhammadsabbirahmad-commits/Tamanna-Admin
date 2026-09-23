package com.tamanna.admin

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray

data class EnterpriseProduct(
    val code: String,
    val name: String,
    val purchasePrice: Double,
    val salePrice: Double,
    val stockQuantity: Int
)

object EnterpriseProductReader {
    fun load(context: Context, onResult: (Boolean, List<EnterpriseProduct>, String) -> Unit) {
        try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val user = FirebaseAuth.getInstance(app).currentUser
                ?: return onResult(false, emptyList(), "Enterprise Firebase-এ Admin Gmail সংযুক্ত নেই।")
            val businessId = AdminBusinessContext.getBusinessId(context).trim()
            if (businessId.isBlank() || businessId == AdminBusinessContext.DEFAULT_ID) {
                return onResult(false, emptyList(), "Settings থেকে আসল Business ID সংরক্ষণ করুন।")
            }
            val db = FirebaseFirestore.getInstance(app)
            db.collection("businesses").document(businessId).collection("members").document(user.uid)
                .get(Source.SERVER)
                .addOnSuccessListener { membership ->
                    val approved = membership.exists() && membership.getBoolean("approved") == true && membership.getBoolean("blocked") != true
                    if (!approved) {
                        onResult(false, emptyList(), "Enterprise Business membership অনুমোদিত নয়।")
                        return@addOnSuccessListener
                    }
                    db.collection("businesses").document(businessId).collection("data").document("tamanna_enterprise_products")
                        .get(Source.SERVER)
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                onResult(true, emptyList(), "Enterprise Cloud-এ Product Data পাওয়া যায়নি। Read-only")
                                return@addOnSuccessListener
                            }
                            val raw = doc.get("values") as? Map<*, *>
                            val productsJson = raw?.get("products")?.toString().orEmpty()
                            if (productsJson.isBlank()) {
                                onResult(true, emptyList(), "Enterprise Cloud-এ Product তালিকা খালি। Read-only")
                                return@addOnSuccessListener
                            }
                            val products = runCatching {
                                val array = JSONArray(productsJson)
                                buildList {
                                    for (i in 0 until array.length()) {
                                        val item = array.getJSONObject(i)
                                        add(EnterpriseProduct(item.optString("code"), item.optString("name"), item.optDouble("purchasePrice", 0.0), item.optDouble("salePrice", 0.0), item.optInt("stockQuantity", 0)))
                                    }
                                }.sortedBy { it.code }
                            }.getOrElse { emptyList() }
                            onResult(true, products, "মোট " + products.size + "টি Product পাওয়া গেছে। Read-only")
                        }
                        .addOnFailureListener { error ->
                            onResult(false, emptyList(), "Enterprise Product Data পড়া ব্যর্থ: " + (error.message ?: "Firestore Rules/Internet পরীক্ষা করুন।"))
                        }
                }
                .addOnFailureListener { error ->
                    onResult(false, emptyList(), "Enterprise membership যাচাই ব্যর্থ: " + (error.message ?: "আবার চেষ্টা করুন।"))
                }
        } catch (e: Exception) {
            onResult(false, emptyList(), "Enterprise Product Reader সংযোগ ব্যর্থ: " + (e.message ?: "আবার চেষ্টা করুন।"))
        }
    }
}
