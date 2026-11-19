package com.jia.academy.app.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JiaQuizModeTest {
    @Test
    fun practiceIsTheOnlyModeAvailableToLearners() {
        assertEquals(listOf(JiaQuizMode.PRACTICE), JiaQuizMode.learnerOptions)
    }

    @Test
    fun practiceIsTheDefaultModeAndUsesBackendContractValue() {
        assertEquals("PRACTICE", JiaQuizMode.PRACTICE.apiValue)
        assertEquals("Practice", JiaQuizMode.PRACTICE.displayName)
        assertTrue(JiaQuizMode.PRACTICE.availableToLearners)
    }

    @Test
    fun unfinishedModesRemainUnavailable() {
        listOf(
            JiaQuizMode.TIMED_MOCK,
            JiaQuizMode.DAILY_CHALLENGE,
            JiaQuizMode.WEAK_AREA
        ).forEach { mode ->
            assertFalse(mode.availableToLearners)
            assertFalse(mode in JiaQuizMode.learnerOptions)
        }
    }
}
