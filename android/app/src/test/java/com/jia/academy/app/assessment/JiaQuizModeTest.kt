package com.jia.academy.app.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JiaQuizModeTest {
    @Test
    fun practiceTimedMockAndDailyChallengeAreAvailableToLearners() {
        assertEquals(listOf(JiaQuizMode.PRACTICE, JiaQuizMode.TIMED_MOCK, JiaQuizMode.DAILY_CHALLENGE), JiaQuizMode.learnerOptions)
    }

    @Test
    fun practiceIsTheDefaultModeAndUsesBackendContractValue() {
        assertEquals("PRACTICE", JiaQuizMode.PRACTICE.apiValue)
        assertEquals("Practice", JiaQuizMode.PRACTICE.displayName)
        assertTrue(JiaQuizMode.PRACTICE.availableToLearners)
    }

    @Test
    fun weakAreaRemainsUnavailable() {
        assertFalse(JiaQuizMode.WEAK_AREA.availableToLearners)
        assertFalse(JiaQuizMode.WEAK_AREA in JiaQuizMode.learnerOptions)
    }
}
