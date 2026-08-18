package com.example.data.repository

import com.example.data.database.GeofenceArea
import com.example.data.database.LocationPoint
import com.example.data.database.TrackingDao
import com.example.data.database.TrackingSession
import com.example.domain.model.TrackingMetrics
import com.example.domain.model.TrackingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface TrackingRepository {
    val currentMetrics: StateFlow<TrackingMetrics>
    fun updateMetrics(transform: (TrackingMetrics) -> TrackingMetrics)

    // DB Operations
    fun getAllSessions(): Flow<List<TrackingSession>>
    suspend fun getSessionById(sessionId: String): TrackingSession?
    fun getSessionByIdFlow(sessionId: String): Flow<TrackingSession?>
    suspend fun insertSession(session: TrackingSession)
    suspend fun updateSession(session: TrackingSession)
    suspend fun deleteSession(sessionId: String)
    suspend fun deleteAllHistory()

    fun getPointsForSessionFlow(sessionId: String): Flow<List<LocationPoint>>
    suspend fun getPointsForSession(sessionId: String): List<LocationPoint>
    suspend fun insertPoint(point: LocationPoint): Long
    fun getLatestPointFlow(): Flow<LocationPoint?>
    fun getTotalPointCountFlow(): Flow<Int>

    // Geofences
    fun getAllGeofencesFlow(): Flow<List<GeofenceArea>>
    suspend fun getActiveGeofences(): List<GeofenceArea>
    suspend fun insertGeofence(geofence: GeofenceArea): Long
    suspend fun updateGeofence(geofence: GeofenceArea)
    suspend fun deleteGeofence(id: Long)

    // Observed Devices (Multi-Device Remote Tracking)
    fun getAllObservedDevicesFlow(): Flow<List<com.example.data.database.ObservedDevice>>
    suspend fun getAllObservedDevices(): List<com.example.data.database.ObservedDevice>
    suspend fun getObservedDeviceById(deviceId: String): com.example.data.database.ObservedDevice?
    fun getObservedDeviceByIdFlow(deviceId: String): Flow<com.example.data.database.ObservedDevice?>
    suspend fun insertObservedDevice(device: com.example.data.database.ObservedDevice)
    suspend fun insertObservedDevices(devices: List<com.example.data.database.ObservedDevice>)
    suspend fun updateObservedDevice(device: com.example.data.database.ObservedDevice)
    suspend fun deleteObservedDevice(deviceId: String)
    suspend fun deleteAllObservedDevices()

    // Remote Device History Logs
    fun getDeviceLocationLogsFlow(deviceId: String): Flow<List<com.example.data.database.DeviceLocationLog>>
    suspend fun getDeviceLocationLogs(deviceId: String): List<com.example.data.database.DeviceLocationLog>
    suspend fun insertDeviceLocationLog(log: com.example.data.database.DeviceLocationLog): Long
    suspend fun deleteDeviceLocationLogs(deviceId: String)
}

class TrackingRepositoryImpl(
    private val trackingDao: TrackingDao
) : TrackingRepository {

    private val _currentMetrics = MutableStateFlow(TrackingMetrics())
    override val currentMetrics: StateFlow<TrackingMetrics> = _currentMetrics.asStateFlow()

    override fun updateMetrics(transform: (TrackingMetrics) -> TrackingMetrics) {
        _currentMetrics.value = transform(_currentMetrics.value)
    }

    override fun getAllSessions(): Flow<List<TrackingSession>> = trackingDao.getAllSessionsFlow()

    override suspend fun getSessionById(sessionId: String): TrackingSession? =
        trackingDao.getSessionById(sessionId)

    override fun getSessionByIdFlow(sessionId: String): Flow<TrackingSession?> =
        trackingDao.getSessionByIdFlow(sessionId)

    override suspend fun insertSession(session: TrackingSession) =
        trackingDao.insertSession(session)

    override suspend fun updateSession(session: TrackingSession) =
        trackingDao.updateSession(session)

    override suspend fun deleteSession(sessionId: String) {
        trackingDao.deletePointsForSession(sessionId)
        trackingDao.deleteSessionById(sessionId)
    }

    override suspend fun deleteAllHistory() {
        trackingDao.deleteAllLocationPoints()
        trackingDao.deleteAllSessions()
    }

    override fun getPointsForSessionFlow(sessionId: String): Flow<List<LocationPoint>> =
        trackingDao.getPointsForSessionFlow(sessionId)

    override suspend fun getPointsForSession(sessionId: String): List<LocationPoint> =
        trackingDao.getPointsForSession(sessionId)

    override suspend fun insertPoint(point: LocationPoint): Long =
        trackingDao.insertLocationPoint(point)

    override fun getLatestPointFlow(): Flow<LocationPoint?> =
        trackingDao.getLatestPointFlow()

    override fun getTotalPointCountFlow(): Flow<Int> =
        trackingDao.getTotalPointCountFlow()

    override fun getAllGeofencesFlow(): Flow<List<GeofenceArea>> =
        trackingDao.getAllGeofencesFlow()

    override suspend fun getActiveGeofences(): List<GeofenceArea> =
        trackingDao.getActiveGeofences()

    override suspend fun insertGeofence(geofence: GeofenceArea): Long =
        trackingDao.insertGeofence(geofence)

    override suspend fun updateGeofence(geofence: GeofenceArea) =
        trackingDao.updateGeofence(geofence)

    override suspend fun deleteGeofence(id: Long) =
        trackingDao.deleteGeofenceById(id)

    // Observed Devices Implementation
    override fun getAllObservedDevicesFlow(): Flow<List<com.example.data.database.ObservedDevice>> =
        trackingDao.getAllObservedDevicesFlow()

    override suspend fun getAllObservedDevices(): List<com.example.data.database.ObservedDevice> =
        trackingDao.getAllObservedDevices()

    override suspend fun getObservedDeviceById(deviceId: String): com.example.data.database.ObservedDevice? =
        trackingDao.getObservedDeviceById(deviceId)

    override fun getObservedDeviceByIdFlow(deviceId: String): Flow<com.example.data.database.ObservedDevice?> =
        trackingDao.getObservedDeviceByIdFlow(deviceId)

    override suspend fun insertObservedDevice(device: com.example.data.database.ObservedDevice) =
        trackingDao.insertObservedDevice(device)

    override suspend fun insertObservedDevices(devices: List<com.example.data.database.ObservedDevice>) =
        trackingDao.insertObservedDevices(devices)

    override suspend fun updateObservedDevice(device: com.example.data.database.ObservedDevice) =
        trackingDao.updateObservedDevice(device)

    override suspend fun deleteObservedDevice(deviceId: String) {
        trackingDao.deleteDeviceLocationLogs(deviceId)
        trackingDao.deleteObservedDeviceById(deviceId)
    }

    override suspend fun deleteAllObservedDevices() =
        trackingDao.deleteAllObservedDevices()

    override fun getDeviceLocationLogsFlow(deviceId: String): Flow<List<com.example.data.database.DeviceLocationLog>> =
        trackingDao.getDeviceLocationLogsFlow(deviceId)

    override suspend fun getDeviceLocationLogs(deviceId: String): List<com.example.data.database.DeviceLocationLog> =
        trackingDao.getDeviceLocationLogs(deviceId)

    override suspend fun insertDeviceLocationLog(log: com.example.data.database.DeviceLocationLog): Long =
        trackingDao.insertDeviceLocationLog(log)

    override suspend fun deleteDeviceLocationLogs(deviceId: String) =
        trackingDao.deleteDeviceLocationLogs(deviceId)
}
