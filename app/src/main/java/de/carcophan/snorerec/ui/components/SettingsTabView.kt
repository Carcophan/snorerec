package de.carcophan.snorerec.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.carcophan.snorerec.data.preferences.RecordingMode
import de.carcophan.snorerec.data.preferences.SnoreSettingsState
import de.carcophan.snorerec.data.repository.StorageUsageInfo
import de.carcophan.snorerec.ui.theme.SnoreEpicRed
import de.carcophan.snorerec.ui.theme.SnoreLabCyan
import de.carcophan.snorerec.ui.theme.SnoreLabSurfaceElevated
import de.carcophan.snorerec.ui.theme.SnoreLightYellow
import de.carcophan.snorerec.ui.theme.SnoreLoudOrange
import de.carcophan.snorerec.ui.theme.SnoreQuietGreen
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsTabView(
    settings: SnoreSettingsState,
    storageUsage: StorageUsageInfo,
    onSensitivityChanged: (Float) -> Unit,
    onRecordingModeChanged: (RecordingMode) -> Unit,
    onRetentionDaysChanged: (Int) -> Unit,
    onProfileChanged: (age: Int, heightCm: Int, weightKg: Float, goal: Int) -> Unit,
    onExportCsv: () -> Unit,
    onSeedDemoData: () -> Unit,
    onDeleteAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearAllDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Microphone Sensitivity & AI Detection Calibration
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🎙️ Mikrofon-Empfindlichkeit & KI-Erkennung",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Passt den Auslöse-Schwellenwert vor der YAMNet-KI-Prüfung an. Standard ist 42 dB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aktuelle Schwelle:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${settings.effectiveMinDbThreshold.toInt()} dB (${
                            when {
                                settings.sensitivityOffsetDb < -1f -> "Höhere Empfindlichkeit"
                                settings.sensitivityOffsetDb > 1f -> "Geringere Empfindlichkeit"
                                else -> "Standard (Empfohlen)"
                            }
                        })",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SnoreLabCyan
                    )
                }

                Slider(
                    value = -settings.sensitivityOffsetDb, // right = more sensitive
                    onValueChange = { onSensitivityChanged((-it).roundToInt().toFloat()) },
                    valueRange = -8f..8f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = SnoreLabCyan,
                        activeTrackColor = SnoreLabCyan
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Unempfindlicher (50 dB)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Standard (42 dB)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Empfindlicher (34 dB)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 2. Recording Mode (Aufnahmemodus)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💾 Aufnahmemodus (Audio-Samples)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                RecordingMode.entries.forEach { mode ->
                    val selected = settings.recordingMode == mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) SnoreLabSurfaceElevated else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onRecordingModeChanged(mode) }
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) SnoreLabCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        // 3. Local Storage & Retention (Speicherplatz & Aufbewahrung)
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
                        text = "🧹 Lokaler Speicher & Auto-Cleanup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SnoreLabCyan.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f MB (%d Clips)", storageUsage.totalMegabytes, storageUsage.totalFilesCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = SnoreLabCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Alte Audio-Dateien werden automatisch bereinigt (Statistiken und ⭐ markierte Favoriten bleiben dauerhaft erhalten).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                val retentionOptions = listOf(
                    7 to "7 Tage",
                    14 to "14 Tage",
                    30 to "30 Tage",
                    90 to "90 Tage",
                    -1 to "Nie"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    retentionOptions.forEach { (days, label) ->
                        val selected = settings.retentionDays == days
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) SnoreLabCyan else SnoreLabSurfaceElevated)
                                .clickable { onRetentionDaysChanged(days) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 4. Local Personal Profile & BMI (Persönliches Profil)
        var age by remember(settings.userAge) { mutableIntStateOf(settings.userAge) }
        var heightCm by remember(settings.userHeightCm) { mutableIntStateOf(settings.userHeightCm) }
        var weightKg by remember(settings.userWeightKg) { mutableFloatStateOf(settings.userWeightKg) }
        var scoreGoal by remember(settings.snoreScoreGoal) { mutableIntStateOf(settings.snoreScoreGoal) }

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
                        text = "👤 Persönliches Schlaf-Profil (100% lokal)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val bmi = settings.bmi
                    val bmiColor = when {
                        bmi < 25f -> SnoreQuietGreen
                        bmi < 30f -> SnoreLightYellow
                        else -> SnoreLoudOrange
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bmiColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "BMI %.1f", bmi),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = bmiColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Schnarch-Score-Ziel: ≤ $scoreGoal", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = scoreGoal.toFloat(),
                    onValueChange = {
                        scoreGoal = it.roundToInt()
                        onProfileChanged(age, heightCm, weightKg, scoreGoal)
                    },
                    valueRange = 5f..80f
                )

                Text("Körpergröße: $heightCm cm", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = heightCm.toFloat(),
                    onValueChange = {
                        heightCm = it.roundToInt()
                        onProfileChanged(age, heightCm, weightKg, scoreGoal)
                    },
                    valueRange = 140f..210f
                )

                Text(String.format(Locale.getDefault(), "Gewicht: %.1f kg", weightKg), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = weightKg,
                    onValueChange = {
                        weightKg = (it * 2f).roundToInt() / 2f
                        onProfileChanged(age, heightCm, weightKg, scoreGoal)
                    },
                    valueRange = 45f..150f
                )
            }
        }

        // 5. Local Data Export, Demo Nights & Reset
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "🔒 Lokale Datenverwaltung (Ohne Cloud / Firebase)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Alle Aufnahmen, Scores und Profile liegen ausschließlich lokal auf diesem Gerät in der privaten SQLite-Datenbank.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onExportCsv,
                    colors = ButtonDefaults.buttonColors(containerColor = SnoreLabCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alle Nächte als CSV exportieren")
                }

                FilledTonalButton(
                    onClick = onSeedDemoData,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("10 Beispiel-Nächte generieren (Demo-Daten)")
                }

                OutlinedButton(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = SnoreEpicRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alle lokalen Aufzeichnungen löschen", color = SnoreEpicRed)
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Alle Daten unwiderruflich löschen?") },
            text = { Text("Möchtest du wirklich sämtliche gespeicherten Nächte und Audioaufnahmen auf diesem Gerät löschen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllData()
                        showClearAllDialog = false
                    }
                ) {
                    Text("Alles löschen", color = SnoreEpicRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

