package com.jia.academy.app.learning

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Entity(tableName = "cached_lessons", primaryKeys = ["slug"])
data class CachedLessonEntity(
    val slug: String,
    val id: String,
    val title: String,
    val summary: String?,
    val difficulty: String,
    @ColumnInfo(name = "content_revision") val contentRevision: Int,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "content_json") val contentJson: String,
    val checksum: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

@Entity(tableName = "pending_progress", primaryKeys = ["lesson_slug"])
data class PendingProgressEntity(
    @ColumnInfo(name = "lesson_slug") val lessonSlug: String,
    @ColumnInfo(name = "content_revision") val contentRevision: Int,
    @ColumnInfo(name = "progress_percent") val progressPercent: Double,
    @ColumnInfo(name = "last_position_json") val lastPositionJson: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "offline_bookmarks", primaryKeys = ["lesson_slug"])
data class OfflineBookmarkEntity(
    @ColumnInfo(name = "lesson_slug") val lessonSlug: String,
    @ColumnInfo(name = "is_bookmarked") val isBookmarked: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "pending_bookmarks", primaryKeys = ["lesson_slug"])
data class PendingBookmarkEntity(
    @ColumnInfo(name = "lesson_slug") val lessonSlug: String,
    @ColumnInfo(name = "is_bookmarked") val isBookmarked: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "sync_diagnostics", primaryKeys = ["id"])
data class SyncDiagnosticsEntity(
    val id: Int = 1,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long?,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "synced_progress_count", defaultValue = "0") val syncedProgressCount: Int = 0,
    @ColumnInfo(name = "synced_bookmark_count", defaultValue = "0") val syncedBookmarkCount: Int = 0
)

@Dao
interface CachedLessonDao {
    @Query("SELECT * FROM cached_lessons WHERE slug=:slug")
    fun find(slug: String): CachedLessonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replace(lesson: CachedLessonEntity)

    @Query("DELETE FROM cached_lessons WHERE slug=:slug")
    fun delete(slug: String)
}

@Dao
interface OfflineProgressDao {
    @Query("SELECT * FROM pending_progress WHERE lesson_slug=:slug")
    fun find(slug: String): PendingProgressEntity?

    @Query("SELECT * FROM pending_progress ORDER BY updated_at ASC")
    fun pending(): List<PendingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(progress: PendingProgressEntity)

    @Query("DELETE FROM pending_progress WHERE lesson_slug=:slug AND updated_at=:updatedAt")
    fun acknowledge(slug: String, updatedAt: Long): Int
}

@Dao
interface OfflineBookmarkDao {
    @Query("SELECT is_bookmarked FROM offline_bookmarks WHERE lesson_slug=:slug")
    fun isBookmarked(slug: String): Boolean?

    @Query("SELECT lesson_slug FROM offline_bookmarks WHERE is_bookmarked=1")
    fun bookmarkedSlugs(): List<String>

    @Query("SELECT * FROM pending_bookmarks ORDER BY updated_at ASC")
    fun pending(): List<PendingBookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertLocal(bookmark: OfflineBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertPending(bookmark: PendingBookmarkEntity)

    @Query("DELETE FROM pending_bookmarks WHERE lesson_slug=:slug AND updated_at=:updatedAt")
    fun acknowledge(slug: String, updatedAt: Long): Int
}

@Dao
interface OfflineSyncDiagnosticsDao {
    @Query("SELECT * FROM sync_diagnostics WHERE id=1")
    fun get(): SyncDiagnosticsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfAbsent(value: SyncDiagnosticsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replace(value: SyncDiagnosticsEntity)
}

@Database(
    entities = [
        CachedLessonEntity::class, PendingProgressEntity::class,
        OfflineBookmarkEntity::class, PendingBookmarkEntity::class, SyncDiagnosticsEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class JIAOfflineRoomDatabase : RoomDatabase() {
    abstract fun cachedLessonDao(): CachedLessonDao
    abstract fun progressDao(): OfflineProgressDao
    abstract fun bookmarkDao(): OfflineBookmarkDao
    abstract fun diagnosticsDao(): OfflineSyncDiagnosticsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS offline_bookmarks (lesson_slug TEXT PRIMARY KEY NOT NULL, is_bookmarked INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_bookmarks (lesson_slug TEXT PRIMARY KEY NOT NULL, is_bookmarked INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_diagnostics (id INTEGER PRIMARY KEY CHECK(id=1), last_attempt_at INTEGER, last_success_at INTEGER, last_error TEXT, synced_progress_count INTEGER NOT NULL DEFAULT 0, synced_bookmark_count INTEGER NOT NULL DEFAULT 0)")
            }
        }

        // Version 2 is the deployed SQLiteOpenHelper schema. Version 3 adopts Room metadata
        // without changing any existing table or learner-owned rows.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE sync_diagnostics_room_new (id INTEGER NOT NULL PRIMARY KEY, last_attempt_at INTEGER, last_success_at INTEGER, last_error TEXT, synced_progress_count INTEGER NOT NULL DEFAULT 0, synced_bookmark_count INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO sync_diagnostics_room_new (id,last_attempt_at,last_success_at,last_error,synced_progress_count,synced_bookmark_count) SELECT id,last_attempt_at,last_success_at,last_error,synced_progress_count,synced_bookmark_count FROM sync_diagnostics")
                db.execSQL("DROP TABLE sync_diagnostics")
                db.execSQL("ALTER TABLE sync_diagnostics_room_new RENAME TO sync_diagnostics")
            }
        }

        fun open(context: Context): JIAOfflineRoomDatabase =
            Room.databaseBuilder(context.applicationContext, JIAOfflineRoomDatabase::class.java,
                "jia-offline-learning.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .allowMainThreadQueries() // Legacy facade remains synchronous; callers are migrated to IO incrementally.
                .build()
    }
}
