package com.example.studentexpensetrackerandroid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton

class GroupsActivity : AppCompatActivity() {
    private lateinit var groupsContainer: android.widget.LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_groups)
        groupsContainer = findViewById(R.id.groupsContainer)
        findViewById<MaterialButton>(R.id.groupsBackButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.createGroupButton).setOnClickListener { startActivity(Intent(this, CreateGroupActivity::class.java)) }
    }
    override fun onResume() { super.onResume(); loadGroups() }
    private fun loadGroups() { ApiClient.getGroups(SessionManager(this).token ?: return) { result -> runOnUiThread {
        groupsContainer.removeAllViews()
        result.onSuccess { groups ->
            if (groups.length() == 0) groupsContainer.addView(android.widget.TextView(this).apply { text = "No groups yet. Create one to start sharing expenses."; setPadding(0, 24, 0, 24) })
            for (i in 0 until groups.length()) { val group = groups.getJSONObject(i); groupsContainer.addView(groupCard(group)) }
        }.onFailure { groupsContainer.addView(android.widget.TextView(this).apply { text = it.message }) }
    } } }

    private fun groupCard(group: org.json.JSONObject): com.google.android.material.card.MaterialCardView {
        val people = group.optJSONArray("member_details")
        val names = (0 until (people?.length() ?: 0)).joinToString(", ") { index -> people?.optJSONObject(index)?.optString("name").orEmpty() }
        return com.google.android.material.card.MaterialCardView(this).apply {
            radius = 20f; strokeWidth = 1; setStrokeColor(getColor(R.color.outline)); cardElevation = 0f
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 }
            addView(android.widget.LinearLayout(this@GroupsActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL; setPadding(20, 18, 20, 18)
                addView(android.widget.TextView(this@GroupsActivity).apply { text = group.optString("name"); textSize = 18f; setTextColor(getColor(R.color.text_primary)); setTypeface(typeface, 1) })
                addView(android.widget.TextView(this@GroupsActivity).apply { text = "${group.optJSONArray("members")?.length() ?: 0} members${if (names.isBlank()) "" else " • $names"}"; textSize = 13f; setTextColor(getColor(R.color.text_secondary)); setPadding(0, 5, 0, 0) })
            })
            setOnClickListener { startActivity(Intent(this@GroupsActivity, GroupDashboardActivity::class.java).putExtra("group", group.toString())) }
        }
    }
}
