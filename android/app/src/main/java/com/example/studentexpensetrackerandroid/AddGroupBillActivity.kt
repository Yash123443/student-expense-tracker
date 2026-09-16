package com.example.studentexpensetrackerandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject

class AddGroupBillActivity : AppCompatActivity() {
    private lateinit var group: JSONObject
    private val selectedMembers = mutableSetOf<String>()
    private var myId = ""

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_add_group_bill)
        group = JSONObject(intent.getStringExtra("group") ?: "{}")
        findViewById<android.widget.TextView>(R.id.addBillGroupNameText).text = group.optString("name")
        findViewById<MaterialButton>(R.id.addBillBackButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.saveGroupBillButton).setOnClickListener { saveBill() }
        loadMembers()
    }
    private fun loadMembers() { val token = SessionManager(this).token ?: return
        ApiClient.getProfile(token) { profileResult -> runOnUiThread { profileResult.onSuccess { profile ->
            myId = profile.optString("id"); val members = group.optJSONArray("members") ?: JSONArray(); val container = findViewById<android.widget.LinearLayout>(R.id.billMembersContainer)
            for (i in 0 until members.length()) { val id = members.optString(i); selectedMembers.add(id); container.addView(android.widget.CheckBox(this).apply { text = if (id == myId) "You" else memberName(id); isChecked = true; setOnCheckedChangeListener { _, checked -> if (checked) selectedMembers.add(id) else selectedMembers.remove(id) } }) }
        } } }
    }
    private fun saveBill() { val title = findViewById<TextInputEditText>(R.id.billTitleInput).text?.toString()?.trim().orEmpty(); val amount = findViewById<TextInputEditText>(R.id.billAmountInput).text?.toString()?.toDoubleOrNull(); val category = findViewById<TextInputEditText>(R.id.billCategoryInput).text?.toString()?.trim().orEmpty().ifBlank { "misc" }
        if (title.isBlank() || amount == null || amount <= 0 || selectedMembers.isEmpty() || myId.isBlank()) { android.widget.Toast.makeText(this, "Enter bill details and select participants.", android.widget.Toast.LENGTH_LONG).show(); return }
        val body = JSONObject().put("group_id", group.optString("id")).put("amount", amount).put("category", category).put("description", title).put("paid_by", myId).put("participants", JSONArray(selectedMembers.toList()))
        ApiClient.addGroupExpense(SessionManager(this).token ?: return, body) { result -> runOnUiThread { if (result.isSuccess) { android.widget.Toast.makeText(this, "Bill split successfully", android.widget.Toast.LENGTH_LONG).show(); finish() } else android.widget.Toast.makeText(this, result.exceptionOrNull()?.message, android.widget.Toast.LENGTH_LONG).show() } }
    }
    private fun memberName(id: String): String { val details = group.optJSONArray("member_details"); for (i in 0 until (details?.length() ?: 0)) { val person = details?.optJSONObject(i); if (person?.optString("id") == id) return person.optString("name", "Group member") }; return "Group member" }
}
