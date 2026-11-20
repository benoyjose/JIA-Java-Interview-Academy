package com.jia.academy.app.learning

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jia.academy.app.auth.SecureSessionStore
import com.jia.academy.app.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LearnerProgressSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val baseUrl = inputData.getString(KEY_BASE_URL)?.trimEnd('/')
            ?.takeIf(String::isNotBlank) ?: return@withContext Result.failure()
        val sessionStore = SecureSessionStore(applicationContext)
        val store = OfflineLearningStore(applicationContext)
        store.recordSyncAttempt()
        try {
            val token = ensureAccessToken(baseUrl, sessionStore)
            downloadReleaseLessons(baseUrl, token, store)
            val progressCount = syncPendingProgress(baseUrl, token, store)
            val bookmarkCount = syncPendingBookmarks(baseUrl, token, store)
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
        val manifestResponse = request(baseUrl, "/content/releases/latest", null, "GET")
        if (manifestResponse.first == 404) return
        if (manifestResponse.first !in 200..299) throw IOException("Content manifest request failed")
        val manifest = JSONObject(manifestResponse.second)
        val version = manifest.getLong("version")
        val items = manifest.getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val slug = item.getString("slug")
            val checksum = item.getString("checksum")
            if (store.hasCachedLesson(slug, checksum)) continue
            val path = "/content/releases/$version/lessons/" + URLEncoder.encode(slug, Charsets.UTF_8.name())
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
            store.cacheLesson(lesson, checksum)
        }
    }

    private fun syncPendingProgress(baseUrl: String, token: String, store: OfflineLearningStore): Int {
        var synced = 0
        for (item in store.pendingProgress()) {
            val encodedSlug = URLEncoder.encode(item.lessonSlug, Charsets.UTF_8.name())
            val path = "/me/lesson-progress/" + encodedSlug
            val body = JSONObject()
                .put("contentRevision", item.contentRevision)
                .put("progressPercent", item.progressPercent)
            item.lastPositionJson?.let { body.put("lastPosition", JSONObject(it)) }
            val (status, _) = request(baseUrl, path, token, "PUT", body)
            if (status in 200..299) {
                store.markProgressSynced(item)
                synced++
            } else if (status == 409) {
                val (lessonStatus, lessonJson) = request(baseUrl, "/lessons/" + encodedSlug, null, "GET")
                if (lessonStatus in 200..299) {
                    val currentRevision = JSONObject(lessonJson).optInt("contentRevision", 0)
                    if (currentRevision > 0) {
                        store.enqueueProgress(item.lessonSlug, currentRevision, item.progressPercent, item.lastPositionJson)
                        throw IOException("Progress revision rebased; retrying synchronization")
                    }
                }
                throw IllegalStateException("Progress conflict could not be reconciled")
            } else if (status == 408 || status == 429 || status >= 500) {
                throw IOException("Progress sync should be retried")
            } else {
                throw IllegalStateException("Progress sync rejected with HTTP $status")
            }
        }
        return synced
    }

    private fun syncPendingBookmarks(baseUrl: String, token: String, store: OfflineLearningStore): Int {
        var synced = 0
        for (item in store.pendingBookmarks()) {
            val path = "/me/bookmarks/" + URLEncoder.encode(item.lessonSlug, Charsets.UTF_8.name())
            val (status, _) = request(baseUrl, path, token, if (item.bookmarked) "PUT" else "DELETE")
            if (status in 200..299 || (!item.bookmarked && status == 404)) {
                store.markBookmarkSynced(item)
                synced++
            } else if (status == 408 || status == 429 || status >= 500) {
                throw IOException("Bookmark sync should be retried")
            } else {
                throw IllegalStateException("Bookmark sync rejected with HTTP $status")
            }
        }
        return synced
    }

    private suspend fun ensureAccessToken(baseUrl: String, store: SecureSessionStore): String {
        val accessToken = store.accessToken() ?: throw IllegalStateException("Sign in to synchronize learning activity")
        val auth = AuthRepository()
        if (runCatching { auth.currentUser(baseUrl, accessToken) }.isSuccess) return accessToken
        val refreshToken = store.refreshToken()
            ?: throw IllegalStateException("Your session expired. Sign in again to synchronize learning activity.")
        val session = auth.refresh(baseUrl, refreshToken)
        val userJson = JSONObject()
            .put("id", session.account.id)
            .put("name", session.account.name)
            .put("email", session.account.email)
            .put("roles", org.json.JSONArray(session.account.roles))
            .toString()
        store.save(session.accessToken, session.refreshToken, session.expiresAt, userJson)
        return session.accessToken
    }

    private fun request(
        baseUrl: String, path: String, token: String?, method: String, body: JSONObject? = null
    ): Pair<Int, String> {
        val connection = (URL(baseUrl + "/api/v1" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            status to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
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
