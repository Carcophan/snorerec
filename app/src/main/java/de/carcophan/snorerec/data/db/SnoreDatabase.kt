package de.carcophan.snorerec.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreEvent
import java.io.File

class SnoreDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "snore_rec.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_SESSIONS = "night_sessions"
        private const val COL_SESSION_ID = "id"
        private const val COL_SESSION_START = "start_time"
        private const val COL_SESSION_END = "end_time"
        private const val COL_SESSION_SNORE_DURATION = "total_snore_duration_ms"
        private const val COL_SESSION_SNORE_COUNT = "snore_count"
        private const val COL_SESSION_PEAK_DB = "peak_db"
        private const val COL_SESSION_AVG_DB = "avg_db"
        private const val COL_SESSION_NOTE = "note"

        private const val TABLE_EVENTS = "snore_events"
        private const val COL_EVENT_ID = "id"
        private const val COL_EVENT_SESSION_ID = "session_id"
        private const val COL_EVENT_TIMESTAMP = "timestamp"
        private const val COL_EVENT_DURATION = "duration_ms"
        private const val COL_EVENT_PEAK_DB = "peak_db"
        private const val COL_EVENT_AVG_DB = "avg_db"
        private const val COL_EVENT_AUDIO_PATH = "audio_file_path"
        private const val COL_EVENT_SCORE = "snore_score"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SESSIONS (
                $COL_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_START INTEGER NOT NULL,
                $COL_SESSION_END INTEGER,
                $COL_SESSION_SNORE_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_SESSION_SNORE_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_SESSION_PEAK_DB REAL NOT NULL DEFAULT 0.0,
                $COL_SESSION_AVG_DB REAL NOT NULL DEFAULT 0.0,
                $COL_SESSION_NOTE TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_EVENTS (
                $COL_EVENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EVENT_SESSION_ID INTEGER NOT NULL,
                $COL_EVENT_TIMESTAMP INTEGER NOT NULL,
                $COL_EVENT_DURATION INTEGER NOT NULL,
                $COL_EVENT_PEAK_DB REAL NOT NULL,
                $COL_EVENT_AVG_DB REAL NOT NULL,
                $COL_EVENT_AUDIO_PATH TEXT,
                $COL_EVENT_SCORE REAL NOT NULL DEFAULT 0.0,
                FOREIGN KEY ($COL_EVENT_SESSION_ID) REFERENCES $TABLE_SESSIONS($COL_SESSION_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_events_session ON $TABLE_EVENTS($COL_EVENT_SESSION_ID)")
        db.execSQL("CREATE INDEX idx_events_time ON $TABLE_EVENTS($COL_EVENT_TIMESTAMP)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    fun createSession(startTime: Long): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SESSION_START, startTime)
            putNull(COL_SESSION_END)
            put(COL_SESSION_SNORE_DURATION, 0L)
            put(COL_SESSION_SNORE_COUNT, 0)
            put(COL_SESSION_PEAK_DB, 0.0f)
            put(COL_SESSION_AVG_DB, 0.0f)
            put(COL_SESSION_NOTE, "")
        }
        return db.insert(TABLE_SESSIONS, null, values)
    }

    fun endSession(sessionId: Long, endTime: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SESSION_END, endTime)
        }
        db.update(TABLE_SESSIONS, values, "$COL_SESSION_ID = ?", arrayOf(sessionId.toString()))
    }

    fun insertSnoreEvent(event: SnoreEvent): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_EVENT_SESSION_ID, event.sessionId)
            put(COL_EVENT_TIMESTAMP, event.timestamp)
            put(COL_EVENT_DURATION, event.durationMs)
            put(COL_EVENT_PEAK_DB, event.peakDb)
            put(COL_EVENT_AVG_DB, event.avgDb)
            put(COL_EVENT_AUDIO_PATH, event.audioFilePath)
            put(COL_EVENT_SCORE, event.snoreScore)
        }
        val eventId = db.insert(TABLE_EVENTS, null, values)

        // Update aggregated session stats
        updateSessionAggregates(db, event.sessionId)
        return eventId
    }

    private fun updateSessionAggregates(db: SQLiteDatabase, sessionId: Long) {
        val query = """
            SELECT 
                COUNT(*) as cnt,
                COALESCE(SUM($COL_EVENT_DURATION), 0) as total_dur,
                COALESCE(MAX($COL_EVENT_PEAK_DB), 0.0) as max_db,
                COALESCE(AVG($COL_EVENT_AVG_DB), 0.0) as avg_db
            FROM $TABLE_EVENTS
            WHERE $COL_EVENT_SESSION_ID = ?
        """.trimIndent()

        db.rawQuery(query, arrayOf(sessionId.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                val count = cursor.getInt(cursor.getColumnIndexOrThrow("cnt"))
                val totalDuration = cursor.getLong(cursor.getColumnIndexOrThrow("total_dur"))
                val maxDb = cursor.getFloat(cursor.getColumnIndexOrThrow("max_db"))
                val avgDb = cursor.getFloat(cursor.getColumnIndexOrThrow("avg_db"))

                val values = ContentValues().apply {
                    put(COL_SESSION_SNORE_COUNT, count)
                    put(COL_SESSION_SNORE_DURATION, totalDuration)
                    put(COL_SESSION_PEAK_DB, maxDb)
                    put(COL_SESSION_AVG_DB, avgDb)
                }
                db.update(TABLE_SESSIONS, values, "$COL_SESSION_ID = ?", arrayOf(sessionId.toString()))
            }
        }
    }

    fun getSession(sessionId: Long): NightSession? {
        val db = readableDatabase
        db.query(
            TABLE_SESSIONS,
            null,
            "$COL_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToSession(cursor)
            }
        }
        return null
    }

    fun getLatestSession(): NightSession? {
        val db = readableDatabase
        db.query(
            TABLE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "$COL_SESSION_START DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToSession(cursor)
            }
        }
        return null
    }

    fun getAllSessions(): List<NightSession> {
        val list = mutableListOf<NightSession>()
        val db = readableDatabase
        db.query(
            TABLE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "$COL_SESSION_START DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToSession(cursor))
            }
        }
        return list
    }

    fun getEventsForSession(sessionId: Long): List<SnoreEvent> {
        val list = mutableListOf<SnoreEvent>()
        val db = readableDatabase
        db.query(
            TABLE_EVENTS,
            null,
            "$COL_EVENT_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            "$COL_EVENT_TIMESTAMP ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToEvent(cursor))
            }
        }
        return list
    }

    fun deleteOldAudioFiles(olderThanDays: Int = 14): Int {
        val cutoffTime = System.currentTimeMillis() - (olderThanDays.toLong() * 24 * 3600 * 1000L)
        val db = writableDatabase
        var deletedFilesCount = 0

        db.query(
            TABLE_EVENTS,
            arrayOf(COL_EVENT_ID, COL_EVENT_AUDIO_PATH),
            "$COL_EVENT_TIMESTAMP < ? AND $COL_EVENT_AUDIO_PATH IS NOT NULL",
            arrayOf(cutoffTime.toString()),
            null,
            null,
            null
        ).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(COL_EVENT_ID)
            val pathCol = cursor.getColumnIndexOrThrow(COL_EVENT_AUDIO_PATH)
            while (cursor.moveToNext()) {
                val eventId = cursor.getLong(idCol)
                val path = cursor.getString(pathCol)
                if (path != null) {
                    try {
                        val file = File(path)
                        if (file.exists()) file.delete()
                        deletedFilesCount++
                    } catch (_: Exception) {}
                }
                val values = ContentValues().apply {
                    putNull(COL_EVENT_AUDIO_PATH)
                }
                db.update(TABLE_EVENTS, values, "$COL_EVENT_ID = ?", arrayOf(eventId.toString()))
            }
        }
        return deletedFilesCount
    }

    fun deleteSession(sessionId: Long) {
        val events = getEventsForSession(sessionId)
        for (event in events) {
            event.audioFilePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
        }
        val db = writableDatabase
        db.delete(TABLE_EVENTS, "$COL_EVENT_SESSION_ID = ?", arrayOf(sessionId.toString()))
        db.delete(TABLE_SESSIONS, "$COL_SESSION_ID = ?", arrayOf(sessionId.toString()))
    }

    private fun cursorToSession(cursor: Cursor): NightSession {
        val endCol = cursor.getColumnIndexOrThrow(COL_SESSION_END)
        val endTime = if (cursor.isNull(endCol)) null else cursor.getLong(endCol)

        return NightSession(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_ID)),
            startTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_START)),
            endTime = endTime,
            totalSnoreDurationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_SNORE_DURATION)),
            snoreCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SESSION_SNORE_COUNT)),
            peakDb = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_SESSION_PEAK_DB)),
            avgDb = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_SESSION_AVG_DB)),
            note = cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_NOTE)) ?: ""
        )
    }

    private fun cursorToEvent(cursor: Cursor): SnoreEvent {
        val pathCol = cursor.getColumnIndexOrThrow(COL_EVENT_AUDIO_PATH)
        val audioPath = if (cursor.isNull(pathCol)) null else cursor.getString(pathCol)

        return SnoreEvent(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_ID)),
            sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_SESSION_ID)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_TIMESTAMP)),
            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_DURATION)),
            peakDb = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_EVENT_PEAK_DB)),
            avgDb = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_EVENT_AVG_DB)),
            audioFilePath = audioPath,
            snoreScore = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_EVENT_SCORE))
        )
    }
}
