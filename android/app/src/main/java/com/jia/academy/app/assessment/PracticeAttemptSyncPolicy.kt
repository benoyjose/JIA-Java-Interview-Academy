package com.jia.academy.app.assessment

import java.io.IOException

/**
 * WorkManager retries are exponential by scheduler configuration. Keep batch
 * sizes bounded and stop retrying a single event after repeated failures.
 * The journal row remains pending at the retry limit for explicit recovery.
 */
object PracticeAttemptSyncPolicy {
    const val batchSize: Int = 75
    const val maximumEventRetries: Int = 8

    fun shouldRetry(retryCount: Int, failure: Throwable): Boolean =
        failure is IOException && retryCount < maximumEventRetries
}
