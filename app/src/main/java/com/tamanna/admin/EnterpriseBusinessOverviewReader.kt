package com.tamanna.admin

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EnterpriseBusinessOverviewSummary(
    val totalProducts: Int,
    val currentStock: Int,
    val todaySales: Double,
    val todayPurchase: Double,
    val todayProfit: Double
)

object EnterpriseBusinessOverviewReader {
    private const val PRODUCTS = "tamanna_enterprise_products"
    private const val SALES = "tamanna_enterprise_sales"
    private const val PURCHASES = "tamanna_enterprise_purchases"

    fun load(context: Context, onResult: (Boolean, EnterpriseBusinessOverviewSummary?, String) -> Unit) {
        try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val user = FirebaseAuth.getInstance(app).currentUser
                ?: return onResult(false, null, "Enterprise Firebase-এ Admin Gmail সংযুক্ত নেই।")
            val businessId = AdminBusinessContext.getBusinessId(context).trim()
            if (businessId.isBlank() || businessId == AdminBusinessContext.DEFAULT_ID) {
                return onResult(false, null, "Settings থেকে আসল Business ID সংরক্ষণ করুন।")
            }
            val db = FirebaseFirestore.getInstance(app)
            val base = db.collection("businesses").document(businessId)
            base.collection("members").document(user.uid).get(Source.SERVER)
                .addOnSuccessListener { member ->
                    val approved = member.exists() &&
                        member.getBoolean("approved") == true &&
                        member.getBoolean("blocked") != true
                    if (!approved) {
                        onResult(false, null, "এই Admin Gmail এই Business-এর approved member নয়।")
                        return@addOnSuccessListener
                    }
                    val refs = listOf(PRODUCTS, SALES, PURCHASES).map {
                        base.collection("data").document(it).get(Source.SERVER)
                    }
                    com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(refs)
                        .addOnSuccessListener { docs ->
                            val products = parseArray(docs[0].get("values")?.let { (it as? Map<*, *>)?.get("products") })
                            val sales = parseArray(docs[1].get("values")?.let { (it as? Map<*, *>)?.get("sales") })
                            val purchases = parseArray(docs[2].get("values")?.let { (it as? Map<*, *>)?.get("purchases") })

                            var stock = 0
                            for (i in 0 until products.length()) {
                                stock += products.optJSONObject(i)?.optInt("stockQuantity", 0) ?: 0
                            }

                            var todaySales = 0.0
                            var todayPurchase = 0.0
                            var todayProfit = 0.0
                            for (i in 0 until sales.length()) {
                                val o = sales.optJSONObject(i) ?: continue
                                if (isToday(o.optString("date"))) {
                                    val q = o.optDouble("quantity", 0.0)
                                    val sale = o.optDouble("salePrice", 0.0)
                                    val cost = o.optDouble("purchasePrice", 0.0)
                                    todaySales += sale * q
                                    todayProfit += (sale - cost) * q
                                }
                            }
                            for (i in 0 until purchases.length()) {
                                val o = purchases.optJSONObject(i) ?: continue
                                if (isToday(o.optString("date"))) {
                                    todayPurchase += o.optDouble("purchasePrice", 0.0) *
                                        o.optDouble("quantity", 0.0)
                                }
                            }

                            onResult(
                                true,
                                EnterpriseBusinessOverviewSummary(
                                    products.length(),
                                    stock,
                                    todaySales,
                                    todayPurchase,
                                    todayProfit
                                ),
                                "Enterprise Business Overview server data পড়া হয়েছে."
                            )
                        }
                        .addOnFailureListener {
                            onResult(false, null, "Enterprise Overview data পড়া ব্যর্থ: " + (it.message ?: "Firestore Rules/Internet পরীক্ষা করুন।"))
                        }
                }
                .addOnFailureListener {
                    onResult(false, null, "Business membership যাচাই ব্যর্থ: " + (it.message ?: "Firestore Rules/Internet পরীক্ষা করুন।"))
                }
        } catch (e: Exception) {
            onResult(false, null, "Enterprise Firebase সংযোগ ব্যর্থ: " + (e.message ?: "আবার চেষ্টা করুন।"))
        }
    }

    private fun parseArray(value: Any?): JSONArray {
        if (value is JSONArray) return value
        val raw = value?.toString().orEmpty()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    private fun isToday(value: String): Boolean {
        val text = value.trim()
        if (text.isBlank()) return false
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (text.startsWith(today)) return true
        val patterns = listOf("dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy", "yyyy/MM/dd")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(text)
            }.getOrNull() ?: continue
            if (SimpleDateFormat(pattern, Locale.US).format(parsed) ==
                SimpleDateFormat(pattern, Locale.US).format(Date())) return true
        }
        return false
    }
}