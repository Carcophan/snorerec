package de.carcophan.snorerec.audio

import android.util.Log
import java.io.File
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class SnoreEventResult(
    val timestamp: Long,
    val durationMs: Long,
    val peakDb: Float,
    val avgDb: Float,
    val audioFile: File,
    val snoreScore: Float
)

class SnoreDetector(
    private val recordingsDir: File,
    private val ringBuffer: RingBuffer,
    private val minDbThreshold: Float = AudioConfig.SNORE_MIN_DB,
    private val yamnetClassifier: YamnetClassifier? = null,
    private val onSnoreDetected: (SnoreEventResult) -> Unit,
    private val onAudioLevel: (Float) -> Unit
) {
    companion object {
        private const val TAG = "SnoreDetector"
    }

    private enum class State {
        IDLE,
        CANDIDATE,
        ACTIVE
    }

    private var state = State.IDLE
    private var candidateStartTime = 0L
    private var activeStartTime = 0L
    private var lastSnoreFrameTime = 0L
    private var peakDb = 0f
    private var sumDb = 0.0
    private var frameCount = 0

    private var currentWriter: AudioWriter? = null
    private var currentOutputFile: File? = null

    // YAMNet confidence tracking for current event
    private var peakSnoreConfidence = 0f
    private var lastMlCheckTime = 0L

    // Low-pass filter state for snore band energy estimation (~80-700Hz)
    // Simple 1-pole low pass filter at ~700Hz (sampleRate 16kHz)
    // alpha = dt / (RC + dt) ~= 2 * pi * fc / fs ~= 2 * 3.1415 * 700 / 16000 ~= 0.27
    private var filterState = 0.0f
    private val alpha = 0.27f

    fun processAudioFrame(samples: ShortArray, offset: Int = 0, count: Int = samples.size) {
        if (count <= 0) return

        // Write live samples into ring buffer
        ringBuffer.write(samples, offset, count)

        // 1. Calculate RMS & Decibel
        var sumSquares = 0.0
        var lowPassSumSquares = 0.0
        var zeroCrossings = 0
        var prevSign = samples[offset] >= 0

        for (i in 0 until count) {
            val sample = samples[offset + i]
            val sFloat = sample.toFloat()
            sumSquares += sFloat * sFloat

            // Low-pass filtered sample (isolating lower frequency snore range)
            filterState += alpha * (sFloat - filterState)
            lowPassSumSquares += filterState * filterState

            val currentSign = sample >= 0
            if (currentSign != prevSign) {
                zeroCrossings++
                prevSign = currentSign
            }
        }

        val rms = sqrt(sumSquares / count)
        val lowPassRms = sqrt(lowPassSumSquares / count)
        val zcr = zeroCrossings.toFloat() / count.toFloat()

        // Decibel SPL estimation: 32767 is max amplitude -> maps ~20 dB (silence) to ~95 dB (loud)
        val db = if (rms > 1.0) {
            (20.0 * log10(rms / 32767.0) + 90.0).toFloat().coerceIn(20f, 100f)
        } else {
            20f
        }

        onAudioLevel(db)

        val now = System.currentTimeMillis()

        // Snoring characteristics:
        // - Loud enough (> minDbThreshold, typically ~42-45 dB)
        // - Dominant low frequency energy (lowPassRms / rms >= 0.50)
        // - Low zero-crossing rate (typical for voiced/flutter vibration, < 0.22)
        val lowFreqRatio = if (rms > 0.001) (lowPassRms / rms).toFloat() else 0f
        val isSnoreFrame = db >= minDbThreshold && lowFreqRatio >= 0.50f && zcr < 0.22f

        when (state) {
            State.IDLE -> {
                if (isSnoreFrame) {
                    state = State.CANDIDATE
                    candidateStartTime = now
                    lastSnoreFrameTime = now
                    peakDb = db
                    sumDb = db.toDouble()
                    frameCount = 1
                    peakSnoreConfidence = 0f
                }
            }

            State.CANDIDATE -> {
                if (isSnoreFrame) {
                    lastSnoreFrameTime = now
                    peakDb = max(peakDb, db)
                    sumDb += db
                    frameCount++

                    // If candidate persisted for min duration -> verify with ML and transition to ACTIVE
                    if (now - candidateStartTime >= AudioConfig.SNORE_MIN_DURATION_MS) {
                        var allowTransition = true
                        var initialConfidence = 0f

                        // Two-Stage ML verification with YAMNet
                        if (yamnetClassifier != null && yamnetClassifier.isReady) {
                            val candidateAudio = ringBuffer.readLast(AudioConfig.YAMNET_SAMPLE_COUNT)
                            val classification = yamnetClassifier.classify(candidateAudio)
                            if (classification != null) {
                                initialConfidence = classification.snoreConfidence
                                Log.d(TAG, "Candidate ML: snoreConf=${classification.snoreConfidence}, top=${classification.topLabel} (${classification.topConfidence}), isSnoring=${classification.isSnoring}")
                                // Reject false positives early: obvious non-snoring sounds (chair creak, speech, cough, etc.)
                                // where snoring confidence is negligible (< 0.05) and another sound dominates (>= 0.20)
                                if (!classification.isSnoring && (classification.topConfidence >= 0.20f && classification.snoreConfidence < 0.05f)) {
                                    Log.i(TAG, "Rejected candidate as non-snore: ${classification.topLabel} (${classification.topConfidence})")
                                    allowTransition = false
                                }
                            }
                        }

                        if (allowTransition) {
                            state = State.ACTIVE
                            activeStartTime = candidateStartTime
                            peakSnoreConfidence = initialConfidence
                            lastMlCheckTime = now

                            // Start AAC audio file (compact .m4a format, falls back to WAV if needed)
                            val m4aFile = File(recordingsDir, "snore_${activeStartTime}.m4a")
                            val writer: AudioWriter = try {
                                val aacWriter = AacAudioWriter(m4aFile)
                                currentOutputFile = m4aFile
                                aacWriter
                            } catch (_: Exception) {
                                val wavFile = File(recordingsDir, "snore_${activeStartTime}.wav")
                                currentOutputFile = wavFile
                                WavAudioWriter(wavFile)
                            }
                            currentWriter = writer

                            // Prepend pre-roll buffer from ring buffer
                            val preRoll = ringBuffer.readSnapshot()
                            if (preRoll.isNotEmpty()) {
                                writer.writeSamples(preRoll)
                            }
                            // Write current frame
                            writer.writeSamples(samples, offset, count)
                        } else {
                            // Rejected by ML
                            state = State.IDLE
                        }
                    }
                } else {
                    // Check if candidate dropped
                    if (now - lastSnoreFrameTime > 400L) {
                        state = State.IDLE
                    }
                }
            }

            State.ACTIVE -> {
                // Write audio frame
                currentWriter?.writeSamples(samples, offset, count)

                if (isSnoreFrame) {
                    lastSnoreFrameTime = now
                    peakDb = max(peakDb, db)
                    sumDb += db
                    frameCount++

                    // Periodically verify with YAMNet every ~500ms during ongoing snore
                    if (yamnetClassifier != null && yamnetClassifier.isReady && now - lastMlCheckTime >= 500L) {
                        lastMlCheckTime = now
                        val activeAudio = ringBuffer.readLast(AudioConfig.YAMNET_SAMPLE_COUNT)
                        val classification = yamnetClassifier.classify(activeAudio)
                        if (classification != null) {
                            peakSnoreConfidence = max(peakSnoreConfidence, classification.snoreConfidence)
                            Log.d(TAG, "Active ML check: snoreConf=${classification.snoreConfidence}, peak=$peakSnoreConfidence, top=${classification.topLabel}")
                        }
                    }
                }

                val silenceDuration = now - lastSnoreFrameTime
                val totalDuration = now - activeStartTime

                // Finalize if silence exceeded or max clip length reached
                if (silenceDuration >= AudioConfig.SNORE_POST_SILENCE_MS || totalDuration >= AudioConfig.SNORE_MAX_EVENT_DURATION_MS) {
                    finalizeCurrentSnore(now)
                }
            }
        }
    }

    private fun finalizeCurrentSnore(currentTime: Long) {
        val writer = currentWriter
        val file = currentOutputFile
        val start = activeStartTime

        writer?.finish()
        currentWriter = null
        currentOutputFile = null
        state = State.IDLE

        if (file != null && file.exists() && file.length() > 44) {
            // If peakSnoreConfidence is still below threshold, perform a focused check on the
            // audio slice where the sound actually occurred (before post-silence window)
            if (yamnetClassifier != null && yamnetClassifier.isReady && peakSnoreConfidence < AudioConfig.YAMNET_SNORE_THRESHOLD) {
                val silenceMs = (currentTime - lastSnoreFrameTime).coerceAtLeast(0L)
                val silenceSamples = (silenceMs * AudioConfig.SAMPLE_RATE / 1000).toInt()
                val soundAudio = ringBuffer.readWindow(AudioConfig.YAMNET_SAMPLE_COUNT, offsetFromEnd = silenceSamples)
                if (soundAudio.isNotEmpty()) {
                    val finalClassification = yamnetClassifier.classify(soundAudio)
                    if (finalClassification != null) {
                        peakSnoreConfidence = max(peakSnoreConfidence, finalClassification.snoreConfidence)
                        Log.d(TAG, "Final sound-window ML: snoreConf=${finalClassification.snoreConfidence}, top=${finalClassification.topLabel}")
                    }
                }
            }

            // CRITICAL: When YAMNet is available, ONLY record if YAMNet actually confirmed snoring!
            val isConfirmedSnore = if (yamnetClassifier != null && yamnetClassifier.isReady) {
                peakSnoreConfidence >= AudioConfig.YAMNET_SNORE_THRESHOLD
            } else {
                // Fallback heuristic if YAMNet failed to initialize
                true
            }

            if (!isConfirmedSnore) {
                Log.i(TAG, "Discarded false positive event (peakSnoreConfidence: $peakSnoreConfidence < ${AudioConfig.YAMNET_SNORE_THRESHOLD})")
                file.delete()
                return
            }

            val duration = (currentTime - start).coerceAtLeast(600L)
            val avgDb = if (frameCount > 0) (sumDb / frameCount).toFloat() else peakDb

            // Snore score: Use ML confidence when available, else fallback to dB heuristic
            val score = if (yamnetClassifier != null && yamnetClassifier.isReady && peakSnoreConfidence > 0f) {
                peakSnoreConfidence.coerceIn(0.1f, 1.0f)
            } else {
                ((peakDb - minDbThreshold) / 40f).coerceIn(0.1f, 1.0f)
            }

            val result = SnoreEventResult(
                timestamp = start,
                durationMs = duration,
                peakDb = peakDb,
                avgDb = avgDb,
                audioFile = file,
                snoreScore = score
            )
            onSnoreDetected(result)
        }
    }

    fun stopAndFlush() {
        if (state == State.ACTIVE) {
            finalizeCurrentSnore(System.currentTimeMillis())
        }
        state = State.IDLE
        peakSnoreConfidence = 0f
        ringBuffer.clear()
    }
}
