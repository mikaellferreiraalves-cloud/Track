package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDao {

    // --- Location Points ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationPoint(point: LocationPoint): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationPoints(points: List<LocationPoint>)

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getPointsForSessionFlow(sessionId: String): Flow<List<LocationPoint>>

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPointsForSession(sessionId: String): List<LocationPoint>

    @Query("SELECT * FROM location_points WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getPointsByDateRange(startTime: Long, endTime: Long): List<LocationPoint>

    @Query("SELECT * FROM location_points ORDER BY timestamp DESC LIMIT 1")
    fun getLatestPointFlow(): Flow<LocationPoint?>

    @Query("SELECT * FROM location_points ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPoint(): LocationPoint?

    @Query("SELECT COUNT(*) FROM location_points WHERE sessionId = :sessionId")
    suspend fun getPointCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM location_points")
    fun getTotalPointCountFlow(): Flow<Int>

    // --- Tracking Sessions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TrackingSession)

    @Update
    suspend fun updateSession(session: TrackingSession)

    @Query("SELECT * FROM tracking_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): TrackingSession?

    @Query("SELECT * FROM tracking_sessions WHERE id = :sessionId")
    fun getSessionByIdFlow(sessionId: String): Flow<TrackingSession?>

    @Query("SELECT * FROM tracking_sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<TrackingSession>>

    @Query("SELECT * FROM tracking_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<TrackingSession>

    @Query("DELETE FROM tracking_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM location_points WHERE sessionId = :sessionId")
    suspend fun deletePointsForSession(sessionId: String)

    @Query("DELETE FROM tracking_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM location_points")
    suspend fun deleteAllLocationPoints()

    // --- Geofence Areas ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeofence(geofence: GeofenceArea): Long

    @Update
    suspend fun updateGeofence(geofence: GeofenceArea)

    @Query("SELECT * FROM geofence_areas ORDER BY id DESC")
    fun getAllGeofencesFlow(): Flow<List<GeofenceArea>>

    @Query("SELECT * FROM geofence_areas WHERE isEnabled = 1")
    suspend fun getActiveGeofences(): List<GeofenceArea>

    @Query("DELETE FROM geofence_areas WHERE id = :id")
    suspend fun deleteGeofenceById(id: Long)

    // --- Observed Devices (Multi-Device Fleet / Family) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservedDevice(device: ObservedDevice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservedDevices(devices: List<ObservedDevice>)

    @Update
    suspend fun updateObservedDevice(device: ObservedDevice)

    @Query("SELECT * FROM observed_devices ORDER BY lastUpdated DESC")
    fun getAllObservedDevicesFlow(): Flow<List<ObservedDevice>>

    @Query("SELECT * FROM observed_devices ORDER BY lastUpdated DESC")
    suspend fun getAllObservedDevices(): List<ObservedDevice>

    @Query("SELECT * FROM observed_devices WHERE deviceId = :deviceId")
    suspend fun getObservedDeviceById(deviceId: String): ObservedDevice?

    @Query("SELECT * FROM observed_devices WHERE deviceId = :deviceId")
    fun getObservedDeviceByIdFlow(deviceId: String): Flow<ObservedDevice?>

    @Query("DELETE FROM observed_devices WHERE deviceId = :deviceId")
    suspend fun deleteObservedDeviceById(deviceId: String)

    @Query("DELETE FROM observed_devices")
    suspend fun deleteAllObservedDevices()

    // --- Device Location Logs (Remote Path History) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceLocationLog(log: DeviceLocationLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceLocationLogs(logs: List<DeviceLocationLog>)

    @Query("SELECT * FROM device_location_logs WHERE deviceId = :deviceId ORDER BY timestamp ASC")
    fun getDeviceLocationLogsFlow(deviceId: String): Flow<List<DeviceLocationLog>>

    @Query("SELECT * FROM device_location_logs WHERE deviceId = :deviceId ORDER BY timestamp ASC")
    suspend fun getDeviceLocationLogs(deviceId: String): List<DeviceLocationLog>

    @Query("DELETE FROM device_location_logs WHERE deviceId = :deviceId")
    suspend fun deleteDeviceLocationLogs(deviceId: String)
}
