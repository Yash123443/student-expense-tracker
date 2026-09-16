package com.example.studentexpensetrackerandroid.data

import android.content.Context

class SessionManager(context: Context) {
    private val preferences = context.getSharedPreferences("split_karo_session", Context.MODE_PRIVATE)
    val token: String? get() = preferences.getString("auth_token", null)
    val userName: String get() = preferences.getString("user_name", "there").orEmpty()
    val isDarkMode: Boolean get() = preferences.getBoolean("dark_mode", false)

    fun save(token: String, userName: String) {
        preferences.edit().putString("auth_token", token).putString("user_name", userName).apply()
    }

    fun setDarkMode(enabled: Boolean) = preferences.edit().putBoolean("dark_mode", enabled).apply()

    // Keep appearance preferences when the user signs out.
    fun clear() = preferences.edit()
        .remove("auth_token")
        .remove("user_name")
        .apply()
}
