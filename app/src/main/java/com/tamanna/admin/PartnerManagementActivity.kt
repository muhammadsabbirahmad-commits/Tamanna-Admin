package com.tamanna.admin

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source\nimport java.util.Locale

data class AdminPartner(val id: String, val status: String, val email: String, val name: String)

class PartnerManagementActivity : AppCompatActivity() {
    private val partners = mutableListOf<AdminPartner>()
    private val enterprisePartners = mutableListOf<EnterprisePartner>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var emptyText: TextView
    private lateinit var enterpriseStatus: TextView
    private lateinit var enterpriseAdapter: ArrayAdapter<String>
    private lateinit var firestore: FirebaseFirestore

    override fun onResume() {
        super.onResume()
        AdminSecurityGuard.requireUnlocked(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_partner_management)
        firestore = FirebaseFirestore.getInstance()
        emptyText = findViewById(R.id.tvEmpty)
        enterpriseStatus = findViewById(R.id.tvEnterprisePartnerStatus)
        val list = findViewById<ListView>(R.id.lvPartners)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        list.adapter = adapter
        findViewById<Button>(R.id.btnAddPartner).setOnClickListener { showAddDialog() }
        val enterpriseList = findViewById<ListView>(R.id.lvEnterprisePartners)
        enterpriseAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        enterpriseList.adapter = enterpriseAdapter
        findViewById<Button>(R.id.btnRefreshEnterprisePartners).setOnClickListener { loadEnterprisePartners() }
        list.setOnItemClickListener { _, _, position, _ -> showPartnerActions(position) }
        loadPartners()
    }

    private fun partnersRef() = firestore.collection("admin_data").document("partners").collection("items")

    private fun showAddDialog() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 0, 40, 0)
        }
        val nameInput = EditText(this).apply { hint = "Partner name" }
        val emailInput = EditText(this).apply {
            hint = "Gmail address"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        container.addView(nameInput)
        container.addView(emailInput)

        AlertDialog.Builder(this)
            .setTitle("New Partner Request")
            .setMessage("Partner requestটি Server Firestore-এ সংরক্ষণ হবে।")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                if (name.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "সঠিক নাম ও Gmail address দিন।", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (partners.any { it.email.equals(email, ignoreCase = true) }) {
                    Toast.makeText(this, "এই Gmail ইতিমধ্যে তালিকায় আছে।", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val data = hashMapOf("status" to "PENDING", "email" to email, "name" to name)
                val id = email.lowercase().replace(Regex("[^a-z0-9]"), "_")
                partnersRef().document(id).set(data)
                    .addOnSuccessListener {
                        loadPartners()
                        Toast.makeText(this, "Partner request Server-এ যোগ হয়েছে।", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "Partner save ব্যর্থ: " + (error.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                    }
            }.show()
    }

    private fun showPartnerActions(position: Int) {
        val p = partners[position]
        val options = when (p.status) {
            "PENDING" -> arrayOf("Approve", "Reject / Remove")
            "APPROVED" -> arrayOf("Suspend", "Remove")
            "SUSPENDED" -> arrayOf("Activate", "Remove")
            else -> arrayOf("Remove")
        }
        AlertDialog.Builder(this)
            .setTitle(p.name)
            .setMessage(p.email + "\nStatus: " + p.status)
            .setItems(options) { _, which ->
                when (p.status) {
                    "PENDING" -> if (which == 0) updateStatus(p, "APPROVED") else removePartner(p)
                    "APPROVED" -> if (which == 0) updateStatus(p, "SUSPENDED") else removePartner(p)
                    "SUSPENDED" -> if (which == 0) updateStatus(p, "APPROVED") else removePartner(p)
                    else -> removePartner(p)
                }
            }.show()
    }

    private fun updateStatus(p: AdminPartner, status: String) {
        partnersRef().document(p.id).update("status", status)
            .addOnSuccessListener { loadPartners() }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Status update ব্যর্থ: " + (error.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
    }

    private fun removePartner(p: AdminPartner) {
        AlertDialog.Builder(this)
            .setTitle("Remove partner?")
            .setMessage("Server থেকে এই partner-কে সরানো হবে।")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                partnersRef().document(p.id).delete()
                    .addOnSuccessListener { loadPartners() }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "Remove ব্যর্থ: " + (error.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                    }
            }.show()
    }

    private fun loadPartners() {
        partnersRef().get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                partners.clear()
                snapshot.documents.forEach { doc ->
                    partners.add(
                        AdminPartner(
                            id = doc.id,
                            status = doc.getString("status").orEmpty(),
                            email = doc.getString("email").orEmpty(),
                            name = doc.getString("name").orEmpty()
                        )
                    )
                }
                refreshList()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Server থেকে Partner list আনা যায়নি: " + (error.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
    }

    private fun loadEnterprisePartners() {
        enterpriseStatus.text = "Tamanna Enterprise server থেকে Partner data যাচাই হচ্ছে..."
        EnterprisePartnerReader.load(this) { _, list, message ->
            runOnUiThread {
                enterprisePartners.clear()
                enterprisePartners.addAll(list)
                enterpriseAdapter.clear()
                enterpriseAdapter.addAll(enterprisePartners.map {
                    val state = if (it.active) "ACTIVE" else "INACTIVE"
                    "${it.name}\nInvestment: ৳ ${money(it.investment)}\nProfit Share: ${money(it.percentage)}% • $state"
                })
                enterpriseAdapter.notifyDataSetChanged()
                enterpriseStatus.text = message
            }
        }
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun refreshList() {
        partners.sortBy { it.name.lowercase() }
        adapter.clear()
        adapter.addAll(partners.map { it.name + "\n" + it.email + "\nStatus: " + it.status })
        adapter.notifyDataSetChanged()
        emptyText.visibility = if (partners.isEmpty()) TextView.VISIBLE else TextView.GONE
    }
}
