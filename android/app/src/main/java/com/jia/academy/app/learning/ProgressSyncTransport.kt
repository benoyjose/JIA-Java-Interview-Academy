package com.jia.academy.app.learning

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

internal data class SyncHttpResponse(val status: Int, val body: String)

internal fun interface ProgressSyncHttpTransport {
    fun execute(url: String, method: String, token: String?, body: String?): SyncHttpResponse
}

internal class UrlConnectionProgressSyncTransport : ProgressSyncHttpTransport {
    override fun execute(url: String, method: String, token: String?, body: String?): SyncHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
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
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            SyncHttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

internal enum class SyncResponseAction { ACKNOWLEDGE, REBASE, RETRY, REJECT }

internal object ProgressSyncResponsePolicy {
    fun classify(status: Int): SyncResponseAction = when {
        status in 200..299 -> SyncResponseAction.ACKNOWLEDGE
        status == 409 -> SyncResponseAction.REBASE
        status == 408 || status == 429 || status >= 500 -> SyncResponseAction.RETRY
        else -> SyncResponseAction.REJECT
    }

    fun requestBody(item: PendingLessonProgress): JSONObject =
        JSONObject().put("contentRevision", item.contentRevision)
            .put("progressPercent", item.progressPercent)
            .also { json -> item.lastPositionJson?.let { json.put("lastPosition", JSONObject(it)) } }
}

internal data class BatchSyncEvent(
    val eventId: String,
    val json: JSONObject,
    val progressSnapshot: PendingLessonProgress? = null,
    val bookmarkSnapshot: PendingBookmark? = null
)

internal object ProgressBatchEventFactory {
    fun progress(item: PendingLessonProgress): BatchSyncEvent {
        val eventId = stableId("progress", item.lessonSlug, item.updatedAt)
        val payload = JSONObject()
            .put("contentRevision", item.contentRevision)
            .put("progressPercent", item.progressPercent)
        item.lastPositionJson?.let { payload.put("lastPosition", JSONObject(it)) }
        return BatchSyncEvent(eventId, envelope(eventId,
            if (item.progressPercent >= 100.0) "LESSON_COMPLETED" else "LESSON_STARTED",
            item.lessonSlug, item.updatedAt, payload), progressSnapshot = item)
    }

    fun bookmark(item: PendingBookmark): BatchSyncEvent {
        val eventId = stableId("bookmark", item.lessonSlug, item.updatedAt)
        return BatchSyncEvent(eventId, envelope(eventId, "BOOKMARK_CHANGED", item.lessonSlug,
            item.updatedAt, JSONObject().put("bookmarked", item.bookmarked)), bookmarkSnapshot = item)
    }

    fun batchBody(events: List<BatchSyncEvent>): JSONObject {
        require(events.isNotEmpty() && events.size <= ProgressBatchReceiptParser.MAX_BATCH_SIZE)
        return JSONObject().put("events", JSONArray().also { array -> events.forEach { array.put(it.json) } })
    }

    private fun envelope(eventId: String, type: String, slug: String, updatedAt: Long, payload: JSONObject) =
        JSONObject().put("eventId", eventId)
            .put("eventType", type)
            .put("occurredAt", Instant.ofEpochMilli(updatedAt).toString())
            .put("lessonSlug", slug)
            .put("payload", payload)

    /** Same persisted snapshot always yields the same UUID across retries and process restarts. */
    private fun stableId(kind: String, slug: String, updatedAt: Long): String =
        UUID.nameUUIDFromBytes("jia-progress-v1:$kind:$slug:$updatedAt".toByteArray(StandardCharsets.UTF_8)).toString()
}

internal enum class BatchSyncResponseAction { ACKNOWLEDGE, REBASE, RETRY, REJECT }

internal object ProgressBatchSyncResponsePolicy {
    fun classify(status: Int): BatchSyncResponseAction = when {
        status in 200..299 -> BatchSyncResponseAction.ACKNOWLEDGE
        status == 409 -> BatchSyncResponseAction.REBASE
        status == 408 || status == 429 || status >= 500 -> BatchSyncResponseAction.RETRY
        else -> BatchSyncResponseAction.REJECT
    }
}

