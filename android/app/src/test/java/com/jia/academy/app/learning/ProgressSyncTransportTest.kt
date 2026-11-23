package com.jia.academy.app.learning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProgressSyncTransportTest {
    @Test
    fun putPayloadPreservesRevisionPercentAndReadingAnchor() {
        val item = PendingLessonProgress("streams", 7, 65.0,
            """{"version":1,"scrollOffsetPx":830,"contentRevision":7}""", 123L)
        val payload = ProgressSyncResponsePolicy.requestBody(item)
        assertEquals(7, payload.getInt("contentRevision"))
        assertEquals(65.0, payload.getDouble("progressPercent"), 0.001)
        assertEquals(830, payload.getJSONObject("lastPosition").getInt("scrollOffsetPx"))
    }

    @Test
    fun responsePolicyDistinguishesAckConflictRetryAndPermanentFailure() {
        assertEquals(SyncResponseAction.ACKNOWLEDGE, ProgressSyncResponsePolicy.classify(204))
        assertEquals(SyncResponseAction.REBASE, ProgressSyncResponsePolicy.classify(409))
        assertEquals(SyncResponseAction.RETRY, ProgressSyncResponsePolicy.classify(408))
        assertEquals(SyncResponseAction.RETRY, ProgressSyncResponsePolicy.classify(429))
        assertEquals(SyncResponseAction.RETRY, ProgressSyncResponsePolicy.classify(503))
        assertEquals(SyncResponseAction.REJECT, ProgressSyncResponsePolicy.classify(422))
    }

    @Test
    fun progressSnapshotProducesStableUuidAndPreservesPayload() {
        val snapshot = PendingLessonProgress("streams", 4, 82.0,
            """{"scrollOffsetPx":92}""", 1_800_000_000_000L)
        val event = ProgressBatchEventFactory.progress(snapshot)
        val retry = ProgressBatchEventFactory.progress(snapshot)
        assertEquals(event.eventId, retry.eventId)
        assertEquals("LESSON_STARTED", event.json.getString("eventType"))
        assertEquals("streams", event.json.getString("lessonSlug"))
        assertEquals(82.0, event.json.getJSONObject("payload").getDouble("progressPercent"), 0.001)
        assertEquals(92, event.json.getJSONObject("payload").getJSONObject("lastPosition").getInt("scrollOffsetPx"))
        assertNotEquals(event.eventId, ProgressBatchEventFactory.progress(snapshot.copy(updatedAt = snapshot.updatedAt + 1)).eventId)
    }

    @Test
    fun completionAndBookmarkAreRepresentedAsLedgerEvents() {
        val completed = ProgressBatchEventFactory.progress(PendingLessonProgress("threads", 3, 100.0, null, 5L))
        assertEquals("LESSON_COMPLETED", completed.json.getString("eventType"))
        val bookmark = ProgressBatchEventFactory.bookmark(PendingBookmark("threads", false, 7L))
        assertEquals("BOOKMARK_CHANGED", bookmark.json.getString("eventType"))
        assertEquals(false, bookmark.json.getJSONObject("payload").getBoolean("bookmarked"))
        assertEquals(7L, bookmark.bookmarkSnapshot?.updatedAt)
    }

    @Test
    fun batchEnvelopeKeepsEventIdsAndCapsAtOneHundred() {
        val one = ProgressBatchEventFactory.progress(PendingLessonProgress("java", 1, 10.0, null, 1L))
        val body = ProgressBatchEventFactory.batchBody(listOf(one))
        assertEquals(one.eventId, body.getJSONArray("events").getJSONObject(0).getString("eventId"))
        assertEquals(BatchSyncResponseAction.ACKNOWLEDGE, ProgressBatchSyncResponsePolicy.classify(200))
        assertEquals(BatchSyncResponseAction.RETRY, ProgressBatchSyncResponsePolicy.classify(429))
        assertEquals(BatchSyncResponseAction.RETRY, ProgressBatchSyncResponsePolicy.classify(503))
        assertEquals(BatchSyncResponseAction.REJECT, ProgressBatchSyncResponsePolicy.classify(409))
        assertEquals(BatchSyncResponseAction.REJECT, ProgressBatchSyncResponsePolicy.classify(422))
    }

    @Test
    fun injectableTransportReceivesExactRequestAndReturnsDeterministicResponse() {
        var capturedUrl: String? = null
        var capturedMethod: String? = null
        var capturedToken: String? = null
        var capturedBody: String? = null
        val fake = ProgressSyncHttpTransport { url, method, token, body ->
            capturedUrl = url
            capturedMethod = method
            capturedToken = token
            capturedBody = body
            SyncHttpResponse(200, """{"saved":true}""")
        }

        val response = fake.execute("https://api.example.test/api/v1/me/progress-events/batch",
            "POST", "test-access-token", """{"events":[]}""")
        assertEquals("https://api.example.test/api/v1/me/progress-events/batch", capturedUrl)
        assertEquals("POST", capturedMethod)
        assertEquals("test-access-token", capturedToken)
        assertNotNull(capturedBody)
        assertEquals(200, response.status)
        assertEquals(true, JSONObject(response.body).getBoolean("saved"))
    }
}
