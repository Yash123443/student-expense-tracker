package com.example.studentexpensetrackerandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject

class SubscriptionsActivity : AppCompatActivity() {
    private lateinit var list: android.widget.LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscriptions)
        list = findViewById(R.id.subscriptionList)
        findViewById<MaterialButton>(R.id.subBackButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.addSubscriptionButton).setOnClickListener { add() }
    }

    override fun onResume() { super.onResume(); load() }

    private fun load() {
        ApiClient.getSubscriptions(SessionManager(this).token ?: return) { result -> runOnUiThread {
            list.removeAllViews()
            result.onSuccess { items ->
                if (items.length() == 0) list.addView(android.widget.TextView(this).apply {
                    text = "No subscriptions yet. Add your first recurring bill above."
                    setTextColor(getColor(R.color.text_secondary)); setPadding(0, 24, 0, 0)
                })
                for (i in 0 until items.length()) list.addView(subscriptionCard(items.getJSONObject(i)))
            }.onFailure { list.addView(android.widget.TextView(this).apply { text = it.message; setTextColor(getColor(R.color.error)) }) }
        } }
    }

    private fun subscriptionCard(item: JSONObject): com.google.android.material.card.MaterialCardView = com.google.android.material.card.MaterialCardView(this).apply {
        radius = 18f; strokeWidth = 1; setStrokeColor(getColor(R.color.outline)); cardElevation = 0f
        layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
        addView(android.widget.LinearLayout(this@SubscriptionsActivity).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setPadding(18, 16, 18, 16)
            addView(android.widget.TextView(this@SubscriptionsActivity).apply { text = item.optString("name"); textSize = 17f; setTextColor(getColor(R.color.text_primary)); setTypeface(typeface, 1) })
            addView(android.widget.TextView(this@SubscriptionsActivity).apply {
                val split = item.optInt("splitCount", 1).coerceAtLeast(1)
                text = "₹${item.optDouble("cost")} / month • Your share ₹${item.optDouble("cost") / split}\nLong press to remove"
                textSize = 13f; setTextColor(getColor(R.color.text_secondary)); setPadding(0, 5, 0, 0)
            })
        })
        setOnLongClickListener {
            ApiClient.deleteSubscription(SessionManager(this@SubscriptionsActivity).token ?: return@setOnLongClickListener true, item.optString("id")) { runOnUiThread { load() } }
            true
        }
    }

    private fun add() {
        val name = findViewById<TextInputEditText>(R.id.subscriptionNameInput).text?.toString()?.trim().orEmpty()
        val cost = findViewById<TextInputEditText>(R.id.subscriptionCostInput).text?.toString()?.toDoubleOrNull()
        val split = findViewById<TextInputEditText>(R.id.subscriptionSplitInput).text?.toString()?.toIntOrNull() ?: 1
        if (name.isBlank() || cost == null || cost <= 0 || split < 1) { toast("Enter valid subscription details"); return }
        val body = JSONObject().put("name", name).put("cost", cost).put("splitCount", split).put("emoji", "card").put("active", true)
        ApiClient.addSubscription(SessionManager(this).token ?: return, body) { result -> runOnUiThread {
            result.onSuccess { findViewById<TextInputEditText>(R.id.subscriptionNameInput).setText(""); findViewById<TextInputEditText>(R.id.subscriptionCostInput).setText(""); load() }
                .onFailure { toast(it.message ?: "Could not add subscription") }
        } }
    }

    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
}
