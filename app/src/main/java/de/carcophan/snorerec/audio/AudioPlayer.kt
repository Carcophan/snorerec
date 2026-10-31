package de.carcophan.snorerec.audio

import android.media.MediaPlayer
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
    private var progressJob: Job? = null

    private val _playingEventId = MutableStateFlow<Long?>(null)
    val playingEventId: StateFlow<Long?> = _playingEventId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    fun play(eventId: Long, filePath: String) {
        if (_playingEventId.value == eventId && _isPlaying.value) {
            stop()
            return
        }

        stop()

        val file = File(filePath)
        if (!file.exists()) return

        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    stop()
                }
                start()
            }
            mediaPlayer = player
            _playingEventId.value = eventId
            _isPlaying.value = true

            progressJob = scope.launch(Dispatchers.Main) {
                while (isActive && _isPlaying.value) {
                    val mp = mediaPlayer
                    if (mp != null && mp.isPlaying) {
                        val duration = mp.duration
                        val current = mp.currentPosition
                        if (duration > 0) {
                            _playbackProgress.value = current.toFloat() / duration.toFloat()
                        }
                    }
                    delay(50)
                }
            }
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        _isPlaying.value = false
        _playingEventId.value = null
        _playbackProgress.value = 0f
    }
}
