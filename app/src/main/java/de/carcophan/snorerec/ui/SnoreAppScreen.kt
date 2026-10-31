package de.carcophan.snorerec.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.ui.components.LiveMonitoringCard
import de.carcophan.snorerec.ui.components.SnoreEventCard
import de.carcophan.snorerec.ui.components.SnorePatternAnalysisView
import de.carcophan.snorerec.ui.components.SnoreTimelineChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoreAppScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current

    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
    val currentDb by viewModel.currentDb.collectAsStateWithLifecycle()
    val liveSnoreCount by viewModel.liveSnoreCount.collectAsStateWithLifecycle()

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()
    val events by viewModel.selectedSessionEvents.collectAsStateWithLifecycle()
    val analysis by viewModel.currentAnalysis.collectAsStateWithLifecycle()

    val playingEventId by viewModel.playingEventId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Übersicht & Muster, 1: Audio-Aufnahmen

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            viewModel.toggleMonitoring(context)
        } else {
            Toast.makeText(context, "Mikrofon-Berechtigung wird zur Schnarcherkennung benötigt", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NightsStay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SnoreRec",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    if (selectedSession != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Nacht löschen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Monitoring or Start Action Card
            item {
                if (isMonitoring) {
                    LiveMonitoringCard(
                        currentDb = currentDb,
                        snoreCount = liveSnoreCount,
                        onStopClicked = { viewModel.toggleMonitoring(context) }
                    )
                } else {
                    StartMonitoringHeroCard(
                        onStartClicked = {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                arrayOf(Manifest.permission.RECORD_AUDIO)
                            }
                            permissionLauncher.launch(perms)
                        }
                    )
                }
            }

            // 2. Session Selector (if sessions exist)
            if (sessions.isNotEmpty()) {
                item {
                    SessionSelectionBar(
                        sessions = sessions,
                        selectedSession = selectedSession,
                        onSelectSession = { viewModel.selectSession(it.id) }
                    )
                }
            }

            // 3. Selected Session Content
            if (selectedSession != null) {
                item {
                    // Navigation Tabs: [Muster & Analyse] / [Aufnahmen (X)]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TabButton(
                            text = "Muster & Analyse",
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        TabButton(
                            text = "Aufnahmen (${events.size})",
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedTab == 0) {
                    // TAB 0: Timeline Chart & Pattern Analysis
                    item {
                        SnoreTimelineChart(
                            session = selectedSession!!,
                            events = events,
                            selectedEventId = playingEventId,
                            onEventSelected = { event ->
                                event.audioFilePath?.let { path ->
                                    viewModel.playAudio(event.id, path)
                                }
                            }
                        )
                    }

                    if (analysis != null) {
                        item {
                            SnorePatternAnalysisView(analysis = analysis!!)
                        }
                    }
                } else {
                    // TAB 1: Individual recorded audio clips
                    if (events.isEmpty()) {
                        item {
                            EmptyEventsCard()
                        }
                    } else {
                        items(events, key = { it.id }) { event ->
                            SnoreEventCard(
                                event = event,
                                isPlaying = playingEventId == event.id && isPlaying,
                                progress = if (playingEventId == event.id) playbackProgress else 0f,
                                onPlayToggle = {
                                    event.audioFilePath?.let { path ->
                                        viewModel.playAudio(event.id, path)
                                    }
                                }
                            )
                        }
                    }
                }
            } else if (!isMonitoring) {
                // Empty State
                item {
                    EmptyHistoryHeroCard()
                }
            }
        }
    }

    if (showDeleteConfirmDialog && selectedSession != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Aufzeichnung löschen?") },
            text = { Text("Möchtest du diese Nachtaufzeichnung und alle zugehörigen Audio-Schnipsel wirklich löschen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedSession?.let { viewModel.deleteSession(it.id) }
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun StartMonitoringHeroCard(
    onStartClicked: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Schlafaufzeichnung starten",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Überwacht Schnarchgeräusche die ganze Nacht im Hintergrund. Erkennt Schnarchen, misst Intensität und speichert Audio-Ausschnitte.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onStartClicked,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Aufzeichnung für heute Nacht starten",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun SessionSelectionBar(
    sessions: List<NightSession>,
    selectedSession: NightSession?,
    onSelectSession: (NightSession) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd. MMM", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Ausgewählte Nacht:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val label = if (selectedSession != null) {
                    "${dateFormat.format(Date(selectedSession.startTime))} (${timeFormat.format(Date(selectedSession.startTime))} Uhr)"
                } else {
                    "Keine ausgewählt"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${sessions.size} Nächte ▾",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                sessions.forEach { s ->
                    val isSelected = s.id == selectedSession?.id
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = "${dateFormat.format(Date(s.startTime))} – ${s.snoreCount} Schnarcher",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = "Start: ${timeFormat.format(Date(s.startTime))}, Dauer: ${(s.durationMs / 3600000)} Std.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onSelectSession(s)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EmptyEventsCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Keine Schnarchgeräusche erkannt",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "In dieser Nacht wurde kein Schnarchen oberhalb des Schwellenwerts registriert. Ruhiger Schlaf!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHistoryHeroCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Noch keine Schlafaufzeichnungen",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tippe oben auf 'Aufzeichnung für heute Nacht starten', lege dein Smartphone neben das Bett und lass dein Schnarchen automatisch analysieren.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
