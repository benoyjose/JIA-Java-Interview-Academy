package com.jia.academy.app.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JiaQuizModeTest {
    @Test
    fun allImplementedModesAreAvailableToLearners() {
        assertEquals(listOf(JiaQuizMode.PRACTICE, JiaQuizMode.TIMED_MOCK, JiaQuizMode.DAILY_CHALLENGE, JiaQuizMode.WEAK_AREA), JiaQuizMode.learnerOptions)
    }

    @Test
    fun practiceIsTheDefaultModeAndUsesBackendContractValue() {
        assertEquals("PRACTICE", JiaQuizMode.PRACTICE.apiValue)
        assertEquals("Practice", JiaQuizMode.PRACTICE.displayName)
        assertTrue(JiaQuizMode.PRACTICE.availableToLearners)
    }

    @Test
    fun weakAreaUsesBackendContractValue() {
        assertEquals("WEAK_AREA", JiaQuizMode.WEAK_AREA.apiValue)
        assertTrue(JiaQuizMode.WEAK_AREA.availableToLearners)
    }
}
