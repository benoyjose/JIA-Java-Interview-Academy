package com.jia.academy.app.api

import org.json.JSONObject

/**
 * Reads the shared JIA error envelope while remaining compatible with legacy
 * Laravel (message) and Spring (detail) error bodies.
 */
internal object ApiErrorMessage {
    fun from(responseBody: String, fallback: String, status: Int): String {
        val parsed = runCatching {
            val json = JSONObject(responseBody)
            json.optString("message")
                .ifBlank { json.optString("detail") }
                .ifBlank { firstFieldError(json) }
        }.getOrNull()

        return parsed?.takeIf(String::isNotBlank) ?: "$fallback (HTTP $status)."
    }

    private fun firstFieldError(json: JSONObject): String {
        val errors = json.optJSONObject("errors") ?: return ""
        val keys = errors.keys()
        while (keys.hasNext()) {
            val messages = errors.optJSONArray(keys.next()) ?: continue
            if (messages.length() > 0) {
                return messages.optString(0).takeIf(String::isNotBlank).orEmpty()
            }
        }
        return ""
    }
}
