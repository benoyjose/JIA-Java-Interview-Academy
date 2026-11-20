package com.jia.academy.app.auth

import com.jia.academy.app.api.ApiErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class JiaAccount(val id: String, val name: String, val email: String?, val roles: List<String>)
data class JiaSession(val accessToken: String, val refreshToken: String, val expiresAt: String, val account: JiaAccount)

class AuthRepository {
    suspend fun login(baseUrl: String, email: String, password: String): JiaSession =
        post(baseUrl, "/auth/login", JSONObject().put("email", email).put("password", password))

    suspend fun register(baseUrl: String, name: String, email: String, password: String): JiaSession =
        post(baseUrl, "/auth/register", JSONObject().put("name", name).put("email", email).put("password", password))

    suspend fun refresh(baseUrl: String, refreshToken: String): JiaSession =
        post(baseUrl, "/auth/refresh", JSONObject().put("refreshToken", refreshToken))


    suspend fun loginWithGoogle(baseUrl: String, idToken: String, nonce: String): JiaSession =
        post(baseUrl, "/auth/federated/google",
            JSONObject().put("idToken", idToken).put("nonce", nonce))

    suspend fun unlinkFederatedIdentity(baseUrl: String, accessToken: String, provider: String) =
        withContext(Dispatchers.IO) {
            val normalizedProvider = provider.lowercase(Locale.ROOT)
            require(normalizedProvider == "google" || normalizedProvider == "apple") {
                "Only Google or Apple identities can be unlinked."
            }
            val connection = (URL(url(baseUrl, "/auth/federated/link/$normalizedProvider"))
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    val response = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error(ApiErrorMessage.from(response, "Could not unlink sign-in method", status))
                }
            } finally { connection.disconnect() }
        }

    suspend fun requestPhoneOtp(baseUrl: String, phoneNumber: String): Unit = withContext(Dispatchers.IO) {
        val connection = (URL(url(baseUrl, "/auth/phone/otp")).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(JSONObject().put("phoneNumber", phoneNumber).toString())
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                error(ApiErrorMessage.from(response, "OTP request failed", status))
            }
        } finally { connection.disconnect() }
    }

    suspend fun verifyPhoneOtp(baseUrl: String, phoneNumber: String, code: String): JiaSession =
        post(baseUrl, "/auth/phone/verify", JSONObject().put("phoneNumber", phoneNumber).put("code", code))

    suspend fun requestPasswordReset(baseUrl: String, email: String) =
        postWithoutSession(baseUrl, "/auth/password/reset/request", JSONObject().put("email", email))

    suspend fun confirmPasswordReset(baseUrl: String, token: String, newPassword: String) =
        postWithoutSession(baseUrl, "/auth/password/reset/confirm",
            JSONObject().put("token", token).put("newPassword", newPassword))

    private suspend fun postWithoutSession(baseUrl: String, path: String, body: JSONObject) =
        withContext(Dispatchers.IO) {
            val connection = (URL(url(baseUrl, path)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    error(ApiErrorMessage.from(response, "Password recovery failed", status))
                }
            } finally { connection.disconnect() }
        }

    suspend fun currentUser(baseUrl: String, token: String): JiaAccount = withContext(Dispatchers.IO) {
        val connection = (URL(url(baseUrl, "/auth/me")).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error(ApiErrorMessage.from(response, "Session validation failed", status))
            val user = JSONObject(response)
            val rolesJson = user.optJSONArray("roles")
            val roles = (0 until (rolesJson?.length() ?: 0)).map { rolesJson!!.getString(it) }
            JiaAccount(user.getString("id"), user.getString("name"),
                user.optString("email").takeIf(String::isNotBlank), roles)
        } finally { connection.disconnect() }
    }

    suspend fun logout(baseUrl: String, token: String) = withContext(Dispatchers.IO) {
        val connection = (URL(url(baseUrl, "/auth/logout")).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val response = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(ApiErrorMessage.from(response, "Sign out failed", status))
            }
        } finally { connection.disconnect() }
    }

    private suspend fun post(baseUrl: String, path: String, body: JSONObject): JiaSession = withContext(Dispatchers.IO) {
        val connection = (URL(url(baseUrl, path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                error(ApiErrorMessage.from(response, "Sign in failed", status))
            }
            val json = JSONObject(response)
            val user = json.getJSONObject("user")
            val rolesJson = user.optJSONArray("roles")
            val roles = (0 until (rolesJson?.length() ?: 0)).map { rolesJson!!.getString(it) }
            JiaSession(
                json.getString("accessToken"),
                json.getString("refreshToken"),
                json.getString("expiresAt"),
                JiaAccount(user.getString("id"), user.getString("name"), user.optString("email").takeIf(String::isNotBlank), roles)
            )
        } finally { connection.disconnect() }
    }

    private fun url(baseUrl: String, path: String): String {
        require(baseUrl.isNotBlank()) { "Configure JIA_API_BASE_URL in ~/.gradle/gradle.properties." }
        return baseUrl.trimEnd('/') + "/api/v1" + path
    }
}
