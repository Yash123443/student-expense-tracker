package com.example.studentexpensetrackerandroid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var signInButton: MaterialButton
    private lateinit var progress: CircularProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = SessionManager(this)
        AppCompatDelegate.setDefaultNightMode(
            if (session.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        if (SessionManager(this).token != null) {
            openDashboard()
            return
        }
        setContentView(R.layout.activity_main)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        signInButton = findViewById(R.id.signInButton)
        progress = findViewById(R.id.progress)
        signInButton.setOnClickListener { login() }
        findViewById<MaterialButton>(R.id.registerButton).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun login() {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()
        emailLayout.error = if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) null else "Enter a valid email address"
        passwordLayout.error = if (password.isNotBlank()) null else "Password is required"
        if (emailLayout.error != null || passwordLayout.error != null) return
        setLoading(true)
        ApiClient.login(email, password) { result ->
            runOnUiThread {
                setLoading(false)
                result.onSuccess { response ->
                    SessionManager(this).save(response.accessToken, response.userName)
                    openDashboard()
                }.onFailure { passwordLayout.error = it.message ?: "Unable to sign in." }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        signInButton.isEnabled = !loading
        progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun openDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
