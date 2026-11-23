package com.jia.academy.app.review

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SpacedReviewSessionTest {
    private val now = Instant.parse("2026-11-24T00:00:00Z")
    @Test fun answerMustBeRevealedBeforeGradeAndSessionAdvances() {
        val initial = ReviewSessionState(listOf(ReviewCard("1", "Q", "A", now)))
        try { initial.grade(4, now); fail("Expected answer gate") } catch (_: IllegalArgumentException) { }
        val done = initial.reveal().grade(4, now)
        assertTrue(done.isComplete)
        assertEquals(1, done.completed.size)
        assertEquals(now.plusSeconds(86400), done.completed.single().dueAt)
    }
    @Test fun invalidGradeIsRejected() {
        val state = ReviewSessionState(listOf(ReviewCard("1", "Q", "A", now))).reveal()
        try { state.grade(9, now); fail("Expected invalid grade") } catch (_: IllegalArgumentException) { }
    }
}
