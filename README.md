# SnoreRec 🌙💤 (SnoreLab Local Edition)

**SnoreRec** ist eine vollständige, datenschutzorientierte Android-App zur nächtlichen Schnarchüberwachung, Schlafanalyse und automatischen Audioaufzeichnung im Look & Feel von **SnoreLab** – entwickelt für den **ausschließlich privaten Gebrauch (100% lokal auf dem Gerät, ohne Firebase oder Cloud-Anbindung)**.

Dank **On-Device Machine Learning (Google AI Edge LiteRT & YAMNet)** und Pegelanalyse erkennt die App Schnarchgeräusche zuverlässig in Echtzeit, berechnet den **Schnarch-Score (Snore Score)**, analysiert die Wirksamkeit von **Hilfsmitteln (Remedies)** sowie **Lebensstil-Faktoren (Factors)** und speichert Audioaufnahmen platzsparend als AAC-Dateien (`.m4a`).

---

## ✨ Hauptfunktionen (SnoreLab Feature-Set)

- 🌙 **Authentisches SnoreLab Midnight-Navy UI mit 5 Haupt-Tabs:**
  - **1. Aufzeichnen:** Großer animierter Start-Ring, Konfigurationskarten vor dem Schlafen (Verzögerter Start, Einschlafgeräusche, Smart-Wecker, Hilfsmittel & Faktoren) sowie Nacht-Überwachungsbildschirm mit Digitaluhr und Live-Spektrum.
  - **2. Ergebnisse:** Horizontale Nacht-Auswahlleiste mit Schnarch-Score-Badges, großer **Schnarch-Score-Ring**, interaktives **4-Stufen-Nachtdiagramm (*Ruhe*, *Leicht*, *Laut*, *Episch*)** mit Inline-Player, Favoriten-Sternen (⭐), Schlafqualitäts-Bewertung (1–5 Sterne) und Notizen.
  - **3. Trends:** Mehrnächtige Verlaufsdiagramme (7, 30, 90, Alle Nächte), Durchschnittswerte, **Hilfsmittel-Wirksamkeitsanalyse** (prozentuale Score-Reduktion gegenüber Nächten ohne Hilfsmittel) und **Faktoren-Einflussanalyse**.
  - **4. Hilfsmittel & Ratgeber:** Verwaltung vordefinierter und eigener Hilfsmittel (Seitenschlaf, Nasenpflaster, Nasenspray, Mundpflaster, Schnarchschiene, Keilkissen, Luftbefeuchter, CPAP u.v.m.) & Faktoren (Alkohol, Rückenschlaf, Erkältung, Übermüdung, Spätes Essen) inklusive integriertem Schnarch-Wissensratgeber.
  - **5. Profil & Einstellungen:** Mikrofon-Empfindlichkeitskalibrierung (`-8 dB` bis `+8 dB`), Aufnahmemodus-Wahl, Speicherplatz-Anzeige mit automatischem Cleanup (Favoriten ⭐ sind geschützt), lokales Schlafprofil mit BMI-Rechner, **CSV-Export** und **Demo-Daten-Generator (10 Beispiel-Nächte)**.
- 🌊 **Prozedurale Einschlafgeräusche (Sleep Sounds):**
  - Direkt in Echtzeit über `AudioTrack` synthetisierte Einschlafklänge (*Sanfter Nachtregen*, *Meereswellen*, *Warmes Rosa Rauschen*, *Nachtwind im Wald*, *Tiefer 110-Hz-Schlafklang*) mit sanftem Fade-Out vor Überwachungsbeginn.
- ⏰ **Verzögerter Start & Smart-Wecker:**
  - Frei wählbarer Einschlaftimer (Sofort bis 60 Min., jederzeit überspringbar) sowie optionaler Aufwach-Wecker mit Smart-Wakeup-Zeitfenster.
- 🔒 **100% Lokal & Privat (Kein Firebase / Keine Cloud):**
  - Sämtliche Daten liegen ausschließlich in der lokalen SQLite-Datenbank (`snore_rec.db`) und den lokalen `SharedPreferences`.

---

## 🛠️ Technologie-Stack

| Komponente | Technologie |
|---|---|
| **Programmiersprache** | [Kotlin](https://kotlinlang.org/) (2.2.x) |
| **UI-Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) mit Material 3 (SnoreLab Midnight Navy Theme) |
| **KI / Audio-Inferenz** | [Google AI Edge LiteRT](https://ai.google.dev/edge/litert) (v2.3.2) & YAMNet TFLite |
| **Audio-Pipeline** | Android `AudioRecord` (16 kHz Mono PCM), Custom `RingBuffer`, `AudioTrack` Sleep Sound Synthesizer |
| **Audio-Encoding** | Hardware-beschleunigtes `MediaCodec` + `MediaMuxer` (AAC / `.m4a`) & WAV-Fallback |
| **Hintergrunddienst** | Android Foreground Service (`FOREGROUND_SERVICE_MICROPHONE`) + Coroutines / Kotlin StateFlow |
| **Datenbank & Settings** | 100% lokale SQLite-Datenbank (`SQLiteOpenHelper` v2) & lokale `SharedPreferences` |

---

## 🚀 Bauen und Ausführen

### Schnellstart über Skript (Linux / macOS)

```bash
chmod +x build_and_install.sh
./build_and_install.sh
```

### Manuelles Bauen über Gradle

- **Debug-Build bauen:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Unit-Tests ausführen:**
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 📄 Lizenz

Ausschließlich für den privaten Gebrauch. Alle Rechte vorbehalten.
