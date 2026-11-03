package de.carcophan.snorerec.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.carcophan.snorerec.SnoreRecApp
import de.carcophan.snorerec.audio.AudioPlayer
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreEvent
import de.carcophan.snorerec.data.model.SnoreIntensity
import de.carcophan.snorerec.data.preferences.RecordingMode
import de.carcophan.snorerec.data.preferences.SnoreSettingsState
import de.carcophan.snorerec.data.repository.StorageUsageInfo
import de.carcophan.snorerec.service.SnoreMonitoringService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

data class SnoreIntensityBreakdown(
    val quietDurationMs: Long,
    val mildDurationMs: Long,
    val moderateDurationMs: Long,
    val heavyDurationMs: Long,
    val quietPercentageOfSleep: Float,
    val mildPercentageOfSleep: Float,
    val moderatePercentageOfSleep: Float,
    val heavyPercentageOfSleep: Float,
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
    val snoreScore: Int,
    val avgAllTimeScore: Int,
    val scoreDiffPercent: Int,
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

data class ItemEffectivenessStat(
    val name: String,
    val nightsCount: Int,
    val avgSnoreScore: Int,
    val avgSnorePercentage: Float,
    val deltaScorePercentVsBaseline: Int // negative = reduces snoring, positive = increases snoring
)

data class TrendsAnalysis(
    val totalNights: Int = 0,
    val avgSnoreScore: Int = 0,
    val lowestSnoreScore: Int = 0,
    val highestSnoreScore: Int = 0,
    val avgSnorePercentage: Float = 0f,
    val avgSleepDurationMs: Long = 0L,
    val baselineNoRemedyScore: Int = 0,
    val remedyStats: List<ItemEffectivenessStat> = emptyList(),
    val factorStats: List<ItemEffectivenessStat> = emptyList()
)

enum class AudioClipFilter(val label: String) {
    ALL("Alle"),
    LOUDEST("Lauteste 🔥"),
    BOOKMARKED("Markiert ⭐"),
    EPIC("Episch"),
    LOUD("Laut"),
    LIGHT("Leicht")
}

class MainViewModel : ViewModel() {

    private val app = SnoreRecApp.instance
    private val repository = app.repository
    private val preferences = app.preferences
    private val audioPlayer = AudioPlayer(viewModelScope)

    val settings: StateFlow<SnoreSettingsState> = preferences.state

    val isMonitoring: StateFlow<Boolean> = SnoreMonitoringService.isMonitoring
    val isInDelayedStart: StateFlow<Boolean> = SnoreMonitoringService.isInDelayedStart
    val delayedStartRemainingSeconds: StateFlow<Int> = SnoreMonitoringService.delayedStartRemainingSeconds
    val sessionStartTimeMs: StateFlow<Long> = SnoreMonitoringService.sessionStartTimeMs
    val currentDb: StateFlow<Float> = SnoreMonitoringService.currentDb
    val liveSnoreCount: StateFlow<Int> = SnoreMonitoringService.snoreCount

    val sessions: StateFlow<List<NightSession>> = repository.sessions
    val selectedSession: StateFlow<NightSession?> = repository.selectedSession
    val selectedSessionEvents: StateFlow<List<SnoreEvent>> = repository.selectedSessionEvents

    val playingEventId: StateFlow<Long?> = audioPlayer.playingEventId
    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val playbackProgress: StateFlow<Float> = audioPlayer.playbackProgress
    val playbackDurationMs: StateFlow<Long> = audioPlayer.playbackDurationMs

    private val _storageUsage = MutableStateFlow(StorageUsageInfo(0, 0L))
    val storageUsage: StateFlow<StorageUsageInfo> = _storageUsage.asStateFlow()

    val currentAnalysis: StateFlow<SessionAnalysis?> = combine(
        selectedSession,
        selectedSessionEvents,
        sessions
    ) { session, events, allSessions ->
        if (session == null) null else computeAnalysis(session, events, allSessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val trendsAnalysis: StateFlow<TrendsAnalysis> = sessions.combine(settings) { allSessions, _ ->
        computeTrends(allSessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrendsAnalysis())

    init {
        viewModelScope.launch {
            val retention = preferences.state.value.retentionDays
            if (retention > 0) {
                repository.cleanupOldAudioFiles(olderThanDays = retention)
            }
            repository.refreshSessions()
            refreshStorageUsage()
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            _storageUsage.value = repository.computeStorageUsage(app.filesDir)
        }
    }

    fun toggleMonitoring(context: Context) {
        if (isMonitoring.value) {
            SnoreMonitoringService.stop(context)
            refreshStorageUsage()
        } else {
            SnoreMonitoringService.start(context)
        }
    }

    fun skipDelayedStart(context: Context) {
        SnoreMonitoringService.skipDelayedStart(context)
    }

    fun selectSession(sessionId: Long) {
        viewModelScope.launch {
            audioPlayer.stop()
            repository.selectSession(sessionId)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            audioPlayer.stop()
            repository.deleteSession(sessionId)
            refreshStorageUsage()
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            audioPlayer.stop()
            repository.deleteAllSessions()
            refreshStorageUsage()
        }
    }

    fun seedDemoSessions() {
        viewModelScope.launch {
            audioPlayer.stop()
            repository.seedDemoData(app.filesDir)
            refreshStorageUsage()
        }
    }

    fun toggleBookmark(event: SnoreEvent) {
        viewModelScope.launch {
            repository.toggleBookmark(event)
        }
    }

    fun deleteSnoreEvent(eventId: Long) {
        viewModelScope.launch {
            if (playingEventId.value == eventId) {
                audioPlayer.stop()
            }
            repository.deleteSnoreEvent(eventId)
            refreshStorageUsage()
        }
    }

    fun updateSelectedSessionMetadata(
        remedies: List<String>? = null,
        factors: List<String>? = null,
        sleepRating: Int? = null,
        note: String? = null
    ) {
        val current = selectedSession.value ?: return
        viewModelScope.launch {
            repository.updateSessionMetadata(
                sessionId = current.id,
                remedies = remedies,
                factors = factors,
                sleepRating = sleepRating,
                note = note
            )
        }
    }

    fun playAudio(eventId: Long, filePath: String, onFinished: (() -> Unit)? = null) {
        audioPlayer.play(eventId, filePath, onFinished)
    }

    fun seekAudio(
        progress: Float,
        eventId: Long? = null,
        filePath: String? = null,
        onFinished: (() -> Unit)? = null
    ) {
        audioPlayer.seekTo(
            progress = progress,
            eventId = eventId,
            filePath = filePath,
            onFinished = onFinished
        )
    }

    fun playNextEventInList(currentEventId: Long, events: List<SnoreEvent>) {
        val playable = events.filter { it.audioFilePath != null }
        val currentIndex = playable.indexOfFirst { it.id == currentEventId }
        if (currentIndex in 0 until playable.size - 1) {
            val nextEvent = playable[currentIndex + 1]
            nextEvent.audioFilePath?.let { path ->
                playAudio(nextEvent.id, path) {
                    playNextEventInList(nextEvent.id, events)
                }
            }
        }
    }

    fun consolidateSelectedSession(onResult: ((Int) -> Unit)? = null) {
        val current = selectedSession.value ?: return
        viewModelScope.launch {
            val count = repository.consolidateFragmentedEvents(current.id)
            refreshStorageUsage()
            onResult?.invoke(count)
        }
    }

    fun stopAudio() {
        audioPlayer.stop()
    }

    // Preferences delegators
    fun setDelayedStartMinutes(minutes: Int) = preferences.setDelayedStartMinutes(minutes)

    fun toggleRemedyForTonight(name: String) = preferences.toggleRemedyForTonight(name)
    fun toggleFactorForTonight(name: String) = preferences.toggleFactorForTonight(name)
    fun addCustomRemedy(name: String) = preferences.addCustomRemedy(name)
    fun removeCustomRemedy(name: String) = preferences.removeCustomRemedy(name)
    fun addCustomFactor(name: String) = preferences.addCustomFactor(name)
    fun removeCustomFactor(name: String) = preferences.removeCustomFactor(name)

    fun setSensitivityOffsetDb(offsetDb: Float) = preferences.setSensitivityOffsetDb(offsetDb)
    fun setRecordingMode(mode: RecordingMode) = preferences.setRecordingMode(mode)
    fun setRetentionDays(days: Int) {
        preferences.setRetentionDays(days)
        if (days > 0) {
            viewModelScope.launch {
                repository.cleanupOldAudioFiles(days)
                refreshStorageUsage()
            }
        }
    }

    fun setProfile(age: Int, scoreGoal: Int) {
        preferences.setProfile(age, scoreGoal)
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            val csvContent = repository.exportSessionsToCsv()
            val exportFile = File(context.cacheDir, "SnoreRec_Export.csv")
            exportFile.writeText(csvContent)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "SnoreRec Schlaf- & Schnarch-Statistik (CSV)")
                putExtra(Intent.EXTRA_TEXT, csvContent)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Schnarch-Daten exportieren"))
        }
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }

    private fun computeAnalysis(
        session: NightSession,
        events: List<SnoreEvent>,
        allSessions: List<NightSession>
    ): SessionAnalysis {
        val totalSleep = session.durationMs.coerceAtLeast(1000L)
        val totalSnore = events.sumOf { it.durationMs }.let { if (it > 0L) it else session.totalSnoreDurationMs }
        val count = if (events.isNotEmpty()) events.size else session.snoreCount

        var mildCount = 0
        var moderateCount = 0
        var heavyCount = 0
        var mildDur = 0L
        var moderateDur = 0L
        var heavyDur = 0L
        var maxDb = 0f
        var sumDb = 0f

        events.forEach { event ->
            if (event.peakDb > maxDb) maxDb = event.peakDb
            sumDb += event.avgDb
            when (event.intensity) {
                SnoreIntensity.MILD -> {
                    mildCount++
                    mildDur += event.durationMs
                }
                SnoreIntensity.MODERATE -> {
                    moderateCount++
                    moderateDur += event.durationMs
                }
                SnoreIntensity.HEAVY -> {
                    heavyCount++
                    heavyDur += event.durationMs
                }
            }
        }

        if (events.isEmpty()) {
            mildDur = session.mildDurationMs
            moderateDur = session.moderateDurationMs
            heavyDur = session.heavyDurationMs
        }

        val quietDur = (totalSleep - totalSnore).coerceAtLeast(0L)

        val mildPct = if (count > 0) (mildCount.toFloat() / count * 100f) else 0f
        val modPct = if (count > 0) (moderateCount.toFloat() / count * 100f) else 0f
        val heavyPct = if (count > 0) (heavyCount.toFloat() / count * 100f) else 0f
        val avgDb = if (count > 0 && sumDb > 0f) (sumDb / count) else session.avgDb
        val peakDb = if (maxDb > 0f) maxDb else session.peakDb

        val quietSleepPct = (quietDur.toFloat() / totalSleep * 100f).coerceIn(0f, 100f)
        val mildSleepPct = (mildDur.toFloat() / totalSleep * 100f).coerceIn(0f, 100f)
        val modSleepPct = (moderateDur.toFloat() / totalSleep * 100f).coerceIn(0f, 100f)
        val heavySleepPct = (heavyDur.toFloat() / totalSleep * 100f).coerceIn(0f, 100f)

        val breakdown = SnoreIntensityBreakdown(
            quietDurationMs = quietDur,
            mildDurationMs = mildDur,
            moderateDurationMs = moderateDur,
            heavyDurationMs = heavyDur,
            quietPercentageOfSleep = quietSleepPct,
            mildPercentageOfSleep = mildSleepPct,
            moderatePercentageOfSleep = modSleepPct,
            heavyPercentageOfSleep = heavySleepPct,
            mildCount = mildCount,
            moderateCount = moderateCount,
            heavyCount = heavyCount,
            mildPercentage = mildPct,
            moderatePercentage = modPct,
            heavyPercentage = heavyPct
        )

        val hourlyBuckets = mutableListOf<HourlySnoreBucket>()
        val sessionStart = session.startTime
        val sessionEnd = session.endTime ?: (sessionStart + totalSleep)
        val bucketSpanMs = 3600_000L

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

        val score = NightSession.computeSnoreScore(
            totalSleepMs = totalSleep,
            totalSnoreMs = totalSnore,
            mildMs = mildDur,
            moderateMs = moderateDur,
            heavyMs = heavyDur,
            avgDb = avgDb,
            peakDb = peakDb
        )

        val allAvgScore = if (allSessions.isNotEmpty()) {
            allSessions.map { it.snoreScore }.average().roundToInt()
        } else {
            score
        }

        val diffPct = if (allAvgScore > 0) {
            (((score - allAvgScore).toFloat() / allAvgScore.toFloat()) * 100f).roundToInt()
        } else {
            0
        }

        return SessionAnalysis(
            snoreScore = score,
            avgAllTimeScore = allAvgScore,
            scoreDiffPercent = diffPct,
            totalSleepDurationMs = totalSleep,
            totalSnoreDurationMs = totalSnore,
            snorePercentage = snorePct,
            snoreCount = count,
            snoresPerHour = snoresPerHour,
            peakDb = peakDb,
            avgDb = avgDb,
            intensityBreakdown = breakdown,
            hourlyBuckets = hourlyBuckets
        )
    }

    private fun computeTrends(allSessions: List<NightSession>): TrendsAnalysis {
        if (allSessions.isEmpty()) return TrendsAnalysis()

        val scores = allSessions.map { it.snoreScore }
        val avgScore = scores.average().roundToInt()
        val minScore = scores.minOrNull() ?: 0
        val maxScore = scores.maxOrNull() ?: 0
        val avgPct = allSessions.map { it.snorePercentage }.average().toFloat()
        val avgSleep = allSessions.map { it.durationMs }.average().toLong()

        val noRemedySessions = allSessions.filter { it.remedies.isEmpty() }
        val baselineNoRemedyScore = if (noRemedySessions.isNotEmpty()) {
            noRemedySessions.map { it.snoreScore }.average().roundToInt().coerceAtLeast(1)
        } else {
            avgScore.coerceAtLeast(1)
        }

        // Remedy effectiveness
        val remedyGroups = mutableMapOf<String, MutableList<NightSession>>()
        allSessions.forEach { s ->
            s.remedies.forEach { r ->
                remedyGroups.getOrPut(r) { mutableListOf() }.add(s)
            }
        }
        val remedyStats = remedyGroups.map { (name, list) ->
            val rAvg = list.map { it.snoreScore }.average().roundToInt()
            val rPct = list.map { it.snorePercentage }.average().toFloat()
            val delta = (((rAvg - baselineNoRemedyScore).toFloat() / baselineNoRemedyScore.toFloat()) * 100f).roundToInt()
            ItemEffectivenessStat(
                name = name,
                nightsCount = list.size,
                avgSnoreScore = rAvg,
                avgSnorePercentage = rPct,
                deltaScorePercentVsBaseline = delta
            )
        }.sortedBy { it.avgSnoreScore }

        // Factor impact
        val factorGroups = mutableMapOf<String, MutableList<NightSession>>()
        allSessions.forEach { s ->
            s.factors.forEach { f ->
                factorGroups.getOrPut(f) { mutableListOf() }.add(s)
            }
        }
        val noFactorSessions = allSessions.filter { it.factors.isEmpty() }
        val baselineNoFactorScore = if (noFactorSessions.isNotEmpty()) {
            noFactorSessions.map { it.snoreScore }.average().roundToInt().coerceAtLeast(1)
        } else {
            avgScore.coerceAtLeast(1)
        }

        val factorStats = factorGroups.map { (name, list) ->
            val fAvg = list.map { it.snoreScore }.average().roundToInt()
            val fPct = list.map { it.snorePercentage }.average().toFloat()
            val delta = (((fAvg - baselineNoFactorScore).toFloat() / baselineNoFactorScore.toFloat()) * 100f).roundToInt()
            ItemEffectivenessStat(
                name = name,
                nightsCount = list.size,
                avgSnoreScore = fAvg,
                avgSnorePercentage = fPct,
                deltaScorePercentVsBaseline = delta
            )
        }.sortedByDescending { it.avgSnoreScore }

        return TrendsAnalysis(
            totalNights = allSessions.size,
            avgSnoreScore = avgScore,
            lowestSnoreScore = minScore,
            highestSnoreScore = maxScore,
            avgSnorePercentage = avgPct,
            avgSleepDurationMs = avgSleep,
            baselineNoRemedyScore = baselineNoRemedyScore,
            remedyStats = remedyStats,
            factorStats = factorStats
        )
    }
}
