package de.carcophan.snorerec.audio

interface AudioWriter {
    fun writeSamples(samples: ShortArray, offset: Int = 0, count: Int = samples.size)
    fun finish()
}
