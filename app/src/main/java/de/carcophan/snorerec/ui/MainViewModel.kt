package de.carcophan.snorerec.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.carcophan.snorerec.SnoreRecApp
import de.carcophan.snorerec.audio.AudioPlayer
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreEvent
import de.carcophan.snorerec.data.model.SnoreIntensity
import de.carcophan.snorerec.service.SnoreMonitoringService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SnoreIntensityBreakdown(
    val mildCount: Int,
    val moderateCount: Int,
    val heavyCount: Int,
    val mildPercentage: Float,
    val moderatePercentage: Float,
    val heavyPercentage: Float
)

data class HourlySnoreBucket(
    val hourLabel: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val snoreCount: Int,
    val totalDurationMs: Long,
    val avgDb: Float
)

data class SessionAnalysis(
    val totalSleepDurationMs: Long,
    val totalSnoreDurationMs: Long,
    val snorePercentage: Float,
    val snoreCount: Int,
    val snoresPerHour: Float,
    val peakDb: Float,
    val avgDb: Float,
    val intensityBreakdown: SnoreIntensityBreakdown,
    val hourlyBuckets: List<HourlySnoreBucket>
)

class MainViewModel : ViewModel() {

    private val repository = SnoreRecApp.instance.repository
    private val audioPlayer = AudioPlayer(viewModelScope)

    val isMonitoring: StateFlow<Boolean> = SnoreMonitoringService.isMonitoring
    val currentDb: StateFlow<Float> = SnoreMonitoringService.currentDb
    val liveSnoreCount: StateFlow<Int> = SnoreMonitoringService.snoreCount

    val sessions: StateFlow<List<NightSession>> = repository.sessions
    val selectedSession: StateFlow<NightSession?> = repository.selectedSession
    val selectedSessionEvents: StateFlow<List<SnoreEvent>> = repository.selectedSessionEvents

    val playingEventId: StateFlow<Long?> = audioPlayer.playingEventId
    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val playbackProgress: StateFlow<Float> = audioPlayer.playbackProgress

    val currentAnalysis: StateFlow<SessionAnalysis?> = combine(
        selectedSession,
        selectedSessionEvents
    ) { session, events ->
        if (session == null) null else computeAnalysis(session, events)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            repository.cleanupOldAudioFiles(olderThanDays = 14)
            repository.refreshSessions()
        }
    }

    fun toggleMonitoring(context: Context) {
        if (isMonitoring.value) {
            SnoreMonitoringService.stop(context)
        } else {
            SnoreMonitoringService.start(context)
        }
    }

    fun selectSession(sessionId: Long) {
        viewModelScope.launch {
            repository.selectSession(sessionId)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            audioPlayer.stop()
            repository.deleteSession(sessionId)
        }
    }

    fun playAudio(eventId: Long, filePath: String) {
        audioPlayer.play(eventId, filePath)
    }

    fun stopAudio() {
        audioPlayer.stop()
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }

    private fun computeAnalysis(session: NightSession, events: List<SnoreEvent>): SessionAnalysis {
        val totalSleep = session.durationMs.coerceAtLeast(1000L)
        val totalSnore = events.sumOf { it.durationMs }
        val count = events.size

        var mild = 0
        var moderate = 0
        var heavy = 0
        var maxDb = 0f
        var sumDb = 0f

        events.forEach { event ->
            if (event.peakDb > maxDb) maxDb = event.peakDb
            sumDb += event.avgDb
            when (event.intensity) {
                SnoreIntensity.MILD -> mild++
                SnoreIntensity.MODERATE -> moderate++
                SnoreIntensity.HEAVY -> heavy++
            }
        }

        val mildPct = if (count > 0) (mild.toFloat() / count * 100f) else 0f
        val modPct = if (count > 0) (moderate.toFloat() / count * 100f) else 0f
        val heavyPct = if (count > 0) (heavy.toFloat() / count * 100f) else 0f
        val avgDb = if (count > 0) (sumDb / count) else 0f

        val breakdown = SnoreIntensityBreakdown(
            mildCount = mild,
            moderateCount = moderate,
            heavyCount = heavy,
            mildPercentage = mildPct,
            moderatePercentage = modPct,
            heavyPercentage = heavyPct
        )

        // Compute hourly buckets from session start to session end
        val hourlyBuckets = mutableListOf<HourlySnoreBucket>()
        val sessionStart = session.startTime
        val sessionEnd = session.endTime ?: (sessionStart + totalSleep)
        val bucketSpanMs = 3600_000L // 1 hour buckets

        var currentBucketStart = sessionStart
        val hourFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

        while (currentBucketStart < sessionEnd) {
            val currentBucketEnd = currentBucketStart + bucketSpanMs
            val bucketEvents = events.filter { it.timestamp in currentBucketStart until currentBucketEnd }
            val bucketSnoreCount = bucketEvents.size
            val bucketSnoreDuration = bucketEvents.sumOf { it.durationMs }
            val bucketAvgDb = if (bucketSnoreCount > 0) bucketEvents.map { it.avgDb }.average().toFloat() else 0f

            val label = hourFormat.format(java.util.Date(currentBucketStart))
            hourlyBuckets.add(
                HourlySnoreBucket(
                    hourLabel = label,
                    startTimestamp = currentBucketStart,
                    endTimestamp = currentBucketEnd,
                    snoreCount = bucketSnoreCount,
                    totalDurationMs = bucketSnoreDuration,
                    avgDb = bucketAvgDb
                )
            )
            currentBucketStart = currentBucketEnd
        }

        val hours = totalSleep / 3600000.0f
        val snoresPerHour = if (hours > 0.05f) count / hours else 0f
        val snorePct = ((totalSnore.toFloat() / totalSleep.toFloat()) * 100f).coerceIn(0f, 100f)

        return SessionAnalysis(
            totalSleepDurationMs = totalSleep,
            totalSnoreDurationMs = totalSnore,
            snorePercentage = snorePct,
            snoreCount = count,
            snoresPerHour = snoresPerHour,
            peakDb = if (maxDb > 0f) maxDb else session.peakDb,
            avgDb = if (avgDb > 0f) avgDb else session.avgDb,
            intensityBreakdown = breakdown,
            hourlyBuckets = hourlyBuckets
        )
    }
}
