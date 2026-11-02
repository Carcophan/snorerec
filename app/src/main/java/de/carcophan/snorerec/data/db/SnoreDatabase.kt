package de.carcophan.snorerec.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreEvent
import de.carcophan.snorerec.data.model.SnoreIntensity
import java.io.File

class SnoreDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "snore_rec.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_SESSIONS = "night_sessions"
        private const val COL_SESSION_ID = "id"
        private const val COL_SESSION_START = "start_time"
        private const val COL_SESSION_END = "end_time"
        private const val COL_SESSION_SNORE_DURATION = "total_snore_duration_ms"
        private const val COL_SESSION_SNORE_COUNT = "snore_count"
        private const val COL_SESSION_PEAK_DB = "peak_db"
        private const val COL_SESSION_AVG_DB = "avg_db"
        private const val COL_SESSION_NOTE = "note"
        private const val COL_SESSION_REMEDIES = "remedies"
        private const val COL_SESSION_FACTORS = "factors"
        private const val COL_SESSION_SLEEP_RATING = "sleep_rating"
        private const val COL_SESSION_MILD_DURATION = "mild_duration_ms"
        private const val COL_SESSION_MODERATE_DURATION = "moderate_duration_ms"
        private const val COL_SESSION_HEAVY_DURATION = "heavy_duration_ms"
        private const val COL_SESSION_DELAYED_START = "delayed_start_minutes"
        private const val COL_SESSION_SNORE_SCORE = "snore_score"

        private const val TABLE_EVENTS = "snore_events"
        private const val COL_EVENT_ID = "id"
        private const val COL_EVENT_SESSION_ID = "session_id"
        private const val COL_EVENT_TIMESTAMP = "timestamp"
        private const val COL_EVENT_DURATION = "duration_ms"
        private const val COL_EVENT_PEAK_DB = "peak_db"
        private const val COL_EVENT_AVG_DB = "avg_db"
        private const val COL_EVENT_AUDIO_PATH = "audio_file_path"
        private const val COL_EVENT_SCORE = "snore_score"
        private const val COL_EVENT_BOOKMARKED = "is_bookmarked"

        private const val LIST_SEPARATOR = "||"
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
                $COL_SESSION_NOTE TEXT NOT NULL DEFAULT '',
                $COL_SESSION_REMEDIES TEXT NOT NULL DEFAULT '',
                $COL_SESSION_FACTORS TEXT NOT NULL DEFAULT '',
                $COL_SESSION_SLEEP_RATING INTEGER NOT NULL DEFAULT 0,
                $COL_SESSION_MILD_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_SESSION_MODERATE_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_SESSION_HEAVY_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_SESSION_DELAYED_START INTEGER NOT NULL DEFAULT 0,
                $COL_SESSION_SNORE_SCORE INTEGER NOT NULL DEFAULT -1
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
                $COL_EVENT_BOOKMARKED INTEGER NOT NULL DEFAULT 0,
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
        if (oldVersion < 2) {
            safeAddColumn(db, TABLE_SESSIONS, "$COL_SESSION_REMEDIES TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, TABLE_SESSIONS, "$COL_SESSION_FACTORS TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, TABLE_SESSIONS, "$COL_SESSION_SLEEP_RATING INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, TABLE_SESSIONS, "$COL_SESSION_MILD_DURATION INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, TABLE_SESSIONS, "$COL_SESSION_MODERATE_DURATION INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, TABLE_SESSIONS, "$COL_SESSION_HEAVY_DURATION INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, TABLE_SESSIONS, "$COL_SESSION_DELAYED_START INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, TABLE_SESSIONS, "$COL_SESSION_SNORE_SCORE INTEGER NOT NULL DEFAULT -1")
            safeAddColumn(db, TABLE_EVENTS, "$COL_EVENT_BOOKMARKED INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun safeAddColumn(db: SQLiteDatabase, table: String, columnDef: String) {
        try {
            db.execSQL("ALTER TABLE $table ADD COLUMN $columnDef")
        } catch (_: Exception) {}
    }

    fun createSession(
        startTime: Long,
        remedies: List<String> = emptyList(),
        factors: List<String> = emptyList(),
        delayedStartMinutes: Int = 0
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SESSION_START, startTime)
            putNull(COL_SESSION_END)
            put(COL_SESSION_SNORE_DURATION, 0L)
            put(COL_SESSION_SNORE_COUNT, 0)
            put(COL_SESSION_PEAK_DB, 0.0f)
            put(COL_SESSION_AVG_DB, 0.0f)
            put(COL_SESSION_NOTE, "")
            put(COL_SESSION_REMEDIES, encodeList(remedies))
            put(COL_SESSION_FACTORS, encodeList(factors))
            put(COL_SESSION_SLEEP_RATING, 0)
            put(COL_SESSION_MILD_DURATION, 0L)
            put(COL_SESSION_MODERATE_DURATION, 0L)
            put(COL_SESSION_HEAVY_DURATION, 0L)
            put(COL_SESSION_DELAYED_START, delayedStartMinutes)
            put(COL_SESSION_SNORE_SCORE, 0)
        }
        return db.insert(TABLE_SESSIONS, null, values)
    }

    fun endSession(sessionId: Long, endTime: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SESSION_END, endTime)
        }
        db.update(TABLE_SESSIONS, values, "$COL_SESSION_ID = ?", arrayOf(sessionId.toString()))
        updateSessionAggregates(db, sessionId)
    }

    fun updateSessionMetadata(
        sessionId: Long,
        remedies: List<String>? = null,
        factors: List<String>? = null,
        sleepRating: Int? = null,
        note: String? = null
    ) {
        val db = writableDatabase
        val values = ContentValues()
        remedies?.let { values.put(COL_SESSION_REMEDIES, encodeList(it)) }
        factors?.let { values.put(COL_SESSION_FACTORS, encodeList(it)) }
        sleepRating?.let { values.put(COL_SESSION_SLEEP_RATING, it.coerceIn(0, 5)) }
        note?.let { values.put(COL_SESSION_NOTE, it) }
        if (values.size() > 0) {
            db.update(TABLE_SESSIONS, values, "$COL_SESSION_ID = ?", arrayOf(sessionId.toString()))
        }
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
            put(COL_EVENT_BOOKMARKED, if (event.isBookmarked) 1 else 0)
        }
        val eventId = db.insert(TABLE_EVENTS, null, values)
        updateSessionAggregates(db, event.sessionId)
        return eventId
    }

    fun toggleEventBookmark(eventId: Long, isBookmarked: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_EVENT_BOOKMARKED, if (isBookmarked) 1 else 0)
        }
        db.update(TABLE_EVENTS, values, "$COL_EVENT_ID = ?", arrayOf(eventId.toString()))
    }

    fun updateSnoreEvent(event: SnoreEvent) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_EVENT_TIMESTAMP, event.timestamp)
            put(COL_EVENT_DURATION, event.durationMs)
            put(COL_EVENT_PEAK_DB, event.peakDb)
            put(COL_EVENT_AVG_DB, event.avgDb)
            put(COL_EVENT_AUDIO_PATH, event.audioFilePath)
            put(COL_EVENT_SCORE, event.snoreScore)
            put(COL_EVENT_BOOKMARKED, if (event.isBookmarked) 1 else 0)
        }
        db.update(TABLE_EVENTS, values, "$COL_EVENT_ID = ?", arrayOf(event.id.toString()))
        updateSessionAggregates(db, event.sessionId)
    }

    fun deleteSnoreEventRecordOnly(eventId: Long): Long? {
        val db = writableDatabase
        var sessionId: Long? = null
        db.query(
            TABLE_EVENTS,
            arrayOf(COL_EVENT_SESSION_ID),
            "$COL_EVENT_ID = ?",
            arrayOf(eventId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_SESSION_ID))
            }
        }
        db.delete(TABLE_EVENTS, "$COL_EVENT_ID = ?", arrayOf(eventId.toString()))
        sessionId?.let { updateSessionAggregates(db, it) }
        return sessionId
    }

    fun deleteSnoreEvent(eventId: Long): Long? {
        val db = writableDatabase
        var sessionId: Long? = null
        db.query(
            TABLE_EVENTS,
            arrayOf(COL_EVENT_SESSION_ID, COL_EVENT_AUDIO_PATH),
            "$COL_EVENT_ID = ?",
            arrayOf(eventId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_SESSION_ID))
                val pathCol = cursor.getColumnIndexOrThrow(COL_EVENT_AUDIO_PATH)
                if (!cursor.isNull(pathCol)) {
                    val path = cursor.getString(pathCol)
                    try {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
            }
        }
        db.delete(TABLE_EVENTS, "$COL_EVENT_ID = ?", arrayOf(eventId.toString()))
        sessionId?.let { updateSessionAggregates(db, it) }
        return sessionId
    }

    private fun updateSessionAggregates(db: SQLiteDatabase, sessionId: Long) {
        val events = mutableListOf<SnoreEvent>()
        db.query(
            TABLE_EVENTS,
            null,
            "$COL_EVENT_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                events.add(cursorToEvent(cursor))
            }
        }

        val count = events.size
        val totalDuration = events.sumOf { it.durationMs }
        val maxDb = events.maxOfOrNull { it.peakDb } ?: 0.0f
        val avgDb = if (count > 0) events.map { it.avgDb }.average().toFloat() else 0.0f

        var mildDur = 0L
        var modDur = 0L
        var heavyDur = 0L
        events.forEach { ev ->
            when (ev.intensity) {
                SnoreIntensity.MILD -> mildDur += ev.durationMs
                SnoreIntensity.MODERATE -> modDur += ev.durationMs
                SnoreIntensity.HEAVY -> heavyDur += ev.durationMs
            }
        }

        var sessionStart = System.currentTimeMillis()
        var sessionEnd: Long? = null
        db.query(
            TABLE_SESSIONS,
            arrayOf(COL_SESSION_START, COL_SESSION_END),
            "$COL_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            null
        ).use { c ->
            if (c.moveToFirst()) {
                sessionStart = c.getLong(0)
                if (!c.isNull(1)) sessionEnd = c.getLong(1)
            }
        }

        val sleepDurationMs = ((sessionEnd ?: System.currentTimeMillis()) - sessionStart).coerceAtLeast(1000L)
        val computedScore = NightSession.computeSnoreScore(
            totalSleepMs = sleepDurationMs,
            totalSnoreMs = totalDuration,
            mildMs = mildDur,
            moderateMs = modDur,
            heavyMs = heavyDur,
            avgDb = avgDb,
            peakDb = maxDb
        )

        val values = ContentValues().apply {
            put(COL_SESSION_SNORE_COUNT, count)
            put(COL_SESSION_SNORE_DURATION, totalDuration)
            put(COL_SESSION_PEAK_DB, maxDb)
            put(COL_SESSION_AVG_DB, avgDb)
            put(COL_SESSION_MILD_DURATION, mildDur)
            put(COL_SESSION_MODERATE_DURATION, modDur)
            put(COL_SESSION_HEAVY_DURATION, heavyDur)
            put(COL_SESSION_SNORE_SCORE, computedScore)
        }
        db.update(TABLE_SESSIONS, values, "$COL_SESSION_ID = ?", arrayOf(sessionId.toString()))
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
        if (olderThanDays <= 0) return 0
        val cutoffTime = System.currentTimeMillis() - (olderThanDays.toLong() * 24 * 3600 * 1000L)
        val db = writableDatabase
        var deletedFilesCount = 0

        // Note: Bookmarked (starred) events are protected from automatic cleanup just like in SnoreLab!
        db.query(
            TABLE_EVENTS,
            arrayOf(COL_EVENT_ID, COL_EVENT_AUDIO_PATH),
            "$COL_EVENT_TIMESTAMP < ? AND $COL_EVENT_AUDIO_PATH IS NOT NULL AND $COL_EVENT_BOOKMARKED = 0",
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

    fun deleteAllSessions() {
        val all = getAllSessions()
        for (s in all) {
            deleteSession(s.id)
        }
    }

    fun insertCompleteHistoricalSession(session: NightSession, events: List<SnoreEvent>): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SESSION_START, session.startTime)
            if (session.endTime != null) put(COL_SESSION_END, session.endTime) else putNull(COL_SESSION_END)
            put(COL_SESSION_SNORE_DURATION, session.totalSnoreDurationMs)
            put(COL_SESSION_SNORE_COUNT, session.snoreCount)
            put(COL_SESSION_PEAK_DB, session.peakDb)
            put(COL_SESSION_AVG_DB, session.avgDb)
            put(COL_SESSION_NOTE, session.note)
            put(COL_SESSION_REMEDIES, encodeList(session.remedies))
            put(COL_SESSION_FACTORS, encodeList(session.factors))
            put(COL_SESSION_SLEEP_RATING, session.sleepRating)
            put(COL_SESSION_MILD_DURATION, session.mildDurationMs)
            put(COL_SESSION_MODERATE_DURATION, session.moderateDurationMs)
            put(COL_SESSION_HEAVY_DURATION, session.heavyDurationMs)
            put(COL_SESSION_DELAYED_START, session.delayedStartMinutes)
            put(COL_SESSION_SNORE_SCORE, session.snoreScore)
        }
        val sessionId = db.insert(TABLE_SESSIONS, null, values)
        for (ev in events) {
            val evValues = ContentValues().apply {
                put(COL_EVENT_SESSION_ID, sessionId)
                put(COL_EVENT_TIMESTAMP, ev.timestamp)
                put(COL_EVENT_DURATION, ev.durationMs)
                put(COL_EVENT_PEAK_DB, ev.peakDb)
                put(COL_EVENT_AVG_DB, ev.avgDb)
                put(COL_EVENT_AUDIO_PATH, ev.audioFilePath)
                put(COL_EVENT_SCORE, ev.snoreScore)
                put(COL_EVENT_BOOKMARKED, if (ev.isBookmarked) 1 else 0)
            }
            db.insert(TABLE_EVENTS, null, evValues)
        }
        updateSessionAggregates(db, sessionId)
        return sessionId
    }

    private fun cursorToSession(cursor: Cursor): NightSession {
        val endCol = cursor.getColumnIndexOrThrow(COL_SESSION_END)
        val endTime = if (cursor.isNull(endCol)) null else cursor.getLong(endCol)

        val remediesStr = getStringOrDefault(cursor, COL_SESSION_REMEDIES, "")
        val factorsStr = getStringOrDefault(cursor, COL_SESSION_FACTORS, "")
        val rating = getIntOrDefault(cursor, COL_SESSION_SLEEP_RATING, 0)
        val mildDur = getLongOrDefault(cursor, COL_SESSION_MILD_DURATION, 0L)
        val modDur = getLongOrDefault(cursor, COL_SESSION_MODERATE_DURATION, 0L)
        val heavyDur = getLongOrDefault(cursor, COL_SESSION_HEAVY_DURATION, 0L)
        val delayedStart = getIntOrDefault(cursor, COL_SESSION_DELAYED_START, 0)
        val storedScore = getIntOrDefault(cursor, COL_SESSION_SNORE_SCORE, -1)

        return NightSession(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_ID)),
            startTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_START)),
            endTime = endTime,
            totalSnoreDurationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_SNORE_DURATION)),
            snoreCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SESSION_SNORE_COUNT)),
            peakDb = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_SESSION_PEAK_DB)),
            avgDb = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_SESSION_AVG_DB)),
            note = cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_NOTE)) ?: "",
            remedies = decodeList(remediesStr),
            factors = decodeList(factorsStr),
            sleepRating = rating,
            mildDurationMs = mildDur,
            moderateDurationMs = modDur,
            heavyDurationMs = heavyDur,
            delayedStartMinutes = delayedStart,
            storedSnoreScore = storedScore
        )
    }

    private fun cursorToEvent(cursor: Cursor): SnoreEvent {
        val pathCol = cursor.getColumnIndexOrThrow(COL_EVENT_AUDIO_PATH)
        val audioPath = if (cursor.isNull(pathCol)) null else cursor.getString(pathCol)
        val bookmarked = getIntOrDefault(cursor, COL_EVENT_BOOKMARKED, 0) == 1

        return SnoreEvent(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_ID)),
            sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_SESSION_ID)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_TIMESTAMP)),
            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EVENT_DURATION)),
            peakDb = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_EVENT_PEAK_DB)),
            avgDb = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_EVENT_AVG_DB)),
            audioFilePath = audioPath,
            snoreScore = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_EVENT_SCORE)),
            isBookmarked = bookmarked
        )
    }

    private fun getStringOrDefault(cursor: Cursor, colName: String, default: String): String {
        val idx = cursor.getColumnIndex(colName)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else default
    }

    private fun getIntOrDefault(cursor: Cursor, colName: String, default: Int): Int {
        val idx = cursor.getColumnIndex(colName)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getInt(idx) else default
    }

    private fun getLongOrDefault(cursor: Cursor, colName: String, default: Long): Long {
        val idx = cursor.getColumnIndex(colName)
        return if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else default
    }

    private fun encodeList(items: List<String>): String =
        items.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(LIST_SEPARATOR)

    private fun decodeList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split(LIST_SEPARATOR).filter { it.isNotBlank() }
}
