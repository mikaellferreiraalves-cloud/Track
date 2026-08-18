package com.example.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TrackerApp
import com.example.data.database.GeofenceArea
import com.example.data.database.LocationPoint
import com.example.data.model.CloudDevice
import com.example.data.model.CloudLocationPoint
import com.example.domain.model.TrackingMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SelectedDeviceInfo(
    val device: CloudDevice,
    val historyPoints: List<CloudLocationPoint> = emptyList(),
    val isShowingHistory: Boolean = false,
    val selectedTimeFilter: String = "TODAY" // "TODAY", "YESTERDAY", "WEEK", "ALL"
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrackerApp
    private val repo = app.trackingRepository
    private val syncManager = app.firestoreSyncManager

    val metrics: StateFlow<TrackingMetrics> = repo.currentMetrics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrackingMetrics()
        )

    private val _followUserLocation = MutableStateFlow(true)
    val followUserLocation: StateFlow<Boolean> = _followUserLocation.asStateFlow()

    fun toggleFollowUserLocation() {
        _followUserLocation.value = !_followUserLocation.value
    }

    fun setFollowUserLocation(follow: Boolean) {
        _followUserLocation.value = follow
    }

    // Active session points
    val activePoints: StateFlow<List<LocationPoint>> = metrics.flatMapLatest { m ->
        val sessionId = m.sessionId
        if (sessionId != null) {
            repo.getPointsForSessionFlow(sessionId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val geofences: StateFlow<List<GeofenceArea>> = repo.getAllGeofencesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Cloud & Authorized devices for multi-device map
    val cloudDevices: StateFlow<List<CloudDevice>> = combine(
        syncManager.myDevices,
        syncManager.sharedWithMeDevices
    ) { mine, shared ->
        val list = mutableListOf<CloudDevice>()
        list.addAll(mine)
        for (s in shared) {
            if (list.none { it.deviceId == s.deviceId }) {
                list.add(s)
            }
        }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedDevice = MutableStateFlow<SelectedDeviceInfo?>(null)
    val selectedDevice: StateFlow<SelectedDeviceInfo?> = _selectedDevice.asStateFlow()

    private val _focusedDeviceId = MutableStateFlow<String?>(null)
    val focusedDeviceId: StateFlow<String?> = _focusedDeviceId.asStateFlow()

    fun focusOnDevice(deviceId: String?) {
        _focusedDeviceId.value = deviceId
        if (deviceId != null) {
            _followUserLocation.value = false
            val dev = cloudDevices.value.find { it.deviceId == deviceId }
            if (dev != null) {
                selectDevice(dev)
            }
        } else {
            _selectedDevice.value = null
        }
    }

    fun selectDevice(device: CloudDevice) {
        _selectedDevice.value = SelectedDeviceInfo(device = device)
    }

    fun dismissSelectedDevice() {
        _selectedDevice.value = null
    }

    fun loadDeviceHistory(deviceId: String, timeFilter: String = "TODAY") {
        viewModelScope.launch {
            val points = syncManager.getDeviceLocationHistory(deviceId, timeFilter)
            val current = _selectedDevice.value
            if (current != null && current.device.deviceId == deviceId) {
                _selectedDevice.value = current.copy(
                    historyPoints = points,
                    isShowingHistory = true,
                    selectedTimeFilter = timeFilter
                )
            }
        }
    }
}
