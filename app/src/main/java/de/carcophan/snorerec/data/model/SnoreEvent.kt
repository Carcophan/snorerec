package de.carcophan.snorerec.data.model

enum class SnoreIntensity(val label: String, val snoreLabLabel: String, val minDb: Float, val scoreWeight: Float) {
    MILD("Leicht", "Leicht", 0f, 1.0f),
    MODERATE("Mäßig", "Laut", 52f, 2.2f),
    HEAVY("Laut", "Episch", 68f, 3.8f);

    companion object {
        fun fromDb(db: Float): SnoreIntensity = when {
            db >= HEAVY.minDb -> HEAVY
            db >= MODERATE.minDb -> MODERATE
            else -> MILD
        }

        fun fromEvent(avgDb: Float, peakDb: Float): SnoreIntensity {
            // Sustained level determines baseline, with peak contributing moderately.
            // Mild snoring (e.g. avg 42-48 dB) will never trigger HEAVY ("Episch") even with occasional peaks.
            val effectiveDb = (avgDb * 0.70f + peakDb * 0.30f).coerceAtLeast(avgDb)
            return when {
                effectiveDb >= HEAVY.minDb || (peakDb >= 72f && avgDb >= 58f) -> HEAVY
                effectiveDb >= MODERATE.minDb || peakDb >= 58f -> MODERATE
                else -> MILD
            }
        }
    }
}

data class SnoreEvent(
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val durationMs: Long,
    val peakDb: Float,
    val avgDb: Float,
    val audioFilePath: String? = null,
    val snoreScore: Float = 0f,
    val isBookmarked: Boolean = false
) {
    val intensity: SnoreIntensity
        get() = SnoreIntensity.fromEvent(avgDb, peakDb)
}
