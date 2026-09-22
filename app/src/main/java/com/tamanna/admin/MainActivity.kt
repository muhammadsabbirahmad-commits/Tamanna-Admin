package com.tamanna.admin

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    companion object {
        private const val MASTER_PIN_HASH =
            "70e44e5698e141bb8ce716b771cc9029d8d01672881f6d685b14b5dcadc5af29"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pinInput = findViewById<EditText>(R.id.etMasterPin)
        findViewById<Button>(R.id.btnMasterPin).setOnClickListener {
            if (sha256(pinInput.text.toString()) == MASTER_PIN_HASH) {
                Toast.makeText(this, "Master PIN verified.", Toast.LENGTH_SHORT).show()
            } else {
                pinInput.text.clear()
                Toast.makeText(this, "ভুল Master PIN। আবার চেষ্টা করুন।", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
