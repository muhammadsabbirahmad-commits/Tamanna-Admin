package com.tamanna.admin

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class BusinessOverviewActivity : AppCompatActivity() {
    private lateinit var tvProducts: TextView
    private lateinit var tvStock: TextView
    private lateinit var tvSales: TextView
    private lateinit var tvPurchase: TextView
    private lateinit var tvProfit: TextView
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminSecurityGuard.requireUnlocked(this)) return
        setContentView(R.layout.activity_business_overview)
        tvProducts = findViewById(R.id.tvOverviewProducts)
        tvStock = findViewById(R.id.tvOverviewStock)
        tvSales = findViewById(R.id.tvOverviewSales)
        tvPurchase = findViewById(R.id.tvOverviewPurchase)
        tvProfit = findViewById(R.id.tvOverviewProfit)
        tvStatus = findViewById(R.id.tvOverviewStatus)
        findViewById<Button>(R.id.btnRefreshOverview).setOnClickListener { loadOverview() }
        loadOverview()
    }

    override fun onResume() {
        super.onResume()
        if (!AdminSecurityGuard.requireUnlocked(this)) return
    }

    private fun loadOverview() {
        tvStatus.text = "Tamanna Enterprise server যাচাই হচ্ছে..."
        EnterpriseBusinessOverviewReader.load(this) { success, summary, message ->
            runOnUiThread {
                if (!success || summary == null) {
                    tvStatus.text = message
                    return@runOnUiThread
                }
                tvProducts.text = "Total Products\n${summary.totalProducts}"
                tvStock.text = "Current Stock\n${summary.currentStock}"
                tvSales.text = "Today's Sales\n৳ ${money(summary.todaySales)}"
                tvPurchase.text = "Today's Purchase\n৳ ${money(summary.todayPurchase)}"
                tvProfit.text = "Today's Profit\n৳ ${money(summary.todayProfit)}"
                tvStatus.text = message + "\nRead-only: Admin app থেকে Enterprise data পরিবর্তন করা হচ্ছে না."
            }
        }
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
}
