package com.jia.academy.app.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorMessageTest {
    @Test
    fun readsSharedEnvelopeMessage() {
        assertEquals(
            "Authentication is required.",
            ApiErrorMessage.from(
                """{"code":"UNAUTHENTICATED","message":"Authentication is required."}""",
                "Request failed",
                401
            )
        )
    }

    @Test
    fun supportsLegacySpringDetail() {
        assertEquals(
            "Legacy problem detail",
            ApiErrorMessage.from("""{"detail":"Legacy problem detail"}""", "Request failed", 400)
        )
    }

    @Test
    fun fallsBackToFirstValidationMessage() {
        assertEquals(
            "The value is invalid.",
            ApiErrorMessage.from(
                """{"code":"VALIDATION_ERROR","errors":{"email":["The value is invalid."]}}""",
                "Request failed",
                422
            )
        )
    }

    @Test
    fun usesSafeFallbackForMalformedBody() {
        assertEquals(
            "Request failed (HTTP 503).",
            ApiErrorMessage.from("not-json", "Request failed", 503)
        )
    }
}
