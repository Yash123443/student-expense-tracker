package com.example.studentexpensetrackerandroid

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AddTransactionActivity : AppCompatActivity() {
    private lateinit var titleInput: TextInputEditText
    private lateinit var amountInput: TextInputEditText
    private lateinit var categoryInput: TextInputEditText
    private lateinit var titleLayout: TextInputLayout
    private lateinit var amountLayout: TextInputLayout
    private lateinit var saveButton: MaterialButton
    private lateinit var progress: CircularProgressIndicator
    private lateinit var typeGroup: RadioGroup
    private lateinit var categoryLayout: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)
        titleInput = findViewById(R.id.transactionTitleInput)
        amountInput = findViewById(R.id.transactionAmountInput)
        categoryInput = findViewById(R.id.transactionCategoryInput)
        titleLayout = findViewById(R.id.transactionTitleLayout)
        amountLayout = findViewById(R.id.transactionAmountLayout)
        saveButton = findViewById(R.id.saveTransactionButton)
        progress = findViewById(R.id.transactionProgress)
        typeGroup = findViewById(R.id.transactionTypeGroup)
        categoryLayout = findViewById(R.id.transactionCategoryLayout)
        findViewById<MaterialButton>(R.id.transactionBackButton).setOnClickListener { finish() }
        saveButton.setOnClickListener { save() }
        typeGroup.setOnCheckedChangeListener { _, checkedId -> updateForm(checkedId == R.id.incomeRadio) }
    }

    private fun updateForm(isIncome: Boolean) {
        categoryLayout.visibility = if (isIncome) View.GONE else View.VISIBLE
        titleLayout.hint = if (isIncome) "Income source" else "Expense title"
        saveButton.text = if (isIncome) "Add Income" else "Add Expense"
        findViewById<android.widget.TextView>(R.id.transactionSubtitleText).text = if (isIncome) "Keep track of money coming in." else "Keep track of where your money goes."
    }

    private fun save() {
        val title = titleInput.text?.toString()?.trim().orEmpty()
        val amount = amountInput.text?.toString()?.toDoubleOrNull()
        titleLayout.error = if (title.isBlank()) "Enter a title or income source" else null
        amountLayout.error = if (amount != null && amount > 0) null else "Enter an amount greater than zero"
        if (titleLayout.error != null || amountLayout.error != null) return
        val token = SessionManager(this).token ?: return
        setLoading(true)
        val callback: (Result<Unit>) -> Unit = { result -> runOnUiThread {
            setLoading(false)
            result.onSuccess { finish() }.onFailure { amountLayout.error = it.message ?: "Could not save transaction." }
        } }
        if (typeGroup.checkedRadioButtonId == R.id.incomeRadio) {
            ApiClient.addIncome(token, title, amount!!, callback)
        } else {
            ApiClient.addPersonalExpense(token, title, amount!!, categoryInput.text?.toString()?.trim().orEmpty().ifBlank { "misc" }, callback)
        }
    }

    private fun setLoading(loading: Boolean) {
        saveButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
