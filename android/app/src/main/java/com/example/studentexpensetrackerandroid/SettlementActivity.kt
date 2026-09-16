package com.example.studentexpensetrackerandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject

class SettlementActivity : AppCompatActivity() {
    private lateinit var group: JSONObject
    private var myId = ""
    private var selectedPeerId = ""
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_settlement)
        group = JSONObject(intent.getStringExtra("group") ?: "{}")
        findViewById<android.widget.TextView>(R.id.settlementGroupNameText).text = "Settle up in ${group.optString("name")}" 
        findViewById<MaterialButton>(R.id.settlementBackButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.sendSettlementButton).setOnClickListener { sendSettlement() }
        findViewById<android.widget.RadioGroup>(R.id.paymentMethodGroup).setOnCheckedChangeListener { _, id -> findViewById<TextInputLayout>(R.id.utrLayout).visibility = if (id == R.id.upiMethodRadio) android.view.View.VISIBLE else android.view.View.GONE }
        findViewById<TextInputLayout>(R.id.utrLayout).visibility = android.view.View.GONE
        loadMembers()
    }
    private fun loadMembers() { ApiClient.getProfile(SessionManager(this).token ?: return) { result -> runOnUiThread { result.onSuccess { profile ->
        myId = profile.optString("id"); val radioGroup = findViewById<android.widget.RadioGroup>(R.id.peerRadioGroup); val members = group.optJSONArray("members")
        for (i in 0 until (members?.length() ?: 0)) { val id = members?.optString(i).orEmpty(); if (id != myId) radioGroup.addView(android.widget.RadioButton(this).apply { text = memberName(id); tag = id; setOnCheckedChangeListener { button, checked -> if (checked) selectedPeerId = button.tag as String } }) }
    } } } }
    private fun sendSettlement() { val amount = findViewById<TextInputEditText>(R.id.settlementAmountInput).text?.toString()?.toDoubleOrNull(); if (selectedPeerId.isBlank() || amount == null || amount <= 0) { android.widget.Toast.makeText(this, "Select a member and enter amount.", android.widget.Toast.LENGTH_LONG).show(); return }
        val method = if (findViewById<android.widget.RadioGroup>(R.id.paymentMethodGroup).checkedRadioButtonId == R.id.upiMethodRadio) "upi" else "cash"
        val utr = findViewById<TextInputEditText>(R.id.utrInput).text?.toString()?.trim().orEmpty()
        if (method == "upi" && utr.isBlank()) { android.widget.Toast.makeText(this, "Enter the UPI reference / UTR.", android.widget.Toast.LENGTH_LONG).show(); return }
        val body = JSONObject().put("peerId", selectedPeerId).put("amount", amount).put("paymentMethod", method).put("utr", utr).put("groupId", group.optString("id"))
        ApiClient.createSettlement(SessionManager(this).token ?: return, body) { result -> runOnUiThread { if (result.isSuccess) { android.widget.Toast.makeText(this, "Settlement sent for approval", android.widget.Toast.LENGTH_LONG).show(); finish() } else android.widget.Toast.makeText(this, result.exceptionOrNull()?.message, android.widget.Toast.LENGTH_LONG).show() } }
    }
    private fun memberName(id: String): String { val details = group.optJSONArray("member_details"); for (i in 0 until (details?.length() ?: 0)) { val person = details?.optJSONObject(i); if (person?.optString("id") == id) return person.optString("name", "Group member") }; return "Group member" }
}
