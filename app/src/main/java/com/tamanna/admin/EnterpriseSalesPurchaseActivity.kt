package com.tamanna.admin

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class EnterpriseSalesPurchaseActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminSecurityGuard.requireUnlocked(this)) return
        setContentView(R.layout.activity_enterprise_sales_purchase)
        status = findViewById(R.id.tvSalesPurchaseStatus)
        list = findViewById(R.id.lvSalesPurchase)
        findViewById<Button>(R.id.btnRefreshSalesPurchase).setOnClickListener { loadData() }
        loadData()
    }

    override fun onResume() {
        super.onResume()
        if (!AdminSecurityGuard.requireUnlocked(this)) return
    }

    private fun loadData() {
        status.text = "Enterprise Sales & Purchase: যাচাই হচ্ছে..."
        EnterpriseSalesPurchaseReader.load(this) { success, sales, purchases, message ->
            runOnUiThread {
                status.text = if (success) message else "Error: $message"
                val rows = mutableListOf<String>()
                rows.add("SALES")
                rows.addAll(sales.map {
                    "${it.date} • ${it.productCode} • ${it.productName}\nQty: ${it.quantity} | Sale: ${it.salePrice} | Profit: ${it.profit}"
                })
                rows.add("")
                rows.add("PURCHASES")
                rows.addAll(purchases.map {
                    "${it.date} • ${it.productCode} • ${it.productName}\nQty: ${it.quantity} | Purchase: ${it.purchasePrice} | Supplier: ${it.supplier}"
                })
                list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
            }
        }
    }
}
