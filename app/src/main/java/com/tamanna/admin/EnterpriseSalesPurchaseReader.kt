package com.tamanna.admin

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray

data class EnterpriseSale(
    val date: String,
    val transactionId: String,
    val productCode: String,
    val productName: String,
    val quantity: Int,
    val salePrice: Double,
    val purchasePrice: Double
) {
    val profit: Double get() = (salePrice - purchasePrice) * quantity
}

data class EnterprisePurchase(
    val date: String,
    val productCode: String,
    val productName: String,
    val quantity: Int,
    val purchasePrice: Double,
    val supplier: String
)

object EnterpriseSalesPurchaseReader {
    fun load(context: Context, onResult: (Boolean, List<EnterpriseSale>, List<EnterprisePurchase>, String) -> Unit) {
        try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val user = FirebaseAuth.getInstance(app).currentUser
                ?: return onResult(false, emptyList(), emptyList(), "Enterprise Firebase-এ Admin Gmail সংযুক্ত নেই।")
            val businessId = AdminBusinessContext.getBusinessId(context).trim()
            if (businessId.isBlank() || businessId == AdminBusinessContext.DEFAULT_ID) {
                return onResult(false, emptyList(), emptyList(), "Settings থেকে আসল Business ID সংরক্ষণ করুন।")
            }
            val db = FirebaseFirestore.getInstance(app)
            val base = db.collection("businesses").document(businessId)
            base.collection("members").document(user.uid).get(Source.SERVER)
                .addOnSuccessListener { membership ->
                    val approved = membership.exists() &&
                        membership.getBoolean("approved") == true &&
                        membership.getBoolean("blocked") != true
                    if (!approved) {
                        onResult(false, emptyList(), emptyList(), "Enterprise Business membership অনুমোদিত নয়।")
                        return@addOnSuccessListener
                    }

                    fun read(namespace: String, key: String, done: (String) -> Unit) {
                        base.collection("data").document(namespace).get(Source.SERVER)
                            .addOnSuccessListener { doc ->
                                val values = doc.get("values") as? Map<*, *>
                                done(values?.get(key)?.toString().orEmpty())
                            }
                            .addOnFailureListener { done("") }
                    }

                    read("tamanna_enterprise_sales", "sales") { salesJson ->
                        read("tamanna_enterprise_purchases", "purchases") { purchasesJson ->
                            val sales = parseSales(salesJson)
                            val purchases = parsePurchases(purchasesJson)
                            onResult(true, sales, purchases,
                                "Sales: ${sales.size} • Purchase: ${purchases.size} • Read-only")
                        }
                    }
                }
                .addOnFailureListener { error ->
                    onResult(false, emptyList(), emptyList(),
                        "Enterprise membership যাচাই ব্যর্থ: ${error.message ?: "আবার চেষ্টা করুন।"}")
                }
        } catch (e: Exception) {
            onResult(false, emptyList(), emptyList(),
                "Enterprise Sales/Purchase Reader সংযোগ ব্যর্থ: ${e.message ?: "আবার চেষ্টা করুন।"}")
        }
    }

    private fun parseSales(json: String): List<EnterpriseSale> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(EnterpriseSale(
                    item.optString("date"),
                    item.optString("transactionId"),
                    item.optString("productCode"),
                    item.optString("productName"),
                    item.optInt("quantity"),
                    item.optDouble("salePrice"),
                    item.optDouble("purchasePrice")
                ))
            }
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    private fun parsePurchases(json: String): List<EnterprisePurchase> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(EnterprisePurchase(
                    item.optString("date"),
                    item.optString("productCode"),
                    item.optString("productName"),
                    item.optInt("quantity"),
                    item.optDouble("purchasePrice"),
                    item.optString("supplier")
                ))
            }
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())
}
