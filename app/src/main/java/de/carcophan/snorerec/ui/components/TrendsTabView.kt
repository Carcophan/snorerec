package de.carcophan.snorerec.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.ui.TrendsAnalysis
import de.carcophan.snorerec.ui.theme.SnoreLabCyan
import de.carcophan.snorerec.ui.theme.SnoreQuietGreen
import de.carcophan.snorerec.ui.theme.snoreScoreColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun TrendsTabView(
    sessions: List<NightSession>,
    trends: TrendsAnalysis,
    snoreScoreGoal: Int,
    onSelectNight: (NightSession) -> Unit,
    onSeedDemoClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRangeIndex by remember { mutableIntStateOf(1) } // 0: 7, 1: 30, 2: 90, 3: Alle
    val rangeLimits = listOf(7, 30, 90, Int.MAX_VALUE)
    val rangeLabels = listOf("7 Nächte", "30 Nächte", "90 Nächte", "Alle")

    if (sessions.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Noch keine Trend-Daten vorhanden",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Zeichne mehrere Nächte auf oder lade die 10 Beispiel-Nächte, um deinen Schnarch-Score-Verlauf zu analysieren.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSeedDemoClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = SnoreLabCyan)
                ) {
                    Text("10 Beispiel-Nächte zum Testen laden")
                }
            }
        }
        return
    }

    val filteredSessions = remember(sessions, selectedRangeIndex) {
        sessions.take(rangeLimits[selectedRangeIndex]).reversed() // chronological left-to-right
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Range Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            rangeLabels.forEachIndexed { idx, label ->
                val selected = selectedRangeIndex == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SnoreLabCyan else Color.Transparent)
                        .clickable { selectedRangeIndex = idx }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Multi-Night Snore Score Bar Chart
        SnoreScoreHistoryChart(
            chronologicalSessions = filteredSessions,
            avgScore = trends.avgSnoreScore,
            goalScore = snoreScoreGoal,
            onSessionTapped = onSelectNight
        )

        // 3. KPI Grid (6 metrics)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrendMetricCard(
                title = "Ø Schnarch-Score",
                value = "${trends.avgSnoreScore}",
                subtitle = "Ziel: ≤ $snoreScoreGoal",
                valueColor = snoreScoreColor(trends.avgSnoreScore),
                modifier = Modifier.weight(1f)
            )
            TrendMetricCard(
                title = "Ø Schnarch-Anteil",
                value = "${trends.avgSnorePercentage.toInt()}%",
                subtitle = "der Schlafzeit",
                valueColor = SnoreLabCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrendMetricCard(
                title = "Beste Nacht",
                value = "Score ${trends.lowestSnoreScore}",
                subtitle = "Niedrigster Wert",
                valueColor = SnoreQuietGreen,
                modifier = Modifier.weight(1f)
            )
            TrendMetricCard(
                title = "Lauteste Nacht",
                value = "Score ${trends.highestSnoreScore}",
                subtitle = "${trends.totalNights} Nächte gesamt",
                valueColor = snoreScoreColor(trends.highestSnoreScore),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SnoreScoreHistoryChart(
    chronologicalSessions: List<NightSession>,
    avgScore: Int,
    goalScore: Int,
    onSessionTapped: (NightSession) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val dateFormat = remember { SimpleDateFormat("dd.MM.", Locale.getDefault()) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

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
                    text = "Schnarch-Score Verlauf",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Balken antippen für Details",
                    style = MaterialTheme.typography.labelSmall,
                    color = SnoreLabCyan
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val maxScore = max(100, (chronologicalSessions.maxOfOrNull { it.snoreScore } ?: 80) + 15)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .pointerInput(chronologicalSessions) {
                        detectTapGestures { offset ->
                            if (chronologicalSessions.isNotEmpty()) {
                                val leftPad = 34.dp.toPx()
                                val plotW = (size.width - leftPad).coerceAtLeast(1f)
                                val slotW = plotW / chronologicalSessions.size
                                val idx = ((offset.x - leftPad) / slotW).toInt()
                                if (idx in chronologicalSessions.indices) {
                                    onSessionTapped(chronologicalSessions[idx])
                                }
                            }
                        }
                    }
            ) {
                val leftPad = 34.dp.toPx()
                val chartH = size.height - 26.dp.toPx()
                val chartW = (size.width - leftPad).coerceAtLeast(10f)

                val ySteps = listOf(0, 25, 50, 75, 100).filter { it <= maxScore }
                ySteps.forEach { stepVal ->
                    val y = chartH - (stepVal.toFloat() / maxScore) * chartH
                    drawLine(
                        color = gridColor.copy(alpha = 0.4f),
                        start = Offset(leftPad, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "$stepVal",
                        topLeft = Offset(0f, (y - 7.sp.toPx()).coerceAtLeast(0f)),
                        style = TextStyle(color = labelColor, fontSize = 10.sp)
                    )
                }

                // Goal dashed line
                if (goalScore in 1..maxScore) {
                    val goalY = chartH - (goalScore.toFloat() / maxScore) * chartH
                    drawLine(
                        color = SnoreQuietGreen.copy(alpha = 0.75f),
                        start = Offset(leftPad, goalY),
                        end = Offset(size.width, goalY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                }

                // Average dashed line
                if (avgScore in 1..maxScore) {
                    val avgY = chartH - (avgScore.toFloat() / maxScore) * chartH
                    drawLine(
                        color = SnoreLabCyan.copy(alpha = 0.75f),
                        start = Offset(leftPad, avgY),
                        end = Offset(size.width, avgY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                val count = chronologicalSessions.size
                if (count > 0) {
                    val slotW = chartW / count
                    val barW = (slotW * 0.62f).coerceIn(6.dp.toPx(), 28.dp.toPx())
                    val labelStep = max(1, count / 6)

                    chronologicalSessions.forEachIndexed { idx, s ->
                        val score = s.snoreScore
                        val barH = ((score.toFloat() / maxScore) * chartH).coerceAtLeast(4.dp.toPx())
                        val x = leftPad + idx * slotW + (slotW - barW) / 2f
                        val y = chartH - barH

                        drawRoundRect(
                            color = snoreScoreColor(score),
                            topLeft = Offset(x, y),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )

                        if (idx % labelStep == 0 || idx == count - 1) {
                            val dateStr = dateFormat.format(Date(s.startTime))
                            drawText(
                                textMeasurer = textMeasurer,
                                text = dateStr,
                                topLeft = Offset(
                                    (x - 8.dp.toPx()).coerceIn(leftPad, size.width - 36.dp.toPx()),
                                    chartH + 6.dp.toPx()
                                ),
                                style = TextStyle(color = labelColor, fontSize = 9.sp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(SnoreLabCyan, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ø Score ($avgScore)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(SnoreQuietGreen, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ziel ($goalScore)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TrendMetricCard(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
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
                fontWeight = FontWeight.Bold,
                color = valueColor
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

