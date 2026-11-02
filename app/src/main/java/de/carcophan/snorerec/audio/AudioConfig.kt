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
    const val SNORE_POST_SILENCE_MS = 3000L // 3.0s post-roll tail for natural snore ending
    const val SNORE_SEQUENCE_GAP_MS = 40000L // Merge snores up to 40s apart into one contiguous sequence
    const val SNORE_MAX_EVENT_DURATION_MS = 300000L // Cap single snore sequence clip at 5 minutes

    // Continuation during active sequence (more forgiving so breathing bouts stay together)
    const val SNORE_CONTINUATION_MIN_DURATION_MS = 250L // 250ms to confirm next breath in ongoing sequence
    const val SNORE_CONTINUATION_DROPOUT_MS = 800L // 800ms dropout tolerance before resetting burst

    // YAMNet Machine Learning Constants
    const val YAMNET_SAMPLE_COUNT = 15600 // 0.975s at 16kHz
    const val YAMNET_SNORING_INDEX = 38
    const val YAMNET_SNORT_INDEX = 41
    const val YAMNET_SNORE_THRESHOLD = 0.15f // 15% confidence threshold for snore detection
}
