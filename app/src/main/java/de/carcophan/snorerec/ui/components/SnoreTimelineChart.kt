package de.carcophan.snorerec.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreEvent
import de.carcophan.snorerec.data.model.SnoreIntensity
import de.carcophan.snorerec.ui.theme.SnoreEpicRed
import de.carcophan.snorerec.ui.theme.SnoreLabCyan
import de.carcophan.snorerec.ui.theme.SnoreLabSurfaceElevated
import de.carcophan.snorerec.ui.theme.SnoreLightYellow
import de.carcophan.snorerec.ui.theme.SnoreLoudOrange
import de.carcophan.snorerec.ui.theme.SnoreQuietGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

@Composable
fun SnoreTimelineChart(
    session: NightSession,
    events: List<SnoreEvent>,
    selectedEventId: Long?,
    isPlaying: Boolean = false,
    playbackProgress: Float = 0f,
    onEventSelected: (SnoreEvent) -> Unit,
    onToggleBookmark: ((SnoreEvent) -> Unit)? = null,
    onDeleteEvent: ((SnoreEvent) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val detailTimeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val quietColor = SnoreQuietGreen
    val mildColor = SnoreLightYellow
    val moderateColor = SnoreLoudOrange
    val heavyColor = SnoreEpicRed
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionHighlight = SnoreLabCyan

    var tappedEvent by remember(session.id, events.size) {
        mutableStateOf(events.maxByOrNull { it.peakDb } ?: events.firstOrNull())
    }

    val activeEvent = events.find { it.id == selectedEventId } ?: tappedEvent

    val sessionStart = session.startTime
    val sessionEnd = session.endTime ?: (sessionStart + session.durationMs.coerceAtLeast(60000L))
    val totalSpan = (sessionEnd - sessionStart).coerceAtLeast(60000L)

    fun findClosestEventAtX(xPx: Float, widthPx: Int): SnoreEvent? {
        if (events.isEmpty() || widthPx <= 0) return null
        val leftAxisPad = 46f
        val plotW = (widthPx - leftAxisPad).coerceAtLeast(1f)
        var closest: SnoreEvent? = null
        var minDistance = Float.MAX_VALUE
        events.forEach { event ->
            val startFraction = (event.timestamp - sessionStart).toFloat() / totalSpan.toFloat()
            val endFraction = ((event.timestamp + event.durationMs) - sessionStart).toFloat() / totalSpan.toFloat()
            val startX = leftAxisPad + startFraction * plotW
            val endX = (leftAxisPad + endFraction * plotW).coerceAtLeast(startX + 10f)
            val dist = when {
                xPx < startX -> startX - xPx
                xPx > endX -> xPx - endX
                else -> 0f
            }
            if (dist < minDistance) {
                minDistance = dist
                closest = event
            }
        }
        return if (minDistance < 65f) closest else null
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Nacht-Diagramm (SnoreLab Ansicht)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Antippen zum Abspielen",
                    style = MaterialTheme.typography.labelSmall,
                    color = SnoreLabCyan
                )
            }

            // Legend with SnoreLab's 4 tiers: Ruhe, Leicht, Laut, Episch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                LegendItem(color = quietColor, label = "Ruhe")
                LegendItem(color = mildColor, label = "Leicht")
                LegendItem(color = moderateColor, label = "Laut")
                LegendItem(color = heavyColor, label = "Episch")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(205.dp)
                    .pointerInput(events, sessionStart, totalSpan) {
                        detectTapGestures { tapOffset ->
                            findClosestEventAtX(tapOffset.x, size.width)?.let { event ->
                                tappedEvent = event
                                onEventSelected(event)
                            }
                        }
                    }
                    .pointerInput(events, sessionStart, totalSpan) {
                        detectHorizontalDragGestures { change, _ ->
                            findClosestEventAtX(change.position.x, size.width)?.let { event ->
                                if (tappedEvent?.id != event.id) {
                                    tappedEvent = event
                                }
                            }
                        }
                    }
            ) {
                val leftPad = 46.dp.toPx()
                val chartHeight = size.height - 28.dp.toPx()
                val chartWidth = (size.width - leftPad).coerceAtLeast(10f)

                // 1. SnoreLab Y-axis bands: Episch, Laut, Leicht, Ruhe
                val bands = listOf(
                    Triple("Episch", 72f, heavyColor),
                    Triple("Laut", 56f, moderateColor),
                    Triple("Leicht", 44f, mildColor),
                    Triple("Ruhe", 28f, quietColor)
                )

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                bands.forEach { (label, dbLevel, bandColor) ->
                    val y = chartHeight - ((dbLevel - 20f) / (82f - 20f)) * chartHeight
                    drawLine(
                        color = gridColor,
                        start = Offset(leftPad, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(0f, (y - 7.sp.toPx()).coerceAtLeast(0f)),
                        style = TextStyle(
                            color = bandColor.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Baseline horizontal axis
                drawLine(
                    color = gridColor,
                    start = Offset(leftPad, chartHeight),
                    end = Offset(size.width, chartHeight),
                    strokeWidth = 1.5.dp.toPx()
                )

                // 2. Continuous Quiet Baseline breathing bars across the night (SnoreLab style)
                val baselineSlots = 72
                val slotWidth = chartWidth / baselineSlots
                for (i in 0 until baselineSlots) {
                    val slotX = leftPad + i * slotWidth
                    val breatheVariation = 0.08f + 0.05f * sin(i * 0.7f)
                    val barH = chartHeight * breatheVariation
                    drawRoundRect(
                        color = quietColor.copy(alpha = 0.35f),
                        topLeft = Offset(slotX + 1f, chartHeight - barH),
                        size = Size((slotWidth - 2f).coerceAtLeast(1.5f), barH),
                        cornerRadius = CornerRadius(1.5.dp.toPx())
                    )
                }

                // 3. Time Axis Labels
                val hourStep = when {
                    totalSpan > 10 * 3600_000L -> 2 * 3600_000L
                    totalSpan > 4 * 3600_000L -> 3600_000L
                    else -> (totalSpan / 4).coerceAtLeast(60_000L)
                }
                var t = sessionStart
                while (t <= sessionEnd) {
                    val fraction = (t - sessionStart).toFloat() / totalSpan.toFloat()
                    val x = leftPad + fraction * chartWidth

                    drawLine(
                        color = gridColor,
                        start = Offset(x, chartHeight),
                        end = Offset(x, chartHeight + 5.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )

                    val timeLabel = timeFormat.format(Date(t))
                    drawText(
                        textMeasurer = textMeasurer,
                        text = timeLabel,
                        topLeft = Offset((x - 14.dp.toPx()).coerceIn(leftPad, size.width - 34.dp.toPx()), chartHeight + 7.dp.toPx()),
                        style = TextStyle(color = labelColor, fontSize = 10.sp)
                    )
                    t += hourStep
                }

                // 4. Snore Sequence Spikes
                val minBarWidth = 5.5.dp.toPx()
                events.forEach { event ->
                    val fraction = (event.timestamp - sessionStart).toFloat() / totalSpan.toFloat()
                    val durationFraction = (event.durationMs.toFloat() / totalSpan.toFloat()).coerceIn(0f, 1f)
                    val barWidth = (durationFraction * chartWidth).coerceAtLeast(minBarWidth)
                    val x = (leftPad + fraction * chartWidth).coerceIn(leftPad, (size.width - barWidth).coerceAtLeast(leftPad))
                    val normalizedDb = ((event.peakDb - 20f) / (82f - 20f)).coerceIn(0.18f, 0.98f)
                    val barHeight = normalizedDb * chartHeight
                    val y = chartHeight - barHeight

                    val color = when (event.intensity) {
                        SnoreIntensity.MILD -> mildColor
                        SnoreIntensity.MODERATE -> moderateColor
                        SnoreIntensity.HEAVY -> heavyColor
                    }

                    val isSelected = event.id == activeEvent?.id

                    if (isSelected) {
                        // Vertical playhead line
                        drawLine(
                            color = selectionHighlight.copy(alpha = 0.7f),
                            start = Offset(x + barWidth / 2f, 0f),
                            end = Offset(x + barWidth / 2f, chartHeight),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawRoundRect(
                            color = selectionHighlight.copy(alpha = 0.35f),
                            topLeft = Offset(x - 3.dp.toPx(), y - 3.dp.toPx()),
                            size = Size(barWidth + 6.dp.toPx(), barHeight + 3.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }

                    drawRoundRect(
                        color = if (isSelected) selectionHighlight else color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2.5.dp.toPx())
                    )

                    if (event.isBookmarked) {
                        drawCircle(
                            color = SnoreLightYellow,
                            radius = 3.5.dp.toPx(),
                            center = Offset(x + barWidth / 2f, (y - 6.dp.toPx()).coerceAtLeast(4.dp.toPx()))
                        )
                    }
                }
            }

            // 5. SnoreLab Inline Scrubber / Selected Event Player Bar
            if (activeEvent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val isCurrentPlaying = selectedEventId == activeEvent.id && isPlaying
                val tierColor = when (activeEvent.intensity) {
                    SnoreIntensity.MILD -> mildColor
                    SnoreIntensity.MODERATE -> moderateColor
                    SnoreIntensity.HEAVY -> heavyColor
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SnoreLabSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledIconButton(
                                    onClick = { onEventSelected(activeEvent) },
                                    enabled = activeEvent.audioFilePath != null,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (isCurrentPlaying) SnoreLabCyan else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Abspielen"
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = detailTimeFormat.format(Date(activeEvent.timestamp)),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = tierColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "${activeEvent.peakDb.toInt()} dB • ${activeEvent.intensity.snoreLabLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tierColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Sequenzdauer: ${activeEvent.durationMs / 1000}s • KI-Konfidenz: ${(activeEvent.snoreScore * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onToggleBookmark != null) {
                                    IconButton(onClick = { onToggleBookmark(activeEvent) }) {
                                        Icon(
                                            imageVector = if (activeEvent.isBookmarked) Icons.Default.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Markieren",
                                            tint = if (activeEvent.isBookmarked) SnoreLightYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (onDeleteEvent != null) {
                                    IconButton(onClick = { onDeleteEvent(activeEvent) }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Clip löschen",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (isCurrentPlaying) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { playbackProgress },
                                color = SnoreLabCyan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
