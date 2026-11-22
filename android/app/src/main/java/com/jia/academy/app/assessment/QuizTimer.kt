package com.jia.academy.app.assessment

import java.time.Duration
import java.time.Instant

internal object QuizTimer {
    fun remainingSeconds(deadline: Instant, now: Instant): Int =
        Duration.between(now, deadline).seconds.coerceAtLeast(0).toInt()

    fun isExpired(deadline: Instant, now: Instant): Boolean = !now.isBefore(deadline)
}