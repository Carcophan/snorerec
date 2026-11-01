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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.carcophan.snorerec.data.model.CatalogItem
import de.carcophan.snorerec.data.model.SnoreCatalog
import de.carcophan.snorerec.ui.TrendsAnalysis
import de.carcophan.snorerec.ui.theme.SnoreLabCyan
import de.carcophan.snorerec.ui.theme.SnoreLabSurfaceElevated
import de.carcophan.snorerec.ui.theme.SnoreLoudOrange
import de.carcophan.snorerec.ui.theme.SnoreQuietGreen

@Composable
fun RemediesAndGuideTabView(
    selectedRemedies: Set<String>,
    selectedFactors: Set<String>,
    customRemedies: Set<String>,
    customFactors: Set<String>,
    trends: TrendsAnalysis,
    onToggleRemedy: (String) -> Unit,
    onToggleFactor: (String) -> Unit,
    onAddCustomRemedy: (String) -> Unit,
    onRemoveCustomRemedy: (String) -> Unit,
    onAddCustomFactor: (String) -> Unit,
    onRemoveCustomFactor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableIntStateOf(0) } // 0: Hilfsmittel, 1: Faktoren, 2: Ratgeber
    var newCustomText by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sub-Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Hilfsmittel", "Faktoren", "Ratgeber").forEachIndexed { idx, title ->
                val selected = subTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SnoreLabCyan else Color.Transparent)
                        .clickable { subTab = idx }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when (subTab) {
            0 -> {
                // Hilfsmittel (Remedies)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Hilfsmittel für heute Nacht auswählen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Wähle alle Mittel aus, die du heute Nacht verwendest. SnoreRec vergleicht automatisch im Trends-Tab, wie stark jedes Mittel deinen Schnarch-Score senkt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newCustomText,
                                onValueChange = { newCustomText = it },
                                placeholder = { Text("Eigenes Hilfsmittel hinzufügen...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newCustomText.isNotBlank()) {
                                        onAddCustomRemedy(newCustomText)
                                        newCustomText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SnoreLabCyan)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                            }
                        }
                    }
                }

                val allRemedies = remember(customRemedies) {
                    val customItems = customRemedies.map {
                        CatalogItem(it, it, "Eigene Hilfsmittel", "✨", "Benutzerdefiniertes Hilfsmittel", isCustom = true)
                    }
                    customItems + SnoreCatalog.defaultRemedies
                }

                allRemedies.forEach { item ->
                    val isSelected = selectedRemedies.contains(item.name)
                    val stat = trends.remedyStats.find { it.name.equals(item.name, ignoreCase = true) }
                    CatalogItemRowCard(
                        item = item,
                        isSelected = isSelected,
                        accentColor = SnoreQuietGreen,
                        statText = stat?.let { "${it.nightsCount}x genutzt • Ø Score ${it.avgSnoreScore}" },
                        onClick = { onToggleRemedy(item.name) },
                        onDeleteCustom = if (item.isCustom) ({ onRemoveCustomRemedy(item.name) }) else null
                    )
                }
            }

            1 -> {
                // Faktoren (Factors)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Einfluss-Faktoren für heute Nacht",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Markiere Faktoren wie Alkohol, Erkältung oder Übermüdung, um ihre Auswirkung auf dein Schnarchen zu messen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newCustomText,
                                onValueChange = { newCustomText = it },
                                placeholder = { Text("Eigenen Faktor hinzufügen...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newCustomText.isNotBlank()) {
                                        onAddCustomFactor(newCustomText)
                                        newCustomText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SnoreLoudOrange)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                            }
                        }
                    }
                }

                val allFactors = remember(customFactors) {
                    val customItems = customFactors.map {
                        CatalogItem(it, it, "Eigene Faktoren", "📌", "Benutzerdefinierter Faktor", isCustom = true)
                    }
                    customItems + SnoreCatalog.defaultFactors
                }

                allFactors.forEach { item ->
                    val isSelected = selectedFactors.contains(item.name)
                    val stat = trends.factorStats.find { it.name.equals(item.name, ignoreCase = true) }
                    CatalogItemRowCard(
                        item = item,
                        isSelected = isSelected,
                        accentColor = SnoreLoudOrange,
                        statText = stat?.let { "${it.nightsCount}x erfasst • Ø Score ${it.avgSnoreScore}" },
                        onClick = { onToggleFactor(item.name) },
                        onDeleteCustom = if (item.isCustom) ({ onRemoveCustomFactor(item.name) }) else null
                    )
                }
            }

            else -> {
                // SnoreLab Ratgeber & Wissen
                val articles = listOf(
                    Triple(
                        "📊 Wie wird der Schnarch-Score berechnet?",
                        "SnoreLab-Metrik verstehen",
                        "Der Schnarch-Score kombiniert die Gesamtdauer deines Schnarchens im Verhältnis zur Schlafzeit mit der gemessenen Lautstärke-Intensität (Leicht = 1,0x, Laut = 2,2x, Episch = 3,8x). Ein Score unter 15 gilt als ruhig, 15–40 als mäßig, 40–80 als laut und über 80 als episch."
                    ),
                    Triple(
                        "🛌 Warum Seitenschlaf oft sofort hilft",
                        "Positionstherapie",
                        "In der Rückenlage sinken Zungengrund und weicher Gaumen durch die Schwerkraft nach hinten und verengen den Rachenraum. Durch konsequenten Seitenschlaf oder ein Keilkissen sinkt der Schnarch-Score bei vielen Menschen um 30–60%."
                    ),
                    Triple(
                        "👃 Nasenatmung vs. Mundschnarchen",
                        "Atemwege frei halten",
                        "Wenn die Nase durch Allergien, eine verkrümmte Nasenscheidewand oder eine Erkältung verengt ist, entsteht beim Einatmen ein Unterdruck im Rachen (Bernoulli-Effekt), der das Gewebe zum Vibrieren bringt. Nasenpflaster, Nasendilatatoren oder Nasenspülungen setzen genau hier an."
                    ),
                    Triple(
                        "🍺 Alkohol & späte Mahlzeiten",
                        "Lebensstil-Einflüsse",
                        "Alkohol innerhalb von 3–4 Stunden vor dem Schlafengehen entspannt die Muskulatur der oberen Atemwege übermäßig. Selbst Menschen, die sonst kaum schnarchen, erreichen nach Alkoholgenuss häufig 'Laute' oder 'Epische' Schnarchphasen."
                    ),
                    Triple(
                        "🩺 Hinweis zu obstruktiver Schlafapnoe (OSA)",
                        "Medizinische Einordnung",
                        "Wenn in deinen Audioaufnahmen längere Atemaussetzer (Stille von >10 Sekunden) gefolgt von lautem, explosivem Nach-Luft-Schnappen auftreten oder du trotz langer Schlafdauer tagsüber unter starker Tagesmüdigkeit leidest, empfiehlt sich eine schlaflabormedizinische Abklärung."
                    )
                )

                articles.forEach { (title, badge, body) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SnoreLabCyan.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SnoreLabCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogItemRowCard(
    item: CatalogItem,
    isSelected: Boolean,
    accentColor: Color,
    statText: String?,
    onClick: () -> Unit,
    onDeleteCustom: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) SnoreLabSurfaceElevated else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.iconEmoji,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (statText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statText,
                            style = MaterialTheme.typography.labelSmall,
                            color = SnoreLabCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDeleteCustom != null) {
                    IconButton(onClick = onDeleteCustom) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Löschen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (isSelected) accentColor else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

