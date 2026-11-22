package com.jia.academy.app.lesson

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonLoadRequestGuardTest {
    @Test fun onlyLatestRouteRequestMayUpdateUiState() {
        val guard = LessonLoadRequestGuard()
        val first = guard.begin()
        val second = guard.begin()
        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))
    }

    @Test fun forceRefreshInvalidatesPreviousInFlightRequest() {
        val guard = LessonLoadRequestGuard()
        val initial = guard.begin()
        val refresh = guard.begin()
        assertFalse(guard.isCurrent(initial))
        assertTrue(guard.isCurrent(refresh))
    }
}
