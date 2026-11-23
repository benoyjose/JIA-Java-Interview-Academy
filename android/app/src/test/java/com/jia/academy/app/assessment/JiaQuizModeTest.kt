package com.jia.academy.app.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JiaQuizModeTest {
    @Test
    fun timedMockAcceptsOnlyPublishedDurationChoicesAndFallsBackSafely() {
        assertEquals(listOf(900, 1800, 3600), JiaQuizMode.timedDurationOptionsSeconds)
        assertEquals(900, JiaQuizMode.durationSecondsFor(JiaQuizMode.TIMED_MOCK, 900))
        assertEquals(1800, JiaQuizMode.durationSecondsFor(JiaQuizMode.TIMED_MOCK, 17))
    }

    @Test
    fun nonTimedModesDoNotSendDurationToTheApi() {
        assertEquals(null, JiaQuizMode.durationSecondsFor(JiaQuizMode.PRACTICE, 3600))
        assertEquals(null, JiaQuizMode.durationSecondsFor(JiaQuizMode.DAILY_CHALLENGE, 1800))
    }

    @Test
    fun allImplementedModesAreAvailableToLearners() {
        assertEquals(listOf(JiaQuizMode.PRACTICE, JiaQuizMode.TIMED_MOCK, JiaQuizMode.DAILY_CHALLENGE, JiaQuizMode.WEAK_AREA, JiaQuizMode.TOPIC_ASSESSMENT, JiaQuizMode.MIXED_CHALLENGE, JiaQuizMode.RETRY_INCORRECT), JiaQuizMode.learnerOptions)
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
