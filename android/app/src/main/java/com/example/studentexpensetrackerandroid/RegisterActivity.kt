package com.example.studentexpensetrackerandroid

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RegisterActivity : AppCompatActivity() {
    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmInput: TextInputEditText
    private lateinit var createButton: MaterialButton
    private lateinit var progress: CircularProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        nameLayout = findViewById(R.id.nameLayout)
        emailLayout = findViewById(R.id.registerEmailLayout)
        passwordLayout = findViewById(R.id.registerPasswordLayout)
        confirmLayout = findViewById(R.id.confirmPasswordLayout)
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.registerEmailInput)
        passwordInput = findViewById(R.id.registerPasswordInput)
        confirmInput = findViewById(R.id.confirmPasswordInput)
        createButton = findViewById(R.id.createAccountButton)
        progress = findViewById(R.id.registerProgress)
        findViewById<MaterialButton>(R.id.backButton).setOnClickListener { finish() }
        createButton.setOnClickListener { register() }
    }

    private fun register() {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()
        val confirmation = confirmInput.text?.toString().orEmpty()
        nameLayout.error = if (name.length >= 2) null else "Full name must be at least 2 characters"
        emailLayout.error = if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) null else "Enter a valid email address"
        passwordLayout.error = if (password.length >= 8) null else "Password must be at least 8 characters"
        confirmLayout.error = if (confirmation == password && confirmation.isNotEmpty()) null else "Passwords do not match"
        if (listOf(nameLayout, emailLayout, passwordLayout, confirmLayout).any { it.error != null }) return

        setLoading(true)
        ApiClient.register(name, email, password) { result ->
            runOnUiThread {
                setLoading(false)
                result.onSuccess {
                    android.widget.Toast.makeText(this, "Account created. Please sign in.", android.widget.Toast.LENGTH_LONG).show()
                    finish()
                }.onFailure { emailLayout.error = it.message ?: "Could not create account." }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        createButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
