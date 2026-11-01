package de.carcophan.snorerec.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.carcophan.snorerec.MainActivity
import de.carcophan.snorerec.R
import de.carcophan.snorerec.SnoreRecApp
import de.carcophan.snorerec.audio.AudioConfig
import de.carcophan.snorerec.audio.RingBuffer
import de.carcophan.snorerec.audio.SleepSoundGenerator
import de.carcophan.snorerec.audio.SnoreDetector
import de.carcophan.snorerec.audio.YamnetClassifier
import de.carcophan.snorerec.data.model.SnoreEvent
import de.carcophan.snorerec.data.preferences.RecordingMode
import de.carcophan.snorerec.data.preferences.SleepSoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class SnoreMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var recordingJob: Job? = null
    private var alarmJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioRecord: AudioRecord? = null
    private var snoreDetector: SnoreDetector? = null
    private var yamnetClassifier: YamnetClassifier? = null
    private val sleepSoundGenerator = SleepSoundGenerator(serviceScope)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_SKIP_DELAY -> {
                _delayedStartRemainingSeconds.value = 0
                sleepSoundGenerator.stop()
                return START_STICKY
            }
            ACTION_DISMISS_ALARM -> {
                dismissAlarm()
                return START_STICKY
            }
        }

        if (!_isMonitoring.value) {
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SnoreRec::AudioRecordingWakeLock").apply {
            acquire(12 * 60 * 60 * 1000L)
        }

        val notification = buildNotification(snoreCount = 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val settings = SnoreRecApp.instance.preferences.state.value

        _isMonitoring.value = true
        _snoreCount.value = 0
        _currentDb.value = 20f
        _sessionStartTimeMs.value = System.currentTimeMillis()
        _isAlarmRinging.value = false

        val delayMin = settings.delayedStartMinutes
        _delayedStartRemainingSeconds.value = delayMin * 60
        _isInDelayedStart.value = delayMin > 0

        recordingJob = serviceScope.launch {
            val repository = SnoreRecApp.instance.repository
            val sessionId = repository.startNewSession(
                startTime = System.currentTimeMillis(),
                remedies = settings.selectedRemedies.toList(),
                factors = settings.selectedFactors.toList(),
                delayedStartMinutes = delayMin
            )
            _currentSessionId.value = sessionId

            // 1. Optional Delayed Start + Sleep Sound Phase
            if (delayMin > 0) {
                if (settings.sleepSoundType != SleepSoundType.NONE) {
                    val soundDurMs = minOf(delayMin, settings.sleepSoundDurationMinutes) * 60_000L
                    sleepSoundGenerator.start(
                        soundType = settings.sleepSoundType,
                        volume = settings.sleepSoundVolume,
                        durationMs = soundDurMs
                    )
                }

                while (isActive && _isMonitoring.value && _delayedStartRemainingSeconds.value > 0) {
                    delay(1000L)
                    if (_delayedStartRemainingSeconds.value > 0) {
                        _delayedStartRemainingSeconds.value -= 1
                    }
                }
                sleepSoundGenerator.stop()
            }

            _isInDelayedStart.value = false
            _delayedStartRemainingSeconds.value = 0

            if (!isActive || !_isMonitoring.value) return@launch

            val recordingsDir = File(filesDir, "snore_audio/session_$sessionId").apply { mkdirs() }
            val ringBuffer = RingBuffer(AudioConfig.RING_BUFFER_CAPACITY)

            val classifier = try {
                YamnetClassifier(applicationContext)
            } catch (_: Exception) {
                null
            }
            yamnetClassifier = classifier

            val currentSettings = SnoreRecApp.instance.preferences.state.value
            val detector = SnoreDetector(
                recordingsDir = recordingsDir,
                ringBuffer = ringBuffer,
                minDbThreshold = currentSettings.effectiveMinDbThreshold,
                yamnetClassifier = classifier,
                onSnoreDetected = { result ->
                    serviceScope.launch {
                        val mode = SnoreRecApp.instance.preferences.state.value.recordingMode
                        val currentCount = _snoreCount.value
                        val audioPath: String? = when (mode) {
                            RecordingMode.METRICS_ONLY -> {
                                try { result.audioFile.delete() } catch (_: Exception) {}
                                null
                            }
                            RecordingMode.TOP_SAMPLES -> {
                                if (currentCount >= 30) {
                                    try { result.audioFile.delete() } catch (_: Exception) {}
                                    null
                                } else {
                                    result.audioFile.absolutePath
                                }
                            }
                            RecordingMode.ALL_SAMPLES -> result.audioFile.absolutePath
                        }

                        val event = SnoreEvent(
                            sessionId = sessionId,
                            timestamp = result.timestamp,
                            durationMs = result.durationMs,
                            peakDb = result.peakDb,
                            avgDb = result.avgDb,
                            audioFilePath = audioPath,
                            snoreScore = result.snoreScore
                        )
                        repository.recordSnoreEvent(event)
                        _snoreCount.value += 1
                        updateNotification(_snoreCount.value)
                    }
                },
                onAudioLevel = { db ->
                    _currentDb.value = db
                    checkSmartAlarm(db)
                }
            )
            snoreDetector = detector

            runAudioLoop(detector)
        }
    }

    private var alarmTriggeredForSession = false

    private fun checkSmartAlarm(currentDb: Float) {
        if (alarmTriggeredForSession || _isAlarmRinging.value) return
        val settings = SnoreRecApp.instance.preferences.state.value
        if (!settings.alarmEnabled) return

        // Only check alarm if session has been running for at least 2 minutes (prevent immediate re-trigger if set to current time)
        if (System.currentTimeMillis() - _sessionStartTimeMs.value < 60_000L) return

        val now = Calendar.getInstance()
        val nowMinutesOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val targetMinutesOfDay = settings.alarmHour * 60 + settings.alarmMinute

        var diffMinutes = targetMinutesOfDay - nowMinutesOfDay
        if (diffMinutes < -720) diffMinutes += 1440

        val inExactWindow = diffMinutes == 0
        val inSmartWindow = settings.smartAlarmWindowMinutes > 0 &&
            diffMinutes in 1..settings.smartAlarmWindowMinutes &&
            currentDb >= 44f

        if (inExactWindow || inSmartWindow) {
            alarmTriggeredForSession = true
            triggerWakeUpAlarm()
        }
    }

    private fun triggerWakeUpAlarm() {
        _isAlarmRinging.value = true
        alarmJob?.cancel()
        alarmJob = serviceScope.launch {
            val toneGen = try {
                ToneGenerator(AudioManager.STREAM_ALARM, 75)
            } catch (_: Exception) {
                null
            }
            try {
                var ticks = 0
                while (isActive && _isAlarmRinging.value && ticks < 60) {
                    toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    delay(1200L)
                    ticks++
                }
            } finally {
                try { toneGen?.release() } catch (_: Exception) {}
            }
        }
    }

    private fun dismissAlarm() {
        _isAlarmRinging.value = false
        alarmJob?.cancel()
        alarmJob = null
    }

    private fun runAudioLoop(detector: SnoreDetector) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT
        ).coerceAtLeast(4096)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_CONFIG,
                AudioConfig.AUDIO_FORMAT,
                minBufferSize * 2
            )
            audioRecord = record

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                stopSelf()
                return
            }

            record.startRecording()
            val buffer = ShortArray(1024)

            while (serviceScope.isActive && _isMonitoring.value) {
                val readCount = record.read(buffer, 0, buffer.size)
                if (readCount > 0) {
                    detector.processAudioFrame(buffer, 0, readCount)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
            audioRecord = null
        }
    }

    private fun stopMonitoring() {
        _isMonitoring.value = false
        _isInDelayedStart.value = false
        _delayedStartRemainingSeconds.value = 0
        dismissAlarm()
        sleepSoundGenerator.stop()

        snoreDetector?.stopAndFlush()
        snoreDetector = null

        yamnetClassifier?.close()
        yamnetClassifier = null

        recordingJob?.cancel()
        recordingJob = null

        val sessionId = _currentSessionId.value
        if (sessionId != null) {
            serviceScope.launch {
                SnoreRecApp.instance.repository.finishSession(sessionId)
            }
        }
        _currentSessionId.value = null
        _currentDb.value = 20f

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    private fun buildNotification(snoreCount: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SnoreMonitoringService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (snoreCount > 0) {
            "$snoreCount Schnarch-Ereignisse aufgezeichnet"
        } else {
            getString(R.string.notification_monitoring_text)
        }

        return NotificationCompat.Builder(this, SnoreRecApp.CHANNEL_ID_MONITORING)
            .setContentTitle(getString(R.string.notification_monitoring_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(snoreCount: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(snoreCount))
    }

    companion object {
        const val ACTION_START = "de.carcophan.snorerec.START"
        const val ACTION_STOP = "de.carcophan.snorerec.STOP"
        const val ACTION_SKIP_DELAY = "de.carcophan.snorerec.SKIP_DELAY"
        const val ACTION_DISMISS_ALARM = "de.carcophan.snorerec.DISMISS_ALARM"
        private const val NOTIFICATION_ID = 1001

        private val _isMonitoring = MutableStateFlow(false)
        val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

        private val _isInDelayedStart = MutableStateFlow(false)
        val isInDelayedStart: StateFlow<Boolean> = _isInDelayedStart.asStateFlow()

        private val _delayedStartRemainingSeconds = MutableStateFlow(0)
        val delayedStartRemainingSeconds: StateFlow<Int> = _delayedStartRemainingSeconds.asStateFlow()

        private val _sessionStartTimeMs = MutableStateFlow(0L)
        val sessionStartTimeMs: StateFlow<Long> = _sessionStartTimeMs.asStateFlow()

        private val _currentSessionId = MutableStateFlow<Long?>(null)
        val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

        private val _currentDb = MutableStateFlow(20f)
        val currentDb: StateFlow<Float> = _currentDb.asStateFlow()

        private val _snoreCount = MutableStateFlow(0)
        val snoreCount: StateFlow<Int> = _snoreCount.asStateFlow()

        private val _isAlarmRinging = MutableStateFlow(false)
        val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SnoreMonitoringService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SnoreMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun skipDelayedStart(context: Context) {
            val intent = Intent(context, SnoreMonitoringService::class.java).apply {
                action = ACTION_SKIP_DELAY
            }
            context.startService(intent)
        }

        fun dismissAlarm(context: Context) {
            val intent = Intent(context, SnoreMonitoringService::class.java).apply {
                action = ACTION_DISMISS_ALARM
            }
            context.startService(intent)
        }
    }
}
