package com.jia.academy.app.assessment

import java.time.Duration
import java.time.Instant

internal object QuizTimer {
    /** Round up for display so the countdown never shows 0 before the deadline. */
    fun remainingSeconds(deadline: Instant, now: Instant): Int {
        val remaining = Duration.between(now, deadline)
        if (remaining.isZero || remaining.isNegative) return 0
        val roundedSeconds = remaining.seconds + if (remaining.nano > 0) 1 else 0
        return roundedSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun isExpired(deadline: Instant, now: Instant): Boolean = !now.isBefore(deadline)
}
