package com.tamanna.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    companion object {
        private const val MASTER_PIN_HASH = "70e44e5698e141bb8ce716b771cc9029d8d01672881f6d685b14b5dcadc5af29"
        private const val PREFS = "admin_security"
        private const val ADMIN_UNLOCKED = "admin_unlocked"
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 5 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lockUntil = prefs.getLong("lock_until", 0L)

        if (prefs.getBoolean(ADMIN_UNLOCKED, false) && now >= lockUntil) {
            openDashboard()
            return
        }

        setContentView(R.layout.activity_main)
        val pinInput = findViewById<EditText>(R.id.etMasterPin)
        val button = findViewById<Button>(R.id.btnMasterPin)

        if (now < lockUntil) {
            button.isEnabled = false
            Toast.makeText(this, "নিরাপত্তার কারণে সাময়িকভাবে লক আছে।", Toast.LENGTH_LONG).show()
        }

        button.setOnClickListener {
            val currentLock = prefs.getLong("lock_until", 0L)
            if (System.currentTimeMillis() < currentLock) return@setOnClickListener

            if (sha256(pinInput.text.toString()) == MASTER_PIN_HASH) {
                prefs.edit().putBoolean(ADMIN_UNLOCKED, true)
                    .putInt("failed_attempts", 0).putLong("lock_until", 0L).apply()
                openDashboard()
            } else {
                val attempts = prefs.getInt("failed_attempts", 0) + 1
                pinInput.text.clear()
                if (attempts >= MAX_ATTEMPTS) {
                    prefs.edit().putInt("failed_attempts", 0)
                        .putLong("lock_until", System.currentTimeMillis() + LOCKOUT_MS).apply()
                    button.isEnabled = false
                    Toast.makeText(this, "৫ বার ভুল PIN হয়েছে। ৫ মিনিটের জন্য লক।", Toast.LENGTH_LONG).show()
                } else {
                    prefs.edit().putInt("failed_attempts", attempts).apply()
                    Toast.makeText(this, "ভুল Master PIN। বাকি চেষ্টা: " + (MAX_ATTEMPTS - attempts), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
