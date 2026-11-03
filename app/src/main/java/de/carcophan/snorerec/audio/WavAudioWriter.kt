package de.carcophan.snorerec.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavAudioWriter(
    val outputFile: File,
    private val sampleRate: Int = AudioConfig.SAMPLE_RATE,
    private val channels: Short = 1,
    private val bitsPerSample: Short = 16
) : AudioWriter {
    private var raf: RandomAccessFile? = null
    private var payloadBytesWritten: Long = 0

    init {
        outputFile.parentFile?.mkdirs()
        raf = RandomAccessFile(outputFile, "rw")
        writeHeader(0)
    }

    override fun writeSamples(samples: ShortArray, offset: Int, count: Int) {
        val file = raf ?: return
        if (count <= 0) return

        val byteBuffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            byteBuffer.putShort(AudioConfig.amplifySample(samples[offset + i]))
        }
        file.write(byteBuffer.array())
        payloadBytesWritten += count * 2
    }

    override fun finish() {
        val file = raf ?: return
        try {
            file.seek(0)
            writeHeader(payloadBytesWritten)
            file.close()
        } catch (_: Exception) {}
        raf = null
    }

    private fun writeHeader(audioDataLength: Long) {
        val file = raf ?: return
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        val totalDataLen = audioDataLength + 36

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray())

        // fmt chunk
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1.toShort()) // AudioFormat 1 = PCM
        header.putShort(channels)
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample)

        // data chunk
        header.put("data".toByteArray())
        header.putInt(audioDataLength.toInt())

        file.seek(0)
        file.write(header.array())
    }
}
