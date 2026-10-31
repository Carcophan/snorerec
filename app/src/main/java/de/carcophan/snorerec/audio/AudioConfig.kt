package de.carcophan.snorerec.audio

import android.media.AudioFormat

object AudioConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // 2.5 seconds of pre-roll ring buffer
    const val PRE_ROLL_SECONDS = 2.5f
    val RING_BUFFER_CAPACITY = (SAMPLE_RATE * PRE_ROLL_SECONDS).toInt()

    // Detection thresholds
    const val SNORE_MIN_DB = 42.0f
    const val SNORE_MIN_DURATION_MS = 600L
    const val SNORE_POST_SILENCE_MS = 1200L
    const val SNORE_MAX_EVENT_DURATION_MS = 15000L // Cap single snore clip at 15s

    // YAMNet Machine Learning Constants
    const val YAMNET_SAMPLE_COUNT = 15600 // 0.975s at 16kHz
    const val YAMNET_SNORING_INDEX = 38
    const val YAMNET_SNORT_INDEX = 41
    const val YAMNET_SNORE_THRESHOLD = 0.15f // 15% confidence threshold for snore detection
}
