package com.jia.academy.app.learning

import android.content.Context
import com.jia.academy.app.catalogue.LessonDetail
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OfflineLibraryDownloadControllerTest {
    private lateinit var context: Context
    private lateinit var store: OfflineLearningStore

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("jia-offline-learning.db")
        store = OfflineLearningStore(context)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase("jia-offline-learning.db")
    }

    @Test fun authenticatedReleaseIsFetchedAndVerifiedLessonIsCached() = runBlocking {
        val slug = "controller-offline-lesson"
        val content = """{"schemaVersion":1,"blocks":[{"type":"text","text":"cache me"}]}"""
        val checksum = OfflineContentChecksum.sha256(content)
        val requests = mutableListOf<Pair<String, String?>>()
        val fake = ProgressSyncHttpTransport { url, method, token, _ ->
            assertEquals("GET", method)
            requests += url to token
            if (url.endsWith("/content/releases/latest")) {
                SyncHttpResponse(200, JSONObject().put("version", 8).put("items",
                    JSONArray().put(JSONObject().put("slug", slug).put("contentRevision", 2)
                        .put("checksum", checksum))).toString())
            } else {
                SyncHttpResponse(200, JSONObject().put("id", "lesson-id").put("slug", slug)
                    .put("title", "Offline lesson").put("contentRevision", 2)
                    .put("sourceType", "ADMIN_AUTHORED").put("contentJson", content).toString())
            }
        }
        val controller = OfflineLibraryDownloadController("https://academy.example.test/",
            store, { "valid-session-token" }, fake)
        val summary = controller.downloadLatest()

        assertEquals(1, summary.downloadedCount)
        assertEquals("Offline lesson", store.cachedLesson(slug)?.title)
        assertEquals(2, store.cachedLesson(slug)?.contentRevision)
        assertEquals(listOf("valid-session-token", "valid-session-token"), requests.map { it.second })
        assertEquals("https://academy.example.test/api/v1/content/releases/8/lessons/$slug", requests[1].first)
    }

    @Test fun premiumDenialStopsBeforeAnyLessonBodyIsRequested() {
        var lessonRequest = false
        val fake = ProgressSyncHttpTransport { url, _, _, _ ->
            if (url.endsWith("/content/releases/latest")) SyncHttpResponse(403, "{}")
            else {
                lessonRequest = true
                SyncHttpResponse(200, "{}")
            }
        }
        val controller = OfflineLibraryDownloadController("https://academy.example.test",
            store, { "expired-session" }, fake)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { controller.downloadLatest() }
        }
        assertEquals(false, lessonRequest)
        assertEquals(0, store.cachedLessonLibrary().size)
    }

    @Test fun transientReleaseFailureRemainsRetryable() {
        val fake = ProgressSyncHttpTransport { _, _, _, _ -> SyncHttpResponse(503, "{}") }
        val controller = OfflineLibraryDownloadController("https://academy.example.test",
            store, { null }, fake)
        assertThrows(java.io.IOException::class.java) {
            runBlocking { controller.downloadLatest() }
        }
    }
}
