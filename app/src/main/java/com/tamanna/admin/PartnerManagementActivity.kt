package com.tamanna.admin
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PartnerManagementActivity : AppCompatActivity() {
    private val partners = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_partner_management)
        emptyText = findViewById(R.id.tvEmpty)
        val list = findViewById<ListView>(R.id.lvPartners)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, partners)
        list.adapter = adapter
        loadPartners()
        findViewById<Button>(R.id.btnAddPartner).setOnClickListener { showAddDialog() }
        list.setOnItemClickListener { _, _, position, _ -> showPartnerActions(position) }
    }

    private fun showAddDialog() {
        val input = EditText(this)
        input.hint = "Partner name"
        AlertDialog.Builder(this).setTitle("Add Partner Request").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) { partners.add("PENDING • " + name); savePartners(); adapter.notifyDataSetChanged(); updateEmptyState() }
            }.show()
    }

    private fun showPartnerActions(position: Int) {
        val current = partners[position]
        val options = if (current.startsWith("PENDING")) arrayOf("Approve", "Reject / Remove") else arrayOf("Suspend", "Remove")
        AlertDialog.Builder(this).setTitle(current).setItems(options) { _, which ->
            if (current.startsWith("PENDING")) {
                if (which == 0) partners[position] = "APPROVED • " + current.substringAfter("•").trim() else partners.removeAt(position)
            } else {
                if (which == 0) partners[position] = "SUSPENDED • " + current.substringAfter("•").trim() else partners.removeAt(position)
            }
            savePartners(); adapter.notifyDataSetChanged(); updateEmptyState()
        }.show()
    }

    private fun savePartners() { getPreferences(MODE_PRIVATE).edit().putStringSet("partners", partners.toSet()).apply() }
    private fun loadPartners() { partners.clear(); partners.addAll(getPreferences(MODE_PRIVATE).getStringSet("partners", emptySet()) ?: emptySet()); updateEmptyState() }
    private fun updateEmptyState() { emptyText.visibility = if (partners.isEmpty()) TextView.VISIBLE else TextView.GONE }
}