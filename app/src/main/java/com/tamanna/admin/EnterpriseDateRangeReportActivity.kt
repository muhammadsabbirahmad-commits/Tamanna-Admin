package com.tamanna.admin

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EnterpriseDateRangeReportActivity : AppCompatActivity() {
    private lateinit var tvFrom: TextView
    private lateinit var tvTo: TextView
    private lateinit var tvSales: TextView
    private lateinit var tvPurchase: TextView
    private lateinit var tvProfit: TextView
    private lateinit var tvSalesQty: TextView
    private lateinit var tvPurchaseQty: TextView
    private lateinit var tvStatus: TextView
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminSecurityGuard.requireUnlocked(this)) return
        setContentView(R.layout.activity_enterprise_date_range_report)
        tvFrom = findViewById(R.id.tvReportFrom)
        tvTo = findViewById(R.id.tvReportTo)
        tvSales = findViewById(R.id.tvReportSales)
        tvPurchase = findViewById(R.id.tvReportPurchase)
        tvProfit = findViewById(R.id.tvReportProfit)
        tvSalesQty = findViewById(R.id.tvReportSalesQty)
        tvPurchaseQty = findViewById(R.id.tvReportPurchaseQty)
        tvStatus = findViewById(R.id.tvReportStatus)
        val today = format.format(Calendar.getInstance().time)
        tvFrom.text = today
        tvTo.text = today
        findViewById<Button>(R.id.btnReportFrom).setOnClickListener { pickDate(tvFrom) }
        findViewById<Button>(R.id.btnReportTo).setOnClickListener { pickDate(tvTo) }
        findViewById<Button>(R.id.btnLoadReport).setOnClickListener { loadReport() }
        loadReport()
    }

    override fun onResume() {
        super.onResume()
        if (!AdminSecurityGuard.requireUnlocked(this)) return
    }

    private fun pickDate(target: TextView) {
        val selected = runCatching { format.parse(target.text.toString()) }.getOrNull() ?: Calendar.getInstance().time
        val cal = Calendar.getInstance().apply { time = selected }
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            target.text = format.format(cal.time)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadReport() {
        val from = tvFrom.text.toString()
        val to = tvTo.text.toString()
        tvStatus.text = "Enterprise server report যাচাই হচ্ছে..."
        EnterpriseDateRangeReportReader.load(this, from, to) { success, report, message ->
            runOnUiThread {
                if (!success || report == null) { tvStatus.text = message; return@runOnUiThread }
                tvSales.text = "Sales\n৳ ${money(report.sales)}"
                tvPurchase.text = "Purchase\n৳ ${money(report.purchase)}"
                tvProfit.text = "Gross Profit\n৳ ${money(report.profit)}"
                tvSalesQty.text = "Sales Quantity\n${report.salesQuantity}"
                tvPurchaseQty.text = "Purchase Quantity\n${report.purchaseQuantity}"
                tvStatus.text = "${report.from} → ${report.to}\n$message"
            }
        }
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
}