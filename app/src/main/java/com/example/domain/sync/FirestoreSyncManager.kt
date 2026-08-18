package com.example.domain.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.database.LocationPoint
import com.example.data.model.CloudDevice
import com.example.data.model.CloudLocationPoint
import com.example.data.model.DeviceAccess
import com.example.data.model.DeviceInvite
import com.example.data.repository.TrackingRepository
import com.example.domain.auth.AuthManager
import com.example.domain.device.DeviceStatusRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

class FirestoreSyncManager(
    private val context: Context,
    private val authManager: AuthManager,
    private val trackingRepository: TrackingRepository,
    private val deviceStatusRepository: DeviceStatusRepository = DeviceStatusRepository(context)
) {
    private val prefs = context.getSharedPreferences("device_cloud_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w("FirestoreSyncManager", "Firestore not available: ${e.message}")
            null
        }
    }

    // Local Device Info
    private val _localDeviceId = MutableStateFlow(getOrCreateDeviceId())
    val localDeviceId: StateFlow<String> = _localDeviceId.asStateFlow()

    private val _localDeviceName = MutableStateFlow(getSavedDeviceName())
    val localDeviceName: StateFlow<String> = _localDeviceName.asStateFlow()

    private val _sharingEnabled = MutableStateFlow(prefs.getBoolean("sharing_enabled", false))
    val sharingEnabled: StateFlow<Boolean> = _sharingEnabled.asStateFlow()

    // Remote lists
    private val _myDevices = MutableStateFlow<List<CloudDevice>>(emptyList())
    val myDevices: StateFlow<List<CloudDevice>> = _myDevices.asStateFlow()

    private val _sharedWithMeDevices = MutableStateFlow<List<CloudDevice>>(emptyList())
    val sharedWithMeDevices: StateFlow<List<CloudDevice>> = _sharedWithMeDevices.asStateFlow()

    private val _activeViewers = MutableStateFlow<List<DeviceAccess>>(emptyList())
    val activeViewers: StateFlow<List<DeviceAccess>> = _activeViewers.asStateFlow()

    private val _pendingInvites = MutableStateFlow<List<DeviceInvite>>(emptyList())
    val pendingInvites: StateFlow<List<DeviceInvite>> = _pendingInvites.asStateFlow()

    private val _pendingOfflinePointsCount = MutableStateFlow(0)
    val pendingOfflinePointsCount: StateFlow<Int> = _pendingOfflinePointsCount.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var myDevicesListener: ListenerRegistration? = null
    private var sharedDevicesListener: ListenerRegistration? = null
    private var viewersListener: ListenerRegistration? = null

    // Cache latest location for status updates
    private var lastKnownLat: Double = 0.0
    private var lastKnownLon: Double = 0.0
    private var lastKnownSpeed: Float = 0f
    private var lastKnownAlt: Double = 0.0

    init {
        // Observe auth user to trigger listeners
        scope.launch {
            authManager.currentUser.collect { user ->
                if (user != null) {
                    registerLocalDeviceInCloud()
                    attachCloudListeners(user.userId, user.email)
                } else {
                    detachCloudListeners()
                }
            }
        }

        // Observe battery & network status changes to sync automatically without waking the device aggressively
        scope.launch {
            deviceStatusRepository.deviceState.collect {
                if (_sharingEnabled.value && authManager.currentUser.value != null) {
                    registerLocalDeviceInCloud(
                        latitude = if (lastKnownLat != 0.0) lastKnownLat else null,
                        longitude = if (lastKnownLon != 0.0) lastKnownLon else null,
                        speedKmh = lastKnownSpeed,
                        altitude = lastKnownAlt
                    )
                }
            }
        }

        // Periodic heartbeat & offline sync check
        scope.launch {
            while (isActive) {
                delay(30000)
                if (_sharingEnabled.value && authManager.currentUser.value != null) {
                    registerLocalDeviceInCloud(
                        latitude = if (lastKnownLat != 0.0) lastKnownLat else null,
                        longitude = if (lastKnownLon != 0.0) lastKnownLon else null,
                        speedKmh = lastKnownSpeed,
                        altitude = lastKnownAlt
                    )
                }
            }
        }
    }

    private fun getOrCreateDeviceId(): String {
        var id = prefs.getString("unique_device_id", null)
        if (id == null) {
            id = "DEV-" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
            prefs.edit().putString("unique_device_id", id).apply()
        }
        return id
    }

    private fun getSavedDeviceName(): String {
        val saved = prefs.getString("custom_device_name", null)
        if (saved != null) return saved
        val defaultName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        prefs.edit().putString("custom_device_name", defaultName).apply()
        return defaultName
    }

    fun updateDeviceName(newName: String) {
        val clean = newName.trim()
        if (clean.isNotBlank()) {
            prefs.edit().putString("custom_device_name", clean).apply()
            _localDeviceName.value = clean
            registerLocalDeviceInCloud()
        }
    }

    fun setSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sharing_enabled", enabled).apply()
        _sharingEnabled.value = enabled
        registerLocalDeviceInCloud()
    }

    fun registerLocalDeviceInCloud(
        latitude: Double? = null,
        longitude: Double? = null,
        speedKmh: Float? = null,
        altitude: Double? = null
    ) {
        if (latitude != null && longitude != null) {
            lastKnownLat = latitude
            lastKnownLon = longitude
            lastKnownSpeed = speedKmh ?: 0f
            lastKnownAlt = altitude ?: 0.0
        }

        val user = authManager.currentUser.value ?: return
        val db = firestore ?: return
        val hardwareState = deviceStatusRepository.getLatestHardwareState()

        scope.launch {
            try {
                val currentDev = CloudDevice(
                    deviceId = _localDeviceId.value,
                    ownerId = user.userId,
                    ownerEmail = user.email,
                    name = _localDeviceName.value,
                    platform = "Android",
                    model = "${Build.MANUFACTURER} ${Build.MODEL}",
                    colorHex = "#00D2FF",
                    iconType = "PHONE",
                    sharingEnabled = _sharingEnabled.value,
                    isOnline = _sharingEnabled.value,
                    // Battery telemetry
                    batteryPercent = hardwareState.batteryPercent,
                    batteryLevel = hardwareState.batteryPercent,
                    isCharging = hardwareState.isCharging,
                    batteryStatus = hardwareState.batteryStatus,
                    // Wi-Fi telemetry
                    wifiConnected = hardwareState.isWifiConnected,
                    wifiSsid = hardwareState.wifiSsid,
                    // Timestamps & Status
                    deviceStatus = if (_sharingEnabled.value) "ONLINE" else "OFFLINE",
                    lastSeen = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    lastLatitude = latitude ?: lastKnownLat,
                    lastLongitude = longitude ?: lastKnownLon,
                    lastSpeedKmh = speedKmh ?: lastKnownSpeed,
                    lastAltitude = altitude ?: lastKnownAlt
                )

                db.collection("devices")
                    .document(_localDeviceId.value)
                    .set(currentDev, SetOptions.merge())
                    .await()

                // Also update local list
                val list = _myDevices.value.toMutableList()
                val idx = list.indexOfFirst { it.deviceId == currentDev.deviceId }
                if (idx >= 0) list[idx] = currentDev else list.add(0, currentDev)
                _myDevices.value = list
            } catch (e: Throwable) {
                Log.w("FirestoreSyncManager", "Failed to update device status in cloud: ${e.message}")
            }
        }
    }

    suspend fun uploadLocationPoint(point: LocationPoint): Boolean {
        if (!_sharingEnabled.value) return false
        val user = authManager.currentUser.value ?: return false
        val db = firestore ?: return false

        return try {
            val pointId = "pt_${point.timestamp}_${Random.nextInt(1000)}"
            val cloudPoint = CloudLocationPoint(
                id = pointId,
                deviceId = _localDeviceId.value,
                timestamp = point.timestamp,
                latitude = point.latitude,
                longitude = point.longitude,
                accuracy = point.accuracy ?: 0f,
                speed = point.speed ?: 0f,
                bearing = point.bearing ?: 0f,
                altitude = point.altitude ?: 0.0
            )

            db.collection("locations")
                .document(_localDeviceId.value)
                .collection("points")
                .document(pointId)
                .set(cloudPoint)
                .await()

            // Update device latest position with live hardware state
            registerLocalDeviceInCloud(
                latitude = point.latitude,
                longitude = point.longitude,
                speedKmh = (point.speed ?: 0f) * 3.6f,
                altitude = point.altitude ?: 0.0
            )
            true
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Failed to upload location: ${e.message}")
            _pendingOfflinePointsCount.value++
            false
        }
    }

    // --- Invites & Access Management ---
    suspend fun generateInviteCode(): Result<DeviceInvite> {
        val user = authManager.currentUser.value
            ?: return Result.failure(IllegalStateException("Usuário não autenticado"))
        val db = firestore

        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val codePart1 = (1..4).map { chars.random() }.joinToString("")
        val codePart2 = (1..4).map { chars.random() }.joinToString("")
        val fullCode = "$codePart1-$codePart2"

        val invite = DeviceInvite(
            inviteId = "inv_${System.currentTimeMillis()}",
            code = fullCode,
            deviceId = _localDeviceId.value,
            deviceName = _localDeviceName.value,
            ownerId = user.userId,
            ownerEmail = user.email,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L,
            status = "PENDING"
        )

        return try {
            if (db != null) {
                db.collection("invites")
                    .document(fullCode)
                    .set(invite)
                    .await()
            }
            val invites = _pendingInvites.value.toMutableList()
            invites.add(0, invite)
            _pendingInvites.value = invites
            Result.success(invite)
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to create invite: ${e.message}")
            // Return local invite for offline/testing
            val invites = _pendingInvites.value.toMutableList()
            invites.add(0, invite)
            _pendingInvites.value = invites
            Result.success(invite)
        }
    }

    suspend fun redeemInviteCode(inputCode: String): Result<String> {
        val cleanCode = inputCode.trim().uppercase()
        val user = authManager.currentUser.value
            ?: return Result.failure(IllegalStateException("Faça login com sua conta Google primeiro"))
        val db = firestore

        if (cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Código de convite não pode estar vazio"))
        }

        try {
            if (db != null) {
                val inviteDoc = db.collection("invites").document(cleanCode).get().await()
                if (!inviteDoc.exists()) {
                    return Result.failure(IllegalArgumentException("Código de convite inválido ou não encontrado"))
                }
                val invite = inviteDoc.toObject(DeviceInvite::class.java)
                    ?: return Result.failure(IllegalArgumentException("Convite inválido"))

                if (System.currentTimeMillis() > invite.expiresAt) {
                    return Result.failure(IllegalArgumentException("Este código de convite expirou (validade de 10 min)"))
                }
                if (invite.ownerId == user.userId) {
                    return Result.failure(IllegalArgumentException("Você já é o proprietário deste dispositivo"))
                }

                // Create or activate deviceAccess
                val accessId = "acc_${invite.deviceId}_${user.userId}"
                val access = DeviceAccess(
                    accessId = accessId,
                    deviceId = invite.deviceId,
                    deviceName = invite.deviceName,
                    ownerId = invite.ownerId,
                    ownerEmail = invite.ownerEmail,
                    viewerId = user.userId,
                    viewerEmail = user.email,
                    status = "ACTIVE",
                    createdAt = System.currentTimeMillis()
                )

                db.collection("deviceAccess").document(accessId).set(access).await()
                db.collection("invites").document(cleanCode).update("status", "ACCEPTED").await()

                return Result.success("Dispositivo \"${invite.deviceName}\" vinculado com sucesso à sua conta!")
            } else {
                // Fallback / Demo linking
                val mockDevice = CloudDevice(
                    deviceId = "REMOTE-${cleanCode.replace("-", "")}",
                    ownerId = "remote_owner",
                    ownerEmail = "outro.celular@gmail.com",
                    name = "Celular ($cleanCode)",
                    platform = "Android",
                    model = "Samsung Galaxy / Moto",
                    colorHex = "#10B981",
                    iconType = "PHONE",
                    sharingEnabled = true,
                    isOnline = true,
                    batteryPercent = 88,
                    batteryLevel = 88,
                    isCharging = false,
                    batteryStatus = "Não carregando",
                    wifiConnected = true,
                    wifiSsid = "MinhaRede_Casa",
                    deviceStatus = "ONLINE",
                    lastSeen = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    lastLatitude = -23.55052 + (Random.nextDouble() - 0.5) * 0.02,
                    lastLongitude = -46.63330 + (Random.nextDouble() - 0.5) * 0.02,
                    lastSpeedKmh = 24.5f
                )
                val current = _sharedWithMeDevices.value.toMutableList()
                current.add(0, mockDevice)
                _sharedWithMeDevices.value = current
                return Result.success("Dispositivo vinculado com sucesso!")
            }
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Error redeeming code: ${e.message}")
            return Result.failure(e)
        }
    }

    suspend fun revokeAccess(accessId: String): Result<Unit> {
        val db = firestore
        return try {
            if (db != null) {
                db.collection("deviceAccess").document(accessId).update("status", "REVOKED").await()
            }
            val list = _activeViewers.value.toMutableList()
            list.removeAll { it.accessId == accessId }
            _activeViewers.value = list
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeviceLocationHistory(deviceId: String, timeFilter: String): List<CloudLocationPoint> {
        val db = firestore ?: return emptyList()

        val calendar = Calendar.getInstance()
        val startTimestamp = when (timeFilter) {
            "TODAY" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            "YESTERDAY" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.timeInMillis
            }
            "WEEK" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                calendar.timeInMillis
            }
            else -> 0L
        }

        return try {
            val snapshot = db.collection("locations")
                .document(deviceId)
                .collection("points")
                .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()

            snapshot.toObjects(CloudLocationPoint::class.java)
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Error fetching history: ${e.message}")
            emptyList()
        }
    }

    private fun attachCloudListeners(userId: String, email: String) {
        val db = firestore ?: return
        detachCloudListeners()

        try {
            // 1. My own devices
            myDevicesListener = db.collection("devices")
                .whereEqualTo("ownerId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("FirestoreSyncManager", "Listen my devices error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        _myDevices.value = snapshot.toObjects(CloudDevice::class.java)
                    }
                }

            // 2. Devices shared with my Google account
            sharedDevicesListener = db.collection("deviceAccess")
                .whereEqualTo("viewerId", userId)
                .whereEqualTo("status", "ACTIVE")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val accesses = snapshot.toObjects(DeviceAccess::class.java)
                        val deviceIds = accesses.map { it.deviceId }
                        if (deviceIds.isNotEmpty()) {
                            // Listen to each shared device
                            fetchSharedDevices(deviceIds)
                        } else {
                            _sharedWithMeDevices.value = emptyList()
                        }
                    }
                }

            // 3. Viewers authorized to see my local device
            viewersListener = db.collection("deviceAccess")
                .whereEqualTo("ownerId", userId)
                .whereEqualTo("status", "ACTIVE")
                .addSnapshotListener { snapshot, error ->
                    if (snapshot != null) {
                        _activeViewers.value = snapshot.toObjects(DeviceAccess::class.java)
                    }
                }
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Error attaching listeners: ${e.message}")
        }
    }

    private fun fetchSharedDevices(deviceIds: List<String>) {
        val db = firestore ?: return
        scope.launch {
            try {
                val devicesList = mutableListOf<CloudDevice>()
                for (id in deviceIds) {
                    val doc = db.collection("devices").document(id).get().await()
                    if (doc.exists()) {
                        val dev = doc.toObject(CloudDevice::class.java)
                        if (dev != null && dev.sharingEnabled) {
                            devicesList.add(dev)
                        }
                    }
                }
                _sharedWithMeDevices.value = devicesList
            } catch (e: Exception) {
                Log.w("FirestoreSyncManager", "Failed to fetch shared devices: ${e.message}")
            }
        }
    }

    private fun detachCloudListeners() {
        myDevicesListener?.remove()
        myDevicesListener = null
        sharedDevicesListener?.remove()
        sharedDevicesListener = null
        viewersListener?.remove()
        viewersListener = null
    }
}
