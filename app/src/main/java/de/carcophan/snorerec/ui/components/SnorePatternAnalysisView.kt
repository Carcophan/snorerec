package de.carcophan.snorerec.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.carcophan.snorerec.ui.SessionAnalysis
import kotlin.math.max

@Composable
fun SnorePatternAnalysisView(
    analysis: SessionAnalysis,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Stat Metric Cards (Grid of 4)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Schnarch-Dauer",
                value = formatDuration(analysis.totalSnoreDurationMs),
                subtitle = "${analysis.snorePercentage.toInt()}% der Nacht",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Schnarch-Anzahl",
                value = "${analysis.snoreCount}",
                subtitle = String.format(java.util.Locale.getDefault(), "%.1f / Stunde", analysis.snoresPerHour),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Spitzenlautstärke",
                value = "${analysis.peakDb.toInt()} dB",
                subtitle = "Durchschnitt: ${analysis.avgDb.toInt()} dB",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Gesamtschlafzeit",
                value = formatDuration(analysis.totalSleepDurationMs),
                subtitle = "Aufzeichnung aktiv",
                modifier = Modifier.weight(1f)
            )
        }

        // 2. Intensity Distribution
        val breakdown = analysis.intensityBreakdown
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Intensitätsverteilung",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar with segments
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (breakdown.mildPercentage > 0) {
                        Box(
                            modifier = Modifier
                                .weight(breakdown.mildPercentage.coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(Color(0xFF4CAF50))
                        )
                    }
                    if (breakdown.moderatePercentage > 0) {
                        Box(
                            modifier = Modifier
                                .weight(breakdown.moderatePercentage.coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(Color(0xFFFFA000))
                        )
                    }
                    if (breakdown.heavyPercentage > 0) {
                        Box(
                            modifier = Modifier
                                .weight(breakdown.heavyPercentage.coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(Color(0xFFE53935))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IntensityStat(
                        color = Color(0xFF4CAF50),
                        label = "Leicht",
                        count = breakdown.mildCount,
                        pct = breakdown.mildPercentage
                    )
                    IntensityStat(
                        color = Color(0xFFFFA000),
                        label = "Mäßig",
                        count = breakdown.moderateCount,
                        pct = breakdown.moderatePercentage
                    )
                    IntensityStat(
                        color = Color(0xFFE53935),
                        label = "Laut",
                        count = breakdown.heavyCount,
                        pct = breakdown.heavyPercentage
                    )
                }
            }
        }

        // 3. Hourly Snoring Distribution (Muster pro Stunde)
        if (analysis.hourlyBuckets.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Stündliches Schnarchmuster",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val maxBucketCount = max(1, analysis.hourlyBuckets.maxOf { it.snoreCount })

                    analysis.hourlyBuckets.forEach { bucket ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = bucket.hourLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(46.dp)
                            )

                            // Horizontal Bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val fraction = (bucket.snoreCount.toFloat() / maxBucketCount).coerceIn(0f, 1f)
                                if (fraction > 0f) {
                                    val barColor = when {
                                        bucket.avgDb > 62f -> Color(0xFFE53935)
                                        bucket.avgDb > 48f -> Color(0xFFFFA000)
                                        else -> Color(0xFF4CAF50)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(barColor)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = "${bucket.snoreCount}x (${formatDuration(bucket.totalDurationMs)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(95.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun IntensityStat(
    color: Color,
    label: String,
    count: Int,
    pct: Float
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$count (${pct.toInt()}%)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
