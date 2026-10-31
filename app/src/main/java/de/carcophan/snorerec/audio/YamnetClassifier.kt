package de.carcophan.snorerec.audio

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.LiteRtException
import com.google.ai.edge.litert.TensorBuffer
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.max

data class YamnetClassification(
    val snoreConfidence: Float,
    val topLabel: String,
    val topConfidence: Float,
    val isSnoring: Boolean,
)

/**
 * On-device audio classifier utilizing Google's YAMNet model via Google AI Edge LiteRT.
 * Input: 15,600 16kHz PCM audio samples (0.975 seconds).
 * Output: Probabilities across 521 AudioSet sound classes.
 */
class YamnetClassifier(context: Context) : Closeable {

    companion object {
        private const val TAG = "YamnetClassifier"
        private const val MODEL_FILE = "yamnet.tflite"
        private const val LABELS_FILE = "yamnet_label_list.txt"

        private val environment by lazy { Environment.create() }
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "YamnetInferenceThread").apply { isDaemon = true }
    }

    private var model: CompiledModel? = null
    private val inputBuffers = mutableListOf<TensorBuffer>()
    private val outputBuffers = mutableListOf<TensorBuffer>()
    private val labels = mutableListOf<String>()

    private val inputFloats = FloatArray(AudioConfig.YAMNET_SAMPLE_COUNT)

    init {
        // Load label map from assets
        try {
            context.assets.open(LABELS_FILE).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) labels.add(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load YAMNet labels", e)
        }

        // Initialize LiteRT CompiledModel on the dedicated background executor
        val initFuture = executor.submit(Callable {
            try {
                val compiled = try {
                    CompiledModel.create(
                        context.assets,
                        MODEL_FILE,
                        CompiledModel.Options(Accelerator.CPU),
                        environment
                    )
                } catch (e: LiteRtException) {
                    Log.e(TAG, "Failed to create LiteRT CompiledModel", e)
                    throw e
                }

                model = compiled
                inputBuffers.addAll(compiled.createInputBuffers())
                outputBuffers.addAll(compiled.createOutputBuffers())

                // Warm-up inference
                if (inputBuffers.isNotEmpty() && outputBuffers.isNotEmpty()) {
                    inputBuffers[0].writeFloat(FloatArray(AudioConfig.YAMNET_SAMPLE_COUNT))
                    compiled.run(inputBuffers, outputBuffers)
                    outputBuffers[0].readFloat()
                }
                Log.i(TAG, "YamnetClassifier initialized successfully with ${labels.size} labels.")
                true
            } catch (e: Exception) {
                closeInternal()
                Log.e(TAG, "Error initializing YamnetClassifier", e)
                false
            }
        })

        try {
            initFuture.get()
        } catch (e: Exception) {
            Log.e(TAG, "Failed during Yamnet initialization wait", e)
        }
    }

    val isReady: Boolean
        get() = (model != null) && inputBuffers.isNotEmpty() && outputBuffers.isNotEmpty()

    /**
     * Classifies 16kHz PCM audio samples.
     * Takes up to [AudioConfig.YAMNET_SAMPLE_COUNT] samples. If fewer, pads with 0; if more, uses the most recent.
     */
    fun classify(pcmSamples: ShortArray): YamnetClassification? {
        if (!isReady) return null

        val sampleCount = AudioConfig.YAMNET_SAMPLE_COUNT
        val startOffset = if (pcmSamples.size > sampleCount) pcmSamples.size - sampleCount else 0
        val countToCopy = minOf(pcmSamples.size, sampleCount)

        // Convert PCM 16-bit Short (-32768..32767) to Float (-1.0f..1.0f)
        for (i in 0 until countToCopy) {
            inputFloats[i] = pcmSamples[startOffset + i] / 32768.0f
        }
        for (i in countToCopy until sampleCount) {
            inputFloats[i] = 0f
        }

        return try {
            val future = executor.submit(Callable {
                val compiled = model ?: return@Callable null
                if (inputBuffers.isEmpty() || outputBuffers.isEmpty()) return@Callable null

                inputBuffers[0].writeFloat(inputFloats)
                compiled.run(inputBuffers, outputBuffers)
                val scores = outputBuffers[0].readFloat()

                parseScores(scores)
            })
            future.get()
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            null
        }
    }

    private fun parseScores(scores: FloatArray): YamnetClassification {
        val snoreScore = scores.getOrElse(AudioConfig.YAMNET_SNORING_INDEX) { 0f }
        val snortScore = scores.getOrElse(AudioConfig.YAMNET_SNORT_INDEX) { 0f }
        val snoreConfidence = max(snoreScore, snortScore)

        var maxIndex = 0
        var maxScore = 0f
        for (i in scores.indices) {
            if (scores[i] > maxScore) {
                maxScore = scores[i]
                maxIndex = i
            }
        }

        val topLabel = labels.getOrElse(maxIndex) { "Class #$maxIndex" }
        val isSnoring = snoreConfidence >= AudioConfig.YAMNET_SNORE_THRESHOLD ||
                (maxIndex == AudioConfig.YAMNET_SNORING_INDEX || maxIndex == AudioConfig.YAMNET_SNORT_INDEX)

        return YamnetClassification(
            snoreConfidence = snoreConfidence,
            topLabel = topLabel,
            topConfidence = maxScore,
            isSnoring = isSnoring
        )
    }

    private fun closeInternal() {
        try {
            (inputBuffers + outputBuffers).forEach {
                try {
                    it.close()
                } catch (_: Exception) {}
            }
            inputBuffers.clear()
            outputBuffers.clear()
            model?.close()
            model = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LiteRT resources", e)
        }
    }

    override fun close() {
        try {
            executor.submit { closeInternal() }.get()
        } catch (_: Exception) {
            closeInternal()
        } finally {
            executor.shutdown()
        }
    }
}
