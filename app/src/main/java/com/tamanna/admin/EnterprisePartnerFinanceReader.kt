package com.tamanna.admin

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray

data class EnterprisePartnerFinanceSummary(val partnerCount:Int,val totalInvestment:Double,val totalPercentage:Double,val totalExpenses:Double,val totalWithdrawals:Double,val totalDamageLoss:Double)

object EnterprisePartnerFinanceReader {
 private const val PARTNERS = "tamanna_enterprise_partners"
 private const val FINANCE = "tamanna_enterprise_finance"

 fun load(context:Context,onResult:(Boolean,EnterprisePartnerFinanceSummary?,String)->Unit){
  try {
   val app=EnterpriseFirebaseConnection.getFirestore(context).app
   val auth=FirebaseAuth.getInstance(app)
   val user=auth.currentUser ?: return onResult(false,null,"Enterprise Firebase-এ Admin Gmail সংযুক্ত নেই।")
   val businessId=AdminBusinessContext.getBusinessId(context).trim()
   if(businessId.isBlank()||businessId==AdminBusinessContext.DEFAULT_ID) return onResult(false,null,"Settings থেকে আসল Business ID সংরক্ষণ করুন।")
   val db=FirebaseFirestore.getInstance(app)
   db.collection("businesses").document(businessId).collection("members").document(user.uid).get(Source.SERVER)
    .addOnSuccessListener { member ->
     val approved=member.exists()&&member.getBoolean("approved")==true&&member.getBoolean("blocked")!=true
     if(!approved){onResult(false,null,"Admin Gmail এই Business-এর approved member নয়।");return@addOnSuccessListener}
     val refs=listOf(PARTNERS,FINANCE).map{n->db.collection("businesses").document(businessId).collection("data").document(n).get(Source.SERVER)}
     Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(refs).addOnSuccessListener { s ->
      val pv=s.getOrNull(0)?.get("values") as? Map<*,*>
      val fv=s.getOrNull(1)?.get("values") as? Map<*,*>
      val summary=EnterprisePartnerFinanceSummary(countPartners(pv?.get("partners")),sumField(pv?.get("partners"),"investment"),sumField(pv?.get("partners"),"percentage"),sumField(fv?.get("expenses"),"amount"),sumField(fv?.get("withdrawals"),"amount"),sumDamage(fv?.get("damages")))
      onResult(true,summary,"Enterprise server Partner/Finance data পড়া হয়েছে।")
     }.addOnFailureListener{onResult(false,null,"Enterprise Partner/Finance data পড়া ব্যর্থ: ${it.message?:"Firestore Rules পরীক্ষা করুন।"}")}
    }.addOnFailureListener{onResult(false,null,"Business membership যাচাই ব্যর্থ: ${it.message?:"Internet/Firestore Rules পরীক্ষা করুন।"}")}
  } catch(e:Exception){onResult(false,null,"Enterprise Firebase সংযোগ ব্যর্থ: ${e.message?:"আবার চেষ্টা করুন।"}")}
 }
 private fun arr(v:Any?):JSONArray?=if(v is String) runCatching{JSONArray(v)}.getOrNull() else null
 private fun countPartners(v:Any?):Int=arr(v)?.length()?:0
 private fun sumField(v:Any?,f:String):Double { val a=arr(v)?:return 0.0; var t=0.0; for(i in 0 until a.length()) t+=a.optJSONObject(i)?.optDouble(f,0.0)?:0.0; return t }
 private fun sumDamage(v:Any?):Double { val a=arr(v)?:return 0.0; var t=0.0; for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;t+=o.optDouble("quantity",0.0)*o.optDouble("unitCost",0.0)}; return t }
}