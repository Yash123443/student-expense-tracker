package com.example.studentexpensetrackerandroid

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class BudgetActivity : AppCompatActivity() {
    private lateinit var limitInput: TextInputEditText
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_budget)
        limitInput = findViewById(R.id.budgetLimitInput)
        findViewById<MaterialButton>(R.id.budgetBackButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.saveBudgetButton).setOnClickListener { save() }
    }
    override fun onResume() { super.onResume(); load() }
    private fun load() { val token = SessionManager(this).token ?: return; ApiClient.getBudget(token) { result -> runOnUiThread { result.onSuccess {
        limitInput.setText(it.optDouble("limit").takeIf { value -> value > 0 }?.toString().orEmpty())
        findViewById<android.widget.TextView>(R.id.budgetSpentText).text = "₹%.2f spent this month".format(it.optDouble("spent"))
    }.onFailure { findViewById<android.widget.TextView>(R.id.budgetSpentText).text = it.message } } } }
    private fun save() { val amount = limitInput.text?.toString()?.toDoubleOrNull(); if (amount == null || amount < 0) { limitInput.error = "Enter a valid amount"; return }
        ApiClient.updateBudget(SessionManager(this).token ?: return, amount) { result -> runOnUiThread { result.onSuccess { load(); android.widget.Toast.makeText(this, "Budget saved", android.widget.Toast.LENGTH_SHORT).show() }.onFailure { limitInput.error = it.message } } }
    }
}
