package com.jia.academy.app.learning

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class JIAOfflineRoomMigrationTest {
    private lateinit var context: Context
    private lateinit var room: JIAOfflineRoomDatabase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("jia-offline-learning.db")
        val legacy = object : SQLiteOpenHelper(context, "jia-offline-learning.db", null, 2) {
            override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
                db.execSQL("CREATE TABLE cached_lessons (slug TEXT PRIMARY KEY NOT NULL, id TEXT NOT NULL, title TEXT NOT NULL, summary TEXT, difficulty TEXT NOT NULL, content_revision INTEGER NOT NULL, source_type TEXT NOT NULL, content_json TEXT NOT NULL, checksum TEXT NOT NULL, cached_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE pending_progress (lesson_slug TEXT PRIMARY KEY NOT NULL, content_revision INTEGER NOT NULL, progress_percent REAL NOT NULL, last_position_json TEXT, updated_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE offline_bookmarks (lesson_slug TEXT PRIMARY KEY NOT NULL, is_bookmarked INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE pending_bookmarks (lesson_slug TEXT PRIMARY KEY NOT NULL, is_bookmarked INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE sync_diagnostics (id INTEGER PRIMARY KEY CHECK(id=1), last_attempt_at INTEGER, last_success_at INTEGER, last_error TEXT, synced_progress_count INTEGER NOT NULL DEFAULT 0, synced_bookmark_count INTEGER NOT NULL DEFAULT 0)")
            }
            override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val db = legacy.writableDatabase
        db.execSQL("INSERT INTO cached_lessons VALUES (?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>("java-memory","lesson-1","JMM",null,"PROFESSIONAL",2,"ADMIN_AUTHORED","{}","checksum",123L))
        db.execSQL("INSERT INTO pending_progress VALUES (?,?,?,?,?)",
            arrayOf<Any?>("java-memory",2,65.0,"{\"block\":4}",456L))
        db.execSQL("INSERT INTO offline_bookmarks VALUES (?,?,?)", arrayOf<Any?>("java-memory",1,789L))
        db.execSQL("INSERT INTO pending_bookmarks VALUES (?,?,?)", arrayOf<Any?>("java-memory",1,789L))
        db.execSQL("INSERT INTO sync_diagnostics VALUES (?,?,?,?,?,?)", arrayOf<Any?>(1,100L,90L,null,3,2))
        legacy.close()

        room = Room.databaseBuilder(context, JIAOfflineRoomDatabase::class.java, "jia-offline-learning.db")
            .addMigrations(JIAOfflineRoomDatabase.MIGRATION_1_2, JIAOfflineRoomDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        room.close()
        context.deleteDatabase("jia-offline-learning.db")
    }

    @Test
    fun versionTwoSQLiteDataSurvivesRoomAdoption() {
        val cached = room.cachedLessonDao().find("java-memory")
        assertNotNull(cached)
        assertEquals(2, cached?.contentRevision)
        assertEquals(65.0, room.progressDao().pending().single().progressPercent, 0.0)
        assertEquals(listOf("java-memory"), room.bookmarkDao().bookmarkedSlugs())
        assertEquals(3, room.diagnosticsDao().get()?.syncedProgressCount)
        assertEquals(true, room.bookmarkDao().pending().single().isBookmarked)
    }
}
