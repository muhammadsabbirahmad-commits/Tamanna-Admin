package com.tamanna.admin

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EnterpriseDateRangeReport(
    val from: String,
    val to: String,
    val sales: Double,
    val purchase: Double,
    val profit: Double,
    val salesQuantity: Int,
    val purchaseQuantity: Int
)

object EnterpriseDateRangeReportReader {
    private const val SALES = "tamanna_enterprise_sales"
    private const val PURCHASES = "tamanna_enterprise_purchases"

    fun load(context: Context, from: String, to: String, onResult: (Boolean, EnterpriseDateRangeReport?, String) -> Unit) {
        if (!validDate(from) || !validDate(to) || from > to) {
            onResult(false, null, "তারিখের Range সঠিক নয়। From/To পরীক্ষা করুন।")
            return
        }
        try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val user = FirebaseAuth.getInstance(app).currentUser
                ?: return onResult(false, null, "Enterprise Firebase-এ Admin Gmail সংযুক্ত নেই।")
            val businessId = AdminBusinessContext.getBusinessId(context).trim()
            if (businessId.isBlank() || businessId == AdminBusinessContext.DEFAULT_ID) {
                return onResult(false, null, "Settings থেকে আসল Business ID সংরক্ষণ করুন।")
            }
            val base = FirebaseFirestore.getInstance(app).collection("businesses").document(businessId)
            base.collection("members").document(user.uid).get(Source.SERVER)
                .addOnSuccessListener { member ->
                    val approved = member.exists() && member.getBoolean("approved") == true && member.getBoolean("blocked") != true
                    if (!approved) {
                        onResult(false, null, "এই Admin Gmail এই Business-এর approved member নয়।")
                        return@addOnSuccessListener
                    }
                    val salesTask = base.collection("data").document(SALES).get(Source.SERVER)
                    val purchasesTask = base.collection("data").document(PURCHASES).get(Source.SERVER)
                    com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(
                        listOf(salesTask, purchasesTask)
                    ).addOnSuccessListener { docs ->
                        val sales = extractArray(docs[0].get("values"), "sales")
                        val purchases = extractArray(docs[1].get("values"), "purchases")
                        var salesTotal = 0.0
                        var purchaseTotal = 0.0
                        var profitTotal = 0.0
                        var salesQty = 0
                        var purchaseQty = 0

                        for (i in 0 until sales.length()) {
                            val o = sales.optJSONObject(i) ?: continue
                            if (!inRange(o.optString("date"), from, to)) continue
                            val q = o.optInt("quantity", 0)
                            val sale = o.optDouble("salePrice", 0.0)
                            val cost = o.optDouble("purchasePrice", 0.0)
                            salesQty += q
                            salesTotal += sale * q
                            profitTotal += (sale - cost) * q
                        }
                        for (i in 0 until purchases.length()) {
                            val o = purchases.optJSONObject(i) ?: continue
                            if (!inRange(o.optString("date"), from, to)) continue
                            val q = o.optInt("quantity", 0)
                            purchaseQty += q
                            purchaseTotal += o.optDouble("purchasePrice", 0.0) * q
                        }

                        onResult(true, EnterpriseDateRangeReport(from, to, salesTotal, purchaseTotal, profitTotal, salesQty, purchaseQty),
                            "Server data পড়া হয়েছে • Read-only")
                    }.addOnFailureListener {
                        onResult(false, null, "Report data পড়া ব্যর্থ: " + (it.message ?: "Firestore Rules/Internet পরীক্ষা করুন।"))
                    }
                }
                .addOnFailureListener {
                    onResult(false, null, "Business membership যাচাই ব্যর্থ: " + (it.message ?: "Firestore Rules/Internet পরীক্ষা করুন।"))
                }
        } catch (e: Exception) {
            onResult(false, null, "Enterprise Report সংযোগ ব্যর্থ: " + (e.message ?: "আবার চেষ্টা করুন।"))
        }
    }

    private fun validDate(value: String): Boolean = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
        true
    }.getOrDefault(false)

    private fun inRange(value: String, from: String, to: String): Boolean {
        val normalized = normalizeDate(value) ?: return false
        return normalized in from..to
    }

    private fun normalizeDate(value: String): String? {
        val text = value.trim()
        if (text.isBlank()) return null
        if (text.length >= 10 && text.substring(0, 10).matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return text.substring(0, 10)
        for (pattern in listOf("dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy", "yyyy/MM/dd")) {
            val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(text) }.getOrNull() ?: continue
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
        }
        return null
    }

    private fun extractArray(values: Any?, key: String): JSONArray {
        val source = (values as? Map<*, *>)?.get(key) ?: return JSONArray()
        if (source is JSONArray) return source
        if (source is Collection<*>) {
            val array = JSONArray()
            source.forEach { item ->
                array.put(if (item is Map<*, *>) JSONObject(item.entries.associate { it.key.toString() to it.value }) else item)
            }
            return array
        }
        return runCatching { JSONArray(source.toString()) }.getOrElse { JSONArray() }
    }
}
