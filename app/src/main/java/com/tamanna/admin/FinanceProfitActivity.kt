package com.tamanna.admin

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class FinanceProfitActivity : AppCompatActivity() {
 private lateinit var tvStatus: TextView
 private lateinit var tvExpenses: TextView
 private lateinit var tvWithdrawals: TextView
 private lateinit var tvDamage: TextView
 private lateinit var tvPartners: TextView

 override fun onResume(){super.onResume();AdminSecurityGuard.requireUnlocked(this)}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_finance_profit);tvStatus=findViewById(R.id.tvFinanceStatus);tvExpenses=findViewById(R.id.tvFinanceExpenses);tvWithdrawals=findViewById(R.id.tvFinanceWithdrawals);tvDamage=findViewById(R.id.tvFinanceDamage);tvPartners=findViewById(R.id.tvFinancePartners);findViewById<Button>(R.id.btnRefreshFinance).setOnClickListener{loadFinance()};loadFinance()}
 private fun loadFinance(){
  tvStatus.text="Tamanna Enterprise server যাচাই হচ্ছে..."
  EnterprisePartnerFinanceReader.load(this){ok,summary,message->runOnUiThread{
   if(!ok||summary==null){tvStatus.text=message;return@runOnUiThread}
   tvExpenses.text="Business Expenses\n৳ ${money(summary.totalExpenses)}"
   tvWithdrawals.text="Partner Withdrawals\n৳ ${money(summary.totalWithdrawals)}"
   tvDamage.text="Damage Loss\n৳ ${money(summary.totalDamageLoss)}"
   tvPartners.text="Partners\n${summary.partnerCount} জন • Investment ৳ ${money(summary.totalInvestment)}\nProfit Share ${money(summary.totalPercentage)}%"
   tvStatus.text=message+"\nRead-only: Admin app থেকে Enterprise data পরিবর্তন করা হচ্ছে না."
  }}
 }
 private fun money(value:Double):String=String.format(Locale.US,"%.2f",value)
}