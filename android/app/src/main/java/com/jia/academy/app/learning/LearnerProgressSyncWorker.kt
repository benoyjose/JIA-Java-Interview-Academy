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
        val token = SecureSessionStore(applicationContext).accessToken()
            ?: return@withContext Result.failure()
        val store = OfflineLearningStore(applicationContext)

        for (item in store.pendingProgress()) {
            try {
                val encodedSlug = URLEncoder.encode(item.lessonSlug, Charsets.UTF_8.name())
                val connection = (URL("$baseUrl/api/v1/me/lesson-progress/$encodedSlug")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val body = JSONObject()
                    .put("contentRevision", item.contentRevision)
                    .put("progressPercent", item.progressPercent)
                item.lastPositionJson?.let { body.put("lastPosition", JSONObject(it)) }
                val status = try {
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
                    connection.responseCode
                } finally {
                    connection.disconnect()
                }
                if (status in 200..299) {
                    store.markProgressSynced(item)
                } else if (status == 408 || status == 429 || status >= 500) {
                    return@withContext Result.retry()
                } else {
                    return@withContext Result.failure(
                        Data.Builder().putInt("httpStatus", status).putString("lessonSlug", item.lessonSlug).build()
                    )
                }
            } catch (_: IOException) {
                return@withContext Result.retry()
            } catch (_: RuntimeException) {
                return@withContext Result.retry()
            }
        }
        Result.success()
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
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
