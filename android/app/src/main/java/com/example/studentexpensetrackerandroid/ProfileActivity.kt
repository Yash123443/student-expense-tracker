package com.example.studentexpensetrackerandroid

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class ProfileActivity : AppCompatActivity() {
    private lateinit var nameText: android.widget.TextView
    private lateinit var emailText: android.widget.TextView
    private lateinit var initialsText: android.widget.TextView
    private lateinit var upiInput: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var progress: CircularProgressIndicator
    private lateinit var darkModeSwitch: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        nameText = findViewById(R.id.profileNameText)
        emailText = findViewById(R.id.profileEmailText)
        initialsText = findViewById(R.id.profileInitialsText)
        upiInput = findViewById(R.id.profileUpiInput)
        saveButton = findViewById(R.id.saveProfileButton)
        progress = findViewById(R.id.profileProgress)
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        val session = SessionManager(this)
        darkModeSwitch.isChecked = session.isDarkMode
        darkModeSwitch.setOnCheckedChangeListener { _, enabled ->
            session.setDarkMode(enabled)
            AppCompatDelegate.setDefaultNightMode(
                if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        findViewById<MaterialButton>(R.id.profileBackButton).setOnClickListener { finish() }
        saveButton.setOnClickListener { saveProfile() }
        findViewById<MaterialButton>(R.id.signOutButton).setOnClickListener { signOut() }
    }

    override fun onResume() { super.onResume(); loadProfile() }

    private fun loadProfile() {
        ApiClient.getProfile(SessionManager(this).token ?: return) { result -> runOnUiThread {
            result.onSuccess { profile ->
                val name = profile.optString("name", "SplitKaro User")
                nameText.text = name
                emailText.text = profile.optString("email")
                initialsText.text = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "U" }
                upiInput.setText(profile.optString("upi_id"))
            }.onFailure { showError(it.message ?: "Unable to load profile.") }
        } }
    }

    private fun saveProfile() {
        setLoading(true)
        ApiClient.updateProfile(SessionManager(this).token ?: return, upiInput.text?.toString()?.trim().orEmpty()) { result -> runOnUiThread {
            setLoading(false)
            result.onSuccess { android.widget.Toast.makeText(this, "Profile updated", android.widget.Toast.LENGTH_SHORT).show() }
                .onFailure { showError(it.message ?: "Unable to update profile.") }
        } }
    }

    private fun signOut() {
        SessionManager(this).clear()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAffinity()
    }

    private fun setLoading(loading: Boolean) { saveButton.isEnabled = !loading; progress.visibility = if (loading) View.VISIBLE else View.GONE }
    private fun showError(message: String) { findViewById<android.widget.TextView>(R.id.profileErrorText).text = message }
}
