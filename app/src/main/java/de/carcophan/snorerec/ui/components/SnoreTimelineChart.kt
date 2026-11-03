package de.carcophan.snorerec.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
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
import kotlin.math.abs
import kotlin.math.sin

internal const val MIN_TIMELINE_ZOOM = 1f
internal const val MAX_TIMELINE_ZOOM = 16f

internal data class TimelineZoomViewport(
    val zoomLevel: Float,
    val viewStartFraction: Float
)

/**
 * Computes the updated [TimelineZoomViewport] when a two-finger pinch-to-zoom and/or pan occurs,
 * keeping the timeline point under [focalFractionInPlot] (0..1 within the plot area) anchored.
 */
internal fun computePinchZoomViewport(
    currentZoom: Float,
    currentStartFraction: Float,
    zoomChange: Float,
    panXInPlotFraction: Float,
    focalFractionInPlot: Float,
    minZoom: Float = MIN_TIMELINE_ZOOM,
    maxZoom: Float = MAX_TIMELINE_ZOOM
): TimelineZoomViewport {
    val oldZoom = currentZoom.coerceIn(minZoom, maxZoom)
    val newZoom = (oldZoom * zoomChange).coerceIn(minZoom, maxZoom)
    val clampedFocal = focalFractionInPlot.coerceIn(0f, 1f)
    val anchorFraction = currentStartFraction + clampedFocal / oldZoom
    val panFractionDelta = panXInPlotFraction / newZoom
    val maxStart = (1f - 1f / newZoom).coerceAtLeast(0f)
    val newStart = (anchorFraction - clampedFocal / newZoom - panFractionDelta).coerceIn(0f, maxStart)
    return TimelineZoomViewport(zoomLevel = newZoom, viewStartFraction = newStart)
}

/**
 * Computes an appropriate time axis grid step in milliseconds for a given [visibleSpanMs].
 */
internal fun computeTimeAxisStepMs(visibleSpanMs: Long): Long = when {
    visibleSpanMs > 10 * 3600_000L -> 2 * 3600_000L
    visibleSpanMs > 4 * 3600_000L -> 3600_000L
    visibleSpanMs > 2 * 3600_000L -> 30 * 60_000L
    visibleSpanMs > 60 * 60_000L -> 15 * 60_000L
    visibleSpanMs > 20 * 60_000L -> 5 * 60_000L
    visibleSpanMs > 8 * 60_000L -> 2 * 60_000L
    visibleSpanMs > 3 * 60_000L -> 60_000L
    else -> 30_000L
}

