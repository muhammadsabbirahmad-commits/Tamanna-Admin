package com.tamanna.admin

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source

class AdminAuditActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var adapter: ArrayAdapter<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminSecurityGuard.requireUnlocked(this)) return
        setContentView(R.layout.activity_admin_audit)
        status = findViewById(R.id.tvAuditStatus)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        findViewById<ListView>(R.id.lvAudit).adapter = adapter
        findViewById<Button>(R.id.btnRefreshAudit).setOnClickListener { loadAudit() }
        loadAudit()
    }
    private fun loadAudit() {
        status.text = "Admin Audit server থেকে যাচাই হচ্ছে..."
        FirebaseFirestore.getInstance().collection("admin_audit").orderBy("timestamp", Query.Direction.DESCENDING).limit(100).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                adapter.clear()
                adapter.addAll(snapshot.documents.map {
                    val time = it.getTimestamp("timestamp")?.toDate()?.toString() ?: ""
                    val action = it.getString("action").orEmpty()
                    val target = it.getString("target").orEmpty()
                    val email = it.getString("email").orEmpty()
                    time + "\n" + action + (if (target.isBlank()) "" else " • " + target) + "\n" + email
                })
                adapter.notifyDataSetChanged()
                status.text = "সর্বশেষ " + snapshot.size() + "টি Admin activity দেখানো হচ্ছে।"
            }.addOnFailureListener { e -> status.text = "Audit data আনা যায়নি: " + (e.message ?: "Unknown error") }
    }
}