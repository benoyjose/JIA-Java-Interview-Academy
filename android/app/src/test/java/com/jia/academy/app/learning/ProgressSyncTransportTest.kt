package com.jia.academy.app.learning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        val response = fake.execute("https://api.example.test/api/v1/me/lesson-progress/streams",
            "PUT", "test-access-token", """{"progressPercent":50}""")
        assertEquals("https://api.example.test/api/v1/me/lesson-progress/streams", capturedUrl)
        assertEquals("PUT", capturedMethod)
        assertEquals("test-access-token", capturedToken)
        assertNotNull(capturedBody)
        assertEquals(200, response.status)
        assertEquals(true, JSONObject(response.body).getBoolean("saved"))
    }
}
