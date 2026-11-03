package de.carcophan.snorerec.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.carcophan.snorerec.ui.theme.SnoreEpicRed
import de.carcophan.snorerec.ui.theme.SnoreLabCyan
import de.carcophan.snorerec.ui.theme.SnoreLabSurfaceElevated
import de.carcophan.snorerec.ui.theme.SnoreLightYellow
import de.carcophan.snorerec.ui.theme.SnoreLoudOrange
import de.carcophan.snorerec.ui.theme.SnoreQuietGreen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

@Composable
fun LiveMonitoringCard(
    currentDb: Float,
    snoreCount: Int,
    onStopClicked: () -> Unit,
    isInDelayedStart: Boolean = false,
    delayedStartRemainingSeconds: Int = 0,
    onSkipDelayClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val clockFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Row: Active indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(pulseScale)
                        .background(
                            if (isInDelayedStart) SnoreLightYellow else SnoreEpicRed,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isInDelayedStart) "Verzögerter Start aktiv" else "Schnarchüberwachung aktiv",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Large SnoreLab Night Clock
            Text(
                text = clockFormat.format(Date(currentTimeMillis)),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (isInDelayedStart && delayedStartRemainingSeconds > 0) {
                // Delayed Start Countdown Card
                val min = delayedStartRemainingSeconds / 60
                val sec = delayedStartRemainingSeconds % 60
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SnoreLabSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Aufnahme startet automatisch in",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%02d:%02d", min, sec),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SnoreLightYellow
                        )
                        if (onSkipDelayClicked != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(onClick = onSkipDelayClicked) {
                                Icon(imageVector = Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Jetzt sofort starten")
                            }
                        }
                    }
                }
            } else {
                // Live Audio Waveform & Decibel Meter
                val normalizedDb = ((currentDb - 20f) / 60f).coerceIn(0.04f, 1f)
                val levelColor = when {
                    currentDb >= 68f -> SnoreEpicRed
                    currentDb >= 52f -> SnoreLoudOrange
                    currentDb >= 42f -> SnoreLightYellow
                    else -> SnoreQuietGreen
                }
                val levelLabel = when {
                    currentDb >= 68f -> "Episch"
                    currentDb >= 52f -> "Laut"
                    currentDb >= 42f -> "Leicht"
                    else -> "Ruhe"
                }

                // Multi-bar visualizer
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    val barCount = 32
                    val spacing = 4.dp.toPx()
                    val barW = ((size.width - spacing * (barCount - 1)) / barCount).coerceAtLeast(3f)
                    for (i in 0 until barCount) {
                        val centerFactor = 1f - kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                        val wave = 0.35f + 0.65f * ((sin(wavePhase + i * 0.45f) + 1f) * 0.5f)
                        val hFrac = (normalizedDb * centerFactor * wave + 0.08f).coerceIn(0.08f, 1f)
                        val h = size.height * hFrac
                        drawRoundRect(
                            color = levelColor.copy(alpha = 0.85f),
                            topLeft = Offset(i * (barW + spacing), (size.height - h) / 2f),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pegel: ${currentDb.toInt()} dB ($levelLabel)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = levelColor
                    )
                    Text(
                        text = "$snoreCount Schnarch-Sequenzen",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SnoreLabCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { normalizedDb },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = levelColor,
                    trackColor = SnoreLabSurfaceElevated
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onStopClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SnoreEpicRed,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stoppen",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Aufnahme beenden",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
