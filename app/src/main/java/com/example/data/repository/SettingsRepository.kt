package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

enum class LocationIntervalMode {
    AUTOMATIC, // Adaptive GPS frequency
    HIGH_PRECISION, // Frequent 1s - 2s
    BATTERY_SAVING // 10s - 30s
}

enum class MinAccuracyLevel(val maxMeters: Float, val label: String) {
    HIGH(25.0f, "Alta (25m)"),
    MEDIUM(45.0f, "Média (45m)"),
    LOW(80.0f, "Baixa (80m)")
}

data class AppSettings(
    val intervalMode: LocationIntervalMode = LocationIntervalMode.AUTOMATIC,
    val accuracyLevel: MinAccuracyLevel = MinAccuracyLevel.MEDIUM,
    val saveHistory: Boolean = true,
    val patternDetectionEnabled: Boolean = true,
    val routePredictionEnabled: Boolean = true,
    val geofenceAlertsEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val INTERVAL_MODE = stringPreferencesKey("interval_mode")
        val ACCURACY_LEVEL = stringPreferencesKey("accuracy_level")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val PATTERN_DETECTION = booleanPreferencesKey("pattern_detection")
        val ROUTE_PREDICTION = booleanPreferencesKey("route_prediction")
        val GEOFENCE_ALERTS = booleanPreferencesKey("geofence_alerts")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val intervalModeStr = preferences[PreferencesKeys.INTERVAL_MODE] ?: LocationIntervalMode.AUTOMATIC.name
        val accuracyLevelStr = preferences[PreferencesKeys.ACCURACY_LEVEL] ?: MinAccuracyLevel.MEDIUM.name

        AppSettings(
            intervalMode = runCatching { LocationIntervalMode.valueOf(intervalModeStr) }.getOrDefault(LocationIntervalMode.AUTOMATIC),
            accuracyLevel = runCatching { MinAccuracyLevel.valueOf(accuracyLevelStr) }.getOrDefault(MinAccuracyLevel.MEDIUM),
            saveHistory = preferences[PreferencesKeys.SAVE_HISTORY] ?: true,
            patternDetectionEnabled = preferences[PreferencesKeys.PATTERN_DETECTION] ?: true,
            routePredictionEnabled = preferences[PreferencesKeys.ROUTE_PREDICTION] ?: true,
            geofenceAlertsEnabled = preferences[PreferencesKeys.GEOFENCE_ALERTS] ?: true
        )
    }

    suspend fun updateIntervalMode(mode: LocationIntervalMode) {
        context.dataStore.edit { it[PreferencesKeys.INTERVAL_MODE] = mode.name }
    }

    suspend fun updateAccuracyLevel(level: MinAccuracyLevel) {
        context.dataStore.edit { it[PreferencesKeys.ACCURACY_LEVEL] = level.name }
    }

    suspend fun updateSaveHistory(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SAVE_HISTORY] = enabled }
    }

    suspend fun updatePatternDetection(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.PATTERN_DETECTION] = enabled }
    }

    suspend fun updateRoutePrediction(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ROUTE_PREDICTION] = enabled }
    }

    suspend fun updateGeofenceAlerts(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.GEOFENCE_ALERTS] = enabled }
    }
}
