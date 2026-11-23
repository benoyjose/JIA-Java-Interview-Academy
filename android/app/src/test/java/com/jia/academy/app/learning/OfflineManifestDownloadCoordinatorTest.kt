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

    private fun manifest(slug: String, revision: Int, expectedContent: String) =
        OfflineReleaseManifest.parse(JSONObject().apply {
            put("version", 1)
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
