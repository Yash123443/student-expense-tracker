package com.example.studentexpensetrackerandroid.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class LoginResponse(val accessToken: String, val userName: String)
data class Balance(val net: Double, val credit: Double, val debt: Double)

object ApiClient {
    // Android Emulator reaches the development machine through 10.0.2.2.
    // For a physical phone, replace this with http://<computer-LAN-IP>:8000.
    private const val BASE_URL = "http://10.0.2.2:8000"
    private val executor = Executors.newSingleThreadExecutor()

    fun login(email: String, password: String, callback: (Result<LoginResponse>) -> Unit) =
        postObject("/api/auth/login/", null, JSONObject().put("email", email).put("password", password), callback) { json ->
            LoginResponse(json.getString("access_token"), json.getJSONObject("user").optString("name"))
        }

    fun register(name: String, email: String, password: String, callback: (Result<Unit>) -> Unit) =
        postObject("/api/auth/register/", null, JSONObject().put("name", name).put("email", email).put("password", password), callback) { Unit }

    fun getBalance(token: String, callback: (Result<Balance>) -> Unit) = getObject("/api/expenses/balance/", token, callback) {
        Balance(it.optDouble("netBalance"), it.optDouble("creditPosition"), it.optDouble("debtPosition"))
    }
    fun addPersonalExpense(token: String, title: String, amount: Double, category: String, callback: (Result<Unit>) -> Unit) =
        postObject("/api/expenses/personal/", token, JSONObject().put("title", title).put("amount", amount).put("category", category), callback) { Unit }
    fun addIncome(token: String, source: String, amount: Double, callback: (Result<Unit>) -> Unit) =
        postObject("/api/expenses/income/", token, JSONObject().put("source", source).put("amount", amount), callback) { Unit }

    fun getBudget(token: String, callback: (Result<JSONObject>) -> Unit) = getObject("/api/expenses/budgets/", token, callback) { it }
    fun updateBudget(token: String, limit: Double, callback: (Result<JSONObject>) -> Unit) = postObject("/api/expenses/budgets/", token, JSONObject().put("limit", limit), callback) { it }
    fun getInsights(token: String, callback: (Result<JSONObject>) -> Unit) = getObject("/api/expenses/insights/", token, callback) { it }

    fun getSubscriptions(token: String, callback: (Result<JSONArray>) -> Unit) = getArray("/api/expenses/subscriptions/", token, callback) { it }
    fun addSubscription(token: String, payload: JSONObject, callback: (Result<JSONObject>) -> Unit) = postObject("/api/expenses/subscriptions/", token, payload, callback) { it }
    fun deleteSubscription(token: String, id: String, callback: (Result<Unit>) -> Unit) = request("DELETE", "/api/expenses/subscriptions/$id/", token, null, callback) { Unit }

    fun getFriends(token: String, callback: (Result<JSONArray>) -> Unit) = getArray("/api/auth/friends/list/", token, callback) { it }
    fun searchUsers(token: String, email: String, callback: (Result<JSONArray>) -> Unit) = getArray("/api/auth/users/search/?email=${URLEncoder.encode(email, "UTF-8")}", token, callback) { it }
    fun addFriend(token: String, id: String, callback: (Result<Unit>) -> Unit) = postObject("/api/auth/friends/add/", token, JSONObject().put("friend_id", id), callback) { Unit }
    fun getGroups(token: String, callback: (Result<JSONArray>) -> Unit) = getArray("/api/expenses/groups/", token, callback) { it }
    fun getGroupExpenses(token: String, groupId: String, callback: (Result<JSONArray>) -> Unit) = getArray("/api/expenses/groups/$groupId/expenses/", token, callback) { it }
    fun createGroup(token: String, name: String, members: JSONArray, callback: (Result<JSONObject>) -> Unit) = postObject("/api/expenses/groups/create/", token, JSONObject().put("name", name).put("members", members), callback) { it }
    fun addGroupExpense(token: String, payload: JSONObject, callback: (Result<JSONObject>) -> Unit) = postObject("/api/expenses/expenses/add-group/", token, payload, callback) { it }

    fun getSettlements(token: String, callback: (Result<JSONArray>) -> Unit) = getArray("/api/settlements/", token, callback) { it }
    fun createSettlement(token: String, payload: JSONObject, callback: (Result<JSONObject>) -> Unit) = postObject("/api/settlements/", token, payload, callback) { it }
    fun updateSettlement(token: String, id: String, status: String, callback: (Result<JSONObject>) -> Unit) = request("PATCH", "/api/settlements/$id/", token, JSONObject().put("status", status), callback) { JSONObject(it) }

    fun getProfile(token: String, callback: (Result<JSONObject>) -> Unit) = getObject("/api/auth/profile/", token, callback) { it }
    fun updateProfile(token: String, upiId: String, callback: (Result<JSONObject>) -> Unit) = request("PATCH", "/api/auth/profile/", token, JSONObject().put("upi_id", upiId), callback) { JSONObject(it) }

    private fun <T> getObject(path: String, token: String?, callback: (Result<T>) -> Unit, mapper: (JSONObject) -> T) = request("GET", path, token, null, callback) { mapper(JSONObject(it)) }
    private fun <T> getArray(path: String, token: String?, callback: (Result<T>) -> Unit, mapper: (JSONArray) -> T) = request("GET", path, token, null, callback) { mapper(JSONArray(it)) }
    private fun <T> postObject(path: String, token: String?, body: JSONObject, callback: (Result<T>) -> Unit, mapper: (JSONObject) -> T) = request("POST", path, token, body, callback) { mapper(JSONObject(it)) }

    private fun <T> request(method: String, path: String, token: String?, body: JSONObject?, callback: (Result<T>) -> Unit, mapper: (String) -> T) {
        executor.execute { callback(runCatching {
            val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method; connectTimeout = 15_000; readTimeout = 15_000
                if (body != null) doOutput = true
                setRequestProperty("Accept", "application/json")
                if (body != null) setRequestProperty("Content-Type", "application/json")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { BufferedReader(it.reader()).use { reader -> reader.readText() } }.orEmpty()
            if (connection.responseCode !in 200..299) throw IllegalStateException(readError(text))
            mapper(text)
        }) }
    }

    private fun readError(text: String): String = runCatching { JSONObject(text).optString("detail").ifBlank { "Request failed." } }.getOrDefault("Request failed. Please try again.")
}
