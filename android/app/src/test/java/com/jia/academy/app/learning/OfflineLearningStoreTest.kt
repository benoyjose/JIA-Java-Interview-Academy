package com.jia.academy.app.learning

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OfflineLearningStoreTest {
    private lateinit var context: Context
    private lateinit var store: OfflineLearningStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("jia-offline-learning.db")
        store = OfflineLearningStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("jia-offline-learning.db")
    }

    @Test
    fun checksumFailureDoesNotReplaceLastKnownGoodLesson() {
        val old = lesson("streams", 1, """{"schemaVersion":1,"blocks":[]}""")
        store.cacheLesson(old)
        val rejected = lesson("streams", 2, """{"schemaVersion":1,"blocks":[{"type":"text"}]}""")

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            store.cacheLesson(rejected, "not-the-content-sha")
        }

        assertEquals(1, store.cachedLesson("streams")?.contentRevision)
        assertTrue(store.hasCachedLesson("streams", OfflineContentChecksum.sha256(old.contentJson!!)))
    }

    @Test
    fun detectsAndEvictsCorruptedCachedContent() {
        val item = lesson("jvm", 3, """{"schemaVersion":1,"blocks":[{"type":"text","text":"valid"}]}""")
        store.cacheLesson(item)
        store.writableDatabase.execSQL(
            "UPDATE cached_lessons SET content_json=? WHERE slug=?",
            arrayOf("""{"tampered":true}""", "jvm")
        )

        assertNull(store.cachedLesson("jvm"))
        assertFalse(store.hasCachedLesson("jvm", OfflineContentChecksum.sha256(item.contentJson!!)))
    }

    @Test
    fun progressQueueDeduplicatesAndNeverRegressesAcrossRevision() {
        store.enqueueProgress("lesson-a", 4, 45.0, """{"block":4}""")
        val first = store.pendingProgress().single()
        store.enqueueProgress("lesson-a", 4, 80.0, """{"block":8}""")
        store.enqueueProgress("lesson-a", 3, 20.0, """{"block":1}""")

        val merged = store.pendingProgress().single()
        assertEquals(4, merged.contentRevision)
        assertEquals(80.0, merged.progressPercent, 0.0)
        assertEquals("""{"block":8}""", merged.lastPositionJson)
        assertTrue(merged.updatedAt > first.updatedAt)
    }

    @Test
    fun acknowledgementForOldSnapshotCannotDeleteNewerQueuedProgress() {
        store.enqueueProgress("lesson-b", 1, 20.0, """{"offset":1}""")
        val sent = store.pendingProgress().single()
        store.enqueueProgress("lesson-b", 1, 60.0, """{"offset":6}""")

        store.markProgressSynced(sent)

        assertEquals(60.0, store.pendingProgress().single().progressPercent, 0.0)
    }

    @Test
    fun bookmarkQueueKeepsLatestIntentAndConditionalAck() {
        store.setBookmarkAndSchedule(context, "https://example.test", "lesson-c", true)
        val sent = store.pendingBookmarks().single()
        store.setBookmarkAndSchedule(context, "https://example.test", "lesson-c", false)

        store.markBookmarkSynced(sent)

        assertFalse(store.isBookmarked("lesson-c"))
        assertEquals(false, store.pendingBookmarks().single().bookmarked)
    }

    @Test
    fun syncFailureIsObservableAndDoesNotEraseLastSuccess() {
        store.recordSyncSuccess(progressCount = 2, bookmarkCount = 1)
        store.recordSyncFailure("network timeout")

        val diagnostics = store.syncDiagnostics()
        assertEquals("network timeout", diagnostics?.lastError)
        assertTrue((diagnostics?.lastSuccessAt ?: 0L) > 0L)
        assertTrue((diagnostics?.lastAttemptAt ?: 0L) >= (diagnostics?.lastSuccessAt ?: 0L))
    }

    private fun lesson(slug: String, revision: Int, content: String) =
        com.jia.academy.app.catalogue.LessonDetail(
            id = "lesson-id-$slug", slug = slug, title = slug, summary = null,
            difficulty = "PROFESSIONAL", contentRevision = revision,
            sourceType = "ADMIN_AUTHORED", contentJson = content
        )
}
