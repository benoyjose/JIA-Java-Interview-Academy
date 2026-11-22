package com.jia.academy.app.auth

/** HTTP response failure with its status preserved for safe session recovery decisions. */
internal class AuthHttpException(
    val statusCode: Int,
    message: String
) : IllegalStateException(message)
