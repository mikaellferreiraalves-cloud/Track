package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DeviceIconType(val label: String) {
    PHONE("Celular"),
    CAR("Veículo / Carro"),
    PERSON("Pessoa / Família"),
    BIKE("Bicicleta / Moto"),
    TABLET("Tablet")
}

enum class DeviceActivity(val label: String) {
    STATIONARY("Parado"),
    WALKING("Caminhando"),
    RUNNING("Correndo"),
    IN_VEHICLE("Em Veículo")
}

@Entity(tableName = "observed_devices")
data class ObservedDevice(
    @PrimaryKey
    val deviceId: String,
    val name: String,
    val modelInfo: String = "Android Device",
    val iconType: String = DeviceIconType.PHONE.name,
    val colorHex: String = "#00D2FF",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val isOnline: Boolean = true,
    val activityType: String = DeviceActivity.STATIONARY.name,
    val lastUpdated: Long = System.currentTimeMillis(),
    val pairingCode: String = "",
    val isObserved: Boolean = true,
    val notes: String = ""
)

@Entity(tableName = "device_location_logs")
data class DeviceLocationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
