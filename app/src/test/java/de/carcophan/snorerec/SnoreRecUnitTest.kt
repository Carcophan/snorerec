package de.carcophan.snorerec

import de.carcophan.snorerec.audio.AudioConfig
import de.carcophan.snorerec.audio.RingBuffer
import de.carcophan.snorerec.audio.SnoreDetector
import de.carcophan.snorerec.audio.SnoreEventResult
import de.carcophan.snorerec.audio.WavAudioWriter
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.PI
import kotlin.math.sin

class SnoreRecUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun ringBuffer_preservesLatestSamplesWhenOverflown() {
        val ring = RingBuffer(capacity = 5)
        val initial = shortArrayOf(1, 2, 3)
        ring.write(initial)
        val snap1 = ring.readSnapshot()
        assertEquals(3, snap1.size)
        assertEquals(1.toShort(), snap1[0])
        assertEquals(3.toShort(), snap1[2])

        // Overflow by writing 4 more samples (total capacity 5)
        val second = shortArrayOf(4, 5, 6, 7)
        ring.write(second)
        val snap2 = ring.readSnapshot()
        assertEquals(5, snap2.size)
        // Should contain last 5 samples: 3, 4, 5, 6, 7
        assertEquals(3.toShort(), snap2[0])
        assertEquals(4.toShort(), snap2[1])
        assertEquals(5.toShort(), snap2[2])
        assertEquals(6.toShort(), snap2[3])
        assertEquals(7.toShort(), snap2[4])

        // Test readLast(3) -> 5, 6, 7
        val last3 = ring.readLast(3)
        assertEquals(3, last3.size)
        assertEquals(5.toShort(), last3[0])
        assertEquals(6.toShort(), last3[1])
        assertEquals(7.toShort(), last3[2])

