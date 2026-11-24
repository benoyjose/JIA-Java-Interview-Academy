package com.jia.academy.app.assessment

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeAttemptSyncPolicyTest {
    @Test fun retriesTransientIoFailuresBelowRetryLimit() {
        assertTrue(PracticeAttemptSyncPolicy.shouldRetry(0, IOException("offline")))
        assertTrue(PracticeAttemptSyncPolicy.shouldRetry(7, IOException("timeout")))
    }

    @Test fun stopsRetryingAtRetryLimit() {
        assertFalse(PracticeAttemptSyncPolicy.shouldRetry(8, IOException("offline")))
    }

    @Test fun doesNotRetryNonIoFailures() {
        assertFalse(PracticeAttemptSyncPolicy.shouldRetry(0, IllegalArgumentException("invalid event")))
    }
}
