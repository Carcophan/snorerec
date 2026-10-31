package de.carcophan.snorerec.data.model

enum class SnoreIntensity(val label: String, val minDb: Float) {
    MILD("Leicht", 0f),
    MODERATE("Mäßig", 48f),
    HEAVY("Laut", 62f);

    companion object {
        fun fromDb(db: Float): SnoreIntensity = when {
            db >= HEAVY.minDb -> HEAVY
            db >= MODERATE.minDb -> MODERATE
            else -> MILD
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
    val snoreScore: Float = 0f
) {
    val intensity: SnoreIntensity
        get() = SnoreIntensity.fromDb(peakDb)
}
