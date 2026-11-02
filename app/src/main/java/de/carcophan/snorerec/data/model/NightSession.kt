package de.carcophan.snorerec.data.model

import kotlin.math.roundToInt

data class NightTimelinePoint(
    val timestamp: Long,
    val avgDb: Float,
    val peakDb: Float
)

data class NightSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalSnoreDurationMs: Long = 0L,
    val snoreCount: Int = 0,
    val peakDb: Float = 0f,
    val avgDb: Float = 0f,
    val note: String = "",
    val remedies: List<String> = emptyList(),
    val factors: List<String> = emptyList(),
    val sleepRating: Int = 0, // 0 = unrated, 1..5 stars
    val mildDurationMs: Long = 0L,
    val moderateDurationMs: Long = 0L,
    val heavyDurationMs: Long = 0L,
    val delayedStartMinutes: Int = 0,
    val storedSnoreScore: Int = -1
) {
    val durationMs: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    val quietDurationMs: Long
        get() = (durationMs - totalSnoreDurationMs).coerceAtLeast(0L)

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

    /**
     * SnoreLab-style Snore Score (Schnarch-Score):
     * Combines snoring percentage and intensity weighting (Leicht=1.0x, Laut=2.2x, Episch=3.8x).
     * Typical scores:
     * 0 - 15: Ruhig / Niedrig (Green)
     * 16 - 40: Mäßig / Leicht (Yellow)
     * 41 - 80: Laut (Orange)
     * 80+: Episch (Red)
     */
    val snoreScore: Int
        get() {
            if (storedSnoreScore >= 0) return storedSnoreScore
            return computeSnoreScore(
                totalSleepMs = durationMs,
                totalSnoreMs = totalSnoreDurationMs,
                mildMs = mildDurationMs,
                moderateMs = moderateDurationMs,
                heavyMs = heavyDurationMs,
                avgDb = avgDb,
                peakDb = peakDb
            )
        }

    companion object {
        fun computeSnoreScore(
            totalSleepMs: Long,
            totalSnoreMs: Long,
            mildMs: Long,
            moderateMs: Long,
            heavyMs: Long,
            avgDb: Float,
            peakDb: Float
        ): Int {
            if (totalSnoreMs <= 0L || totalSleepMs <= 0L) return 0
            val effectiveSleepMs = totalSleepMs.coerceAtLeast(1800_000L) // normalize short tests against min 30m window
            val categorizedSum = mildMs + moderateMs + heavyMs
            val weightedSnoreMs = if (categorizedSum > 0L) {
                (mildMs * 1.0 + moderateMs * 2.2 + heavyMs * 3.8)
            } else {
                val dbFactor = when {
                    peakDb >= 68f || avgDb >= 62f -> 2.8
                    peakDb >= 52f || avgDb >= 48f -> 1.8
                    else -> 1.1
                }
                totalSnoreMs * dbFactor
            }
            val rawScore = (weightedSnoreMs / effectiveSleepMs.toDouble()) * 100.0 * 1.35
            return rawScore.roundToInt().coerceIn(1, 250)
        }
    }
}
