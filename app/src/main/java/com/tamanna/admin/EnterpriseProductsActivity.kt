package com.tamanna.admin

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class EnterpriseProductsActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminSecurityGuard.requireUnlocked(this)) return
        setContentView(R.layout.activity_enterprise_products)
        status = findViewById(R.id.tvProductStatus)
        list = findViewById(R.id.lvEnterpriseProducts)
        findViewById<Button>(R.id.btnRefreshProducts).setOnClickListener { loadProducts() }
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        if (!AdminSecurityGuard.requireUnlocked(this)) return
    }

    private fun loadProducts() {
        status.text = "Enterprise Product Data: যাচাই হচ্ছে..."
        EnterpriseProductReader.load(this) { success, products, message ->
            runOnUiThread {
                status.text = if (success) message else "Error: " + message
                val rows = products.map {
                    it.code + "  •  " + it.name + "\nক্রয়: " + it.purchasePrice + " | বিক্রয়: " + it.salePrice + " | Stock: " + it.stockQuantity
                }
                list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
            }
        }
    }
}
