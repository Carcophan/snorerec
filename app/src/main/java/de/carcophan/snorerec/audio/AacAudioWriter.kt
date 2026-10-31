package de.carcophan.snorerec.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes 16-bit 16kHz PCM audio directly to an AAC (.m4a) file using hardware/native MediaCodec.
 * Reduces storage requirements by ~85-90% compared to raw WAV.
 */
class AacAudioWriter(
    val outputFile: File,
    private val sampleRate: Int = AudioConfig.SAMPLE_RATE,
    private val channelCount: Int = 1,
    private val bitRate: Int = 32000
) : AudioWriter {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private var presentationTimeUs = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    init {
        outputFile.parentFile?.mkdirs()
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            codec = encoder

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (_: Exception) {
            release()
        }
    }

    override fun writeSamples(samples: ShortArray, offset: Int, count: Int) {
        val encoder = codec ?: return
        if (count <= 0) return

        val byteBuffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            byteBuffer.putShort(samples[offset + i])
        }
        val bytes = byteBuffer.array()
        var bytesOffset = 0
        var bytesRemaining = bytes.size

        while (bytesRemaining > 0) {
            val inputIndex = encoder.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputIndex) ?: break
                inputBuffer.clear()

                val toWrite = minOf(bytesRemaining, inputBuffer.remaining())
                inputBuffer.put(bytes, bytesOffset, toWrite)

                // 2 bytes per sample, sampleRate samples per sec -> 1 sec = sampleRate * 2 bytes
                val durationUs = (toWrite.toLong() * 1_000_000L) / (sampleRate * channelCount * 2)
                encoder.queueInputBuffer(inputIndex, 0, toWrite, presentationTimeUs, 0)
                presentationTimeUs += durationUs

                bytesOffset += toWrite
                bytesRemaining -= toWrite
            }

            drainEncoder(endOfStream = false)
        }
    }

    override fun finish() {
        val encoder = codec ?: return
        try {
            // Signal EOS
            val inputIndex = encoder.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainEncoder(endOfStream = true)
        } catch (_: Exception) {
        } finally {
            release()
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val encoder = codec ?: return
        val currentMuxer = muxer ?: return

        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) {
                    throw IllegalStateException("Format changed twice")
                }
                val newFormat = encoder.outputFormat
                audioTrackIndex = currentMuxer.addTrack(newFormat)
                currentMuxer.start()
                muxerStarted = true
            } else if (outputIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputIndex)
                if (outputBuffer != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        currentMuxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                    }
                }
                encoder.releaseOutputBuffer(outputIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun release() {
        try {
            codec?.stop()
        } catch (_: Exception) {}
        try {
            codec?.release()
        } catch (_: Exception) {}
        codec = null

        try {
            if (muxerStarted) {
                muxer?.stop()
            }
        } catch (_: Exception) {}
        try {
            muxer?.release()
        } catch (_: Exception) {}
        muxer = null
        muxerStarted = false
    }
}
