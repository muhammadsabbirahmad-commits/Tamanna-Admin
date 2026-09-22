package com.tamanna.admin

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PartnerManagementActivity : AppCompatActivity() {

    private val partners = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var emptyText: TextView

    private val prefs by lazy {
        getSharedPreferences("partner_management", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_partner_management)

        emptyText = findViewById(R.id.tvEmpty)
        val list = findViewById<ListView>(R.id.lvPartners)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, partners)
        list.adapter = adapter

        loadPartners()

        findViewById<Button>(R.id.btnAddPartner).setOnClickListener {
            showAddDialog()
        }

        list.setOnItemClickListener { _, _, position, _ ->
            showPartnerActions(position)
        }
    }

    private fun showAddDialog() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 0, 40, 0)
        }

        val nameInput = EditText(this).apply {
            hint = "Partner name"
        }
        val emailInput = EditText(this).apply {
            hint = "Gmail address"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        container.addView(nameInput)
        container.addView(emailInput)

        AlertDialog.Builder(this)
            .setTitle("New Partner Request")
            .setMessage("Partner requestটি এখানে যোগ করুন। পরে তালিকা থেকে Approve/Reject করা যাবে।")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()

                if (name.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "সঠিক নাম ও Gmail address দিন।", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val duplicate = partners.any {
                    it.substringAfter("|").substringBefore("|").equals(email, ignoreCase = true)
                }
                if (duplicate) {
                    Toast.makeText(this, "এই Gmail ইতিমধ্যে তালিকায় আছে।", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                partners.add("PENDING|"+email+"|"+name)
                savePartners()
                refreshList()
                Toast.makeText(this, "Partner request যোগ হয়েছে।", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showPartnerActions(position: Int) {
        val raw = partners[position]
        val parts = raw.split("|", limit = 3)
        val status = parts.getOrNull(0) ?: "PENDING"
        val email = parts.getOrNull(1) ?: ""
        val name = parts.getOrNull(2) ?: email

        val options = when (status) {
            "PENDING" -> arrayOf("Approve", "Reject / Remove")
            "APPROVED" -> arrayOf("Suspend", "Remove")
            "SUSPENDED" -> arrayOf("Activate", "Remove")
            else -> arrayOf("Remove")
        }

        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(email + "\nStatus: " + status)
            .setItems(options) { _, which ->
                when (status) {
                    "PENDING" -> if (which == 0) updateStatus(position, "APPROVED") else removePartner(position)
                    "APPROVED" -> if (which == 0) updateStatus(position, "SUSPENDED") else removePartner(position)
                    "SUSPENDED" -> if (which == 0) updateStatus(position, "APPROVED") else removePartner(position)
                    else -> removePartner(position)
                }
            }
            .show()
    }

    private fun updateStatus(position: Int, newStatus: String) {
        val parts = partners[position].split("|", limit = 3)
        partners[position] = newStatus + "|" +
                parts.getOrElse(1) { "" } + "|" +
                parts.getOrElse(2) { "" }
        savePartners()
        refreshList()
    }

    private fun removePartner(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Remove partner?")
            .setMessage("এই partner-কে তালিকা থেকে সরানো হবে।")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                partners.removeAt(position)
                savePartners()
                refreshList()
            }
            .show()
    }

    private fun savePartners() {
        prefs.edit().putStringSet("partners", partners.toSet()).apply()
    }

    private fun loadPartners() {
        partners.clear()
        partners.addAll(prefs.getStringSet("partners", emptySet()) ?: emptySet())
        refreshList()
    }

    private fun refreshList() {
        partners.sortBy { it.substringAfter("|").substringAfter("|").lowercase() }
        adapter.notifyDataSetChanged()
        emptyText.visibility = if (partners.isEmpty()) TextView.VISIBLE else TextView.GONE
    }
}
