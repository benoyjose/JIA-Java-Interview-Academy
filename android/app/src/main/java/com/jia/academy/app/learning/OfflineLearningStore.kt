package com.jia.academy.app.learning

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import com.jia.academy.app.catalogue.LessonDetail

data class PendingBookmark(val lessonSlug: String, val bookmarked: Boolean, val updatedAt: Long)

data class OfflineSyncDiagnostics(val lastAttemptAt: Long?, val lastSuccessAt: Long?, val lastError: String?, val syncedProgressCount: Int, val syncedBookmarkCount: Int)


internal data class ProgressSnapshot(val contentRevision: Int, val progressPercent: Double, val lastPositionJson: String?, val updatedAt: Long)
internal object ProgressMergePolicy {
    fun merge(current: ProgressSnapshot?, revision: Int, percent: Double, position: String?, now: Long): ProgressSnapshot {
        val isNewerRevision = current == null || revision >= current.contentRevision
        return ProgressSnapshot(maxOf(revision, current?.contentRevision ?: revision),
            maxOf(percent, current?.progressPercent ?: percent),
            if (isNewerRevision) position ?: current?.lastPositionJson else current?.lastPositionJson,
            maxOf(now, (current?.updatedAt ?: (now - 1L)) + 1L))
    }
}

data class PendingLessonProgress(
    val lessonSlug: String,
    val contentRevision: Int,
    val progressPercent: Double,
    val lastPositionJson: String?,
    val updatedAt: Long
)

