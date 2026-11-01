package de.carcophan.snorerec.data.model

data class CatalogItem(
    val id: String,
    val name: String,
    val category: String,
    val iconEmoji: String,
    val description: String,
    val isCustom: Boolean = false
)

object SnoreCatalog {

    val defaultRemedies: List<CatalogItem> = listOf(
        CatalogItem(
            id = "side_sleeping",
            name = "Seitenschlaf",
            category = "Position",
            iconEmoji = "🛌",
            description = "Verhindert das Zurückfallen der Zunge in den Rachenraum durch Seitenlage."
        ),
        CatalogItem(
            id = "nasal_strips",
            name = "Nasenpflaster",
            category = "Nase",
            iconEmoji = "🩹",
            description = "Weitet die Nasenflügel mechanisch und erleichtert die Nasenatmung."
        ),
        CatalogItem(
            id = "nasal_spray",
            name = "Nasenspray",
            category = "Nase",
            iconEmoji = "💧",
            description = "Befreit und befeuchtet die Nasenschleimhäute vor dem Schlafengehen."
        ),
        CatalogItem(
            id = "nasal_dilator",
            name = "Nasendilatator",
            category = "Nase",
            iconEmoji = "👃",
            description = "Kleiner Einsatz, der die Nasenlöcher von innen sanft offen hält."
        ),
        CatalogItem(
            id = "mouth_tape",
            name = "Mundpflaster (Mouth Tape)",
            category = "Mund & Rachen",
            iconEmoji = "🤐",
            description = "Fördert die natürliche Nasenatmung und verhindert offenes Mundschnarchen."
        ),
        CatalogItem(
            id = "mandibular_splint",
            name = "Schnarchschiene (UPS)",
            category = "Mund & Rachen",
            iconEmoji = "🦷",
            description = "Hält den Unterkiefer leicht vorn, damit die oberen Atemwege frei bleiben."
        ),
        CatalogItem(
            id = "wedge_pillow",
            name = "Keilkissen / Erhöhter Kopf",
            category = "Position",
            iconEmoji = "📐",
            description = "Leichte Hochlagerung des Oberkörpers reduziert den Gewebedruck im Hals."
        ),
        CatalogItem(
            id = "humidifier",
            name = "Luftbefeuchter",
            category = "Schlafzimmer",
            iconEmoji = "💨",
            description = "Verhindert trockene Schleimhäute und Reizungen durch trockene Raumluft."
        ),
        CatalogItem(
            id = "nasal_rinse",
            name = "Nasenspülung",
            category = "Nase",
            iconEmoji = "🌊",
            description = "Spült Allergene, Staub und Schleim vor dem Schlafen aus den Nebenhöhlen."
        ),
        CatalogItem(
            id = "throat_spray",
            name = "Rachen-Spray / Gurgeln",
            category = "Mund & Rachen",
            iconEmoji = "🌿",
            description = "Befeuchtet das Gaumensegel und reduziert Gewebevibrationen."
        ),
        CatalogItem(
            id = "cpap",
            name = "CPAP-Maske",
            category = "Medizinisch",
            iconEmoji = "😷",
            description = "Überdrucktherapie zur zuverlässigen Offenhaltung der Atemwege bei Apnoe."
        ),
        CatalogItem(
            id = "chin_strap",
            name = "Kinnriemen",
            category = "Mund & Rachen",
            iconEmoji = "🎗️",
            description = "Stützt den Unterkiefer sanft gegen das Aufklappen im Tiefschlaf."
        )
    )

    val defaultFactors: List<CatalogItem> = listOf(
        CatalogItem(
            id = "alcohol",
            name = "Alkohol",
            category = "Konsum",
            iconEmoji = "🍺",
            description = "Entspannt die Rachenmuskulatur stark und verstärkt das Schnarchen deutlich."
        ),
        CatalogItem(
            id = "overtired",
            name = "Stark übermüdet",
            category = "Zustand",
            iconEmoji = "🥱",
            description = "Tieferer Erschöpfungsschlaf führt zu stärkerer Muskelentspannung im Hals."
        ),
        CatalogItem(
            id = "blocked_nose",
            name = "Verstopfte Nase / Erkältung",
            category = "Gesundheit",
            iconEmoji = "🤧",
            description = "Erzwingt Mundatmung und erzeugt Unterdruck in den oberen Atemwegen."
        ),
        CatalogItem(
            id = "allergy",
            name = "Allergie / Heuschnupfen",
            category = "Gesundheit",
            iconEmoji = "🌼",
            description = "Geschwollene Schleimhäute verengen den Luftstrom durch die Nase."
        ),
        CatalogItem(
            id = "back_sleeping",
            name = "Rückenschlaf",
            category = "Position",
            iconEmoji = "⬆️",
            description = "Durch die Schwerkraft sinken Zunge und Gaumensegel nach hinten."
        ),
        CatalogItem(
            id = "late_meal",
            name = "Spätes / Schweres Essen",
            category = "Konsum",
            iconEmoji = "🍕",
            description = "Voller Magen drückt auf das Zwerchfell und fördert stillen Reflux."
        ),
        CatalogItem(
            id = "caffeine",
            name = "Spätes Koffein",
            category = "Konsum",
            iconEmoji = "☕",
            description = "Stört die Schlafarchitektur und führt zu unruhigem Schlaf."
        ),
        CatalogItem(
            id = "smoking",
            name = "Rauchen",
            category = "Konsum",
            iconEmoji = "🚬",
            description = "Reizt die Atemwege und verursacht Schwellungen der Rachenschleimhaut."
        ),
        CatalogItem(
            id = "stress",
            name = "Hoher Stress",
            category = "Zustand",
            iconEmoji = "⚡",
            description = "Erhöhte Anspannung und fragmentierter Schlaf beeinflussen die Atmung."
        ),
        CatalogItem(
            id = "sedatives",
            name = "Schlafmittel / Medikamente",
            category = "Gesundheit",
            iconEmoji = "💊",
            description = "Muskelrelaxierende Wirkstoffe verringern den Tonus der oberen Atemwege."
        ),
        CatalogItem(
            id = "dry_air",
            name = "Trockene Heizungsluft",
            category = "Schlafzimmer",
            iconEmoji = "🏜️",
            description = "Trocknet Nase und Rachen über Nacht aus."
        )
    )

    fun findRemedy(nameOrId: String, customNames: Set<String> = emptySet()): CatalogItem {
        defaultRemedies.find { it.id == nameOrId || it.name.equals(nameOrId, ignoreCase = true) }?.let { return it }
        return CatalogItem(
            id = nameOrId,
            name = nameOrId,
            category = "Eigene Hilfsmittel",
            iconEmoji = "✨",
            description = "Benutzerdefiniertes Hilfsmittel",
            isCustom = customNames.contains(nameOrId) || true
        )
    }

    fun findFactor(nameOrId: String, customNames: Set<String> = emptySet()): CatalogItem {
        defaultFactors.find { it.id == nameOrId || it.name.equals(nameOrId, ignoreCase = true) }?.let { return it }
        return CatalogItem(
            id = nameOrId,
            name = nameOrId,
            category = "Eigene Faktoren",
            iconEmoji = "📌",
            description = "Benutzerdefinierter Faktor",
            isCustom = customNames.contains(nameOrId) || true
        )
    }
}

