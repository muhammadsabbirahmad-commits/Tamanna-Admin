package com.tamanna.admin

import android.content.Context

object AdminBusinessContext {
    private const val PREFS = "admin_business_context"
    private const val KEY_ID = "business_id"
    private const val KEY_NAME = "business_name"
    const val DEFAULT_ID = "legacy-business"
    const val DEFAULT_NAME = "Tamanna Enterprise"

    fun getBusinessId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ID, DEFAULT_ID)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_ID

    fun getBusinessName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, DEFAULT_NAME)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_NAME

    fun save(context: Context, businessId: String, businessName: String): Boolean {
        val id = businessId.trim()
        val name = businessName.trim()
        if (id.isBlank() || name.isBlank()) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ID, id)
            .putString(KEY_NAME, name)
            .apply()
        return true
    }
}
