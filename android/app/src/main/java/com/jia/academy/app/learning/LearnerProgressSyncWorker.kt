package com.jia.academy.app.learning

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jia.academy.app.auth.SecureSessionStore
import com.jia.academy.app.auth.SharedSessionRefreshCoordinator
import com.jia.academy.app.auth.AuthRepository
import com.jia.academy.app.auth.AuthHttpException
import com.jia.academy.app.auth.SessionValidationPolicy
import com.jia.academy.app.auth.SessionValidationAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class LearnerProgressSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    internal var httpTransport: ProgressSyncHttpTransport = UrlConnectionProgressSyncTransport()

    internal constructor(context: Context, parameters: WorkerParameters, transport: ProgressSyncHttpTransport) : this(context, parameters) {
        httpTransport = transport
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val baseUrl = inputData.getString(KEY_BASE_URL)?.trimEnd('/')
            ?.takeIf(String::isNotBlank) ?: return@withContext Result.failure()
        val sessionStore = SecureSessionStore(applicationContext)
        val store = OfflineLearningStore(applicationContext)
        store.recordSyncAttempt()
        try {
            val token = ensureAccessToken(baseUrl, sessionStore)
            downloadReleaseLessons(baseUrl, token, store)
            val (progressCount, bookmarkCount) = syncPendingEvents(baseUrl, token, store)
            store.recordSyncSuccess(progressCount, bookmarkCount)
            Result.success()
        } catch (error: IOException) {
            store.recordSyncFailure(error.message ?: "Network synchronization failed")
            Result.retry()
        } catch (error: IllegalStateException) {
            store.recordSyncFailure(error.message ?: "Synchronization was rejected")
            Result.failure()
        } catch (error: RuntimeException) {
            store.recordSyncFailure(error.message ?: "Synchronization failed")
            Result.retry()
        }
    }

    private fun downloadReleaseLessons(baseUrl: String, token: String, store: OfflineLearningStore) {
        val connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivity.isActiveNetworkMetered && !store.downloadsOnMeteredNetwork()) return
        val manifestResponse = request(baseUrl, "/content/releases/latest", null, "GET")
        if (manifestResponse.first == 404) return
        if (manifestResponse.first !in 200..299) throw IOException("Content manifest request failed")
        val manifest = OfflineReleaseManifest.parse(JSONObject(manifestResponse.second))
        for (item in manifest.items) {
            val slug = item.slug
            val checksum = item.checksum
            if (store.hasCachedLesson(slug, checksum)) continue
            val path = "/content/releases/${manifest.version}/lessons/" + URLEncoder.encode(slug, Charsets.UTF_8.name())
            val (status, response) = request(baseUrl, path, null, "GET")
            if (status !in 200..299) throw IOException("Lesson download failed: HTTP $status")
            val json = JSONObject(response)
            val lesson = com.jia.academy.app.catalogue.LessonDetail(
                json.getString("id"), json.getString("slug"), json.getString("title"),
                json.optString("summary").takeIf(String::isNotBlank),
                json.optString("difficulty", "Not specified"), json.optInt("contentRevision", 1),
                json.optString("sourceType", "Unknown"),
                json.optString("contentJson").takeIf(String::isNotBlank)
            )
            require(lesson.contentRevision == item.contentRevision) {
                "Downloaded lesson revision does not match release manifest"
            }
            store.cacheLesson(lesson, checksum)
        }
    }

    /**
     * Flushes durable progress and bookmark snapshots through the event ledger's atomic batch API.
     * UUIDs are derived from each persisted snapshot so a retry/restart sends the identical body.
     * No queue row is removed until the entire response has passed exact receipt validation.
     */
    private fun syncPendingEvents(baseUrl: String, token: String, store: OfflineLearningStore): Pair<Int, Int> {
        val pending = buildList {
            store.pendingProgress().forEach { add(ProgressBatchEventFactory.progress(it)) }
            store.pendingBookmarks().forEach { add(ProgressBatchEventFactory.bookmark(it)) }
        }
        var syncedProgress = 0
        var syncedBookmarks = 0
        for (batch in pending.chunked(ProgressBatchReceiptParser.MAX_BATCH_SIZE)) {
            val (status, responseBody) = request(
                baseUrl, "/me/progress-events/batch", token, "POST",
                ProgressBatchEventFactory.batchBody(batch)
            )
            when (ProgressBatchSyncResponsePolicy.classify(status)) {
                BatchSyncResponseAction.RETRY -> throw IOException("Progress event batch should be retried (HTTP $status)")
                BatchSyncResponseAction.REJECT -> throw IllegalStateException("Progress event batch rejected with HTTP $status")
                BatchSyncResponseAction.ACKNOWLEDGE -> Unit
            }

            // Parse first, acknowledge second. A malformed or partial response leaves all rows queued.
            val receipts = ProgressBatchReceiptParser.parse(responseBody, batch.map { it.eventId })
            val byId = batch.associateBy { it.eventId }
            for (receipt in receipts) {
                val acknowledged = byId.getValue(receipt.eventId)
                acknowledged.progressSnapshot?.let {
                    store.markProgressSynced(it)
                    syncedProgress++
                }
                acknowledged.bookmarkSnapshot?.let {
                    store.markBookmarkSynced(it)
                    syncedBookmarks++
                }
            }
        }
        return syncedProgress to syncedBookmarks
    }

    private suspend fun ensureAccessToken(baseUrl: String, store: SecureSessionStore): String {
        val accessToken = store.accessToken() ?: throw IllegalStateException("Sign in to synchronize learning activity")
        val auth = AuthRepository()
        try {
            auth.currentUser(baseUrl, accessToken)
            return accessToken
        } catch (error: AuthHttpException) {
            when (SessionValidationPolicy.classify(error.statusCode)) {
                SessionValidationAction.REFRESH -> Unit
                SessionValidationAction.RETRY -> throw IOException("Session validation temporarily unavailable", error)
                SessionValidationAction.FAIL -> throw IllegalStateException(error.message ?: "Session validation rejected", error)
            }
        }
        val refreshToken = store.refreshToken()
            ?: throw IllegalStateException("Your session expired. Sign in again to synchronize learning activity.")
        return SharedSessionRefreshCoordinator.instance.refreshIfUnchanged(
            rejectedAccessToken = accessToken,
            currentAccessToken = { store.accessToken() },
            refreshAndPersist = {
                val session = auth.refresh(baseUrl, refreshToken)
                val userJson = JSONObject()
                    .put("id", session.account.id)
                    .put("name", session.account.name)
                    .put("email", session.account.email)
                    .put("roles", org.json.JSONArray(session.account.roles))
                    .toString()
                store.save(session.accessToken, session.refreshToken, session.expiresAt, userJson)
                session.accessToken
            }
        )
    }

    internal fun request(
        baseUrl: String, path: String, token: String?, method: String, body: JSONObject? = null
    ): Pair<Int, String> {
        val response = httpTransport.execute(baseUrl + "/api/v1" + path, method, token, body?.toString())
        return response.status to response.body
    }

    companion object {
        const val KEY_BASE_URL = "baseUrl"
        const val UNIQUE_WORK_NAME = "jia-learner-progress-sync"
    }
}

object LearnerSyncScheduler {
    fun enqueue(context: Context, baseUrl: String) {
        val request = OneTimeWorkRequestBuilder<LearnerProgressSyncWorker>()
            .setInputData(Data.Builder().putString(LearnerProgressSyncWorker.KEY_BASE_URL, baseUrl).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            LearnerProgressSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
