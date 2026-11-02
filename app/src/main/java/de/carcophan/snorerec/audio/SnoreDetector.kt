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
    private val onAudioLevel: (Float) -> Unit,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
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
    private var lastWrittenAudioTime = 0L
    private var inSnoreBurst = false
    private var burstStartTime = 0L
    private var lastBurstFrameTime = 0L
    private var peakDb = 0f
    private var sumDb = 0.0
    private var frameCount = 0

    private var currentWriter: AudioWriter? = null
    private var currentOutputFile: File? = null

    // Buffer for audio frames during breathing pauses between snores in a sequence
    // (after SNORE_POST_SILENCE_MS up to SNORE_SEQUENCE_GAP_MS).
    // Flushed to the audio file if another snore follows; discarded if the sequence ends.
    private val maxGapSamples = ((AudioConfig.SNORE_SEQUENCE_GAP_MS + 4000L) * AudioConfig.SAMPLE_RATE / 1000L).toInt()
    private val pendingGapBuffer = ShortArray(maxGapSamples)
    private var pendingGapCount = 0

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

        val now = timeProvider()

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
                            lastSnoreFrameTime = now
                            lastWrittenAudioTime = now
                            inSnoreBurst = true
                            burstStartTime = candidateStartTime
                            lastBurstFrameTime = now
                            pendingGapCount = 0
                            peakSnoreConfidence = initialConfidence
                            lastMlCheckTime = now

                            // Start AAC audio file (compact .m4a format, falls back to WAV if needed)
                            val m4aFile = File(recordingsDir, "snore_${activeStartTime}.m4a")
                            val writer: AudioWriter = try {
                                val aacWriter = AacAudioWriter(m4aFile)
                                currentOutputFile = m4aFile
                                aacWriter
                            } catch (_: Throwable) {
                                m4aFile.delete()
                                val wavFile = File(recordingsDir, "snore_${activeStartTime}.wav")
                                currentOutputFile = wavFile
                                WavAudioWriter(wavFile)
                            }
                            currentWriter = writer

                            // Prepend pre-roll buffer from ring buffer (already includes current frame)
                            val preRoll = ringBuffer.readSnapshot()
                            if (preRoll.isNotEmpty()) {
                                writer.writeSamples(preRoll)
                            }
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
                if (inSnoreBurst) {
                    if (isSnoreFrame) {
                        lastBurstFrameTime = now
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
                    } else if (now - lastBurstFrameTime > AudioConfig.SNORE_CONTINUATION_DROPOUT_MS) {
                        // Individual snore breath ended; enter inter-snore breathing pause
                        inSnoreBurst = false
                        burstStartTime = 0L
                        verifyRecentBurstWithMl(now)
                    }
                } else {
                    // Currently in breathing pause between snores in a sequence
                    if (isSnoreFrame) {
                        if (burstStartTime == 0L) {
                            burstStartTime = now
                        }
                        lastBurstFrameTime = now
                        // Keep sequence alive immediately whenever snore sound is detected
                        lastSnoreFrameTime = now
                        peakDb = max(peakDb, db)
                        sumDb += db
                        frameCount++

                        // For continuation in an already established sequence, use lower duration threshold
                        if (now - burstStartTime >= AudioConfig.SNORE_CONTINUATION_MIN_DURATION_MS) {
                            var allowBurst = true
                            if (yamnetClassifier != null && yamnetClassifier.isReady) {
                                val candidateAudio = ringBuffer.readLast(AudioConfig.YAMNET_SAMPLE_COUNT)
                                val classification = yamnetClassifier.classify(candidateAudio)
                                if (classification != null) {
                                    if (!classification.isSnoring && (classification.topConfidence >= 0.40f && classification.snoreConfidence < 0.05f)) {
                                        Log.i(TAG, "Ignored non-snore sound during sequence pause: ${classification.topLabel} (${classification.topConfidence})")
                                        allowBurst = false
                                    } else {
                                        peakSnoreConfidence = max(peakSnoreConfidence, classification.snoreConfidence)
                                    }
                                }
                            }

                            if (allowBurst) {
                                // Next snore in the sequence confirmed!
                                inSnoreBurst = true
                                lastSnoreFrameTime = now
                                lastMlCheckTime = now
                            } else {
                                burstStartTime = 0L
                            }
                        }
                    } else if (burstStartTime != 0L && now - lastBurstFrameTime > AudioConfig.SNORE_CONTINUATION_DROPOUT_MS) {
                        burstStartTime = 0L
                    }
                }

                val silenceDuration = now - lastSnoreFrameTime
                val totalDuration = now - activeStartTime

                if (inSnoreBurst || silenceDuration <= AudioConfig.SNORE_POST_SILENCE_MS) {
                    // Flush any buffered breathing-pause samples so the sequence audio is continuous
                    if (pendingGapCount > 0) {
                        currentWriter?.writeSamples(pendingGapBuffer, 0, pendingGapCount)
                        pendingGapCount = 0
                    }
                    currentWriter?.writeSamples(samples, offset, count)
                    lastWrittenAudioTime = now
                } else {
                    // Past post-silence tail: if the initial sound was never confirmed as a snore by ML,
                    // discard immediately rather than holding a 20s sequence window open.
                    if (yamnetClassifier != null && yamnetClassifier.isReady &&
                        peakSnoreConfidence < AudioConfig.YAMNET_SNORE_THRESHOLD && burstStartTime == 0L
                    ) {
                        finalizeCurrentSnore(lastWrittenAudioTime)
                        return
                    }

                    // Buffer pause audio in memory in case another snore follows in this sequence
                    val toCopy = minOf(count, pendingGapBuffer.size - pendingGapCount)
                    if (toCopy > 0) {
                        System.arraycopy(samples, offset, pendingGapBuffer, pendingGapCount, toCopy)
                        pendingGapCount += toCopy
                    }
                }

                // Finalize sequence if silence between snores exceeded SNORE_SEQUENCE_GAP_MS
                // or maximum sequence clip duration reached
                val sequenceGapExceeded = !inSnoreBurst && burstStartTime == 0L &&
                    silenceDuration >= AudioConfig.SNORE_SEQUENCE_GAP_MS
                if (sequenceGapExceeded || totalDuration >= AudioConfig.SNORE_MAX_EVENT_DURATION_MS) {
                    finalizeCurrentSnore(lastWrittenAudioTime)
                }
            }
        }
    }

    private fun verifyRecentBurstWithMl(currentTime: Long) {
        if (yamnetClassifier != null && yamnetClassifier.isReady && peakSnoreConfidence < AudioConfig.YAMNET_SNORE_THRESHOLD) {
            val silenceMs = (currentTime - lastSnoreFrameTime).coerceAtLeast(0L)
            val silenceSamples = (silenceMs * AudioConfig.SAMPLE_RATE / 1000).toInt()
            val soundAudio = ringBuffer.readWindow(AudioConfig.YAMNET_SAMPLE_COUNT, offsetFromEnd = silenceSamples)
            if (soundAudio.isNotEmpty()) {
                val finalClassification = yamnetClassifier.classify(soundAudio)
                if (finalClassification != null) {
                    peakSnoreConfidence = max(peakSnoreConfidence, finalClassification.snoreConfidence)
                    Log.d(TAG, "Burst sound-window ML: snoreConf=${finalClassification.snoreConfidence}, top=${finalClassification.topLabel}")
                }
            }
        }
    }

    private fun finalizeCurrentSnore(endTime: Long) {
        val writer = currentWriter
        val file = currentOutputFile
        val start = activeStartTime

        writer?.finish()
        currentWriter = null
        currentOutputFile = null
        state = State.IDLE
        inSnoreBurst = false
        burstStartTime = 0L
        pendingGapCount = 0

        if (file != null && file.exists() && file.length() > 44) {
            verifyRecentBurstWithMl(endTime)

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

            val duration = (endTime - start).coerceAtLeast(600L)
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
            val endTime = if (lastWrittenAudioTime >= activeStartTime) lastWrittenAudioTime else timeProvider()
            finalizeCurrentSnore(endTime)
        }
        state = State.IDLE
        inSnoreBurst = false
        burstStartTime = 0L
        pendingGapCount = 0
        peakSnoreConfidence = 0f
        ringBuffer.clear()
    }
}
