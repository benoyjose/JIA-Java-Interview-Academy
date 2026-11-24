package com.jia.academy.app.assessment

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Uploads durable attempt events. A row is acknowledged only after upload returns;
 * on a transient failure, stop the batch so WorkManager retries with backoff.
 */
class PracticeAttemptSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dependencies = PracticeAttemptSyncDependencies.resolve(applicationContext)
            ?: return Result.retry()
        val (dao, uploader) = dependencies
        val events = dao.pending(PracticeAttemptSyncPolicy.batchSize)
        if (events.isEmpty()) return Result.success()

        for (event in events) {
            try {
                uploader.upload(event)
                dao.markSynced(event.eventId)
            } catch (cancelled: CancellationException) {
                // WorkManager cancellation is control flow, not a failed upload.
                // Preserve the queue row without incrementing its retry budget.
                throw cancelled
            } catch (failure: Exception) {
                dao.recordRetry(event.eventId)
                // Do not continue past a failed event: preserve ordering and avoid
                // repeatedly uploading the remaining batch during this execution.
                return if (PracticeAttemptSyncPolicy.shouldRetry(event.retryCount, failure)) {
                    Result.retry()
                } else {
                    // Keep the event durable for diagnostics/manual recovery. Never
                    // delete or silently acknowledge a permanently rejected attempt.
                    Result.failure()
                }
            }
        }
        return Result.success()
    }
}

object PracticeAttemptSyncDependencies {
    @Volatile private var provider: ((Context) -> Pair<PracticeAttemptDao, PracticeAttemptUploader>)? = null
    fun install(factory: (Context) -> Pair<PracticeAttemptDao, PracticeAttemptUploader>) {
        provider = factory
    }
    fun resolve(context: Context) = provider?.invoke(context)
}

fun interface PracticeAttemptUploader {
    /** Must use eventId as an idempotency key and throw when the server did not accept it. */
    suspend fun upload(event: PracticeAttemptEntity)
}
