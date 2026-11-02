package de.carcophan.snorerec.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreCatalog
import de.carcophan.snorerec.ui.SessionAnalysis
import de.carcophan.snorerec.ui.theme.SnoreEpicRed
import de.carcophan.snorerec.ui.theme.SnoreLabCyan
import de.carcophan.snorerec.ui.theme.SnoreLabSurfaceElevated
import de.carcophan.snorerec.ui.theme.SnoreLightYellow
import de.carcophan.snorerec.ui.theme.SnoreLoudOrange
import de.carcophan.snorerec.ui.theme.SnoreQuietGreen
import de.carcophan.snorerec.ui.theme.snoreScoreCategoryLabel
import de.carcophan.snorerec.ui.theme.snoreScoreColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnorePatternAnalysisView(
    analysis: SessionAnalysis,
    session: NightSession? = null,
    onRatingChanged: ((Int) -> Unit)? = null,
    onEditMetadataClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val scoreColor = snoreScoreColor(analysis.snoreScore)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. SnoreLab Score Ring & Core Night Summary Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular Snore Score Gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(116.dp)
                ) {
                    val sweepFraction = (analysis.snoreScore.toFloat() / 120f).coerceIn(0.05f, 1f)
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(modifier = Modifier.size(106.dp)) {
                        drawArc(
                            color = trackColor,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = scoreColor,
                            startAngle = 135f,
                            sweepAngle = 270f * sweepFraction,
                            useCenter = false,
                            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SCORE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${analysis.snoreScore}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor
                        )
                        Text(
                            text = snoreScoreCategoryLabel(analysis.snoreScore),
                            style = MaterialTheme.typography.labelSmall,
                            color = scoreColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right side summary stats (Im Bett, Schnarchen, Vergleich)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (session != null) {
                        val startStr = timeFormat.format(Date(session.startTime))
                        val endStr = session.endTime?.let { timeFormat.format(Date(it)) } ?: "Aktiv"
                        SummaryRowItem(
                            label = "Im Bett ($startStr – $endStr)",
                            value = formatDuration(analysis.totalSleepDurationMs),
                            valueColor = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        SummaryRowItem(
                            label = "Im Bett",
                            value = formatDuration(analysis.totalSleepDurationMs),
                            valueColor = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SummaryRowItem(
                        label = "Schnarchen (${analysis.snorePercentage.toInt()}%)",
                        value = formatDuration(analysis.totalSnoreDurationMs),
                        valueColor = scoreColor
                    )

                    SummaryRowItem(
                        label = "Spitzen- / Ø Pegel",
                        value = "${analysis.peakDb.toInt()} dB / ${analysis.avgDb.toInt()} dB",
                        valueColor = MaterialTheme.colorScheme.primary
                    )

                    // Comparison vs personal average
                    if (analysis.avgAllTimeScore > 0) {
                        val diff = analysis.scoreDiffPercent
                        val badgeColor = if (diff <= 0) SnoreQuietGreen else SnoreLoudOrange
                        val diffText = when {
                            diff < 0 -> "↓ ${abs(diff)}% unter deinem Ø (${analysis.avgAllTimeScore})"
                            diff > 0 -> "↑ +$diff% über deinem Ø (${analysis.avgAllTimeScore})"
                            else -> "Genau auf deinem Ø (${analysis.avgAllTimeScore})"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = diffText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. SnoreLab Intensity Breakdown Table (Ruhe, Leicht, Laut, Episch)
        val breakdown = analysis.intensityBreakdown
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Schlaf- & Schnarch-Aufschlüsselung",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Stacked bar of full night
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (breakdown.quietPercentageOfSleep > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(breakdown.quietPercentageOfSleep.coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(SnoreQuietGreen)
                        )
                    }
                    if (breakdown.mildPercentageOfSleep > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(breakdown.mildPercentageOfSleep.coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(SnoreLightYellow)
                        )
                    }
                    if (breakdown.moderatePercentageOfSleep > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(breakdown.moderatePercentageOfSleep.coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(SnoreLoudOrange)
                        )
                    }
                    if (breakdown.heavyPercentageOfSleep > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(breakdown.heavyPercentageOfSleep.coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(SnoreEpicRed)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                SnoreLabTableRow(
                    dotColor = SnoreQuietGreen,
                    label = "Ruhe",
                    sublabel = "< 42 dB",
                    durationMs = breakdown.quietDurationMs,
                    percentage = breakdown.quietPercentageOfSleep
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SnoreLabTableRow(
                    dotColor = SnoreLightYellow,
                    label = "Leicht",
                    sublabel = "${breakdown.mildCount} Sequenzen (42–52 dB)",
                    durationMs = breakdown.mildDurationMs,
                    percentage = breakdown.mildPercentageOfSleep
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SnoreLabTableRow(
                    dotColor = SnoreLoudOrange,
                    label = "Laut",
                    sublabel = "${breakdown.moderateCount} Sequenzen (52–68 dB)",
                    durationMs = breakdown.moderateDurationMs,
                    percentage = breakdown.moderatePercentageOfSleep
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SnoreLabTableRow(
                    dotColor = SnoreEpicRed,
                    label = "Episch",
                    sublabel = "${breakdown.heavyCount} Sequenzen (≥ 68 dB)",
                    durationMs = breakdown.heavyDurationMs,
                    percentage = breakdown.heavyPercentageOfSleep
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SnoreLabTableRow(
                    dotColor = SnoreLabCyan,
                    label = "Schnarchen Gesamt",
                    sublabel = String.format(Locale.getDefault(), "%.1f Ereignisse / Std.", analysis.snoresPerHour),
                    durationMs = analysis.totalSnoreDurationMs,
                    percentage = analysis.snorePercentage,
                    isBold = true
                )
            }
        }

        // 3. Remedies, Factors, Morning Sleep Rating & Notes Card
        if (session != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hilfsmittel, Faktoren & Schlafgefühl",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (onEditMetadataClicked != null) {
                            IconButton(onClick = onEditMetadataClicked) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Nacht bearbeiten",
                                    tint = SnoreLabCyan
                                )
                            }
                        }
                    }

                    // Morning Sleep Quality 1..5 Stars
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = "Erholung am Morgen:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            for (star in 1..5) {
                                val filled = star <= session.sleepRating
                                Icon(
                                    imageVector = if (filled) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "$star Sterne",
                                    tint = if (filled) SnoreLightYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable(enabled = onRatingChanged != null) {
                                            onRatingChanged?.invoke(star)
                                        }
                                        .padding(2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Remedies & Factors Chips
                    if (session.remedies.isEmpty() && session.factors.isEmpty()) {
                        Text(
                            text = "Keine Hilfsmittel oder Faktoren für diese Nacht hinterlegt. Tippe auf das Stift-Symbol, um welche hinzuzufügen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            session.remedies.forEach { r ->
                                val item = SnoreCatalog.findRemedy(r)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SnoreQuietGreen.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${item.iconEmoji} ${item.name}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SnoreQuietGreen,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            session.factors.forEach { f ->
                                val item = SnoreCatalog.findFactor(f)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SnoreLoudOrange.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${item.iconEmoji} ${item.name}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SnoreLoudOrange,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (session.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SnoreLabSurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "📝 \"${session.note}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Hourly Snoring Distribution
        if (analysis.hourlyBuckets.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Stündliches Schnarchmuster",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val fraction = (bucket.snoreCount.toFloat() / maxBucketCount).coerceIn(0f, 1f)
                                if (fraction > 0f) {
                                    val barColor = when {
                                        bucket.avgDb >= 68f -> SnoreEpicRed
                                        bucket.avgDb >= 52f -> SnoreLoudOrange
                                        else -> SnoreLightYellow
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction)
                                            .clip(RoundedCornerShape(6.dp))
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
private fun SummaryRowItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun SnoreLabTableRow(
    dotColor: Color,
    label: String,
    sublabel: String,
    durationMs: Long,
    percentage: Float,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = dotColor.copy(alpha = 0.18f),
                modifier = Modifier.width(48.dp)
            ) {
                Text(
                    text = "${percentage.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = dotColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
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
