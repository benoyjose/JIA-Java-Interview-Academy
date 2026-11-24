package com.jia.academy.app.assessment

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeReviewGateTest {
    @Test fun lowAccuracyRequiresRetry() {
        assertEquals(ReviewAction.RETRY, PracticeReviewGate.decide(PracticeReviewInput(45, 70, 50)))
    }

    @Test fun uncertainOrTooFastAttemptRequiresReview() {
        assertEquals(ReviewAction.REVIEW, PracticeReviewGate.decide(PracticeReviewInput(90, 30, 50)))
        assertEquals(ReviewAction.REVIEW, PracticeReviewGate.decide(PracticeReviewInput(90, 80, 3, 10)))
    }

    @Test fun strongMeasuredAttemptAdvances() {
        assertEquals(ReviewAction.ADVANCE, PracticeReviewGate.decide(PracticeReviewInput(92, 85, 40, 10)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAccuracyIsRejected() {
        PracticeReviewGate.decide(PracticeReviewInput(120, 80, 20))
    }
}
