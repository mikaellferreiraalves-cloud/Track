package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TrackerApp
import com.example.data.repository.AppSettings
import com.example.data.repository.LocationIntervalMode
import com.example.data.repository.MinAccuracyLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrackerApp
    private val settingsRepo = app.settingsRepository
    private val trackingRepo = app.trackingRepository

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun setIntervalMode(mode: LocationIntervalMode) {
        viewModelScope.launch {
            settingsRepo.updateIntervalMode(mode)
        }
    }

    fun setAccuracyLevel(level: MinAccuracyLevel) {
        viewModelScope.launch {
            settingsRepo.updateAccuracyLevel(level)
        }
    }

    fun setSaveHistory(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSaveHistory(enabled)
        }
    }

    fun setPatternDetection(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updatePatternDetection(enabled)
        }
    }

    fun setRoutePrediction(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateRoutePrediction(enabled)
        }
    }

    fun setGeofenceAlerts(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateGeofenceAlerts(enabled)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            trackingRepo.deleteAllHistory()
        }
    }
}
