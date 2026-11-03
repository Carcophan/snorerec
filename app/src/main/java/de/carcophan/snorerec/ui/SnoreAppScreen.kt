package de.carcophan.snorerec.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreIntensity
import de.carcophan.snorerec.data.preferences.SnoreSettingsState
import de.carcophan.snorerec.ui.components.LiveMonitoringCard
import de.carcophan.snorerec.ui.components.SettingsTabView
import de.carcophan.snorerec.ui.components.SnoreEventCard
import de.carcophan.snorerec.ui.components.SnorePatternAnalysisView
import de.carcophan.snorerec.ui.components.SnoreTimelineChart
import de.carcophan.snorerec.ui.components.TrendsTabView
import de.carcophan.snorerec.ui.theme.SnoreLabBgDark
import de.carcophan.snorerec.ui.theme.SnoreLabCyan
import de.carcophan.snorerec.ui.theme.SnoreLabSurface
import de.carcophan.snorerec.ui.theme.SnoreLabSurfaceElevated
import de.carcophan.snorerec.ui.theme.SnoreQuietGreen
import de.carcophan.snorerec.ui.theme.snoreScoreColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MainBottomTab(val label: String, val icon: ImageVector) {
    RECORD("Aufzeichnen", Icons.Default.Mic),
    RESULTS("Ergebnisse", Icons.Default.GraphicEq),
    TRENDS("Trends", Icons.AutoMirrored.Filled.ShowChart),
    SETTINGS("Profil", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoreAppScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()

    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
    val isInDelayedStart by viewModel.isInDelayedStart.collectAsStateWithLifecycle()
    val delayedStartRemainingSeconds by viewModel.delayedStartRemainingSeconds.collectAsStateWithLifecycle()
    val currentDb by viewModel.currentDb.collectAsStateWithLifecycle()
    val liveSnoreCount by viewModel.liveSnoreCount.collectAsStateWithLifecycle()

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()
    val events by viewModel.selectedSessionEvents.collectAsStateWithLifecycle()
    val analysis by viewModel.currentAnalysis.collectAsStateWithLifecycle()
    val trends by viewModel.trendsAnalysis.collectAsStateWithLifecycle()

    val playingEventId by viewModel.playingEventId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val playbackDurationMs by viewModel.playbackDurationMs.collectAsStateWithLifecycle()

    var currentBottomTab by remember { mutableStateOf(MainBottomTab.RECORD) }
    var resultsSubTab by remember { mutableIntStateOf(0) } // 0: Diagramm & Analyse, 1: Audio-Aufnahmen
    var selectedClipFilter by remember { mutableStateOf(AudioClipFilter.ALL) }
    var autoPlayNextClips by remember { mutableStateOf(false) }
    var consolidationMessage by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDelayedStartDialog by remember { mutableStateOf(false) }

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
        containerColor = SnoreLabBgDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NightsStay,
                            contentDescription = null,
                            tint = SnoreLabCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SnoreRec",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SnoreLabCyan.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "LOCAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = SnoreLabCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (currentBottomTab == MainBottomTab.RESULTS && selectedSession != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Nacht löschen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SnoreLabBgDark
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SnoreLabSurface,
                tonalElevation = 0.dp
            ) {
                MainBottomTab.entries.forEach { tab ->
                    val selected = currentBottomTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            currentBottomTab = tab
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SnoreLabCyan,
                            selectedTextColor = SnoreLabCyan,
                            indicatorColor = SnoreLabSurfaceElevated,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (currentBottomTab) {
                MainBottomTab.RECORD -> {
                    item {
                        if (isMonitoring) {
                            LiveMonitoringCard(
                                currentDb = currentDb,
                                snoreCount = liveSnoreCount,
                                onStopClicked = {
                                    viewModel.toggleMonitoring(context)
                                    currentBottomTab = MainBottomTab.RESULTS
                                },
                                isInDelayedStart = isInDelayedStart,
                                delayedStartRemainingSeconds = delayedStartRemainingSeconds,
                                onSkipDelayClicked = { viewModel.skipDelayedStart(context) }
                            )
                        } else {
                            SnoreLabRecordSetupView(
                                settings = settings,
                                sessionsCount = sessions.size,
                                latestSession = sessions.firstOrNull(),
                                onStartRecording = {
                                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        arrayOf(Manifest.permission.RECORD_AUDIO)
                                    }
                                    permissionLauncher.launch(perms)
                                },
                                onOpenDelayedStart = { showDelayedStartDialog = true },
                                onOpenLatestResult = {
                                    currentBottomTab = MainBottomTab.RESULTS
                                },
                                onSeedDemoClicked = {
                                    viewModel.seedDemoSessions()
                                    currentBottomTab = MainBottomTab.RESULTS
                                }
                            )
                        }
                    }
                }

                MainBottomTab.RESULTS -> {
                    if (sessions.isEmpty()) {
                        item {
                            EmptyHistoryHeroCard(
                                onStartClicked = { currentBottomTab = MainBottomTab.RECORD },
                                onSeedDemoClicked = { viewModel.seedDemoSessions() }
                            )
                        }
                    } else {
                        // Horizontal Night Selector Strip (SnoreLab calendar ribbon style)
                        item {
                            SnoreLabNightRibbon(
                                sessions = sessions,
                                selectedSession = selectedSession,
                                onSelectSession = { viewModel.selectSession(it.id) }
                            )
                        }

                        if (selectedSession != null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TabButton(
                                        text = "Übersicht & Chart",
                                        selected = resultsSubTab == 0,
                                        onClick = { resultsSubTab = 0 },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TabButton(
                                        text = "Aufnahmen (${events.size})",
                                        selected = resultsSubTab == 1,
                                        onClick = { resultsSubTab = 1 },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (resultsSubTab == 0) {
                                item {
                                    SnoreTimelineChart(
                                        session = selectedSession!!,
                                        events = events,
                                        selectedEventId = playingEventId,
                                        isPlaying = isPlaying,
                                        playbackProgress = playbackProgress,
                                        playbackDurationMs = playbackDurationMs,
                                        onEventSelected = { event ->
                                            event.audioFilePath?.let { path ->
                                                viewModel.playAudio(event.id, path)
                                            }
                                        },
                                        onSeekEvent = { event, progress ->
                                            event.audioFilePath?.let { path ->
                                                viewModel.seekAudio(
                                                    progress = progress,
                                                    eventId = event.id,
                                                    filePath = path
                                                )
                                            }
                                        },
                                        onToggleBookmark = { event ->
                                            viewModel.toggleBookmark(event)
                                        },
                                        onDeleteEvent = { event ->
                                            viewModel.deleteSnoreEvent(event.id)
                                        }
                                    )
                                }

                                if (analysis != null) {
                                    item {
                                        SnorePatternAnalysisView(
                                            analysis = analysis!!,
                                            session = selectedSession
                                        )
                                    }
                                }
                            } else {
                                // Audio Clips Filter Chips (Alle, Lauteste, Markiert, Episch, Laut, Leicht)
                                item {
                                    Row(
                                        modifier = Modifier
                                             .fillMaxWidth()
                                             .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AudioClipFilter.entries.forEach { filter ->
                                            val selected = selectedClipFilter == filter
                                            FilterChip(
                                                selected = selected,
                                                onClick = { selectedClipFilter = filter },
                                                label = { Text(filter.label) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = SnoreLabCyan,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            )
                                        }
                                    }
                                }

                                // Continuous playback option & Fragment consolidation action
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { autoPlayNextClips = !autoPlayNextClips }
                                        ) {
                                            Checkbox(
                                                checked = autoPlayNextClips,
                                                onCheckedChange = { autoPlayNextClips = it },
                                                colors = CheckboxDefaults.colors(checkedColor = SnoreLabCyan)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Fortlaufend abspielen",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (events.size > 1) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.consolidateSelectedSession { merged ->
                                                        consolidationMessage = if (merged > 0) {
                                                            "$merged zusammengehörende Schnarch-Fragmente verbunden"
                                                        } else {
                                                            "Keine weiteren zusammengehörenden Fragmente gefunden"
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Fragmente verbinden", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }

                                if (consolidationMessage != null) {
                                    item {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = SnoreLabSurfaceElevated,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = consolidationMessage!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SnoreLabCyan
                                                )
                                                IconButton(
                                                    onClick = { consolidationMessage = null },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Schließen", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                val filteredEvents = when (selectedClipFilter) {
                                    AudioClipFilter.ALL -> events
                                    AudioClipFilter.LOUDEST -> events.sortedByDescending { it.peakDb }.take(15)
                                    AudioClipFilter.BOOKMARKED -> events.filter { it.isBookmarked }
                                    AudioClipFilter.EPIC -> events.filter { it.intensity == SnoreIntensity.HEAVY }
                                    AudioClipFilter.LOUD -> events.filter { it.intensity == SnoreIntensity.MODERATE }
                                    AudioClipFilter.LIGHT -> events.filter { it.intensity == SnoreIntensity.MILD }
                                }

                                if (filteredEvents.isEmpty()) {
                                    item {
                                        EmptyEventsCard()
                                    }
                                } else {
                                    items(filteredEvents, key = { it.id }) { event ->
                                        SnoreEventCard(
                                            event = event,
                                            isPlaying = playingEventId == event.id && isPlaying,
                                            isActive = playingEventId == event.id,
                                            progress = if (playingEventId == event.id) playbackProgress else 0f,
                                            playbackDurationMs = if (playingEventId == event.id) playbackDurationMs else 0L,
                                            onPlayToggle = {
                                                event.audioFilePath?.let { path ->
                                                    if (autoPlayNextClips) {
                                                        viewModel.playAudio(event.id, path) {
                                                            viewModel.playNextEventInList(event.id, filteredEvents)
                                                        }
                                                    } else {
                                                        viewModel.playAudio(event.id, path)
                                                    }
                                                }
                                            },
                                            onSeek = { progress ->
                                                event.audioFilePath?.let { path ->
                                                    viewModel.seekAudio(
                                                        progress = progress,
                                                        eventId = event.id,
                                                        filePath = path,
                                                        onFinished = if (autoPlayNextClips) {
                                                            { viewModel.playNextEventInList(event.id, filteredEvents) }
                                                        } else {
                                                            null
                                                        }
                                                    )
                                                }
                                            },
                                            onBookmarkToggle = { viewModel.toggleBookmark(event) },
                                            onDelete = { viewModel.deleteSnoreEvent(event.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MainBottomTab.TRENDS -> {
                    item {
                        TrendsTabView(
                            sessions = sessions,
                            trends = trends,
                            snoreScoreGoal = settings.snoreScoreGoal,
                            onSelectNight = { session ->
                                viewModel.selectSession(session.id)
                                currentBottomTab = MainBottomTab.RESULTS
                            },
                            onSeedDemoClicked = { viewModel.seedDemoSessions() }
                        )
                    }
                }

                MainBottomTab.SETTINGS -> {
                    item {
                        SettingsTabView(
                            settings = settings,
                            storageUsage = storageUsage,
                            onSensitivityChanged = { viewModel.setSensitivityOffsetDb(it) },
                            onRecordingModeChanged = { viewModel.setRecordingMode(it) },
                            onRetentionDaysChanged = { viewModel.setRetentionDays(it) },
                            onProfileChanged = { age, goal ->
                                viewModel.setProfile(age, goal)
                            },
                            onExportCsv = { viewModel.exportCsv(context) },
                            onSeedDemoData = {
                                viewModel.seedDemoSessions()
                                currentBottomTab = MainBottomTab.RESULTS
                            },
                            onDeleteAllData = { viewModel.deleteAllSessions() }
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---

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

    if (showDelayedStartDialog) {
        val options = listOf(0, 5, 10, 15, 20, 30, 45, 60)
        AlertDialog(
            onDismissRequest = { showDelayedStartDialog = false },
            title = { Text("⏱️ Verzögerter Start") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Warte mit dem Beginn der Schnarchüberwachung, bis du eingeschlafen bist:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    options.chunked(2).forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { min ->
                                val selected = settings.delayedStartMinutes == min
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) SnoreLabCyan else SnoreLabSurfaceElevated,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.setDelayedStartMinutes(min)
                                            showDelayedStartDialog = false
                                        }
                                ) {
                                    Text(
                                        text = if (min == 0) "Sofort" else "$min Min.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDelayedStartDialog = false }) {
                    Text("Schließen")
                }
            }
        )
    }
}

@Composable
private fun SnoreLabRecordSetupView(
    settings: SnoreSettingsState,
    sessionsCount: Int,
    latestSession: NightSession?,
    onStartRecording: () -> Unit,
    onOpenDelayedStart: () -> Unit,
    onOpenLatestResult: () -> Unit,
    onSeedDemoClicked: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "startGlow")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. SnoreLab Centerpiece Circular START Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bereit für heute Nacht",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Glowing Circular SnoreLab START Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(176.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(172.dp)
                            .scale(ringScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        SnoreQuietGreen.copy(alpha = 0.28f),
                                        SnoreLabCyan.copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(138.dp)
                            .clip(CircleShape)
                            .border(3.dp, SnoreQuietGreen, CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF153E35),
                                        Color(0xFF0E292B)
                                    )
                                )
                            )
                            .clickable(onClick = onStartRecording),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = SnoreQuietGreen,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "START",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Lege das Smartphone mit dem Display nach unten neben dein Bett und schließe es ans Ladegerät an.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. SnoreLab Pre-Sleep Options List (Verzögerter Start)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                SetupOptionRow(
                    icon = Icons.Default.Timer,
                    iconTint = SnoreLabCyan,
                    title = "Verzögerter Start",
                    valueText = if (settings.delayedStartMinutes == 0) "Sofort" else "${settings.delayedStartMinutes} Min.",
                    onClick = onOpenDelayedStart
                )
            }
        }

        // 3. Last Night Quick Preview or Demo Data Card
        if (latestSession != null) {
            val dateFormat = remember { SimpleDateFormat("dd. MMM, HH:mm", Locale.getDefault()) }
            val scoreColor = snoreScoreColor(latestSession.snoreScore)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLatestResult)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Letzte Aufzeichnung ($sessionsCount Nächte)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateFormat.format(Date(latestSession.startTime)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Schnarchanteil: ${latestSession.snorePercentage.toInt()}% • ${latestSession.snoreCount} Sequenzen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scoreColor.copy(alpha = 0.18f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("SCORE", style = MaterialTheme.typography.labelSmall, color = scoreColor, fontSize = 9.sp)
                            Text(
                                text = "${latestSession.snoreScore}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = scoreColor
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Neu bei SnoreRec?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lade 10 realistische Beispiel-Nächte mit abspielbaren Schnarch-Samples und Trends, um alle SnoreLab-Funktionen sofort auszuprobieren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onSeedDemoClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = SnoreLabSurfaceElevated)
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = SnoreLabCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("10 Beispiel-Nächte laden", color = SnoreLabCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupOptionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    valueText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = SnoreLabCyan
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SnoreLabNightRibbon(
    sessions: List<NightSession>,
    selectedSession: NightSession?,
    onSelectSession: (NightSession) -> Unit
) {
    val dayFormat = remember { SimpleDateFormat("dd. MMM", Locale.getDefault()) }
    val weekdayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        sessions.forEach { session ->
            val isSelected = session.id == selectedSession?.id
            val scoreColor = snoreScoreColor(session.snoreScore)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) SnoreLabSurfaceElevated else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) SnoreLabCyan else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectSession(session) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = weekdayFormat.format(Date(session.startTime)).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dayFormat.format(Date(session.startTime)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(scoreColor.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${session.snoreScore}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor
                        )
                    }
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
            .background(if (selected) SnoreLabCyan else Color.Transparent)
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
                text = "Keine passenden Aufnahmen",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Für diesen Filter liegen in dieser Nacht keine Audio-Schnipsel vor.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHistoryHeroCard(
    onStartClicked: () -> Unit,
    onSeedDemoClicked: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Starte heute Nacht deine erste Überwachung oder lade 10 Beispiel-Nächte, um das SnoreLab-Diagramm und die Auswertungen direkt zu erkunden.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = SnoreQuietGreen)
                ) {
                    Text("Zur Aufnahme", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSeedDemoClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = SnoreLabSurfaceElevated)
                ) {
                    Text("10 Demo-Nächte laden", color = SnoreLabCyan)
                }
            }
        }
    }
}
