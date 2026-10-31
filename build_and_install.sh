#!/usr/bin/env bash
set -euo pipefail

# In das Projektverzeichnis wechseln
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo "==> 1. ADB lokalisieren..."
ADB_BIN="adb"
if ! command -v "$ADB_BIN" &> /dev/null; then
    # Fallback: Aus local.properties lesen
    if [ -f "local.properties" ]; then
        SDK_DIR=$(grep "^sdk.dir=" local.properties | cut -d'=' -f2 | tr -d '\r')
        if [ -n "$SDK_DIR" ] && [ -x "$SDK_DIR/platform-tools/adb" ]; then
            ADB_BIN="$SDK_DIR/platform-tools/adb"
        fi
    fi
fi

if ! command -v "$ADB_BIN" &> /dev/null && [ ! -x "$ADB_BIN" ]; then
    # Weiterer Fallback auf Standardpfade
    if [ -x "$HOME/Android/Sdk/platform-tools/adb" ]; then
        ADB_BIN="$HOME/Android/Sdk/platform-tools/adb"
    else
        echo "FEHLER: 'adb' wurde nicht im PATH oder Android SDK gefunden." >&2
        exit 1
    fi
fi

echo "Verwende ADB: $ADB_BIN"

# Prüfen, ob ein Gerät verbunden ist
echo "==> 2. Prüfe verbundene Geräte..."
DEVICE_COUNT=$("$ADB_BIN" devices | grep -v "List of devices attached" | grep -v "^$" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "WARNUNG: Kein Android-Gerät per ADB verbunden!"
    echo "Bitte verbinde dein Gerät mit aktiviertem USB-Debugging."
    read -p "Möchtest du trotzdem nur bauen? (j/N): " -r
    if [[ ! $REPLY =~ ^[jJyY]$ ]]; then
        exit 1
    fi
fi

echo "==> 3. Release APK bauen..."
./gradlew assembleRelease

APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "FEHLER: APK wurde nicht gefunden unter $APK_PATH" >&2
    exit 1
fi

if [ "$DEVICE_COUNT" -gt 0 ]; then
    echo "==> 4. Installiere APK auf Gerät ($APK_PATH)..."
    "$ADB_BIN" install -r -d "$APK_PATH"

    echo "==> 5. Starte App..."
    "$ADB_BIN" shell am start -n de.carcophan.snorerec/.MainActivity

    echo "==> Fertig! Release-App wurde gebaut, installiert und gestartet."
else
    echo "==> Fertig! Release-APK erfolgreich gebaut: $APK_PATH"
fi
