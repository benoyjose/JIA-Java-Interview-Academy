package com.jia.academy.app.auth

internal enum class SessionValidationAction { REFRESH, RETRY, FAIL }

internal object SessionValidationPolicy {
    fun classify(statusCode: Int): SessionValidationAction = when {
        statusCode == 401 -> SessionValidationAction.REFRESH
        statusCode == 408 || statusCode == 429 || statusCode >= 500 ->
            SessionValidationAction.RETRY
        else -> SessionValidationAction.FAIL
    }
}
