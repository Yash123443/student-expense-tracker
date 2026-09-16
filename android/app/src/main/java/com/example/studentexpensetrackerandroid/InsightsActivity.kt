package com.example.studentexpensetrackerandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton

class InsightsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_insights)
        findViewById<MaterialButton>(R.id.insightsBackButton).setOnClickListener { finish() }
    }
    override fun onResume() { super.onResume(); load() }
    private fun load() { ApiClient.getInsights(SessionManager(this).token ?: return) { result -> runOnUiThread { result.onSuccess { data ->
        findViewById<android.widget.TextView>(R.id.totalSpentText).text = "₹%.2f".format(data.optDouble("totalSpent"))
        val container = findViewById<android.widget.LinearLayout>(R.id.categoryContainer); container.removeAllViews(); val categories = data.optJSONArray("categories")
        if (categories == null || categories.length() == 0) container.addView(android.widget.TextView(this).apply { text = "No expenses recorded this month. Add an expense to see your spending pattern."; setPadding(0, 24, 0, 0); setTextColor(getColor(R.color.text_secondary)) })
        else for (i in 0 until categories.length()) { val item = categories.getJSONObject(i); container.addView(categoryCard(item.optString("name"), item.optDouble("spent"), item.optDouble("total"))) }
    }.onFailure { findViewById<android.widget.TextView>(R.id.insightsError).text = it.message } } } }
    private fun categoryCard(name: String, spent: Double, total: Double): com.google.android.material.card.MaterialCardView = com.google.android.material.card.MaterialCardView(this).apply {
        radius = 18f; strokeWidth = 1; setStrokeColor(getColor(R.color.outline)); cardElevation = 0f
        layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
        addView(android.widget.LinearLayout(this@InsightsActivity).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(18, 16, 18, 16)
            addView(android.widget.TextView(this@InsightsActivity).apply { text = name; textSize = 16f; setTextColor(getColor(R.color.text_primary)); setTypeface(typeface, 1) })
            addView(android.widget.TextView(this@InsightsActivity).apply { text = "₹%.2f spent".format(spent); textSize = 14f; setTextColor(getColor(R.color.text_secondary)); setPadding(0, 5, 0, 8) })
            addView(android.widget.ProgressBar(this@InsightsActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = if (total > 0) ((spent / total) * 100).toInt().coerceIn(0, 100) else 0 })
        })
    }
}
