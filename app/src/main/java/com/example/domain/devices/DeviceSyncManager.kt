package com.example.domain.devices

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.example.data.database.DeviceActivity
import com.example.data.database.DeviceIconType
import com.example.data.database.DeviceLocationLog
import com.example.data.database.ObservedDevice
import com.example.data.repository.TrackingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class LocalDeviceIdentity(
    val deviceId: String,
    val pairingCode: String,
    val deviceName: String,
    val isBroadcasting: Boolean,
    val broadcastIntervalSec: Int = 10,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false
)

class DeviceSyncManager(
    private val context: Context,
    private val trackingRepository: TrackingRepository
) {
    private val prefs = context.getSharedPreferences("device_sync_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _localIdentity = MutableStateFlow(loadLocalIdentity())
    val localIdentity: StateFlow<LocalDeviceIdentity> = _localIdentity.asStateFlow()

    private var simulationJob: Job? = null

    init {
        startTelemetryLoop()
    }

    private fun loadLocalIdentity(): LocalDeviceIdentity {
        var id = prefs.getString("local_device_id", null)
        var code = prefs.getString("local_pairing_code", null)
        var name = prefs.getString("local_device_name", null)

        if (id == null) {
            id = "DEV-" + UUID.randomUUID().toString().take(6).uppercase()
            prefs.edit().putString("local_device_id", id).apply()
        }
        if (code == null) {
            code = "TRK-" + (10000 + Random.nextInt(90000))
            prefs.edit().putString("local_pairing_code", code).apply()
        }
        if (name == null) {
            name = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            prefs.edit().putString("local_device_name", name).apply()
        }

        val broadcasting = prefs.getBoolean("is_broadcasting", true)
        val interval = prefs.getInt("broadcast_interval", 10)

        val (battery, charging) = getBatteryStatus()

        return LocalDeviceIdentity(
            deviceId = id,
            pairingCode = code,
            deviceName = name,
            isBroadcasting = broadcasting,
            broadcastIntervalSec = interval,
            batteryPercent = battery,
            isCharging = charging
        )
    }

    fun updateDeviceName(newName: String) {
        prefs.edit().putString("local_device_name", newName).apply()
        _localIdentity.value = _localIdentity.value.copy(deviceName = newName)
    }

    fun setBroadcastingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_broadcasting", enabled).apply()
        _localIdentity.value = _localIdentity.value.copy(isBroadcasting = enabled)
    }

    fun regeneratePairingCode(): String {
        val newCode = "TRK-" + (10000 + Random.nextInt(90000))
        prefs.edit().putString("local_pairing_code", newCode).apply()
        _localIdentity.value = _localIdentity.value.copy(pairingCode = newCode)
        return newCode
    }

    suspend fun pairDeviceByCode(
        pairingCode: String,
        customName: String,
        iconType: DeviceIconType = DeviceIconType.PHONE,
        colorHex: String = "#00D2FF",
        baseLat: Double = -23.55052,
        baseLon: Double = -46.63330
    ): Result<ObservedDevice> {
        val cleanCode = pairingCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Código de pareamento inválido"))
        }

        // Verify if not self-pairing
        if (cleanCode == _localIdentity.value.pairingCode) {
            return Result.failure(IllegalArgumentException("Não é possível parear o próprio dispositivo atual"))
        }

        val generatedId = "REMOTE-${cleanCode.replace("-", "")}"
        val initialDevice = ObservedDevice(
            deviceId = generatedId,
            name = customName.ifBlank { "Celular ($cleanCode)" },
            modelInfo = "Android Dispositivo Remoto",
            iconType = iconType.name,
            colorHex = colorHex,
            latitude = baseLat + (Random.nextDouble() - 0.5) * 0.015,
            longitude = baseLon + (Random.nextDouble() - 0.5) * 0.015,
            altitude = 750.0,
            speedKmh = 18f + Random.nextFloat() * 15f,
            batteryPercent = 70 + Random.nextInt(28),
            isCharging = false,
            isOnline = true,
            activityType = DeviceActivity.IN_VEHICLE.name,
            lastUpdated = System.currentTimeMillis(),
            pairingCode = cleanCode,
            isObserved = true
        )

        trackingRepository.insertObservedDevice(initialDevice)
        trackingRepository.insertDeviceLocationLog(
            DeviceLocationLog(
                deviceId = generatedId,
                latitude = initialDevice.latitude,
                longitude = initialDevice.longitude,
                altitude = initialDevice.altitude,
                speedKmh = initialDevice.speedKmh,
                timestamp = initialDevice.lastUpdated
            )
        )

        return Result.success(initialDevice)
    }

    suspend fun addDemoDevices(currentLat: Double = -23.55052, currentLon: Double = -46.63330) {
        val demo1 = ObservedDevice(
            deviceId = "DEMO-MOTO-G84",
            name = "Moto G84 (Entregas)",
            modelInfo = "Motorola Moto G84 5G",
            iconType = DeviceIconType.CAR.name,
            colorHex = "#00D2FF",
            latitude = currentLat + 0.0082,
            longitude = currentLon - 0.0065,
            altitude = 760.0,
            speedKmh = 42.5f,
            batteryPercent = 84,
            isCharging = true,
            isOnline = true,
            activityType = DeviceActivity.IN_VEHICLE.name,
            lastUpdated = System.currentTimeMillis(),
            pairingCode = "TRK-29481",
            notes = "Veículo de serviço / rota sul"
        )

        val demo2 = ObservedDevice(
            deviceId = "DEMO-GALAXY-A54",
            name = "Galaxy A54 (Família)",
            modelInfo = "Samsung Galaxy A54",
            iconType = DeviceIconType.PERSON.name,
            colorHex = "#10B981",
            latitude = currentLat - 0.0055,
            longitude = currentLon + 0.0078,
            altitude = 745.0,
            speedKmh = 4.8f,
            batteryPercent = 67,
            isCharging = false,
            isOnline = true,
            activityType = DeviceActivity.WALKING.name,
            lastUpdated = System.currentTimeMillis(),
            pairingCode = "TRK-74192",
            notes = "Celular pessoal família"
        )

        val demo3 = ObservedDevice(
            deviceId = "DEMO-TABLET-CAR",
            name = "Tablet Painel Carro",
            modelInfo = "Lenovo Tab P11",
            iconType = DeviceIconType.TABLET.name,
            colorHex = "#F59E0B",
            latitude = currentLat + 0.0120,
            longitude = currentLon + 0.0110,
            altitude = 780.0,
            speedKmh = 0f,
            batteryPercent = 95,
            isCharging = true,
            isOnline = true,
            activityType = DeviceActivity.STATIONARY.name,
            lastUpdated = System.currentTimeMillis() - 45000L,
            pairingCode = "TRK-88310",
            notes = "GPS instalado no painel"
        )

        trackingRepository.insertObservedDevices(listOf(demo1, demo2, demo3))
    }

    private fun startTelemetryLoop() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            var step = 0
            while (isActive) {
                delay(3500)
                step++

                // Update local battery info
                val (bat, charging) = getBatteryStatus()
                _localIdentity.value = _localIdentity.value.copy(
                    batteryPercent = bat,
                    isCharging = charging
                )

                // Update simulated live telemetry for active observed devices
                val devices = trackingRepository.getAllObservedDevices()
                if (devices.isNotEmpty()) {
                    for (dev in devices) {
                        if (!dev.isOnline) continue

                        // Motion vector simulation
                        val angle = (step * 25.0 + dev.deviceId.hashCode() % 360) * Math.PI / 180.0
                        val deltaLat = sin(angle) * 0.00025
                        val deltaLon = cos(angle) * 0.00025

                        val newSpeed = when (dev.activityType) {
                            DeviceActivity.IN_VEHICLE.name -> (30f + (sin(step.toDouble()).toFloat() * 15f)).coerceAtLeast(0f)
                            DeviceActivity.WALKING.name -> (4.5f + (cos(step.toDouble()).toFloat() * 1.5f)).coerceAtLeast(0f)
                            DeviceActivity.RUNNING.name -> (9.5f + (cos(step.toDouble()).toFloat() * 2f)).coerceAtLeast(0f)
                            else -> 0f
                        }

                        val updatedDevice = dev.copy(
                            latitude = dev.latitude + deltaLat,
                            longitude = dev.longitude + deltaLon,
                            speedKmh = newSpeed,
                            lastUpdated = System.currentTimeMillis()
                        )

                        trackingRepository.updateObservedDevice(updatedDevice)

                        // Save breadcrumb point every ~15 seconds
                        if (step % 4 == 0) {
                            trackingRepository.insertDeviceLocationLog(
                                DeviceLocationLog(
                                    deviceId = dev.deviceId,
                                    latitude = updatedDevice.latitude,
                                    longitude = updatedDevice.longitude,
                                    altitude = updatedDevice.altitude,
                                    speedKmh = updatedDevice.speedKmh,
                                    timestamp = updatedDevice.lastUpdated
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getBatteryStatus(): Pair<Int, Boolean> {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val percent = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100
            Pair(percent, isCharging)
        } catch (e: Exception) {
            Pair(100, false)
        }
    }
}
