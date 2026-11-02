package de.carcophan.snorerec.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioMerger {
    private const val TAG = "AudioMerger"

    /**
     * Merges two audio files (either both WAV or both M4A) into a single continuous file.
     * Inserts silence in the gap between them if gapDurationMs > 0.
     */
    fun mergeFiles(fileA: File, fileB: File, outputFile: File, gapDurationMs: Long = 0L): Boolean {
        if (!fileA.exists()) return false
        if (!fileB.exists()) return false

        val isWav = fileA.name.endsWith(".wav", ignoreCase = true) && fileB.name.endsWith(".wav", ignoreCase = true)
        val isM4a = fileA.name.endsWith(".m4a", ignoreCase = true) && fileB.name.endsWith(".m4a", ignoreCase = true)

        return try {
            when {
                isWav -> mergeWavFiles(fileA, fileB, outputFile, gapDurationMs)
                isM4a -> mergeM4aFiles(fileA, fileB, outputFile, gapDurationMs)
                else -> {
                    // Fallback: If different formats or unsupported, keep fileA
                    fileA.copyTo(outputFile, overwrite = true)
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge audio files: ${fileA.name} + ${fileB.name}", e)
            false
        }
    }

    private fun mergeWavFiles(fileA: File, fileB: File, outputFile: File, gapDurationMs: Long): Boolean {
        val lenA = fileA.length()
        val lenB = fileB.length()
        if (lenA < 44 || lenB < 44) return false

        val pcmBytesA = lenA - 44
        val pcmBytesB = lenB - 44
        val gapBytes = (gapDurationMs * AudioConfig.SAMPLE_RATE * 2 / 1000L).coerceAtLeast(0L).toInt()
        val totalPayloadBytes = pcmBytesA + gapBytes + pcmBytesB

        FileOutputStream(outputFile).use { out ->
            // 1. Write dummy 44-byte WAV header (will be patched later)
            out.write(ByteArray(44))

            // 2. Stream PCM data from fileA
            FileInputStream(fileA).use { inA ->
                inA.skip(44)
                val buffer = ByteArray(8192)
                var read: Int
                while (inA.read(buffer).also { read = it } > 0) {
                    out.write(buffer, 0, read)
                }
            }

            // 3. Write silence gap (zeros)
            if (gapBytes > 0) {
                val silenceBuffer = ByteArray(minOf(gapBytes, 8192))
                var remainingGap = gapBytes
                while (remainingGap > 0) {
                    val toWrite = minOf(remainingGap, silenceBuffer.size)
                    out.write(silenceBuffer, 0, toWrite)
                    remainingGap -= toWrite
                }
            }

            // 4. Stream PCM data from fileB
            FileInputStream(fileB).use { inB ->
                inB.skip(44)
                val buffer = ByteArray(8192)
                var read: Int
                while (inB.read(buffer).also { read = it } > 0) {
                    out.write(buffer, 0, read)
                }
            }
        }

        // Patch WAV header with total size
        RandomAccessFile(outputFile, "rw").use { raf ->
            val byteRate = AudioConfig.SAMPLE_RATE * 1 * 2
            val blockAlign = (1 * 2).toShort()
            val totalDataLen = totalPayloadBytes + 36

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalDataLen.toInt())
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1.toShort()) // PCM
            header.putShort(1.toShort()) // 1 channel
            header.putInt(AudioConfig.SAMPLE_RATE)
            header.putInt(byteRate)
            header.putShort(blockAlign)
            header.putShort(16.toShort())
            header.put("data".toByteArray())
            header.putInt(totalPayloadBytes.toInt())

            raf.seek(0)
            raf.write(header.array())
        }

        return true
    }

    private fun mergeM4aFiles(fileA: File, fileB: File, outputFile: File, gapDurationMs: Long): Boolean {
        var muxer: MediaMuxer? = null
        val extractorA = MediaExtractor()
        val extractorB = MediaExtractor()

        try {
            extractorA.setDataSource(fileA.absolutePath)
            extractorB.setDataSource(fileB.absolutePath)

            val trackIndexA = selectAudioTrack(extractorA)
            val trackIndexB = selectAudioTrack(extractorB)
            if (trackIndexA < 0 || trackIndexB < 0) return false

            extractorA.selectTrack(trackIndexA)
            extractorB.selectTrack(trackIndexB)

            val formatA = extractorA.getTrackFormat(trackIndexA)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrack = muxer.addTrack(formatA)
            muxer.start()

            val maxBufferSize = 32768
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var lastPtsUsA = 0L
            while (true) {
                buffer.clear()
                val sampleSize = extractorA.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTime = extractorA.sampleTime
                val flags = extractorA.sampleFlags
                bufferInfo.set(0, sampleSize, sampleTime, flags)
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                lastPtsUsA = maxOf(lastPtsUsA, sampleTime)
                extractorA.advance()
            }

            // Duration of fileA plus the silence gap in microseconds
            val gapUs = (gapDurationMs * 1000L).coerceAtLeast(0L)
            // Estimated step: 1024 samples at 16kHz = 64,000 us per frame
            val offsetPtsUs = lastPtsUsA + 64_000L + gapUs

            var firstPtsB = -1L
            while (true) {
                buffer.clear()
                val sampleSize = extractorB.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTime = extractorB.sampleTime
                if (firstPtsB < 0) firstPtsB = sampleTime
                val relativeTime = (sampleTime - firstPtsB).coerceAtLeast(0L)
                val newPtsUs = offsetPtsUs + relativeTime

                val flags = extractorB.sampleFlags
                bufferInfo.set(0, sampleSize, newPtsUs, flags)
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                extractorB.advance()
            }

            return true
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractorA.release() } catch (_: Exception) {}
            try { extractorB.release() } catch (_: Exception) {}
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }
}

