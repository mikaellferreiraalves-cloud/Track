package com.example.ui.history

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TrackerApp
import com.example.data.database.LocationPoint
import com.example.data.database.TrackingSession
import com.example.data.export.RouteExporter
import com.example.data.model.CloudDevice
import com.example.data.model.CloudLocationPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrackerApp
    private val repo = app.trackingRepository
    private val syncManager = app.firestoreSyncManager

    val sessions: StateFlow<List<TrackingSession>> = repo.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val authorizedDevices: StateFlow<List<CloudDevice>> = combine(
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Remote history state
    private val _selectedRemoteDeviceId = MutableStateFlow<String?>(null)
    val selectedRemoteDeviceId: StateFlow<String?> = _selectedRemoteDeviceId.asStateFlow()

    private val _selectedTimeFilter = MutableStateFlow("TODAY") // "TODAY", "YESTERDAY", "WEEK", "ALL"
    val selectedTimeFilter: StateFlow<String> = _selectedTimeFilter.asStateFlow()

    private val _remotePoints = MutableStateFlow<List<CloudLocationPoint>>(emptyList())
    val remotePoints: StateFlow<List<CloudLocationPoint>> = _remotePoints.asStateFlow()

    private val _isLoadingRemote = MutableStateFlow(false)
    val isLoadingRemote: StateFlow<Boolean> = _isLoadingRemote.asStateFlow()

    private val _selectedSessionPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val selectedSessionPoints: StateFlow<List<LocationPoint>> = _selectedSessionPoints.asStateFlow()

    private val _selectedSession = MutableStateFlow<TrackingSession?>(null)
    val selectedSession: StateFlow<TrackingSession?> = _selectedSession.asStateFlow()

    fun selectRemoteDevice(deviceId: String?) {
        _selectedRemoteDeviceId.value = deviceId
        if (deviceId != null) {
            loadRemoteHistory(deviceId, _selectedTimeFilter.value)
        } else {
            _remotePoints.value = emptyList()
        }
    }

    fun setTimeFilter(filter: String) {
        _selectedTimeFilter.value = filter
        val devId = _selectedRemoteDeviceId.value
        if (devId != null) {
            loadRemoteHistory(devId, filter)
        }
    }

    fun loadRemoteHistory(deviceId: String, filter: String) {
        viewModelScope.launch {
            _isLoadingRemote.value = true
            val points = syncManager.getDeviceLocationHistory(deviceId, filter)
            _remotePoints.value = points
            _isLoadingRemote.value = false
        }
    }

    fun loadSessionDetails(sessionId: String) {
        viewModelScope.launch {
            _selectedSession.value = repo.getSessionById(sessionId)
            _selectedSessionPoints.value = repo.getPointsForSession(sessionId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repo.deleteSession(sessionId)
        }
    }

    fun exportRoute(context: Context, session: TrackingSession, isKml: Boolean) {
        viewModelScope.launch {
            val points = repo.getPointsForSession(session.id)
            RouteExporter.shareRouteFile(context, session, points, isKml)
        }
    }
}