        // Test readWindow(2, offsetFromEnd = 2) on [3, 4, 5, 6, 7]:
        // offsetFromEnd = 2 skips 6, 7 -> window ending before 6 -> [4, 5]
        val window = ring.readWindow(2, offsetFromEnd = 2)
        assertEquals(2, window.size)
        assertEquals(4.toShort(), window[0])
        assertEquals(5.toShort(), window[1])
    }

    @Test
    fun snoreIntensity_classifiesCorrectly() {
        assertEquals(SnoreIntensity.MILD, SnoreIntensity.fromDb(35f))
        assertEquals(SnoreIntensity.MILD, SnoreIntensity.fromDb(47.9f))
        assertEquals(SnoreIntensity.MODERATE, SnoreIntensity.fromDb(48f))
        assertEquals(SnoreIntensity.MODERATE, SnoreIntensity.fromDb(61.9f))
        assertEquals(SnoreIntensity.HEAVY, SnoreIntensity.fromDb(62f))
        assertEquals(SnoreIntensity.HEAVY, SnoreIntensity.fromDb(85f))
    }

    @Test
    fun nightSession_calculatesMetricsCorrectly() {
        val start = 1_000_000L
        val end = start + (8 * 3600 * 1000L) // 8 hours = 28,800,000 ms
        val totalSnore = 2 * 3600 * 1000L // 2 hours = 7,200,000 ms
        val count = 120

        val session = NightSession(
            id = 1,
            startTime = start,
            endTime = end,
            totalSnoreDurationMs = totalSnore,
            snoreCount = count,
            peakDb = 72f,
            avgDb = 55f
        )

        assertEquals(8 * 3600 * 1000L, session.durationMs)
        // 2 hours out of 8 hours = 25%
        assertEquals(25.0f, session.snorePercentage, 0.01f)
        // 120 snores in 8 hours = 15 snores/hour
        assertEquals(15.0f, session.snoresPerHour, 0.01f)
    }

    @Test
    fun wavAudioWriter_createsValidWavFileWithHeader() {
        val outputFile = tempFolder.newFile("test_snore.wav")
        val writer = WavAudioWriter(outputFile, sampleRate = 16000)

        val testSamples = ShortArray(1600) { (it % 100).toShort() }
        writer.writeSamples(testSamples)
        writer.finish()

        assertTrue(outputFile.exists())
        // 44 bytes header + 1600 * 2 bytes = 3244 bytes
        assertEquals(44L + 3200L, outputFile.length())
    }

    @Test
    fun snoreDetector_combinesContiguousSnoreSequenceIntoSingleFile() {
        val recordingsDir = tempFolder.newFolder("recordings")
        val ringBuffer = RingBuffer(AudioConfig.RING_BUFFER_CAPACITY)
        val detectedEvents = mutableListOf<SnoreEventResult>()
        var simulatedTimeMs = 1_000_000L

        val detector = SnoreDetector(
            recordingsDir = recordingsDir,
            ringBuffer = ringBuffer,
            yamnetClassifier = null,
            onSnoreDetected = { detectedEvents.add(it) },
            onAudioLevel = {},
            timeProvider = { simulatedTimeMs }
        )

        // Frame size: 1600 samples = 100ms at 16kHz
        val frameSamples = 1600
        val frameDurationMs = 100L

        // Generate 150Hz low-frequency snore-like sine wave (~65 dB)
        val snoreFrame = ShortArray(frameSamples) { i ->
            (3000.0 * sin(2.0 * PI * 150.0 * i / AudioConfig.SAMPLE_RATE)).toInt().toShort()
        }
        val silenceFrame = ShortArray(frameSamples) { 0 }

        fun feedFrames(frame: ShortArray, durationMs: Long) {
            val steps = (durationMs / frameDurationMs).toInt()
            repeat(steps) {
                simulatedTimeMs += frameDurationMs
                detector.processAudioFrame(frame)
            }
        }

        // 1. Snore #1: 1.5s snore
        feedFrames(snoreFrame, 1500L)
        // 2. Breathing pause: 4.0s silence (< SNORE_SEQUENCE_GAP_MS, previously > 1.2s would have split!)
        feedFrames(silenceFrame, 4000L)
        // 3. Snore #2: 1.5s snore
        feedFrames(snoreFrame, 1500L)
        // 4. Longer breathing pause: 8.0s silence (still within 20s sequence gap)
        feedFrames(silenceFrame, 8000L)
        // 5. Snore #3: 1.5s snore
        feedFrames(snoreFrame, 1500L)

        // Still active in the same sequence -> 0 finalized files so far
        assertEquals(0, detectedEvents.size)

        // 6. Now 21s of silence (> SNORE_SEQUENCE_GAP_MS = 20s) to finish the sequence
        feedFrames(silenceFrame, 21000L)

        // All 3 snores + breathing pauses between them must be saved in ONE single file
        assertEquals(1, detectedEvents.size)
        val event = detectedEvents.first()
        assertTrue(event.audioFile.exists())

        // Expected sequence duration:
        // 1.5s + 4.0s + 1.5s + 8.0s + 1.5s = 16.5s of sequence + 1.5s post-silence = ~18.0s
        // (and NOT 16.5s + 21s = 37.5s, because trailing silence after post-roll is discarded)
        assertTrue("Expected duration around 18s, got ${event.durationMs}ms", event.durationMs in 17000L..19000L)
    }

    @Test
    fun snoreScore_weightsIntensityLevelsLikeSnoreLab() {
        val eightHoursMs = 8 * 3600 * 1000L
        val oneHourMs = 3600 * 1000L

        val mildScore = NightSession.computeSnoreScore(
            totalSleepMs = eightHoursMs,
            totalSnoreMs = oneHourMs,
            mildMs = oneHourMs,
            moderateMs = 0L,
            heavyMs = 0L,
            avgDb = 45f,
            peakDb = 47f
        )

        val epicScore = NightSession.computeSnoreScore(
            totalSleepMs = eightHoursMs,
            totalSnoreMs = oneHourMs,
            mildMs = 0L,
            moderateMs = 0L,
            heavyMs = oneHourMs,
            avgDb = 66f,
            peakDb = 72f
        )

        assertTrue("Mild score ($mildScore) should be > 0", mildScore > 0)
        assertTrue("Epic score ($epicScore) must be significantly higher than mild score ($mildScore)", epicScore > mildScore * 3)
    }
}
