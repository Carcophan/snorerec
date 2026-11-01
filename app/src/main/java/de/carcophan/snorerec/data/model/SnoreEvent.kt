package de.carcophan.snorerec.data.model

enum class SnoreIntensity(val label: String, val snoreLabLabel: String, val minDb: Float, val scoreWeight: Float) {
    MILD("Leicht", "Leicht", 0f, 1.0f),
    MODERATE("Mäßig", "Laut", 48f, 2.2f),
    HEAVY("Laut", "Episch", 62f, 3.8f);

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
    val snoreScore: Float = 0f,
    val isBookmarked: Boolean = false
) {
    val intensity: SnoreIntensity
        get() = SnoreIntensity.fromDb(peakDb)
}
