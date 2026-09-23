package com.tamanna.admin

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class EnterpriseUserApprovalActivity : AppCompatActivity() {
    private var loadInProgress = false
    private lateinit var list: LinearLayout
    private val enterpriseGoogleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        EnterpriseAccessManager.finishEnterpriseGoogleSignIn(this, result.data, { loadInProgress = false; load() }, { message -> loadInProgress = false; Toast.makeText(this, message, Toast.LENGTH_LONG).show() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminSecurityGuard.requireUnlocked(this)) return
        setContentView(R.layout.activity_enterprise_user_approval)
        list = findViewById(R.id.approvalList)
        findViewById<Button>(R.id.btnRefreshAccess).setOnClickListener { load() }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (AdminSecurityGuard.isUnlocked(this) && !loadInProgress) load()
    }

    private fun load() {
        if (loadInProgress) return
        loadInProgress = true
        list.removeAllViews()
        addText("Enterprise User Approval", 22, true)
        addText("Gmail request গ্রহণ করে মেয়াদ নির্ধারণ করুন।", 14, false)
        EnterpriseAccessManager.loadRequests(this, enterpriseGoogleLauncher, { requests ->
            runOnUiThread {
                loadInProgress = false
                list.removeAllViews(); addText("Enterprise User Approval", 22, true)
                if (requests.isEmpty()) { addText("কোনো access request পাওয়া যায়নি।", 16, false); return@runOnUiThread }
                requests.forEach { renderRequest(it) }
            }
        }, { message ->
            runOnUiThread { loadInProgress = false; list.removeAllViews(); addText("Enterprise User Approval", 22, true); addText(message, 15, false); Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        })
    }

    private fun renderRequest(request: EnterpriseAccessRequest) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,16,16,16); setBackgroundResource(R.drawable.bg_status_card) }
        addTextTo(box, request.email, 17, true)
        addTextTo(box, "Status: ${request.status}", 15, true)
        if (request.expiresAt > 0L) addTextTo(box, "Expiry: ${formatDate(request.expiresAt)}", 14, false)

        when (request.status) {
            EnterpriseAccessManager.PENDING, EnterpriseAccessManager.REJECTED, EnterpriseAccessManager.EXPIRED -> {
                val duration = Spinner(this)
                duration.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                    arrayOf("3 days","5 days","7 days","15 days","1 month","3 months","6 months","1 year","Custom"))
                box.addView(duration)
                val customDate = Button(this).apply { text = "Custom Expiry Date"; visibility = android.view.View.GONE }
                box.addView(customDate)
                var customExpiry = 0L
                duration.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                    override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { customDate.visibility = if (position == 8) android.view.View.VISIBLE else android.view.View.GONE }
                }
                customDate.setOnClickListener {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(this, { _, y, m, d -> cal.set(y,m,d,23,59,59); customExpiry=cal.timeInMillis; customDate.text="Custom: ${formatDate(customExpiry)}" },
                        cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()
                }
                val accept = Button(this).apply { text="Accept & Activate" }
                val reject = Button(this).apply { text="Reject" }
                box.addView(accept); box.addView(reject)
                accept.setOnClickListener {
                    val now=System.currentTimeMillis()
                    val expiry=if(duration.selectedItemPosition==8) customExpiry else now+durationMillis(duration.selectedItemPosition)
                    if(expiry<=now){Toast.makeText(this,"সঠিক Custom expiry date দিন।",Toast.LENGTH_LONG).show();return@setOnClickListener}
                    EnterpriseAccessManager.updateRequest(this,enterpriseGoogleLauncher,request,EnterpriseAccessManager.ACTIVE,now,expiry,request.notificationsEnabled,
                        {Toast.makeText(this,"User ACTIVE করা হয়েছে।",Toast.LENGTH_SHORT).show();load()},{Toast.makeText(this,it,Toast.LENGTH_LONG).show()})
                }
                reject.setOnClickListener {
                    EnterpriseAccessManager.updateRequest(this,enterpriseGoogleLauncher,request,EnterpriseAccessManager.REJECTED,0L,0L,request.notificationsEnabled,
                        {Toast.makeText(this,"Request REJECTED হয়েছে।",Toast.LENGTH_SHORT).show();load()},{Toast.makeText(this,it,Toast.LENGTH_LONG).show()})
                }
            }
            EnterpriseAccessManager.ACTIVE -> {
                val block=Button(this).apply{text="Block"}; val extend=Button(this).apply{text="Extend"}
                box.addView(block);box.addView(extend)
                block.setOnClickListener { EnterpriseAccessManager.updateRequest(this,enterpriseGoogleLauncher,request,EnterpriseAccessManager.BLOCKED,request.startAt,request.expiresAt,request.notificationsEnabled,{Toast.makeText(this,"User BLOCKED হয়েছে।",Toast.LENGTH_SHORT).show();load()},{Toast.makeText(this,it,Toast.LENGTH_LONG).show()}) }
                extend.setOnClickListener { showExtend(request) }
            }
            EnterpriseAccessManager.BLOCKED -> {
                val unblock=Button(this).apply{text="Unblock"};box.addView(unblock)
                unblock.setOnClickListener {
                    if(request.expiresAt<=System.currentTimeMillis()) showExtend(request)
                    else EnterpriseAccessManager.updateRequest(this,enterpriseGoogleLauncher,request,EnterpriseAccessManager.ACTIVE,request.startAt,request.expiresAt,request.notificationsEnabled,{Toast.makeText(this,"User UNBLOCKED হয়েছে।",Toast.LENGTH_SHORT).show();load()},{Toast.makeText(this,it,Toast.LENGTH_LONG).show()})
                }
            }
        }
        list.addView(box,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,12,0,0)})
    }

    private fun showExtend(request: EnterpriseAccessRequest) {
        val options=arrayOf("3 days","5 days","7 days","15 days","1 month","3 months","6 months","1 year")
        AlertDialog.Builder(this).setTitle("Extend / Reactivate").setItems(options){_,which->
            val now=System.currentTimeMillis(); val expiry=maxOf(now,request.expiresAt)+durationMillis(which)
            EnterpriseAccessManager.updateRequest(this,enterpriseGoogleLauncher,request,EnterpriseAccessManager.ACTIVE,now,expiry,request.notificationsEnabled,{Toast.makeText(this,"User ACTIVE ও Extend হয়েছে।",Toast.LENGTH_SHORT).show();load()},{Toast.makeText(this,it,Toast.LENGTH_LONG).show()})
        }.show()
    }
    private fun durationMillis(p:Int):Long=when(p){0->3L*DAY;1->5L*DAY;2->7L*DAY;3->15L*DAY;4->30L*DAY;5->90L*DAY;6->180L*DAY;else->365L*DAY}
    private fun addText(t:String,s:Int,b:Boolean)=addTextTo(list,t,s,b)
    private fun addTextTo(p:LinearLayout,t:String,s:Int,b:Boolean){p.addView(TextView(this).apply{text=t;textSize=s.toFloat();if(b)setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(0,4,0,4)})}
    private fun formatDate(ms:Long)=SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.getDefault()).format(Date(ms))
    companion object{private const val DAY=24L*60L*60L*1000L}
}
