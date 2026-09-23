package com.tamanna.admin

import android.content.Context
import android.content.Intent

object AdminSecurityGuard {
    fun isUnlocked(context: Context): Boolean {
        return context.getSharedPreferences("admin_security", Context.MODE_PRIVATE)
            .getBoolean("admin_unlocked", false)
    }

    fun requireUnlocked(activity: android.app.Activity): Boolean {
        if (isUnlocked(activity)) return true

        val intent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
        return false
    }
}
