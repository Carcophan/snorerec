# SnoreRec 🌙💤

**SnoreRec** ist eine intelligente, datenschutzorientierte Android-App zur nächtlichen Schnarchüberwachung, Schlafanalyse und automatischen Audioaufzeichnung von Schnarchereignissen.

Dank **On-Device Machine Learning (Google AI Edge LiteRT & YAMNet)** und Pegelanalyse erkennt die App Schnarchgeräusche zuverlässig in Echtzeit, speichert sie platzsparend als AAC-Audiodateien ab und stellt detaillierte Auswertungen für jede Nacht bereit – **100% lokal, ohne Cloud-Übertragung**.

---

## ✨ Hauptfunktionen

- 🎙️ **Intelligente Schnarcherkennung:**
  - **Zweistufige Erkennung:** Kombination aus schneller Dezibel- & Frequenzband-Analyse (80–700 Hz) sowie On-Device KI-Klassifizierung mit **YAMNet** (via Google AI Edge LiteRT).
  - Filtert Stör- und Umgebungsgeräusche zuverlässig heraus, um Fehlalarme zu minimieren.
- ⏱️ **Pre-Roll Ringpuffer (2,5 s):**
  - Ein kontinuierlicher Ringpuffer im Arbeitsspeicher garantiert, dass auch der Beginn eines Schnarchers vollständig aufgenommen wird.
- 💾 **Platzsparende AAC-Codierung:**
  - Direkte Hardware-Kompression über Android `MediaCodec` in `.m4a` (AAC) spart ca. 85–90% Speicherplatz gegenüber unkomprimiertem WAV.
- 📊 **Schlafanalyse & Mustererkennung:**
  - **Interaktiver Timeline-Chart:** Schnarchereignisse über den Verlauf der gesamten Nacht mit Pegel- und Zeitpunkten. Durch Antippen kann die zugehörige Aufnahme direkt abgespielt werden.
  - **Detaillierte Metriken:** Gesamte Schnarchdauer, prozentualer Schnarchanteil an der Schlafzeit, Schnarchfrequenz pro Stunde und Spitzen-/Durchschnittslautstärke in dB.
  - **Intensitäts-Einteilung:** Kategorisierung in *Leicht* (< 48 dB), *Mäßig* (48–62 dB) und *Laut* (≥ 62 dB) sowie stündliche Balkendiagramme.
- 🎧 **Integrierter Audio-Player:**
  - Schnarchereignisse anhören mit Zeitanzeige und Fortschrittsbalken.
- 🔋 **Zuverlässige Nachtüberwachung:**
  - Läuft als stabiler Foreground Service (`FOREGROUND_SERVICE_MICROPHONE`) mit partieller WakeLock-Absicherung, um auch bei gesperrtem Bildschirm die ganze Nacht aktiv zu bleiben.
- 🔒 **100% Privatsphäre & Datenschutz:**
  - Sämtliche Audioverarbeitung und KI-Inferenz läuft lokal auf dem Smartphone. Keine Sprach- oder Tondaten werden an externe Server übertragen.
- 🧹 **Automatischer Speicher-Cleanup:**
  - Automatische Bereinigung alter Audioaufnahmen (standardmäßig nach 14 Tagen), um Gerätespeicher zu schonen.

---

## 🛠️ Technologie-Stack

