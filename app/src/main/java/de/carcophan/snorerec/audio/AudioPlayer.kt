package de.carcophan.snorerec.audio

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
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

class AudioPlayer(private val scope: CoroutineScope) {

    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var progressJob: Job? = null
    private var currentOnFinished: (() -> Unit)? = null

    private val _playingEventId = MutableStateFlow<Long?>(null)
    val playingEventId: StateFlow<Long?> = _playingEventId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(0L)
    val playbackDurationMs: StateFlow<Long> = _playbackDurationMs.asStateFlow()

    fun play(eventId: Long, filePath: String, onFinished: (() -> Unit)? = null) {
        if (_playingEventId.value == eventId && mediaPlayer != null) {
            if (_isPlaying.value) {
                pause()
            } else {
                currentOnFinished = onFinished ?: currentOnFinished
                resume()
            }
            return
        }

        stop()

        val file = File(filePath)
        if (!file.exists()) return

        try {
            currentOnFinished = onFinished
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                setVolume(1.0f, 1.0f)
                prepare()
                setOnCompletionListener {
                    val callback = currentOnFinished
                    stop()
                    callback?.invoke()
                }
            }
            attachLoudnessEnhancer(player.audioSessionId)
            player.start()
            mediaPlayer = player
            _playingEventId.value = eventId
            _isPlaying.value = true
            _playbackDurationMs.value = player.duration.coerceAtLeast(0).toLong()
            startProgressTracking()
        } catch (_: Exception) {
            stop()
        }
    }

    fun pause() {
        progressJob?.cancel()
        progressJob = null
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}
        _isPlaying.value = false
    }

    fun resume() {
        val mp = mediaPlayer ?: return
        try {
            mp.start()
            _isPlaying.value = true
            startProgressTracking()
        } catch (_: Exception) {
            stop()
        }
    }

    fun seekTo(
        progress: Float,
        eventId: Long? = null,
        filePath: String? = null,
        onFinished: (() -> Unit)? = null
    ) {
        val clamped = progress.coerceIn(0f, 1f)

        // If seeking on an event that is not currently loaded, load and start it at that position
        if (eventId != null && filePath != null && (_playingEventId.value != eventId || mediaPlayer == null)) {
            stop()
            val file = File(filePath)
            if (!file.exists()) return
            try {
                currentOnFinished = onFinished
                val player = MediaPlayer().apply {
                    setDataSource(filePath)
                    setVolume(1.0f, 1.0f)
                    prepare()
                    val duration = duration.coerceAtLeast(0)
                    if (duration > 0) {
                        seekTo((duration * clamped).toInt())
                    }
                    setOnCompletionListener {
                        val callback = currentOnFinished
                        stop()
                        callback?.invoke()
                    }
                }
                attachLoudnessEnhancer(player.audioSessionId)
                player.start()
                mediaPlayer = player
                _playingEventId.value = eventId
                _isPlaying.value = true
                _playbackDurationMs.value = player.duration.coerceAtLeast(0).toLong()
                _playbackProgress.value = clamped
                startProgressTracking()
            } catch (_: Exception) {
                stop()
            }
            return
        }

        val mp = mediaPlayer ?: return
        try {
            val duration = mp.duration
            if (duration > 0) {
                val targetMs = (duration * clamped).toInt().coerceIn(0, duration)
                mp.seekTo(targetMs)
                _playbackProgress.value = clamped
                _playbackDurationMs.value = duration.toLong()
            }
        } catch (_: Exception) {}
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive && _isPlaying.value) {
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    val duration = mp.duration
                    val current = mp.currentPosition
                    if (duration > 0) {
                        _playbackDurationMs.value = duration.toLong()
                        _playbackProgress.value = (current.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    }
                }
                delay(50)
            }
        }
    }

    private fun attachLoudnessEnhancer(audioSessionId: Int) {
        releaseLoudnessEnhancer()
        if (audioSessionId == 0) return
        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(AudioConfig.PLAYBACK_LOUDNESS_BOOST_MB)
                enabled = true
            }
        } catch (_: Exception) {
            loudnessEnhancer = null
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        loudnessEnhancer = null
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        currentOnFinished = null

        releaseLoudnessEnhancer()

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        _isPlaying.value = false
        _playingEventId.value = null
        _playbackProgress.value = 0f
        _playbackDurationMs.value = 0L
    }
}
