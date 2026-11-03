package de.carcophan.snorerec

import de.carcophan.snorerec.audio.AudioConfig
import de.carcophan.snorerec.audio.RingBuffer
import de.carcophan.snorerec.audio.SnoreDetector
import de.carcophan.snorerec.audio.SnoreEventResult
import de.carcophan.snorerec.audio.WavAudioWriter
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreIntensity
import de.carcophan.snorerec.ui.components.MAX_TIMELINE_ZOOM
import de.carcophan.snorerec.ui.components.MIN_TIMELINE_ZOOM
import de.carcophan.snorerec.ui.components.computePinchZoomViewport
import de.carcophan.snorerec.ui.components.computeTimeAxisStepMs
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
        assertEquals(SnoreIntensity.MILD, SnoreIntensity.fromDb(51.9f))
        assertEquals(SnoreIntensity.MODERATE, SnoreIntensity.fromDb(52f))
        assertEquals(SnoreIntensity.MODERATE, SnoreIntensity.fromDb(67.9f))
        assertEquals(SnoreIntensity.HEAVY, SnoreIntensity.fromDb(68f))
        assertEquals(SnoreIntensity.HEAVY, SnoreIntensity.fromDb(85f))
    }

    @Test
    fun snoreIntensity_fromEvent_doesNotMarkMildSnoringAsHeavy() {
        // Mild snoring with a momentary spike (e.g. 62 dB peak) should NOT be classified as Episch (HEAVY)
        val mildWithSpike = SnoreIntensity.fromEvent(avgDb = 45f, peakDb = 62f)
        assertTrue("Mild snoring with 62 dB peak should not be HEAVY/Episch", mildWithSpike != SnoreIntensity.HEAVY)

        val mildQuiet = SnoreIntensity.fromEvent(avgDb = 42f, peakDb = 50f)
        assertEquals(SnoreIntensity.MILD, mildQuiet)

        val severeHeavy = SnoreIntensity.fromEvent(avgDb = 65f, peakDb = 72f)
        assertEquals(SnoreIntensity.HEAVY, severeHeavy)
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
        // 2. Breathing pause: 4.0s silence (< SNORE_SEQUENCE_GAP_MS)
        feedFrames(silenceFrame, 4000L)
        // 3. Snore #2: 1.5s snore
        feedFrames(snoreFrame, 1500L)
        // 4. Realistic 25s breathing pause between snore cycles (previously > 20s split into fragments!)
        feedFrames(silenceFrame, 25000L)
        // 5. Snore #3: 1.5s snore
        feedFrames(snoreFrame, 1500L)

        // Still active in the same sequence -> 0 finalized files so far
        assertEquals(0, detectedEvents.size)

        // 6. Now 41s of silence (> SNORE_SEQUENCE_GAP_MS = 40s) to finish the sequence
        feedFrames(silenceFrame, 41000L)

        // All 3 snores + breathing pauses between them must be saved in ONE single cohesive file
        assertEquals(1, detectedEvents.size)
        val event = detectedEvents.first()
        assertTrue(event.audioFile.exists())

        // Expected sequence duration:
        // 1.5s + 4.0s + 1.5s + 25.0s + 1.5s = 33.5s of sequence + 3.0s post-silence = ~36.5s
        // (trailing silence after post-roll is discarded)
        assertTrue("Expected duration around 36.5s, got ${event.durationMs}ms", event.durationMs in 35000L..38000L)
    }

    @Test
    fun audioMerger_mergesWavFilesCorrectly() {
        val fileA = tempFolder.newFile("snoreA.wav")
        val fileB = tempFolder.newFile("snoreB.wav")
        val mergedOut = tempFolder.newFile("snore_merged.wav")

        val writerA = WavAudioWriter(fileA, sampleRate = AudioConfig.SAMPLE_RATE)
        writerA.writeSamples(ShortArray(1600) { 100 }) // 100ms
        writerA.finish()

        val writerB = WavAudioWriter(fileB, sampleRate = AudioConfig.SAMPLE_RATE)
        writerB.writeSamples(ShortArray(3200) { 200 }) // 200ms
        writerB.finish()

        val success = de.carcophan.snorerec.audio.AudioMerger.mergeFiles(fileA, fileB, mergedOut, gapDurationMs = 100L)
        assertTrue(success)
        assertTrue(mergedOut.exists())

        // 1600 + (100ms * 16 = 1600) + 3200 = 6400 samples * 2 bytes = 12800 payload bytes + 44 header = 12844
        assertEquals(44L + 12800L, mergedOut.length())
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

    @Test
    fun amplifySample_boostsQuietSamplesAndSoftLimitsLoudPeaks() {
        // Quiet nightstand snore sample (~500 out of 32767) should be amplified linearly by RECORDING_GAIN_FACTOR (8x -> 4000)
        val quietSample: Short = 500
        val boostedQuiet = AudioConfig.amplifySample(quietSample)
        assertEquals(4000.toShort(), boostedQuiet)

        // Very loud sample (10000 * 8 = 80000 > 32767) should be smoothly soft-limited below 32767 without integer overflow
        val loudSample: Short = 10000
        val boostedLoud = AudioConfig.amplifySample(loudSample)
        assertTrue("Expected soft-limited positive peak > 24000, got $boostedLoud", boostedLoud > 24000)

        val negativeLoudSample: Short = -10000
        val boostedNegativeLoud = AudioConfig.amplifySample(negativeLoudSample)
        assertTrue("Expected soft-limited negative peak < -24000, got $boostedNegativeLoud", boostedNegativeLoud < -24000)
    }

    @Test
    fun timelinePinchZoom_anchorsFocalPointAndClampsBounds() {
        // Zoom in 2x centered at the middle of the chart (0.5f)
        val zoomedIn = computePinchZoomViewport(
            currentZoom = 1f,
            currentStartFraction = 0f,
            zoomChange = 2f,
            panXInPlotFraction = 0f,
            focalFractionInPlot = 0.5f
        )
        assertEquals(2f, zoomedIn.zoomLevel, 0.001f)
        // At 2x zoom, visible span is 0.5 of the night; centered around 0.5 means start is 0.25
        assertEquals(0.25f, zoomedIn.viewStartFraction, 0.001f)

        // Zoom in another 2x (total 4x) at the same center point (0.5f in plot -> 0.5f of night)
        val zoomedMore = computePinchZoomViewport(
            currentZoom = zoomedIn.zoomLevel,
            currentStartFraction = zoomedIn.viewStartFraction,
            zoomChange = 2f,
            panXInPlotFraction = 0f,
            focalFractionInPlot = 0.5f
        )
        assertEquals(4f, zoomedMore.zoomLevel, 0.001f)
        // Visible span is 0.25; centered around 0.5 means start is 0.375
        assertEquals(0.375f, zoomedMore.viewStartFraction, 0.001f)

        // Zoom out back to 1x clamps start fraction to 0f and zoom to MIN_TIMELINE_ZOOM
        val zoomedOut = computePinchZoomViewport(
            currentZoom = zoomedMore.zoomLevel,
            currentStartFraction = zoomedMore.viewStartFraction,
            zoomChange = 0.1f,
            panXInPlotFraction = 0f,
            focalFractionInPlot = 0.5f
        )
        assertEquals(MIN_TIMELINE_ZOOM, zoomedOut.zoomLevel, 0.001f)
        assertEquals(0f, zoomedOut.viewStartFraction, 0.001f)

        // Extreme zoom clamps to MAX_TIMELINE_ZOOM and keeps start within [0, 1 - 1/maxZoom]
        val maxedOut = computePinchZoomViewport(
            currentZoom = 10f,
            currentStartFraction = 0.85f,
            zoomChange = 5f,
            panXInPlotFraction = -0.5f,
            focalFractionInPlot = 0.9f
        )
        assertEquals(MAX_TIMELINE_ZOOM, maxedOut.zoomLevel, 0.001f)
        assertTrue(maxedOut.viewStartFraction in 0f..(1f - 1f / MAX_TIMELINE_ZOOM))
    }

    @Test
    fun timelineAxisStep_adaptsToZoomedVisibleSpan() {
        assertEquals(2 * 3600_000L, computeTimeAxisStepMs(11 * 3600_000L))
        assertEquals(3600_000L, computeTimeAxisStepMs(6 * 3600_000L))
        assertEquals(15 * 60_000L, computeTimeAxisStepMs(90 * 60_000L))
        assertEquals(5 * 60_000L, computeTimeAxisStepMs(30 * 60_000L))
        assertEquals(60_000L, computeTimeAxisStepMs(5 * 60_000L))
    }
}
