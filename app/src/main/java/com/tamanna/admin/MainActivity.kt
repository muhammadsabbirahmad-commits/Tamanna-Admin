package com.tamanna.admin

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnMasterPin).setOnClickListener {
            Toast.makeText(this, "Master PIN module will be added next.", Toast.LENGTH_SHORT).show()
        }
    }
}
