package de.carcophan.snorerec.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import de.carcophan.snorerec.data.preferences.SleepSoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SleepSoundGenerator(private val scope: CoroutineScope) {

    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeSound = MutableStateFlow(SleepSoundType.NONE)
    val activeSound: StateFlow<SleepSoundType> = _activeSound.asStateFlow()

    @Volatile
    private var targetVolume = 0.5f

    fun start(soundType: SleepSoundType, volume: Float = 0.5f, durationMs: Long = 15 * 60_000L, onCompleted: (() -> Unit)? = null) {
        stop()
        if (soundType == SleepSoundType.NONE) {
            onCompleted?.invoke()
            return
        }

        targetVolume = volume.coerceIn(0.05f, 1.0f)
        _activeSound.value = soundType
        _isPlaying.value = true

        playbackJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val bufferSamples = 2048
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufferSamples * 2)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufBytes * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                _isPlaying.value = false
                _activeSound.value = SleepSoundType.NONE
                return@launch
            }

            audioTrack = track
            try {
                track.play()
                val buffer = ShortArray(bufferSamples)
                val rand = Random(System.currentTimeMillis())

                // Filter states for pink/brown noise & wave modulation
                var b0 = 0.0
                var b1 = 0.0
                var b2 = 0.0
                var lp1 = 0.0
                var lp2 = 0.0
                var sampleIndex = 0L
                val startTime = System.currentTimeMillis()
                val fadeOutDurationMs = 5000L

                while (isActive && _isPlaying.value) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= durationMs) break

                    val remaining = durationMs - elapsed
                    val fadeMultiplier = if (remaining < fadeOutDurationMs) {
                        (remaining.toFloat() / fadeOutDurationMs).coerceIn(0f, 1f)
                    } else if (elapsed < 2000L) {
                        (elapsed.toFloat() / 2000f).coerceIn(0.1f, 1f)
                    } else {
                        1f
                    }

                    val amp = (targetVolume * fadeMultiplier * 7500.0)

                    for (i in 0 until bufferSamples) {
                        val t = sampleIndex.toDouble() / sampleRate
                        sampleIndex++
                        val white = rand.nextDouble(-1.0, 1.0)

                        // Simple Kellet pink-ish filter approximation
                        b0 = 0.99765 * b0 + white * 0.0990460
                        b1 = 0.96300 * b1 + white * 0.2965164
                        b2 = 0.57000 * b2 + white * 1.0526913
                        val pink = (b0 + b1 + b2 + white * 0.1848) * 0.22

                        val sampleVal = when (soundType) {
                            SleepSoundType.RAIN -> {
                                lp1 += 0.18 * (pink - lp1)
                                val pattering = 0.85 + 0.15 * sin(2.0 * PI * 0.35 * t)
                                lp1 * pattering
                            }
                            SleepSoundType.WAVES -> {
                                lp1 += 0.06 * (pink - lp1)
                                // Slow ocean wave swell every ~8.5 seconds
                                val waveEnv = 0.25 + 0.75 * ((sin(2.0 * PI * 0.118 * t) + 1.0) * 0.5)
                                lp1 * waveEnv * 1.4
                            }
                            SleepSoundType.PINK_NOISE -> {
                                lp1 += 0.25 * (pink - lp1)
                                lp1 * 0.9
                            }
                            SleepSoundType.WIND -> {
                                val sweep = 0.03 + 0.05 * ((sin(2.0 * PI * 0.08 * t) + 1.0) * 0.5)
                                lp1 += sweep * (pink - lp1)
                                lp2 += sweep * (lp1 - lp2)
                                lp2 * (0.6 + 0.4 * sin(2.0 * PI * 0.19 * t)) * 1.8
                            }
                            SleepSoundType.DEEP_DRONE -> {
                                val f1 = sin(2.0 * PI * 110.0 * t)
                                val f2 = 0.5 * sin(2.0 * PI * 165.0 * t)
                                val f3 = 0.25 * sin(2.0 * PI * 220.0 * t)
                                val slowLfo = 0.75 + 0.25 * sin(2.0 * PI * 0.09 * t)
                                lp1 += 0.04 * (pink - lp1)
                                ((f1 + f2 + f3) * 0.32 * slowLfo) + (lp1 * 0.25)
                            }
                            SleepSoundType.NONE -> 0.0
                        }

                        val pcm = (sampleVal * amp).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        buffer[i] = pcm.toShort()
                    }

                    track.write(buffer, 0, bufferSamples)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
                if (audioTrack === track) audioTrack = null
                val wasPlaying = _isPlaying.value
                _isPlaying.value = false
                _activeSound.value = SleepSoundType.NONE
                if (wasPlaying) {
                    onCompleted?.invoke()
                }
            }
        }
    }

    fun setVolume(volume: Float) {
        targetVolume = volume.coerceIn(0.05f, 1.0f)
    }

    fun stop() {
        _isPlaying.value = false
        _activeSound.value = SleepSoundType.NONE
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}

