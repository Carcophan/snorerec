package de.carcophan.snorerec.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreEvent
import de.carcophan.snorerec.data.model.SnoreIntensity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun SnoreTimelineChart(
    session: NightSession,
    events: List<SnoreEvent>,
    selectedEventId: Long?,
    onEventSelected: (SnoreEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val mildColor = Color(0xFF4CAF50)
    val moderateColor = Color(0xFFFFA000)
    val heavyColor = Color(0xFFE53935)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionHighlight = MaterialTheme.colorScheme.primary

    var tappedEvent by remember { mutableStateOf<SnoreEvent?>(null) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Schnarch-Zeitverlauf (Intensität)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                LegendItem(color = mildColor, label = "Leicht (<48 dB)")
                Spacer(modifier = Modifier.width(12.dp))
                LegendItem(color = moderateColor, label = "Mäßig (48-62 dB)")
                Spacer(modifier = Modifier.width(12.dp))
                LegendItem(color = heavyColor, label = "Laut (>62 dB)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Canvas
            val sessionStart = session.startTime
            val sessionEnd = session.endTime ?: (sessionStart + session.durationMs.coerceAtLeast(60000L))
            val totalSpan = (sessionEnd - sessionStart).coerceAtLeast(60000L)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(events, sessionStart, totalSpan) {
                        detectTapGestures { tapOffset ->
                            val width = size.width
                            val tappedX = tapOffset.x
                            // Find closest event
                            var closest: SnoreEvent? = null
                            var minDistance = Float.MAX_VALUE
                            events.forEach { event ->
                                val eventFraction = (event.timestamp - sessionStart).toFloat() / totalSpan.toFloat()
                                val eventX = eventFraction * width
                                val dist = abs(eventX - tappedX)
                                if (dist < 40f && dist < minDistance) {
                                    minDistance = dist
                                    closest = event
                                }
                            }
                            closest?.let { event ->
                                tappedEvent = event
                                onEventSelected(event)
                            }
                        }
                    }
            ) {
                val chartHeight = size.height - 30.dp.toPx()
                val chartWidth = size.width

                // 1. Draw dB grid lines (40 dB, 60 dB, 80 dB)
                val dbLevels = listOf(40f, 60f, 80f)
                dbLevels.forEach { db ->
                    val y = chartHeight - ((db - 35f) / (85f - 35f)) * chartHeight
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${db.toInt()} dB",
                        topLeft = Offset(4.dp.toPx(), y - 14.sp.toPx()),
                        style = TextStyle(color = labelColor, fontSize = 10.sp)
                    )
                }

                // 2. Draw Time Axis Labels
                val hourStep = if (totalSpan > 12 * 3600_000L) 3 * 3600_000L else if (totalSpan > 5 * 3600_000L) 2 * 3600_000L else 3600_000L
                var t = sessionStart
                while (t <= sessionEnd) {
                    val fraction = (t - sessionStart).toFloat() / totalSpan.toFloat()
                    val x = fraction * chartWidth

                    drawLine(
                        color = gridColor,
                        start = Offset(x, chartHeight),
                        end = Offset(x, chartHeight + 6.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    val timeLabel = timeFormat.format(Date(t))
                    drawText(
                        textMeasurer = textMeasurer,
                        text = timeLabel,
                        topLeft = Offset((x - 14.dp.toPx()).coerceAtLeast(0f), chartHeight + 8.dp.toPx()),
                        style = TextStyle(color = labelColor, fontSize = 11.sp)
                    )
                    t += hourStep
                }

                // 3. Draw Snore Event Bars
                events.forEach { event ->
                    val fraction = (event.timestamp - sessionStart).toFloat() / totalSpan.toFloat()
                    val x = fraction * chartWidth
                    val normalizedDb = ((event.peakDb - 35f) / (85f - 35f)).coerceIn(0.1f, 1.0f)
                    val barHeight = normalizedDb * chartHeight
                    val y = chartHeight - barHeight

                    val color = when (event.intensity) {
                        SnoreIntensity.MILD -> mildColor
                        SnoreIntensity.MODERATE -> moderateColor
                        SnoreIntensity.HEAVY -> heavyColor
                    }

                    val isSelected = event.id == (selectedEventId ?: tappedEvent?.id)

                    // Draw glow / highlight if selected
                    if (isSelected) {
                        drawRoundRect(
                            color = selectionHighlight.copy(alpha = 0.35f),
                            topLeft = Offset(x - 6.dp.toPx(), y - 4.dp.toPx()),
                            size = Size(12.dp.toPx(), barHeight + 4.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }

                    // Main event bar
                    drawRoundRect(
                        color = if (isSelected) selectionHighlight else color,
                        topLeft = Offset(x - 2.5.dp.toPx(), y),
                        size = Size(5.dp.toPx(), barHeight),
                        cornerRadius = CornerRadius(2.5.dp.toPx())
                    )
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
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
