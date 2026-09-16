package com.example.studentexpensetrackerandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.studentexpensetrackerandroid.data.ApiClient
import com.example.studentexpensetrackerandroid.data.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray

class CreateGroupActivity : AppCompatActivity() {
    private lateinit var friendsContainer: android.widget.LinearLayout
    private val selectedIds = mutableSetOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_create_group)
        friendsContainer = findViewById(R.id.friendsContainer)
        findViewById<MaterialButton>(R.id.createGroupBackButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.addFriendButton).setOnClickListener { addFriend() }
        findViewById<MaterialButton>(R.id.confirmCreateGroupButton).setOnClickListener { createGroup() }
    }
    override fun onResume() { super.onResume(); loadFriends() }
    private fun token() = SessionManager(this).token ?: ""
    private fun loadFriends() { ApiClient.getFriends(token()) { result -> runOnUiThread { friendsContainer.removeAllViews(); result.onSuccess { friends ->
        if (friends.length() == 0) friendsContainer.addView(android.widget.TextView(this).apply { text = "Add a friend by their registered email first."; setPadding(0, 12, 0, 12) })
        for (i in 0 until friends.length()) { val friend = friends.getJSONObject(i); val id = friend.optString("id"); friendsContainer.addView(android.widget.CheckBox(this).apply {
            text = "${friend.optString("name")}\n${friend.optString("email")}"; isChecked = selectedIds.contains(id)
            setOnCheckedChangeListener { _, checked -> if (checked) selectedIds.add(id) else selectedIds.remove(id) }
        }) }
    }.onFailure { friendsContainer.addView(android.widget.TextView(this).apply { text = it.message }) } } } }
    private fun addFriend() { val email = findViewById<TextInputEditText>(R.id.friendEmailInput).text?.toString()?.trim().orEmpty(); if (email.isBlank()) return
        ApiClient.searchUsers(token(), email) { result -> runOnUiThread { result.onSuccess { users ->
            if (users.length() == 0) toast("No user found with that email.") else { val user = users.getJSONObject(0); ApiClient.addFriend(token(), user.optString("id")) { added -> runOnUiThread { toast(if (added.isSuccess) "Friend added" else added.exceptionOrNull()?.message ?: "Could not add friend"); if (added.isSuccess) loadFriends() } } }
        }.onFailure { toast(it.message ?: "Search failed") } } }
    }
    private fun createGroup() { val name = findViewById<TextInputEditText>(R.id.groupNameInput).text?.toString()?.trim().orEmpty(); if (name.isBlank() || selectedIds.isEmpty()) { toast("Enter a group name and select at least one friend."); return }
        ApiClient.createGroup(token(), name, JSONArray(selectedIds.toList())) { result -> runOnUiThread { if (result.isSuccess) { toast("Group created"); finish() } else toast(result.exceptionOrNull()?.message ?: "Could not create group") } }
    }
    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
}
