package com.example.studentexpensetrackerandroid

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

class GroupDashboardActivity : AppCompatActivity() {
    private lateinit var group: JSONObject
    private lateinit var activityContainer: LinearLayout
    private var myId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_dashboard)
        group = JSONObject(intent.getStringExtra("group") ?: "{}")
        activityContainer = findViewById(R.id.groupActivityContainer)
        findViewById<TextView>(R.id.groupTitleText).text = group.optString("name")
        findViewById<TextView>(R.id.groupMembersText).text = "${group.optJSONArray("members")?.length() ?: 0} members"
        findViewById<MaterialButton>(R.id.groupDashboardBackButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.addGroupBillButton).setOnClickListener { startActivity(Intent(this, AddGroupBillActivity::class.java).putExtra("group", group.toString())) }
        findViewById<MaterialButton>(R.id.settleUpButton).setOnClickListener { startActivity(Intent(this, SettlementActivity::class.java).putExtra("group", group.toString())) }
        loadProfileAndBalance()
    }

    override fun onResume() { super.onResume(); loadActivity() }

    private fun token() = SessionManager(this).token ?: ""

    private fun loadProfileAndBalance() {
        ApiClient.getProfile(token()) { result -> runOnUiThread {
            result.onSuccess { profile -> myId = profile.optString("id"); showBalance() }
        } }
    }

    private fun loadActivity() {
        if (token().isBlank()) return
        activityContainer.removeAllViews()
        loadBills()
        loadSettlements()
    }

    private fun loadBills() {
        ApiClient.getGroupExpenses(token(), group.optString("id")) { result -> runOnUiThread {
            result.onSuccess { bills ->
                if (bills.length() == 0) activityContainer.addView(messageRow("No bills yet. Add the first group bill."))
                for (i in 0 until bills.length()) {
                    val bill = bills.getJSONObject(i)
                    val title = bill.optString("description").ifBlank { bill.optString("category") }
                    val detail = "₹${bill.optDouble("amount")} • split between ${bill.optJSONArray("participants")?.length() ?: 0} people"
                    activityContainer.addView(activityCard(title, detail))
                }
            }.onFailure { activityContainer.addView(messageRow(it.message ?: "Could not load bills.")) }
        } }
    }

    private fun loadSettlements() {
        ApiClient.getSettlements(token()) { result -> runOnUiThread {
            result.onSuccess { settlements ->
                for (i in 0 until settlements.length()) {
                    val item = settlements.getJSONObject(i)
                    if (item.optString("group_id") == group.optString("id")) addSettlement(item)
                }
            }
        } }
    }

    private fun addSettlement(item: JSONObject) {
        val card = activityCard("Settlement ₹${item.optDouble("amount")}", item.optString("status").replace('_', ' '))
        val content = card.getChildAt(0) as LinearLayout
        if (item.optString("status") == "pending_approval" && item.optString("payee_id") == myId) {
            content.addView(MaterialButton(this).apply {
                text = "Confirm Receipt"
                setOnClickListener { ApiClient.updateSettlement(token(), item.optString("id"), "completed") { runOnUiThread { loadActivity() } } }
            })
            content.addView(MaterialButton(this).apply {
                text = "Dispute Payment"
                setOnClickListener { ApiClient.updateSettlement(token(), item.optString("id"), "disputed") { runOnUiThread { loadActivity() } } }
            })
        }
        activityContainer.addView(card)
    }

    private fun showBalance() {
        if (myId.isBlank()) return
        val balances = group.optJSONObject("balances") ?: return
        var owed = 0.0
        var owes = 0.0
        balances.optJSONObject(myId)?.let { creditors ->
            val keys = creditors.keys()
            while (keys.hasNext()) owes += creditors.optDouble(keys.next())
        }
        val debtors = balances.keys()
        while (debtors.hasNext()) {
            val debtor = debtors.next()
            if (debtor != myId) owed += balances.optJSONObject(debtor)?.optDouble(myId) ?: 0.0
        }
        findViewById<TextView>(R.id.groupBalanceText).text = "You are owed ₹%.2f  •  You owe ₹%.2f".format(owed, owes)
    }

    private fun activityCard(title: String, detail: String) = com.google.android.material.card.MaterialCardView(this).apply {
        radius = 18f; strokeWidth = 1; setStrokeColor(getColor(R.color.outline)); cardElevation = 0f
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
        addView(LinearLayout(this@GroupDashboardActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16)
            addView(TextView(this@GroupDashboardActivity).apply { text = title; textSize = 16f; setTextColor(getColor(R.color.text_primary)); setTypeface(typeface, 1) })
            addView(TextView(this@GroupDashboardActivity).apply { text = detail; textSize = 13f; setTextColor(getColor(R.color.text_secondary)); setPadding(0, 5, 0, 0) })
        })
    }

    private fun messageRow(message: String) = TextView(this).apply { text = message; setPadding(0, 18, 0, 18); setTextColor(getColor(R.color.text_secondary)) }
}
