package com.tamanna.admin

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FinanceProfitActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finance_profit)

        findViewById<Button>(R.id.btnRefreshFinance).setOnClickListener {
            findViewById<TextView>(R.id.tvFinanceStatus).text = "Finance summary refreshed"
        }
    }
}
