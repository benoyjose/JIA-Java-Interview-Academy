package com.jia.academy.app.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionValidationPolicyTest {
    @Test
    fun refreshesOnlyForUnauthorizedResponses() {
        assertEquals(SessionValidationAction.REFRESH, SessionValidationPolicy.classify(401))
        assertEquals(SessionValidationAction.FAIL, SessionValidationPolicy.classify(403))
        assertEquals(SessionValidationAction.FAIL, SessionValidationPolicy.classify(404))
    }

    @Test
    fun retriesTransientAndRateLimitedResponses() {
        assertEquals(SessionValidationAction.RETRY, SessionValidationPolicy.classify(408))
        assertEquals(SessionValidationAction.RETRY, SessionValidationPolicy.classify(429))
        assertEquals(SessionValidationAction.RETRY, SessionValidationPolicy.classify(500))
        assertEquals(SessionValidationAction.RETRY, SessionValidationPolicy.classify(503))
    }

    @Test
    fun doesNotTreatUnexpectedSuccessStatusAsRefresh() {
        assertEquals(SessionValidationAction.FAIL, SessionValidationPolicy.classify(204))
    }
}
