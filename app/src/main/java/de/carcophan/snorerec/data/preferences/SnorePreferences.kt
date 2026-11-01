package de.carcophan.snorerec.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecordingMode(val label: String, val description: String) {
    ALL_SAMPLES("Alle Schnarch-Sequenzen", "Speichert jedes erkannte Schnarchereignis der Nacht (Standard)."),
    TOP_SAMPLES("Max. 30 Aufnahmen / Nacht", "Spart Speicherplatz bei sehr langen Schnarchnächten."),
    METRICS_ONLY("Nur Analyse (ohne Audio)", "Berechnet Score & Diagramm, speichert aber keine Audiodateien.")
}

enum class SleepSoundType(val id: String, val title: String, val iconEmoji: String, val subtitle: String) {
    NONE("none", "Aus", "🔇", "Kein Einschlafgeräusch"),
    RAIN("rain", "Sanfter Nachtregen", "🌧️", "Beruhigendes Regenprasseln"),
    WAVES("waves", "Meereswellen", "🌊", "Rhythmische Brandung am Strand"),
    PINK_NOISE("pink_noise", "Warmes Rauschen", "☁️", "Gleichmäßiges Rosa Rauschen zum Ausblenden von Geräuschen"),
    WIND("wind", "Nachtwind im Wald", "🍃", "Sanft ziehender Wind durch Baumkronen"),
    DEEP_DRONE("deep_drone", "Tiefer Schlafklang", "🌌", "Entspannende harmonische 110-Hz-Klangfläche");

    companion object {
        fun fromId(id: String): SleepSoundType = entries.find { it.id == id } ?: NONE
    }
}

data class SnoreSettingsState(
    val delayedStartMinutes: Int = 0,
    val sleepSoundType: SleepSoundType = SleepSoundType.NONE,
    val sleepSoundDurationMinutes: Int = 15,
    val sleepSoundVolume: Float = 0.5f,
    val alarmEnabled: Boolean = false,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val smartAlarmWindowMinutes: Int = 15,
    val alarmVibrate: Boolean = true,
    val selectedRemedies: Set<String> = emptySet(),
    val selectedFactors: Set<String> = emptySet(),
    val customRemedies: Set<String> = emptySet(),
    val customFactors: Set<String> = emptySet(),
    val sensitivityOffsetDb: Float = 0f, // -6f (more sensitive, 36dB) to +6f (less sensitive, 48dB)
    val recordingMode: RecordingMode = RecordingMode.ALL_SAMPLES,
    val retentionDays: Int = 14, // 7, 14, 30, 90, 365, -1 (unlimited)
    val snoreScoreGoal: Int = 25,
    val userAge: Int = 35,
    val userHeightCm: Int = 178,
    val userWeightKg: Float = 78f
) {
    val effectiveMinDbThreshold: Float
        get() = (42.0f + sensitivityOffsetDb).coerceIn(34.0f, 54.0f)

    val bmi: Float
        get() {
            val heightM = userHeightCm / 100f
            return if (heightM > 0.5f) userWeightKg / (heightM * heightM) else 0f
        }
}

class SnorePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("snorerec_snorelab_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SnoreSettingsState> = _state.asStateFlow()

    private fun loadState(): SnoreSettingsState {
        return SnoreSettingsState(
            delayedStartMinutes = prefs.getInt("delayed_start_min", 0),
            sleepSoundType = SleepSoundType.fromId(prefs.getString("sleep_sound_id", "none") ?: "none"),
            sleepSoundDurationMinutes = prefs.getInt("sleep_sound_dur_min", 15),
            sleepSoundVolume = prefs.getFloat("sleep_sound_vol", 0.5f),
            alarmEnabled = prefs.getBoolean("alarm_enabled", false),
            alarmHour = prefs.getInt("alarm_hour", 7),
            alarmMinute = prefs.getInt("alarm_minute", 0),
            smartAlarmWindowMinutes = prefs.getInt("smart_alarm_window", 15),
            alarmVibrate = prefs.getBoolean("alarm_vibrate", true),
            selectedRemedies = prefs.getStringSet("selected_remedies", emptySet())?.toSet() ?: emptySet(),
            selectedFactors = prefs.getStringSet("selected_factors", emptySet())?.toSet() ?: emptySet(),
            customRemedies = prefs.getStringSet("custom_remedies", emptySet())?.toSet() ?: emptySet(),
            customFactors = prefs.getStringSet("custom_factors", emptySet())?.toSet() ?: emptySet(),
            sensitivityOffsetDb = prefs.getFloat("sensitivity_offset_db", 0f),
            recordingMode = try {
                RecordingMode.valueOf(prefs.getString("recording_mode", RecordingMode.ALL_SAMPLES.name) ?: RecordingMode.ALL_SAMPLES.name)
            } catch (_: Exception) {
                RecordingMode.ALL_SAMPLES
            },
            retentionDays = prefs.getInt("retention_days", 14),
            snoreScoreGoal = prefs.getInt("snore_score_goal", 25),
            userAge = prefs.getInt("user_age", 35),
            userHeightCm = prefs.getInt("user_height_cm", 178),
            userWeightKg = prefs.getFloat("user_weight_kg", 78f)
        )
    }

    private fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = loadState()
    }

    fun setDelayedStartMinutes(minutes: Int) = update {
        putInt("delayed_start_min", minutes.coerceIn(0, 120))
    }

    fun setSleepSound(type: SleepSoundType, durationMinutes: Int = _state.value.sleepSoundDurationMinutes, volume: Float = _state.value.sleepSoundVolume) = update {
        putString("sleep_sound_id", type.id)
        putInt("sleep_sound_dur_min", durationMinutes)
        putFloat("sleep_sound_vol", volume.coerceIn(0.05f, 1.0f))
    }

    fun setAlarm(enabled: Boolean, hour: Int = _state.value.alarmHour, minute: Int = _state.value.alarmMinute, smartWindowMinutes: Int = _state.value.smartAlarmWindowMinutes, vibrate: Boolean = _state.value.alarmVibrate) = update {
        putBoolean("alarm_enabled", enabled)
        putInt("alarm_hour", hour.coerceIn(0, 23))
        putInt("alarm_minute", minute.coerceIn(0, 59))
        putInt("smart_alarm_window", smartWindowMinutes.coerceIn(0, 60))
        putBoolean("alarm_vibrate", vibrate)
    }

    fun toggleRemedyForTonight(remedyName: String) {
        val current = _state.value.selectedRemedies.toMutableSet()
        if (current.contains(remedyName)) current.remove(remedyName) else current.add(remedyName)
        update { putStringSet("selected_remedies", current) }
    }

    fun setRemediesForTonight(remedies: Set<String>) = update {
        putStringSet("selected_remedies", remedies)
    }

    fun toggleFactorForTonight(factorName: String) {
        val current = _state.value.selectedFactors.toMutableSet()
        if (current.contains(factorName)) current.remove(factorName) else current.add(factorName)
        update { putStringSet("selected_factors", current) }
    }

    fun setFactorsForTonight(factors: Set<String>) = update {
        putStringSet("selected_factors", factors)
    }

    fun addCustomRemedy(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val custom = _state.value.customRemedies.toMutableSet().apply { add(trimmed) }
        val selected = _state.value.selectedRemedies.toMutableSet().apply { add(trimmed) }
        update {
            putStringSet("custom_remedies", custom)
            putStringSet("selected_remedies", selected)
        }
    }

    fun removeCustomRemedy(name: String) {
        val custom = _state.value.customRemedies.toMutableSet().apply { remove(name) }
        val selected = _state.value.selectedRemedies.toMutableSet().apply { remove(name) }
        update {
            putStringSet("custom_remedies", custom)
            putStringSet("selected_remedies", selected)
        }
    }

    fun addCustomFactor(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val custom = _state.value.customFactors.toMutableSet().apply { add(trimmed) }
        val selected = _state.value.selectedFactors.toMutableSet().apply { add(trimmed) }
        update {
            putStringSet("custom_factors", custom)
            putStringSet("selected_factors", selected)
        }
    }

    fun removeCustomFactor(name: String) {
        val custom = _state.value.customFactors.toMutableSet().apply { remove(name) }
        val selected = _state.value.selectedFactors.toMutableSet().apply { remove(name) }
        update {
            putStringSet("custom_factors", custom)
            putStringSet("selected_factors", selected)
        }
    }

    fun setSensitivityOffsetDb(offsetDb: Float) = update {
        putFloat("sensitivity_offset_db", offsetDb.coerceIn(-8f, 8f))
    }

    fun setRecordingMode(mode: RecordingMode) = update {
        putString("recording_mode", mode.name)
    }

    fun setRetentionDays(days: Int) = update {
        putInt("retention_days", days)
    }

    fun setProfile(age: Int, heightCm: Int, weightKg: Float, scoreGoal: Int) = update {
        putInt("user_age", age.coerceIn(10, 110))
        putInt("user_height_cm", heightCm.coerceIn(100, 230))
        putFloat("user_weight_kg", weightKg.coerceIn(30f, 250f))
        putInt("snore_score_goal", scoreGoal.coerceIn(5, 100))
    }
}