/** Small transactional cache for last-known-good lessons and queued learner progress. */
class OfflineLearningStore(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val database = JIAOfflineRoomDatabase.open(appContext)
    internal val writableDatabase: SupportSQLiteDatabase get() = database.openHelper.writableDatabase
    private val readableDatabase: SupportSQLiteDatabase get() = database.openHelper.readableDatabase

    override fun close() = database.close()

    fun downloadsOnMeteredNetwork(): Boolean =
        appContext.getSharedPreferences("jia_offline_settings", Context.MODE_PRIVATE)
            .getBoolean("downloads_on_metered_network", false)

    fun setDownloadsOnMeteredNetwork(enabled: Boolean) {
        appContext.getSharedPreferences("jia_offline_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("downloads_on_metered_network", enabled).apply()
    }

    fun cacheLesson(lesson: LessonDetail, expectedChecksum: String? = null) {
        val content = lesson.contentJson ?: return
        require(content.length <= MAX_CONTENT_CHARS) { "Lesson content exceeds the offline cache limit" }
        val checksum = OfflineContentChecksum.sha256(content)
        if (expectedChecksum != null) {
            require(checksum.equals(expectedChecksum, ignoreCase = true)) {
                "Lesson checksum verification failed"
            }
        }
        database.cachedLessonDao().replace(CachedLessonEntity(
            slug = lesson.slug, id = lesson.id, title = lesson.title, summary = lesson.summary,
            difficulty = lesson.difficulty, contentRevision = lesson.contentRevision,
            sourceType = lesson.sourceType, contentJson = content, checksum = checksum,
            cachedAt = System.currentTimeMillis()
        ))
        evictToCacheLimit(protectedSlug = lesson.slug)
    }

    /** Current UTF-8 content bytes retained in the offline lesson cache. */
    fun cachedContentBytes(): Long =
        readableDatabase.query("SELECT COALESCE(SUM(LENGTH(CAST(content_json AS BLOB))),0) FROM cached_lessons", emptyArray()).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }

    fun cacheLimitBytes(): Long = appContext.getSharedPreferences("jia_offline_settings", Context.MODE_PRIVATE)
        .getLong(KEY_CACHE_LIMIT_BYTES, DEFAULT_CACHE_LIMIT_BYTES).coerceAtLeast(MIN_CACHE_LIMIT_BYTES)

    /** Learner-selected limit; enforce immediately while preserving the most recently saved lesson. */
    fun setCacheLimitBytes(limitBytes: Long) {
        require(limitBytes >= MIN_CACHE_LIMIT_BYTES) { "Offline cache limit must be at least 1 MiB" }
        appContext.getSharedPreferences("jia_offline_settings", Context.MODE_PRIVATE)
            .edit().putLong(KEY_CACHE_LIMIT_BYTES, limitBytes).apply()
        evictToCacheLimit()
    }

    /** Removes least-recently-cached lessons only; pending learner activity is stored separately. */
    fun evictToCacheLimit(protectedSlug: String? = null): Int {
        val db = writableDatabase
        var removed = 0
        db.beginTransaction()
        try {
            var bytes = cachedContentBytes()
            val limit = cacheLimitBytes()
            if (bytes > limit) {
                db.query("SELECT slug, LENGTH(CAST(content_json AS BLOB)) FROM cached_lessons ORDER BY cached_at ASC, slug ASC", emptyArray()).use { cursor ->
                    while (bytes > limit && cursor.moveToNext()) {
                        val slug = cursor.getString(0)
                        val size = cursor.getLong(1)
                        if (slug == protectedSlug) continue
                        removed += db.delete("cached_lessons", "slug=?", arrayOf(slug))
                        if (removed > 0) bytes -= size
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return removed
    }

    fun cachedLesson(slug: String): LessonDetail? {
        val row = database.cachedLessonDao().find(slug) ?: return null
        if (!OfflineContentChecksum.sha256(row.contentJson).equals(row.checksum, ignoreCase = true)) {
            database.cachedLessonDao().delete(slug)
            return null
        }
        return LessonDetail(row.id, row.slug, row.title, row.summary, row.difficulty,
            row.contentRevision, row.sourceType, row.contentJson)
    }

    fun hasCachedLesson(slug: String, expectedChecksum: String): Boolean =
        readableDatabase.query("SELECT content_json, checksum FROM cached_lessons WHERE slug=?", arrayOf(slug)).use { cursor ->
            if (!cursor.moveToFirst()) false
            else {
                val content = cursor.getString(0)
                OfflineContentChecksum.sha256(content).equals(cursor.getString(1), ignoreCase = true) &&
                    cursor.getString(1).equals(expectedChecksum, ignoreCase = true)
            }
        }

    /** Stores a lightweight local scroll offset for instant reader restoration. */
    fun saveReadingOffset(lessonSlug: String, offsetPx: Int) {
        require(lessonSlug.isNotBlank())
        appContext.getSharedPreferences("jia_reading_positions", Context.MODE_PRIVATE)
            .edit().putInt("offset:$lessonSlug", offsetPx.coerceAtLeast(0)).apply()
    }

    fun readingOffset(lessonSlug: String): Int =
        appContext.getSharedPreferences("jia_reading_positions", Context.MODE_PRIVATE)
            .getInt("offset:$lessonSlug", 0).coerceAtLeast(0)

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
            var existing: ProgressSnapshot? = null
            db.query("SELECT content_revision, progress_percent, last_position_json, updated_at FROM pending_progress WHERE lesson_slug=?", arrayOf(lessonSlug)).use { cursor ->
                if (cursor.moveToFirst()) existing = ProgressSnapshot(cursor.getInt(0), cursor.getDouble(1), cursor.getString(2), cursor.getLong(3))
            }
            val merged = ProgressMergePolicy.merge(existing, contentRevision, progressPercent, lastPositionJson, System.currentTimeMillis())
            val values = ContentValues().apply {
                put("lesson_slug", lessonSlug)
                put("content_revision", merged.contentRevision)
                put("progress_percent", merged.progressPercent)
                put("last_position_json", merged.lastPositionJson)
                put("updated_at", merged.updatedAt)
            }
            db.insert("pending_progress", SQLiteDatabase.CONFLICT_REPLACE, values)
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
            val existing = db.query("SELECT updated_at FROM offline_bookmarks WHERE lesson_slug=?", arrayOf(lessonSlug)).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            val updatedAt = maxOf(now, existing + 1L)
            val values = ContentValues().apply {
                put("lesson_slug", lessonSlug)
                put("is_bookmarked", if (bookmarked) 1 else 0)
                put("updated_at", updatedAt)
            }
            db.insert("offline_bookmarks", SQLiteDatabase.CONFLICT_REPLACE, values)
            db.insert("pending_bookmarks", SQLiteDatabase.CONFLICT_REPLACE, values)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        LearnerSyncScheduler.enqueue(context, baseUrl)
    }

    fun isBookmarked(lessonSlug: String): Boolean =
        readableDatabase.query("SELECT is_bookmarked FROM offline_bookmarks WHERE lesson_slug=?", arrayOf(lessonSlug)).use {
            it.moveToFirst() && it.getInt(0) == 1
        }

    fun bookmarkedSlugs(): Set<String> =
        readableDatabase.query("SELECT lesson_slug FROM offline_bookmarks WHERE is_bookmarked=1", emptyArray()).use {
            buildSet { while (it.moveToNext()) add(it.getString(0)) }
        }

    fun pendingBookmarks(): List<PendingBookmark> =
        readableDatabase.query("SELECT lesson_slug, is_bookmarked, updated_at FROM pending_bookmarks ORDER BY updated_at ASC", emptyArray()).use { cursor ->
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
        writableDatabase.insert("sync_diagnostics",SQLiteDatabase.CONFLICT_IGNORE,values)
        writableDatabase.update("sync_diagnostics",SQLiteDatabase.CONFLICT_NONE,values,"id=1",null)
    }

    fun recordSyncSuccess(progressCount: Int, bookmarkCount: Int) {
        writableDatabase.insert("sync_diagnostics",SQLiteDatabase.CONFLICT_IGNORE,
            ContentValues().apply { put("id",1) })
        val values = ContentValues().apply {
            put("last_success_at",System.currentTimeMillis()); putNull("last_error")
            put("synced_progress_count",progressCount); put("synced_bookmark_count",bookmarkCount)
        }
        writableDatabase.update("sync_diagnostics",SQLiteDatabase.CONFLICT_NONE,values,"id=1",null)
    }

    fun recordSyncFailure(message: String) {
        val values = ContentValues().apply {
            put("id",1); put("last_attempt_at",System.currentTimeMillis())
            put("last_error",message.take(240))
        }
        writableDatabase.insert("sync_diagnostics",SQLiteDatabase.CONFLICT_IGNORE,values)
        writableDatabase.update("sync_diagnostics",SQLiteDatabase.CONFLICT_NONE,values,"id=1",null)
    }

    fun syncDiagnostics(): OfflineSyncDiagnostics? =
        readableDatabase.query("SELECT last_attempt_at, last_success_at, last_error, synced_progress_count, synced_bookmark_count FROM sync_diagnostics WHERE id=1", emptyArray()).use { cursor ->
            if (!cursor.moveToFirst()) null else OfflineSyncDiagnostics(
                if (cursor.isNull(0)) null else cursor.getLong(0),
                if (cursor.isNull(1)) null else cursor.getLong(1),
                cursor.getString(2),cursor.getInt(3),cursor.getInt(4))
        }

    fun pendingProgress(): List<PendingLessonProgress> =
        readableDatabase.query("SELECT lesson_slug, content_revision, progress_percent, last_position_json, updated_at FROM pending_progress ORDER BY updated_at ASC", emptyArray()).use { cursor ->
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
        private const val KEY_CACHE_LIMIT_BYTES = "offline_cache_limit_bytes"
        const val MIN_CACHE_LIMIT_BYTES = 1L * 1024L * 1024L
        const val DEFAULT_CACHE_LIMIT_BYTES = 64L * 1024L * 1024L
    }
}

/** Canonical SHA-256 representation used for offline lesson integrity checks. */
internal object OfflineContentChecksum {
    fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
