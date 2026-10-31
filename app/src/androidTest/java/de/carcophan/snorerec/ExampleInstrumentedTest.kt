package de.carcophan.snorerec

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.carcophan.snorerec.audio.AudioConfig
import de.carcophan.snorerec.audio.YamnetClassifier
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import android.util.Log

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun testYamnetClassifierOnDevice() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val classifier = YamnetClassifier(appContext)
        assertTrue("YamnetClassifier should be ready", classifier.isReady)

        // Test with 15600 silence samples
        val silence = ShortArray(AudioConfig.YAMNET_SAMPLE_COUNT)
        val resultSilence = classifier.classify(silence)
        assertNotNull("Result should not be null", resultSilence)
        Log.i("YamnetTest", "Silence result: snoreConfidence=${resultSilence?.snoreConfidence}, topLabel=${resultSilence?.topLabel}, topConfidence=${resultSilence?.topConfidence}")

        // Test with a synthetic audio (e.g. 150 Hz tone, snore fundamental range)
        val tone = ShortArray(AudioConfig.YAMNET_SAMPLE_COUNT) { i ->
            (Math.sin(2.0 * Math.PI * 150.0 * i / 16000.0) * 10000).toInt().toShort()
        }
        val resultTone = classifier.classify(tone)
        assertNotNull("Result should not be null", resultTone)
        Log.i("YamnetTest", "Tone result: snoreConfidence=${resultTone?.snoreConfidence}, topLabel=${resultTone?.topLabel}, topConfidence=${resultTone?.topConfidence}")

        classifier.close()
    }
}