package de.carcophan.snorerec.data.repository

import de.carcophan.snorerec.audio.AudioConfig
import de.carcophan.snorerec.audio.WavAudioWriter
import de.carcophan.snorerec.data.db.SnoreDatabase
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

data class StorageUsageInfo(
    val totalFilesCount: Int,
    val totalBytes: Long
) {
    val totalMegabytes: Float
        get() = totalBytes / (1024f * 1024f)
}

class SnoreRepository(
    private val database: SnoreDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _sessions = MutableStateFlow<List<NightSession>>(emptyList())
    val sessions: StateFlow<List<NightSession>> = _sessions.asStateFlow()

    private val _selectedSessionEvents = MutableStateFlow<List<SnoreEvent>>(emptyList())
    val selectedSessionEvents: StateFlow<List<SnoreEvent>> = _selectedSessionEvents.asStateFlow()

    private val _selectedSession = MutableStateFlow<NightSession?>(null)
    val selectedSession: StateFlow<NightSession?> = _selectedSession.asStateFlow()

    suspend fun refreshSessions() = withContext(ioDispatcher) {
        val list = database.getAllSessions()
        _sessions.value = list

        val currentSelectedId = _selectedSession.value?.id
        if (currentSelectedId != null) {
            val updated = database.getSession(currentSelectedId)
            if (updated != null) {
                _selectedSession.value = updated
                _selectedSessionEvents.value = database.getEventsForSession(currentSelectedId)
            } else if (list.isNotEmpty()) {
                selectSession(list.first().id)
            } else {
                _selectedSession.value = null
                _selectedSessionEvents.value = emptyList()
            }
        } else if (list.isNotEmpty()) {
            selectSession(list.first().id)
        }
    }

    suspend fun selectSession(sessionId: Long) = withContext(ioDispatcher) {
        val session = database.getSession(sessionId)
        val events = database.getEventsForSession(sessionId)
        _selectedSession.value = session
        _selectedSessionEvents.value = events
    }

    suspend fun startNewSession(
        startTime: Long = System.currentTimeMillis(),
        remedies: List<String> = emptyList(),
        factors: List<String> = emptyList(),
        delayedStartMinutes: Int = 0
    ): Long = withContext(ioDispatcher) {
        val id = database.createSession(
            startTime = startTime,
            remedies = remedies,
            factors = factors,
            delayedStartMinutes = delayedStartMinutes
        )
        refreshSessions()
        selectSession(id)
        id
    }

    suspend fun finishSession(sessionId: Long, endTime: Long = System.currentTimeMillis()) = withContext(ioDispatcher) {
        database.endSession(sessionId, endTime)
        refreshSessions()
    }

    suspend fun updateSessionMetadata(
        sessionId: Long,
        remedies: List<String>? = null,
        factors: List<String>? = null,
        sleepRating: Int? = null,
        note: String? = null
    ) = withContext(ioDispatcher) {
        database.updateSessionMetadata(
            sessionId = sessionId,
            remedies = remedies,
            factors = factors,
            sleepRating = sleepRating,
            note = note
        )
        refreshSessions()
    }

    suspend fun recordSnoreEvent(event: SnoreEvent): Long = withContext(ioDispatcher) {
        val id = database.insertSnoreEvent(event)
        if (_selectedSession.value?.id == event.sessionId) {
            _selectedSession.value = database.getSession(event.sessionId)
            _selectedSessionEvents.value = database.getEventsForSession(event.sessionId)
        }
        val list = database.getAllSessions()
        _sessions.value = list
        id
    }

    suspend fun toggleBookmark(event: SnoreEvent) = withContext(ioDispatcher) {
        val newState = !event.isBookmarked
        database.toggleEventBookmark(event.id, newState)
        if (_selectedSession.value?.id == event.sessionId) {
            _selectedSessionEvents.value = database.getEventsForSession(event.sessionId)
        }
    }

    suspend fun deleteSnoreEvent(eventId: Long) = withContext(ioDispatcher) {
        val sessionId = database.deleteSnoreEvent(eventId)
        refreshSessions()
        if (sessionId != null && _selectedSession.value?.id == sessionId) {
            _selectedSession.value = database.getSession(sessionId)
            _selectedSessionEvents.value = database.getEventsForSession(sessionId)
        }
    }

    suspend fun deleteSession(sessionId: Long) = withContext(ioDispatcher) {
        database.deleteSession(sessionId)
        if (_selectedSession.value?.id == sessionId) {
            _selectedSession.value = null
            _selectedSessionEvents.value = emptyList()
        }
        refreshSessions()
    }

    suspend fun deleteAllSessions() = withContext(ioDispatcher) {
        database.deleteAllSessions()
        _selectedSession.value = null
        _selectedSessionEvents.value = emptyList()
        refreshSessions()
    }

    suspend fun cleanupOldAudioFiles(olderThanDays: Int = 14): Int = withContext(ioDispatcher) {
        val count = database.deleteOldAudioFiles(olderThanDays)
        if (count > 0) {
            val currentSelectedId = _selectedSession.value?.id
            if (currentSelectedId != null) {
                _selectedSessionEvents.value = database.getEventsForSession(currentSelectedId)
            }
        }
        count
    }

    suspend fun computeStorageUsage(filesDir: File): StorageUsageInfo = withContext(ioDispatcher) {
        val root = File(filesDir, "snore_audio")
        if (!root.exists()) return@withContext StorageUsageInfo(0, 0L)
        var count = 0
        var bytes = 0L
        root.walkTopDown().forEach { f ->
            if (f.isFile) {
                count++
                bytes += f.length()
            }
        }
        StorageUsageInfo(count, bytes)
    }

    suspend fun exportSessionsToCsv(): String = withContext(ioDispatcher) {
        val all = database.getAllSessions()
        val sb = StringBuilder()
        sb.appendLine("ID;Start;Ende;Schlafdauer_Min;Schnarchdauer_Min;Schnarch_Prozent;Schnarch_Score;Schnarch_Anzahl;Spitzen_dB;Durchschnitt_dB;Sterne_Bewertung;Hilfsmittel;Faktoren;Notiz")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        for (s in all) {
            val startStr = sdf.format(java.util.Date(s.startTime))
            val endStr = s.endTime?.let { sdf.format(java.util.Date(it)) } ?: "Aktiv"
            val sleepMin = s.durationMs / 60000
            val snoreMin = s.totalSnoreDurationMs / 60000
            val pct = String.format(java.util.Locale.US, "%.1f", s.snorePercentage)
            val remedies = s.remedies.joinToString(", ")
            val factors = s.factors.joinToString(", ")
            val cleanNote = s.note.replace(";", ",").replace("\n", " ")
            sb.appendLine("${s.id};$startStr;$endStr;$sleepMin;$snoreMin;$pct;${s.snoreScore};${s.snoreCount};${s.peakDb.toInt()};${s.avgDb.toInt()};${s.sleepRating};$remedies;$factors;$cleanNote")
        }
        sb.toString()
    }

    /**
     * Generates 10 realistic historical nights with remedies, factors, sleep ratings, and synthesized
     * playable snore audio samples so the user can explore all SnoreLab features & Trends immediately.
     */
    suspend fun seedDemoData(filesDir: File) = withContext(ioDispatcher) {
        val rand = Random(42)
        val cal = Calendar.getInstance()

        data class DemoNightSpec(
            val daysAgo: Int,
            val remedies: List<String>,
            val factors: List<String>,
            val eventCount: Int,
            val baseDb: Float,
            val sleepRating: Int,
            val note: String
        )

        val specs = listOf(
            DemoNightSpec(
                daysAgo = 10,
                remedies = emptyList(),
                factors = listOf("Alkohol", "Rückenschlaf"),
                eventCount = 26,
                baseDb = 61f,
                sleepRating = 2,
                note = "Abends 2 Bier getrunken, meist auf dem Rücken geschlafen."
            ),
            DemoNightSpec(
                daysAgo = 9,
                remedies = emptyList(),
                factors = listOf("Stark übermüdet"),
                eventCount = 19,
                baseDb = 55f,
                sleepRating = 3,
                note = "Sehr spät ins Bett gegangen."
            ),
            DemoNightSpec(
                daysAgo = 8,
                remedies = listOf("Nasenpflaster"),
                factors = emptyList(),
                eventCount = 14,
                baseDb = 50f,
                sleepRating = 4,
                note = "Nasenpflaster zum ersten Mal getestet – freiere Nase."
            ),
            DemoNightSpec(
                daysAgo = 7,
                remedies = listOf("Seitenschlaf", "Nasenpflaster"),
                factors = emptyList(),
                eventCount = 9,
                baseDb = 45f,
                sleepRating = 5,
                note = "Auf der Seite geschlafen mit Nasenpflaster. Deutlich erholter!"
            ),
            DemoNightSpec(
                daysAgo = 6,
                remedies = emptyList(),
                factors = listOf("Verstopfte Nase / Erkältung", "Rückenschlaf"),
                eventCount = 28,
                baseDb = 63f,
                sleepRating = 2,
                note = "Leichter Schnupfen, viel durch den Mund geatmet."
            ),
            DemoNightSpec(
                daysAgo = 5,
                remedies = listOf("Nasenspray", "Keilkissen / Erhöhter Kopf"),
                factors = listOf("Verstopfte Nase / Erkältung"),
                eventCount = 15,
                baseDb = 51f,
                sleepRating = 3,
                note = "Nasenspray + erhöhtes Kopfteil hat trotz Erkältung geholfen."
            ),
            DemoNightSpec(
                daysAgo = 4,
                remedies = listOf("Seitenschlaf", "Mundpflaster (Mouth Tape)"),
                factors = emptyList(),
                eventCount = 7,
                baseDb = 44f,
                sleepRating = 5,
                note = "Fast durchgehend ruhig geschlafen."
            ),
            DemoNightSpec(
                daysAgo = 3,
                remedies = emptyList(),
                factors = listOf("Spätes / Schweres Essen", "Alkohol"),
                eventCount = 23,
                baseDb = 59f,
                sleepRating = 2,
                note = "Spätes Abendessen mit Freunden."
            ),
            DemoNightSpec(
                daysAgo = 2,
                remedies = listOf("Seitenschlaf", "Nasendilatator"),
                factors = emptyList(),
                eventCount = 8,
                baseDb = 46f,
                sleepRating = 4,
                note = "Gute Nacht, kaum Aufwachen."
            ),
            DemoNightSpec(
                daysAgo = 1,
                remedies = listOf("Seitenschlaf", "Nasenpflaster", "Luftbefeuchter"),
                factors = emptyList(),
                eventCount = 6,
                baseDb = 43f,
                sleepRating = 5,
                note = "Optimale Kombination! Morgens super fit aufgewacht."
            )
        )

        val demoDir = File(filesDir, "snore_audio/demo").apply { mkdirs() }
        // Create 3 reusable synthesized snore audio clips (Leicht, Laut, Episch) so demo nights are playable
        val mildWav = File(demoDir, "demo_mild.wav")
        val modWav = File(demoDir, "demo_mod.wav")
        val heavyWav = File(demoDir, "demo_heavy.wav")
        if (!mildWav.exists()) generateSynthesizedSnoreWav(mildWav, amplitude = 2200.0)
        if (!modWav.exists()) generateSynthesizedSnoreWav(modWav, amplitude = 6000.0)
        if (!heavyWav.exists()) generateSynthesizedSnoreWav(heavyWav, amplitude = 14000.0)

        var latestId = 0L
        for (spec in specs) {
            val nightCal = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -spec.daysAgo)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, rand.nextInt(0, 40))
                set(Calendar.SECOND, 0)
            }
            val startMs = nightCal.timeInMillis
            val durationHours = 7.2f + rand.nextFloat() * 1.1f
            val totalSleepMs = (durationHours * 3600_000L).toLong()
            val endMs = startMs + totalSleepMs

            val events = mutableListOf<SnoreEvent>()
            for (i in 0 until spec.eventCount) {
                val progress = (i + 0.5f) / spec.eventCount.toFloat()
                val jitterMs = rand.nextLong(-15 * 60_000L, 15 * 60_000L)
                val evTime = (startMs + (progress * totalSleepMs).toLong() + jitterMs)
                    .coerceIn(startMs + 15 * 60_000L, endMs - 10 * 60_000L)
                val peakDb = (spec.baseDb + (rand.nextFloat() * 18f - 8f)).coerceIn(42f, 76f)
                val avgDb = (peakDb - (3f + rand.nextFloat() * 4f)).coerceAtLeast(40f)
                val durMs = rand.nextLong(22_000L, 165_000L) // 22s to 2m45s sequence
                val audioPath = when {
                    peakDb >= 62f -> heavyWav.absolutePath
                    peakDb >= 48f -> modWav.absolutePath
                    else -> mildWav.absolutePath
                }
                events.add(
                    SnoreEvent(
                        sessionId = 0,
                        timestamp = evTime,
                        durationMs = durMs,
                        peakDb = peakDb,
                        avgDb = avgDb,
                        audioFilePath = audioPath,
                        snoreScore = ((peakDb - 38f) / 40f).coerceIn(0.2f, 0.98f),
                        isBookmarked = (i == spec.eventCount / 2 && peakDb >= 58f)
                    )
                )
            }

            val sortedEvents = events.sortedBy { it.timestamp }
            val session = NightSession(
                startTime = startMs,
                endTime = endMs,
                totalSnoreDurationMs = sortedEvents.sumOf { it.durationMs },
                snoreCount = sortedEvents.size,
                peakDb = sortedEvents.maxOfOrNull { it.peakDb } ?: 0f,
                avgDb = if (sortedEvents.isNotEmpty()) sortedEvents.map { it.avgDb }.average().toFloat() else 0f,
                note = spec.note,
                remedies = spec.remedies,
                factors = spec.factors,
                sleepRating = spec.sleepRating,
                delayedStartMinutes = 15
            )
            latestId = database.insertCompleteHistoricalSession(session, sortedEvents)
        }
        refreshSessions()
        if (latestId > 0L) {
            selectSession(latestId)
        }
    }

    private fun generateSynthesizedSnoreWav(file: File, amplitude: Double) {
        val sampleRate = AudioConfig.SAMPLE_RATE
        val durationSec = 4
        val totalSamples = sampleRate * durationSec
        val samples = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            // Two realistic snore bursts at t=0.4..1.6s and t=2.3..3.5s
            val env = when {
                t in 0.4..1.6 -> sin(PI * (t - 0.4) / 1.2)
                t in 2.3..3.5 -> sin(PI * (t - 2.3) / 1.2)
                else -> 0.02
            }
            val fundamental = sin(2.0 * PI * 115.0 * t)
            val harmonic2 = 0.55 * sin(2.0 * PI * 230.0 * t)
            val flutter = 1.0 + 0.35 * sin(2.0 * PI * 18.0 * t)
            val raw = amplitude * env * flutter * (fundamental + harmonic2)
            samples[i] = raw.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val writer = WavAudioWriter(file, sampleRate)
        writer.writeSamples(samples)
        writer.finish()
    }

    suspend fun getSessionById(sessionId: Long): NightSession? = withContext(ioDispatcher) {
        database.getSession(sessionId)
    }

    suspend fun getEventsForSession(sessionId: Long): List<SnoreEvent> = withContext(ioDispatcher) {
        database.getEventsForSession(sessionId)
    }
}
