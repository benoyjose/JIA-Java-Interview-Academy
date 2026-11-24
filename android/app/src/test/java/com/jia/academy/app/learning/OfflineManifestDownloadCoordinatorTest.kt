package com.jia.academy.app.learning

import android.content.Context
import com.jia.academy.app.catalogue.LessonDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
class OfflineManifestDownloadCoordinatorTest {
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

    @Test fun verifiesManifestAndCachesThenSkipsAlreadyCurrentLesson() = runBlocking {
        val content = """{"blocks":[]}"""
        val manifest = manifest("unit-lesson", 3, content)
        var fetches = 0
        val coordinator = OfflineManifestDownloadCoordinator(store) { slug ->
            fetches++
            lesson(slug, 3, content)
        }
        val first = coordinator.download(manifest)
        val second = coordinator.download(manifest)

        assertEquals(1, first.downloadedCount)
        assertEquals(1, second.alreadyCurrentCount)
        assertEquals(1, fetches)
    }

    @Test fun checksumMismatchPreservesPreviousCachedRevision() = runBlocking {
        val oldContent = """{"revision":"old"}"""
        store.cacheLesson(lesson("unit-lesson", 2, oldContent))
        val manifest = manifest("unit-lesson", 3, """{"revision":"expected"}""")
        val coordinator = OfflineManifestDownloadCoordinator(store) {
            lesson(it, 3, """{"revision":"tampered"}""")
        }

        assertThrows(IllegalArgumentException::class.java) { runBlocking { coordinator.download(manifest) } }
        assertEquals(2, store.cachedLesson("unit-lesson")?.contentRevision)
        assertEquals(oldContent, store.cachedLesson("unit-lesson")?.contentJson)
    }

    @Test fun coroutineCancellationStopsBeforeFetchingNextItem() = runBlocking {
        val content = """{"ok":true}"""
        val manifest = OfflineReleaseManifest.parse(JSONObject().apply {
            put("version", 4)
            put("items", org.json.JSONArray().put(item("lesson-a", content)).put(item("lesson-b", content)))
        })
        var calls = 0
        val firstFetchStarted = CompletableDeferred<Unit>()
        val holdFetch = CompletableDeferred<LessonDetail>()
        val coordinator = OfflineManifestDownloadCoordinator(store) {
            calls++
            firstFetchStarted.complete(Unit)
            holdFetch.await()
        }
        val downloadJob = launch { coordinator.download(manifest) }
        firstFetchStarted.await()
        downloadJob.cancelAndJoin()
        assertEquals(1, calls)
    }

    @Test fun successfulManifestPrunesStaleContentButPreservesQueuedActivity() = runBlocking {
        val retained = """{"revision":"current"}"""
        val stale = """{"revision":"removed"}"""
        store.cacheLesson(lesson("published", 2, retained))
        store.cacheLesson(lesson("unpublished", 1, stale))
        store.enqueueProgress("unpublished", 1, 40.0, """{"block":2}""")
        val manifest = manifest("published", 2, retained)
        val coordinator = OfflineManifestDownloadCoordinator(store) { error("Current lesson must not be fetched") }

        coordinator.download(manifest)

        assertEquals(listOf("published"), store.cachedLessonLibrary().map { it.slug })
        assertEquals(1, store.pendingProgress().size)
        assertEquals("unpublished", store.pendingProgress().single().lessonSlug)
    }

    @Test fun olderManifestIsRejectedBeforeFetchingAndPreservesCachedRelease() = runBlocking {
        val content = """{"version":"current"}"""
        store.cacheLesson(lesson("current", 2, content))
        store.recordValidatedManifestVersion(5)
        var fetched = false
        val oldManifest = manifest("old", 1, """{"version":"old"}""", version = 4)
        val coordinator = OfflineManifestDownloadCoordinator(store) {
            fetched = true
            lesson(it, 1, """{"version":"old"}""")
        }

        assertThrows(IllegalStateException::class.java) { runBlocking { coordinator.download(oldManifest) } }
        assertFalse(fetched)
        assertEquals(5L, store.lastValidatedManifestVersion())
        assertEquals(listOf("current"), store.cachedLessonLibrary().map { it.slug })
    }

    @Test fun failedManifestDownloadDoesNotPrunePreviouslyCachedContent() = runBlocking {
        val old = """{"revision":"old"}"""
        store.cacheLesson(lesson("old-cached", 1, old))
        val manifest = manifest("new-item", 2, """{"revision":"expected"}""")
        val coordinator = OfflineManifestDownloadCoordinator(store) {
            throw java.io.IOException("network interruption")
        }

        assertThrows(java.io.IOException::class.java) { runBlocking { coordinator.download(manifest) } }
        assertEquals(listOf("old-cached"), store.cachedLessonLibrary().map { it.slug })
    }

    private fun manifest(slug: String, revision: Int, expectedContent: String, version: Long = 1) =
        OfflineReleaseManifest.parse(JSONObject().apply {
            put("version", version)
            put("items", org.json.JSONArray().put(item(slug, expectedContent, revision)))
        })

    private fun item(slug: String, content: String, revision: Int = 1) = JSONObject().apply {
        put("slug", slug)
        put("contentRevision", revision)
        put("checksum", OfflineContentChecksum.sha256(content))
    }

    private fun lesson(slug: String, revision: Int, content: String) = LessonDetail(
        id = "id-$slug", slug = slug, title = slug, summary = null, difficulty = "INTERMEDIATE",
        contentRevision = revision, sourceType = "ADMIN_AUTHORED", contentJson = content
    )
}
