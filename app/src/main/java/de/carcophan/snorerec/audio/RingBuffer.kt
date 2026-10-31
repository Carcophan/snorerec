package de.carcophan.snorerec.audio

class RingBuffer(val capacity: Int) {
    private val buffer = ShortArray(capacity)
    private var writeIndex = 0
    private var totalWritten = 0
    private val lock = Any()

    fun write(samples: ShortArray, offset: Int = 0, count: Int = samples.size) {
        synchronized(lock) {
            for (i in 0 until count) {
                buffer[writeIndex] = samples[offset + i]
                writeIndex = (writeIndex + 1) % capacity
                if (totalWritten < capacity) totalWritten++
            }
        }
    }

    fun readSnapshot(): ShortArray {
        synchronized(lock) {
            val size = totalWritten
            val result = ShortArray(size)
            if (size == 0) return result

            val start = if (totalWritten < capacity) 0 else writeIndex
            for (i in 0 until size) {
                result[i] = buffer[(start + i) % capacity]
            }
            return result
        }
    }

    /**
     * Reads up to [count] of the most recently written audio samples.
     */
    fun readLast(count: Int): ShortArray = readWindow(count, offsetFromEnd = 0)

    /**
     * Reads up to [count] samples ending [offsetFromEnd] samples before the most recent write.
     */
    fun readWindow(count: Int, offsetFromEnd: Int = 0): ShortArray {
        synchronized(lock) {
            val available = totalWritten
            if (available == 0 || count <= 0) return ShortArray(0)
            val effectiveOffset = offsetFromEnd.coerceIn(0, (available - 1).coerceAtLeast(0))
            val n = minOf(count, available - effectiveOffset)
            if (n <= 0) return ShortArray(0)

            val result = ShortArray(n)
            val start = (writeIndex - effectiveOffset - n + capacity * 2) % capacity
            for (i in 0 until n) {
                result[i] = buffer[(start + i) % capacity]
            }
            return result
        }
    }

    fun clear() {
        synchronized(lock) {
            writeIndex = 0
            totalWritten = 0
        }
    }
}
