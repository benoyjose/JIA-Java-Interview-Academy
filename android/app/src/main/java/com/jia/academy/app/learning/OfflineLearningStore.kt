package com.jia.academy.app.learning

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jia.academy.app.catalogue.LessonDetail
import java.security.MessageDigest

data class PendingBookmark(val lessonSlug: String, val bookmarked: Boolean, val updatedAt: Long)

data class OfflineSyncDiagnostics(val lastAttemptAt: Long?, val lastSuccessAt: Long?, val lastError: String?, val syncedProgressCount: Int, val syncedBookmarkCount: Int)

data class PendingLessonProgress(
    val lessonSlug: String,
    val contentRevision: Int,
    val progressPercent: Double,
    val lastPositionJson: String?,
    val updatedAt: Long
)

/** Small transactional cache for last-known-good lessons and queued learner progress. */
class OfflineLearningStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "jia-offline-learning.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE cached_lessons (
                slug TEXT PRIMARY KEY NOT NULL,
                id TEXT NOT NULL,
                title TEXT NOT NULL,
                summary TEXT,
                difficulty TEXT NOT NULL,
                content_revision INTEGER NOT NULL,
                source_type TEXT NOT NULL,
                content_json TEXT NOT NULL,
                checksum TEXT NOT NULL,
                cached_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE pending_progress (
                lesson_slug TEXT PRIMARY KEY NOT NULL,
                content_revision INTEGER NOT NULL,
                progress_percent REAL NOT NULL CHECK(progress_percent >= 0 AND progress_percent <= 100),
                last_position_json TEXT,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        createBookmarkTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema changes must be additive and versioned; never drop learner-owned queued activity.
        if (oldVersion < 2) createBookmarkTables(db)
    }

    private fun createBookmarkTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS offline_bookmarks (lesson_slug TEXT PRIMARY KEY NOT NULL, is_bookmarked INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS pending_bookmarks (lesson_slug TEXT PRIMARY KEY NOT NULL, is_bookmarked INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_diagnostics (id INTEGER PRIMARY KEY CHECK(id=1), last_attempt_at INTEGER, last_success_at INTEGER, last_error TEXT, synced_progress_count INTEGER NOT NULL DEFAULT 0, synced_bookmark_count INTEGER NOT NULL DEFAULT 0)")
    }

    fun cacheLesson(lesson: LessonDetail, expectedChecksum: String? = null) {
        val content = lesson.contentJson ?: return
        require(content.length <= MAX_CONTENT_CHARS) { "Lesson content exceeds the offline cache limit" }
        val checksum = sha256(content)
        if (expectedChecksum != null) {
            require(checksum.equals(expectedChecksum, ignoreCase = true)) {
                "Lesson checksum verification failed"
            }
        }
        val values = ContentValues().apply {
            put("slug", lesson.slug)
            put("id", lesson.id)
            put("title", lesson.title)
            put("summary", lesson.summary)
            put("difficulty", lesson.difficulty)
            put("content_revision", lesson.contentRevision)
            put("source_type", lesson.sourceType)
            put("content_json", content)
            put("checksum", checksum)
            put("cached_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("cached_lessons", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun cachedLesson(slug: String): LessonDetail? =
        readableDatabase.query(
            "cached_lessons",
            arrayOf("id", "slug", "title", "summary", "difficulty", "content_revision", "source_type", "content_json", "checksum"),
            "slug=?",
            arrayOf(slug),
            null, null, null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val content = cursor.getString(7)
            if (!sha256(content).equals(cursor.getString(8), ignoreCase = true)) {
                writableDatabase.delete("cached_lessons", "slug=?", arrayOf(slug))
                return null
            }
            LessonDetail(
                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3), cursor.getString(4), cursor.getInt(5),
                cursor.getString(6), content
            )
        }

    fun hasCachedLesson(slug: String, expectedChecksum: String): Boolean =
        readableDatabase.query(
            "cached_lessons",
            arrayOf("content_json", "checksum"),
            "slug=?",
            arrayOf(slug),
            null, null, null
        ).use { cursor ->
            if (!cursor.moveToFirst()) false
            else {
                val content = cursor.getString(0)
                sha256(content).equals(cursor.getString(1), ignoreCase = true) &&
                    cursor.getString(1).equals(expectedChecksum, ignoreCase = true)
            }
        }

    fun enqueueProgress(
        lessonSlug: String,
        contentRevision: Int,
        progressPercent: Double,
        lastPositionJson: String? = null
    ) {
        require(lessonSlug.isNotBlank())
        require(contentRevision > 0)
        require(progressPercent in 0.0..100.0)
        if (lastPositionJson != null) org.json.JSONObject(lastPositionJson)
        val db = writableDatabase
        db.beginTransaction()
        try {
            var mergedRevision = contentRevision
            var mergedPercent = progressPercent
            var mergedPosition = lastPositionJson
            var updatedAt = System.currentTimeMillis()
            db.query(
                "pending_progress",
                arrayOf("content_revision", "progress_percent", "last_position_json", "updated_at"),
                "lesson_slug=?",
                arrayOf(lessonSlug),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    mergedRevision = maxOf(contentRevision, cursor.getInt(0))
                    mergedPercent = maxOf(progressPercent, cursor.getDouble(1))
                    if (mergedPosition == null) mergedPosition = cursor.getString(2)
                    updatedAt = maxOf(updatedAt, cursor.getLong(3) + 1L)
                }
            }
            val values = ContentValues().apply {
                put("lesson_slug", lessonSlug)
                put("content_revision", mergedRevision)
                put("progress_percent", mergedPercent)
                put("last_position_json", mergedPosition)
                put("updated_at", updatedAt)
            }
            db.insertWithOnConflict("pending_progress", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun enqueueProgressAndSchedule(
        context: Context,
        baseUrl: String,
        lessonSlug: String,
        contentRevision: Int,
        progressPercent: Double,
        lastPositionJson: String? = null
    ) {
        enqueueProgress(lessonSlug, contentRevision, progressPercent, lastPositionJson)
        LearnerSyncScheduler.enqueue(context, baseUrl)
    }

    fun setBookmarkAndSchedule(context: Context, baseUrl: String, lessonSlug: String, bookmarked: Boolean) {
        require(lessonSlug.isNotBlank())
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val existing = db.rawQuery("SELECT updated_at FROM offline_bookmarks WHERE lesson_slug=?", arrayOf(lessonSlug)).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            val updatedAt = maxOf(now, existing + 1L)
            val values = ContentValues().apply {
                put("lesson_slug", lessonSlug)
                put("is_bookmarked", if (bookmarked) 1 else 0)
                put("updated_at", updatedAt)
            }
            db.insertWithOnConflict("offline_bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.insertWithOnConflict("pending_bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        LearnerSyncScheduler.enqueue(context, baseUrl)
    }

    fun isBookmarked(lessonSlug: String): Boolean =
        readableDatabase.rawQuery("SELECT is_bookmarked FROM offline_bookmarks WHERE lesson_slug=?", arrayOf(lessonSlug)).use {
            it.moveToFirst() && it.getInt(0) == 1
        }

    fun bookmarkedSlugs(): Set<String> =
        readableDatabase.rawQuery("SELECT lesson_slug FROM offline_bookmarks WHERE is_bookmarked=1", null).use {
            buildSet { while (it.moveToNext()) add(it.getString(0)) }
        }

    fun pendingBookmarks(): List<PendingBookmark> =
        readableDatabase.query("pending_bookmarks", arrayOf("lesson_slug","is_bookmarked","updated_at"),
            null,null,null,null,"updated_at ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(PendingBookmark(cursor.getString(0),cursor.getInt(1)==1,cursor.getLong(2)))
            }
        }

    fun markBookmarkSynced(item: PendingBookmark) {
        writableDatabase.delete("pending_bookmarks","lesson_slug=? AND updated_at=?",
            arrayOf(item.lessonSlug,item.updatedAt.toString()))
    }

    fun recordSyncAttempt() {
        val values = ContentValues().apply { put("id",1); put("last_attempt_at",System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict("sync_diagnostics",null,values,SQLiteDatabase.CONFLICT_IGNORE)
        writableDatabase.update("sync_diagnostics",values,"id=1",null)
    }

    fun recordSyncSuccess(progressCount: Int, bookmarkCount: Int) {
        val values = ContentValues().apply {
            put("id",1); put("last_success_at",System.currentTimeMillis()); putNull("last_error")
            put("synced_progress_count",progressCount); put("synced_bookmark_count",bookmarkCount)
        }
        writableDatabase.insertWithOnConflict("sync_diagnostics",null,values,SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun recordSyncFailure(message: String) {
        val values = ContentValues().apply {
            put("id",1); put("last_attempt_at",System.currentTimeMillis())
            put("last_error",message.take(240))
        }
        writableDatabase.insertWithOnConflict("sync_diagnostics",null,values,SQLiteDatabase.CONFLICT_IGNORE)
        writableDatabase.update("sync_diagnostics",values,"id=1",null)
    }

    fun syncDiagnostics(): OfflineSyncDiagnostics? =
        readableDatabase.query("sync_diagnostics",arrayOf("last_attempt_at","last_success_at","last_error","synced_progress_count","synced_bookmark_count"),
            "id=1",null,null,null,null).use { cursor ->
            if (!cursor.moveToFirst()) null else OfflineSyncDiagnostics(
                if (cursor.isNull(0)) null else cursor.getLong(0),
                if (cursor.isNull(1)) null else cursor.getLong(1),
                cursor.getString(2),cursor.getInt(3),cursor.getInt(4))
        }

    fun pendingProgress(): List<PendingLessonProgress> =
        readableDatabase.query(
            "pending_progress",
            arrayOf("lesson_slug", "content_revision", "progress_percent", "last_position_json", "updated_at"),
            null, null, null, null, "updated_at ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    PendingLessonProgress(
                        cursor.getString(0), cursor.getInt(1), cursor.getDouble(2),
                        cursor.getString(3), cursor.getLong(4)
                    )
                )
            }
        }

    fun markProgressSynced(item: PendingLessonProgress) {
        writableDatabase.delete(
            "pending_progress",
            "lesson_slug=? AND updated_at=?",
            arrayOf(item.lessonSlug, item.updatedAt.toString())
        )
    }

    companion object {
        private const val MAX_CONTENT_CHARS = 2_000_000
        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
