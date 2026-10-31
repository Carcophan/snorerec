package de.carcophan.snorerec.data.model

data class NightSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalSnoreDurationMs: Long = 0L,
    val snoreCount: Int = 0,
    val peakDb: Float = 0f,
    val avgDb: Float = 0f,
    val note: String = ""
) {
    val durationMs: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    val snorePercentage: Float
        get() {
            val total = durationMs
            return if (total > 0) {
                ((totalSnoreDurationMs.toFloat() / total.toFloat()) * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }
        }

    val snoresPerHour: Float
        get() {
            val hours = durationMs / 3_600_000.0f
            return if (hours > 0.05f) snoreCount / hours else 0f
        }
}
