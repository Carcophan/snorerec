package de.carcophan.snorerec

import de.carcophan.snorerec.audio.RingBuffer
import de.carcophan.snorerec.audio.WavAudioWriter
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
}