@Composable
fun SnoreTimelineChart(
    session: NightSession,
    events: List<SnoreEvent>,
    selectedEventId: Long?,
    isPlaying: Boolean = false,
    playbackProgress: Float = 0f,
    playbackDurationMs: Long = 0L,
    onEventSelected: (SnoreEvent) -> Unit,
    onSeekEvent: ((SnoreEvent, Float) -> Unit)? = null,
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

    var zoomLevel by remember(session.id) { mutableFloatStateOf(1f) }
    var viewStartFraction by remember(session.id) { mutableFloatStateOf(0f) }

    val activeEvent = events.find { it.id == selectedEventId } ?: tappedEvent

    val sessionStart = session.startTime
    val sessionEnd = session.endTime ?: (sessionStart + session.durationMs.coerceAtLeast(60000L))
    val totalSpan = (sessionEnd - sessionStart).coerceAtLeast(60000L)

    val visibleFractionSpan = (1f / zoomLevel).coerceIn(1f / MAX_TIMELINE_ZOOM, 1f)
    val clampedStartFraction = viewStartFraction.coerceIn(0f, (1f - visibleFractionSpan).coerceAtLeast(0f))
    val visibleStart = sessionStart + (clampedStartFraction.toDouble() * totalSpan.toDouble()).toLong()
    val visibleSpan = (totalSpan.toDouble() * visibleFractionSpan.toDouble()).toLong().coerceAtLeast(15_000L)
    val visibleEnd = (visibleStart + visibleSpan).coerceAtMost(sessionEnd)
    val isZoomed = zoomLevel > 1.05f

    fun findClosestEventAtX(
        xPx: Float,
        widthPx: Int,
        leftAxisPadPx: Float,
        currentVisibleStart: Long,
        currentVisibleSpan: Long
    ): SnoreEvent? {
        if (events.isEmpty() || widthPx <= 0 || xPx < leftAxisPadPx) return null
        val plotW = (widthPx - leftAxisPadPx).coerceAtLeast(1f)
        val currentVisibleEnd = currentVisibleStart + currentVisibleSpan
        var closest: SnoreEvent? = null
        var minDistance = Float.MAX_VALUE
        events.forEach { event ->
            val eventEnd = event.timestamp + event.durationMs
            if (eventEnd < currentVisibleStart || event.timestamp > currentVisibleEnd) return@forEach

            val startFraction = (event.timestamp - currentVisibleStart).toFloat() / currentVisibleSpan.toFloat()
            val endFraction = (eventEnd - currentVisibleStart).toFloat() / currentVisibleSpan.toFloat()
            val startX = leftAxisPadPx + startFraction * plotW
            val endX = (leftAxisPadPx + endFraction * plotW).coerceAtLeast(startX + 10f)
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isZoomed) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SnoreLabCyan.copy(alpha = 0.16f),
                        modifier = Modifier.clickable {
                            zoomLevel = 1f
                            viewStartFraction = 0f
                        }
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.1fx • Reset", zoomLevel),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SnoreLabCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Pinch-Zoom & Antippen",
                        style = MaterialTheme.typography.labelSmall,
                        color = SnoreLabCyan
                    )
                }
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
                    .pointerInput(events, sessionStart, totalSpan, visibleStart, visibleSpan) {
                        val leftAxisPadPx = 46.dp.toPx()
                        detectTapGestures { tapOffset ->
                            findClosestEventAtX(
                                xPx = tapOffset.x,
                                widthPx = size.width,
                                leftAxisPadPx = leftAxisPadPx,
                                currentVisibleStart = visibleStart,
                                currentVisibleSpan = visibleSpan
                            )?.let { event ->
                                tappedEvent = event
                                onEventSelected(event)
                            }
                        }
                    }
                    .pointerInput(events, sessionStart, totalSpan) {
                        val leftAxisPadPx = 46.dp.toPx()
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var isMultiTouch = false
                            var isHorizontalDrag = false
                            var totalDx = 0f
                            var totalDy = 0f
                            val touchSlop = viewConfiguration.touchSlop

                            do {
                                val pointerEvent = awaitPointerEvent()
                                val pressedChanges = pointerEvent.changes.filter { it.pressed }
                                val pressedCount = pressedChanges.size

                                if (pressedCount >= 2) {
                                    isMultiTouch = true
                                    val zoomChange = pointerEvent.calculateZoom()
                                    val panChange = pointerEvent.calculatePan()
                                    val centroid = pointerEvent.calculateCentroid(useCurrent = false)
                                    val plotWidth = (size.width - leftAxisPadPx).coerceAtLeast(1f)

                                    if (zoomChange != 1f || panChange != Offset.Zero) {
                                        val focalFraction = if (centroid.isSpecified) {
                                            ((centroid.x - leftAxisPadPx) / plotWidth).coerceIn(0f, 1f)
                                        } else {
                                            0.5f
                                        }
                                        val updated = computePinchZoomViewport(
                                            currentZoom = zoomLevel,
                                            currentStartFraction = viewStartFraction,
                                            zoomChange = zoomChange,
                                            panXInPlotFraction = panChange.x / plotWidth,
                                            focalFractionInPlot = focalFraction
                                        )
                                        zoomLevel = updated.zoomLevel
                                        viewStartFraction = updated.viewStartFraction
                                    }
                                    pointerEvent.changes.forEach { it.consume() }
                                } else if (pressedCount == 1) {
                                    val change = pressedChanges.first()
                                    val delta = change.positionChangeIgnoreConsumed()
                                    val dx = delta.x
                                    val dy = delta.y

                                    if (isMultiTouch) {
                                        // Smoothly continue horizontal panning if the user lifts one finger while zoomed in
                                        if (zoomLevel > 1.01f && abs(dx) > 0.1f) {
                                            val plotWidth = (size.width - leftAxisPadPx).coerceAtLeast(1f)
                                            val maxStart = (1f - 1f / zoomLevel).coerceAtLeast(0f)
                                            val deltaFraction = (dx / plotWidth) / zoomLevel
                                            viewStartFraction = (viewStartFraction - deltaFraction).coerceIn(0f, maxStart)
                                        }
                                        change.consume()
                                    } else {
                                        totalDx += dx
                                        totalDy += dy

                                        if (!isHorizontalDrag) {
                                            if (abs(totalDx) > touchSlop && abs(totalDx) >= abs(totalDy)) {
                                                isHorizontalDrag = true
                                            } else if (abs(totalDy) > touchSlop * 1.25f && abs(totalDy) > abs(totalDx)) {
                                                // Let parent LazyColumn scroll vertically
                                                break
                                            }
                                        }

                                        if (isHorizontalDrag) {
                                            val plotWidth = (size.width - leftAxisPadPx).coerceAtLeast(1f)
                                            if (zoomLevel > 1.01f) {
                                                val maxStart = (1f - 1f / zoomLevel).coerceAtLeast(0f)
                                                val deltaFraction = (dx / plotWidth) / zoomLevel
                                                viewStartFraction = (viewStartFraction - deltaFraction).coerceIn(0f, maxStart)
                                            } else {
                                                val curSpan = (totalSpan.toDouble() / zoomLevel.toDouble()).toLong().coerceAtLeast(15_000L)
                                                val curStart = sessionStart + (viewStartFraction.toDouble() * totalSpan.toDouble()).toLong()
                                                findClosestEventAtX(
                                                    xPx = change.position.x,
                                                    widthPx = size.width,
                                                    leftAxisPadPx = leftAxisPadPx,
                                                    currentVisibleStart = curStart,
                                                    currentVisibleSpan = curSpan
                                                )?.let { ev ->
                                                    if (tappedEvent?.id != ev.id) {
                                                        tappedEvent = ev
                                                    }
                                                }
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            } while (pointerEvent.changes.any { it.pressed })
                        }
                    }
            ) {
                val leftPad = 46.dp.toPx()
                val chartHeight = size.height - 28.dp.toPx()
                val chartWidth = (size.width - leftPad).coerceAtLeast(10f)

                // 1. SnoreLab Y-axis bands: Episch, Laut, Leicht, Ruhe
                val bands = listOf(
                    Triple("Episch", 68f, heavyColor),
                    Triple("Laut", 52f, moderateColor),
                    Triple("Leicht", 42f, mildColor),
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

                // 2. Time Axis Labels (adjusted dynamically to current zoom level)
                val timeStep = computeTimeAxisStepMs(visibleSpan)
                val axisFormat = if (timeStep < 60_000L) detailTimeFormat else timeFormat
                val firstStepIndex = ((visibleStart - sessionStart).coerceAtLeast(0L) / timeStep)
                var t = sessionStart + firstStepIndex * timeStep
                if (t < visibleStart) t += timeStep

                var lastLabelRightPx = leftPad - 10f
                val labelHalfWidthPx = if (timeStep < 60_000L) 22.dp.toPx() else 14.dp.toPx()
                val minLabelGapPx = 6.dp.toPx()

                while (t <= visibleEnd) {
                    val fraction = (t - visibleStart).toFloat() / visibleSpan.toFloat()
                    val x = leftPad + fraction * chartWidth

                    if (x in leftPad..size.width) {
                        drawLine(
                            color = gridColor,
                            start = Offset(x, chartHeight),
                            end = Offset(x, chartHeight + 5.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )

                        val labelLeft = (x - labelHalfWidthPx).coerceIn(leftPad, (size.width - labelHalfWidthPx * 2.2f).coerceAtLeast(leftPad))
                        if (labelLeft >= lastLabelRightPx + minLabelGapPx) {
                            val timeLabel = axisFormat.format(Date(t))
                            drawText(
                                textMeasurer = textMeasurer,
                                text = timeLabel,
                                topLeft = Offset(labelLeft, chartHeight + 7.dp.toPx()),
                                style = TextStyle(color = labelColor, fontSize = 10.sp)
                            )
                            lastLabelRightPx = labelLeft + labelHalfWidthPx * 2.1f
                        }
                    }
                    t += timeStep
                }

                // Clip timeline bars & spikes to the plot region [leftPad, size.width]
                clipRect(left = leftPad, top = 0f, right = size.width, bottom = chartHeight + 4.dp.toPx()) {
                    // 3. Continuous Quiet Baseline breathing bars anchored to timeline timestamps
                    val subdivisions = Integer.highestOneBit(zoomLevel.toInt().coerceAtLeast(1)).coerceAtLeast(1)
                    val totalBaselineSlots = 72 * subdivisions
                    val slotSpanMs = (totalSpan.toDouble() / totalBaselineSlots.toDouble()).coerceAtLeast(1.0)
                    val slotWidth = (chartWidth * zoomLevel) / totalBaselineSlots.toFloat()

                    val firstSlot = ((visibleStart - sessionStart).toDouble() / slotSpanMs).toInt().coerceAtLeast(0)
                    val lastSlot = ((visibleEnd - sessionStart).toDouble() / slotSpanMs).toInt().coerceAtMost(totalBaselineSlots - 1)

                    for (i in firstSlot..lastSlot) {
                        val slotTime = sessionStart + (i * slotSpanMs)
                        val slotFraction = ((slotTime - visibleStart) / visibleSpan.toDouble()).toFloat()
                        val slotX = leftPad + slotFraction * chartWidth
                        val breatheVariation = 0.08f + 0.05f * sin(i * 0.7f)
                        val barH = chartHeight * breatheVariation
                        drawRoundRect(
                            color = quietColor.copy(alpha = 0.35f),
                            topLeft = Offset(slotX + 1f, chartHeight - barH),
                            size = Size((slotWidth - 2f).coerceAtLeast(1.5f), barH),
                            cornerRadius = CornerRadius(1.5.dp.toPx())
                        )
                    }

                    // 4. Snore Sequence Spikes
                    val minBarWidth = 5.5.dp.toPx()
                    events.forEach { event ->
                        val fraction = (event.timestamp - visibleStart).toFloat() / visibleSpan.toFloat()
                        val durationFraction = (event.durationMs.toFloat() / visibleSpan.toFloat()).coerceIn(0f, 1f)
                        val barWidth = (durationFraction * chartWidth).coerceAtLeast(minBarWidth)
                        val x = leftPad + fraction * chartWidth

                        if (x + barWidth < leftPad || x > size.width) return@forEach

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
            }

            // SnoreLab-style Zoom Window Indicator & Mini-Scrollbar when zoomed in
            if (isZoomed) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ausschnitt: ${timeFormat.format(Date(visibleStart))} – ${timeFormat.format(Date(visibleEnd))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ziehen zum Verschieben",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    drawRoundRect(
                        color = gridColor.copy(alpha = 0.35f),
                        size = size,
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    val thumbStartX = clampedStartFraction * size.width
                    val thumbWidth = (visibleFractionSpan * size.width).coerceAtLeast(18.dp.toPx())
                    drawRoundRect(
                        color = SnoreLabCyan.copy(alpha = 0.85f),
                        topLeft = Offset(thumbStartX.coerceAtMost((size.width - thumbWidth).coerceAtLeast(0f)), 0f),
                        size = Size(thumbWidth.coerceAtMost(size.width), size.height),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
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

                        val isActiveClip = selectedEventId == activeEvent.id
                        if (activeEvent.audioFilePath != null && (isActiveClip || onSeekEvent != null)) {
                            val effectiveDurationMs = if (isActiveClip && playbackDurationMs > 0L) {
                                playbackDurationMs
                            } else {
                                activeEvent.durationMs
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            AudioSeekBar(
                                progress = if (isActiveClip) playbackProgress else 0f,
                                durationMs = effectiveDurationMs,
                                onSeek = { newProgress ->
                                    onSeekEvent?.invoke(activeEvent, newProgress)
                                }
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
