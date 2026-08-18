package com.example.ui.geofence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TrackerApp
import com.example.data.database.GeofenceArea
import com.example.domain.model.TrackingMetrics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GeofenceViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrackerApp
    private val repo = app.trackingRepository

    val geofences: StateFlow<List<GeofenceArea>> = repo.getAllGeofencesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val metrics: StateFlow<TrackingMetrics> = repo.currentMetrics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrackingMetrics()
        )

    fun addGeofence(name: String, lat: Double, lon: Double, radius: Double) {
        viewModelScope.launch {
            repo.insertGeofence(
                GeofenceArea(
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    radiusMeters = radius,
                    isEnabled = true
                )
            )
        }
    }

    fun toggleGeofence(geofence: GeofenceArea) {
        viewModelScope.launch {
            repo.updateGeofence(geofence.copy(isEnabled = !geofence.isEnabled))
        }
    }

    fun deleteGeofence(id: Long) {
        viewModelScope.launch {
            repo.deleteGeofence(id)
        }
    }
}