| Komponente | Technologie |
|---|---|
| **Programmiersprache** | [Kotlin](https://kotlinlang.org/) (2.2.x) |
| **UI-Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) mit Material 3 |
| **KI / Audio-Inferenz** | [Google AI Edge LiteRT](https://ai.google.dev/edge/litert) (v2.3.2) & YAMNet TFLite |
| **Audio-Pipeline** | Android `AudioRecord` (16 kHz Mono PCM), Custom `RingBuffer`, Low-Pass Filter |
| **Audio-Encoding** | Hardware-beschleunigtes `MediaCodec` + `MediaMuxer` (AAC / .m4a) & WAV-Fallback |
| **Hintergrunddienst** | Android Foreground Service + Coroutines / Kotlin Flow |
| **Datenbank** | Lokale SQLite-Datenbank (`SQLiteOpenHelper`) für Sessions und Events |
| **Build-System** | Gradle 9.x, Android Gradle Plugin (AGP) 9.5.0 |
| **Zielplattform** | `minSdk` 26 (Android 8.0) bis `targetSdk` 36 / `compileSdk` 37 |

---

## 📁 Projektstruktur

```
SnoreRec/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   ├── yamnet.tflite                # Pre-trained AudioNet Modell
│   │   │   │   └── yamnet_label_list.txt        # Klassen-Labels (u.a. Snoring, Snort)
│   │   │   ├── java/de/carcophan/snorerec/
│   │   │   │   ├── audio/
│   │   │   │   │   ├── AacAudioWriter.kt        # AAC (.m4a) Hardware-Encoder
│   │   │   │   │   ├── AudioConfig.kt           # Samplerate, Schwellenwerte, Puffergrößen
│   │   │   │   │   ├── AudioPlayer.kt           # MediaPlayer für Wiedergabe
│   │   │   │   │   ├── RingBuffer.kt            # Thread-sicherer Ringspeicher für Pre-Roll
│   │   │   │   │   ├── SnoreDetector.kt         # Zustandsmaschine, RMS/dB & Trigger
│   │   │   │   │   ├── WavAudioWriter.kt        # WAV-Dateischreiber (Fallback/Debug)
│   │   │   │   │   └── YamnetClassifier.kt      # LiteRT On-Device Inferenz
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/SnoreDatabase.kt      # SQLite Tabellen für Sessions & Events
│   │   │   │   │   ├── model/                   # NightSession, SnoreEvent, SnoreIntensity
│   │   │   │   │   └── repository/              # SnoreRepository
│   │   │   │   ├── service/
│   │   │   │   │   └── SnoreMonitoringService.kt# Foreground Service für Nachtüberwachung
│   │   │   │   └── ui/
│   │   │   │       ├── components/              # LiveMonitoringCard, TimelineChart, PatternView
│   │   │   │       ├── MainViewModel.kt         # StateFlows, Berechnungen, Steuerung
│   │   │   │       ├── SnoreAppScreen.kt        # Haupt-Screen mit Tabs & Steuerung
│   │   │   │       └── theme/                   # Material 3 Farbpalette & Typografie
│   │   │   └── AndroidManifest.xml
│   │   └── test/                                # Unit-Tests für RingBuffer, Math & Modelle
│   └── build.gradle.kts
├── build_and_install.sh                         # Schnelles Build- & Installationsskript
└── gradle/libs.versions.toml                    # Version Catalog
```

---

## 🚀 Bauen und Ausführen

### Voraussetzungen

- **Android Studio** (Ladybug / Meerkat oder neuer empfohlen)
- **JDK 11** oder höher
- Android-Gerät oder Emulator mit mindestens **Android 8.0 (API Level 26)**
- Für Tests auf physischem Gerät: Aktiviertes **USB-Debugging**

### Schnellstart über Skript (Linux / macOS)

Das Projekt enthält ein bequemes Skript zum automatischen Kompilieren, Installieren und Starten auf einem angeschlossenen Android-Gerät:

```bash
chmod +x build_and_install.sh
./build_and_install.sh
```

### Manuelles Bauen über Gradle

- **Debug-Build bauen:**
  ```bash
  ./gradlew assembleDebug
  ```

- **Release-Build bauen:**
  ```bash
  ./gradlew assembleRelease
  ```

- **Unit-Tests ausführen:**
  ```bash
  ./gradlew test
  ```

---

## 🔐 Benötigte Berechtigungen

Die App benötigt folgende Android-Berechtigungen:

| Berechtigung | Zweck |
|---|---|
| `RECORD_AUDIO` | Zugriff auf das Mikrofon zur Erfassung der Umgebungsgeräusche während der Schlafphase. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MICROPHONE` | Ermöglicht die kontinuierliche Audioüberwachung im Hintergrund während der Nacht. |
| `POST_NOTIFICATIONS` | Anzeige einer persistenten Benachrichtigung mit aktuellem Status und Schnarch-Zähler. |
| `WAKE_LOCK` | Verhindert, dass das Betriebssystem die Audioverarbeitung im Standby/Deep-Sleep pausiert. |

---

## 📈 Erkennungsparameter & Tuning

Zentrale Schwellenwerte können in [`AudioConfig.kt`](app/src/main/java/de/carcophan/snorerec/audio/AudioConfig.kt) angepasst werden:

```kotlin
const val SAMPLE_RATE = 16000                // 16 kHz für YAMNet-Kompatibilität
const val PRE_ROLL_SECONDS = 2.5f            // Puffergröße vor erkanntem Schnarchen
const val SNORE_MIN_DB = 42.0f               // Mindestpegel für Detektion
const val SNORE_MIN_DURATION_MS = 600L       // Minimale Dauer eines Schnarchers
const val SNORE_POST_SILENCE_MS = 1200L      // Nachlaufzeit vor Abschluss des Clips
const val SNORE_MAX_EVENT_DURATION_MS = 15000L // Maximale Cliplänge (15 Sekunden)
const val YAMNET_SNORE_THRESHOLD = 0.15f     // Konfidenz-Schwelle des ML-Modells
```

---

## 📄 Lizenz

Dieses Projekt ist privat / lizenziert nach Absprache. Alle Rechte vorbehalten.
