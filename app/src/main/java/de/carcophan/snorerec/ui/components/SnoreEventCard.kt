package de.carcophan.snorerec.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.carcophan.snorerec.data.model.SnoreEvent
import de.carcophan.snorerec.data.model.SnoreIntensity
import de.carcophan.snorerec.ui.theme.SnoreEpicRed
import de.carcophan.snorerec.ui.theme.SnoreLabCyan
import de.carcophan.snorerec.ui.theme.SnoreLightYellow
import de.carcophan.snorerec.ui.theme.SnoreLoudOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SnoreEventCard(
    event: SnoreEvent,
    isPlaying: Boolean,
    progress: Float,
    onPlayToggle: () -> Unit,
    onBookmarkToggle: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val tierColor = when (event.intensity) {
        SnoreIntensity.MILD -> SnoreLightYellow
        SnoreIntensity.MODERATE -> SnoreLoudOrange
        SnoreIntensity.HEAVY -> SnoreEpicRed
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (event.audioFilePath != null) {
                        FilledIconButton(
                            onClick = onPlayToggle,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isPlaying) SnoreLabCyan else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Abspielen"
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val startText = timeFormat.format(Date(event.timestamp))
                            val timeRangeText = if (event.durationMs >= 5000L) {
                                val endText = timeFormat.format(Date(event.timestamp + event.durationMs))
                                "$startText – $endText"
                            } else {
                                startText
                            }
                            Text(
                                text = timeRangeText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tierColor.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "${event.peakDb.toInt()} dB • ${event.intensity.snoreLabLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tierColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dauer: ${formatEventDuration(event.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBookmarkToggle != null) {
                        IconButton(
                            onClick = onBookmarkToggle,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (event.isBookmarked) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorit",
                                tint = if (event.isBookmarked) SnoreLightYellow else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Löschen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isPlaying) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = SnoreLabCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

private fun formatEventDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        String.format(Locale.getDefault(), "%.1fs", durationMs / 1000f)
    }
}
