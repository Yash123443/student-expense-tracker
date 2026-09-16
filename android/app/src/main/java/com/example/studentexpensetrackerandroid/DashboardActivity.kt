package com.example.studentexpensetrackerandroid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        val name = SessionManager(this).userName
        findViewById<android.widget.TextView>(R.id.welcomeText).text = "Hello, ${name.substringBefore(" ")}"
        findViewById<android.widget.TextView>(R.id.avatarText).text = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "U" }
        findViewById<MaterialButton>(R.id.addTransactionButton).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.budgetButton).setOnClickListener { startActivity(Intent(this, BudgetActivity::class.java)) }
        findViewById<MaterialButton>(R.id.insightsButton).setOnClickListener { startActivity(Intent(this, InsightsActivity::class.java)) }
        findViewById<MaterialButton>(R.id.subscriptionsButton).setOnClickListener { startActivity(Intent(this, SubscriptionsActivity::class.java)) }
        findViewById<MaterialButton>(R.id.groupsButton).setOnClickListener { startActivity(Intent(this, GroupsActivity::class.java)) }
        findViewById<android.view.View>(R.id.profileButton).setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        val token = SessionManager(this).token ?: return
        ApiClient.getBalance(token) { result -> runOnUiThread {
            result.onSuccess { balance ->
                findViewById<android.widget.TextView>(R.id.netBalanceText).text = "₹%.2f".format(balance.net)
                findViewById<android.widget.TextView>(R.id.creditText).text = "₹%.2f".format(balance.credit)
                findViewById<android.widget.TextView>(R.id.debtText).text = "₹%.2f".format(balance.debt)
            }.onFailure { findViewById<android.widget.TextView>(R.id.balanceStatus).text = "Could not refresh balance" }
        } }
    }
}
