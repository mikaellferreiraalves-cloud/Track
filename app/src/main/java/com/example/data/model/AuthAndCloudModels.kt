package com.example.data.model

import androidx.annotation.Keep

@Keep
data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Keep
data class CloudDevice(
    val deviceId: String = "",
    val ownerId: String = "",
    val ownerEmail: String = "",
    val name: String = "Meu Celular",
    val platform: String = "Android",
    val model: String = "",
    val colorHex: String = "#00D2FF",
    val iconType: String = "PHONE",
    val sharingEnabled: Boolean = false,
    val isOnline: Boolean = true,
    // Battery & Energy info
    val batteryPercent: Int = 100,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val batteryStatus: String = "Não carregando", // "Carregando", "Descarregando", "Carregada", "Não carregando"
    // Wi-Fi & Network info
    val wifiConnected: Boolean = false,
    val wifiSsid: String? = null,
    // Status and timestamps
    val deviceStatus: String = "ONLINE", // "ONLINE", "RECENT", "OFFLINE"
    val lastSeen: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastLatitude: Double = 0.0,
    val lastLongitude: Double = 0.0,
    val lastSpeedKmh: Float = 0f,
    val lastAltitude: Double = 0.0,
    val lastAccuracyMeters: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Compute real-time status based on timestamp:
     * - Online: < 3 minutes ago
     * - Recent: < 15 minutes ago
     * - Offline: >= 15 minutes or no communication
     */
    val dynamicStatus: DeviceOnlineStatus
        get() {
            val diff = System.currentTimeMillis() - lastSeen
            return when {
                diff < 3 * 60 * 1000L && isOnline -> DeviceOnlineStatus.ONLINE
                diff < 15 * 60 * 1000L -> DeviceOnlineStatus.RECENT
                else -> DeviceOnlineStatus.OFFLINE
            }
        }

    val displayWifi: String
        get() = when {
            wifiSsid != null && wifiSsid.isNotBlank() && wifiSsid != "Não conectado" -> wifiSsid
            wifiConnected -> "Conectado"
            else -> "Não conectado"
        }

    val displayCharging: String
        get() = if (isCharging) "Sim" else "Não"
}

enum class DeviceOnlineStatus(val label: String, val badgeHex: String) {
    ONLINE("Online", "#10B981"),
    RECENT("Atualização recente", "#F59E0B"),
    OFFLINE("Offline", "#94A3B8")
}

@Keep
data class DeviceAccess(
    val accessId: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val ownerId: String = "",
    val ownerEmail: String = "",
    val viewerId: String = "",
    val viewerEmail: String = "",
    val status: String = "ACTIVE", // "ACTIVE", "REVOKED", "PENDING"
    val createdAt: Long = System.currentTimeMillis()
)

@Keep
data class DeviceInvite(
    val inviteId: String = "",
    val code: String = "", // e.g. "X7K4-92PL"
    val deviceId: String = "",
    val deviceName: String = "",
    val ownerId: String = "",
    val ownerEmail: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 10 * 60 * 1000L, // 10 minutes validity
    val status: String = "PENDING" // "PENDING", "ACCEPTED", "REVOKED", "EXPIRED"
)

@Keep
data class CloudLocationPoint(
    val id: String = "",
    val deviceId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val altitude: Double = 0.0
)
