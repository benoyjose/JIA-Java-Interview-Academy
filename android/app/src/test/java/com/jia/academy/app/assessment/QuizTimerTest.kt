package com.jia.academy.app.assessment

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizTimerTest {
    @Test fun remainingSecondsFloorsAndNeverGoesNegative() {
        val deadline = Instant.parse("2026-11-22T12:00:10Z")
        assertEquals(10, QuizTimer.remainingSeconds(deadline, Instant.parse("2026-11-22T12:00:00Z")))
        assertEquals(0, QuizTimer.remainingSeconds(deadline, Instant.parse("2026-11-22T12:00:11Z")))
    }
    @Test fun deadlineIsExpiredAtExactBoundary() {
        val deadline = Instant.parse("2026-11-22T12:00:00Z")
        assertTrue(QuizTimer.isExpired(deadline, deadline))
    }
}