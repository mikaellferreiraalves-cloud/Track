package com.example.ui.home

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TrackerApp
import com.example.domain.model.TrackingMetrics
import com.example.domain.model.TrackingState
import com.example.service.LocationTrackingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrackerApp
    private val repository = app.trackingRepository

    val metrics: StateFlow<TrackingMetrics> = repository.currentMetrics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrackingMetrics()
        )

    fun startTracking() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun pauseTracking() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeTracking() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopTracking() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        context.startService(intent)
    }
}
