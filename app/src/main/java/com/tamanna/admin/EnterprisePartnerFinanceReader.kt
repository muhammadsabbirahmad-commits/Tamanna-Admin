package com.tamanna.admin

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray

data class EnterprisePartnerFinanceSummary(val totalSales:Double,val totalPurchase:Double,val grossProfit:Double,val totalExpenses:Double,val totalWithdrawals:Double,val totalDamageLoss:Double,val netProfit:Double,val partnerCount:Int,val totalInvestment:Double,val totalPercentage:Double)

object EnterprisePartnerFinanceReader {
 private const val SALES="tamanna_enterprise_sales"
 private const val PURCHASES="tamanna_enterprise_purchases"
 private const val PARTNERS="tamanna_enterprise_partners"
 private const val FINANCE="tamanna_enterprise_finance"
 fun load(context:Context,onResult:(Boolean,EnterprisePartnerFinanceSummary?,String)->Unit){
  try {
   val app=EnterpriseFirebaseConnection.getFirestore(context).app; val auth=FirebaseAuth.getInstance(app); val user=auth.currentUser ?: return onResult(false,null,"Enterprise Firebase-এ Admin Gmail সংযুক্ত নেই।")
   val businessId=AdminBusinessContext.getBusinessId(context).trim(); if(businessId.isBlank()||businessId==AdminBusinessContext.DEFAULT_ID) return onResult(false,null,"Settings থেকে আসল Business ID সংরক্ষণ করুন।")
   val db=FirebaseFirestore.getInstance(app)
   db.collection("businesses").document(businessId).collection("members").document(user.uid).get(Source.SERVER).addOnSuccessListener { member ->
    val approved=member.exists()&&member.getBoolean("approved")==true&&member.getBoolean("blocked")!=true; if(!approved){onResult(false,null,"Admin Gmail এই Business-এর approved member নয়।");return@addOnSuccessListener}
    val ns=listOf(SALES,PURCHASES,PARTNERS,FINANCE); val refs=ns.map{n->db.collection("businesses").document(businessId).collection("data").document(n).get(Source.SERVER)}
    Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(refs).addOnSuccessListener { s ->
     val sales=s[0].get("values") as? Map<*,*>; val purchases=s[1].get("values") as? Map<*,*>; val partners=s[2].get("values") as? Map<*,*>; val finance=s[3].get("values") as? Map<*,*>
     val salesTotal=sumSales(sales?.get("sales")); val purchaseTotal=sumPurchases(purchases?.get("purchases")); val expenses=sumField(finance?.get("expenses"),"amount"); val withdrawals=sumField(finance?.get("withdrawals"),"amount"); val damage=sumDamage(finance?.get("damages")); val gross=salesTotal-costOfSales; val net=gross-expenses-damage
     onResult(true,EnterprisePartnerFinanceSummary(salesTotal,purchaseTotal,gross,expenses,withdrawals,damage,net,countPartners(partners?.get("partners")),sumField(partners?.get("partners"),"investment"),sumField(partners?.get("partners"),"percentage")),"Enterprise server Finance/Partner data পড়া হয়েছে।")
    }.addOnFailureListener{onResult(false,null,"Enterprise Finance/Partner data পড়া ব্যর্থ: "+(it.message?:"Firestore Rules পরীক্ষা করুন।"))}
   }.addOnFailureListener{onResult(false,null,"Business membership যাচাই ব্যর্থ: "+(it.message?:"Internet/Firestore Rules পরীক্ষা করুন।"))}
  }catch(e:Exception){onResult(false,null,"Enterprise Firebase সংযোগ ব্যর্থ: "+(e.message?:"আবার চেষ্টা করুন।"))}
 }
 private fun array(v:Any?):JSONArray?=if(v is String)runCatching{JSONArray(v)}.getOrNull()else null
 private fun countPartners(v:Any?):Int=array(v)?.length()?:0
 private fun sumField(v:Any?,f:String):Double{val a=array(v)?:return 0.0;var t=0.0;for(i in 0 until a.length())t+=a.optJSONObject(i)?.optDouble(f,0.0)?:0.0;return t}
 private fun sumSales(v:Any?):Double{val a=array(v)?:return 0.0;var t=0.0;for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;t+=o.optDouble("salePrice",0.0)*o.optDouble("quantity",0.0)};return t}
 private fun sumSalesCost(v:Any?):Double{val a=array(v)?:return 0.0;var t=0.0;for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;t+=o.optDouble("purchasePrice",0.0)*o.optDouble("quantity",0.0)};return t}\n private fun sumPurchases(v:Any?):Double{val a=array(v)?:return 0.0;var t=0.0;for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;t+=o.optDouble("purchasePrice",0.0)*o.optDouble("quantity",0.0)};return t}
 private fun sumDamage(v:Any?):Double{val a=array(v)?:return 0.0;var t=0.0;for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;t+=o.optDouble("quantity",0.0)*o.optDouble("unitCost",0.0)};return t}
}