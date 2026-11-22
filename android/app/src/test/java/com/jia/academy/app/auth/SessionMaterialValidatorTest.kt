package com.jia.academy.app.auth

import org.junit.Assert.assertThrows
import org.junit.Test

class SessionMaterialValidatorTest {
    @Test fun rejectsBlankAccessToken() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionMaterialValidator.validate("", "refresh", "expiry", "{\"id\":\"u\"}")
        }
    }
    @Test fun rejectsBlankRefreshTokenAndInvalidUserJson() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionMaterialValidator.validate("access", " ", "expiry", "{\"id\":\"u\"}")
        }
        assertThrows(org.json.JSONException::class.java) {
            SessionMaterialValidator.validate("access", "refresh", "expiry", "[]")
        }
    }
    @Test fun acceptsCompleteSessionMaterial() {
        SessionMaterialValidator.validate("access", "refresh", "expiry", "{\"id\":\"u\"}")
    }
}