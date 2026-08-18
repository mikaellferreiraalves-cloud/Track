package com.example.domain.device

import android.content.Context
import android.util.Log
import com.example.data.model.CloudDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DeviceHardwareState(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryStatus: String = "Não carregando",
    val isWifiConnected: Boolean = false,
    val wifiSsid: String = "Não conectado",
    val lastUpdated: Long = System.currentTimeMillis()
)

class DeviceStatusRepository(
    private val context: Context,
    private val batteryMonitor: BatteryMonitor = BatteryMonitor(context),
    private val networkMonitor: NetworkMonitor = NetworkMonitor(context)
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _deviceState = MutableStateFlow(
        DeviceHardwareState(
            batteryPercent = batteryMonitor.getCurrentBatteryState().levelPercent,
            isCharging = batteryMonitor.getCurrentBatteryState().isCharging,
            batteryStatus = batteryMonitor.getCurrentBatteryState().statusText,
            isWifiConnected = networkMonitor.getCurrentNetworkState().isWifiConnected,
            wifiSsid = networkMonitor.getCurrentNetworkState().wifiSsid
        )
    )
    val deviceState: StateFlow<DeviceHardwareState> = _deviceState.asStateFlow()

    init {
        // Observe live changes to battery and network without keeping CPU constantly active
        scope.launch {
            combine(
                batteryMonitor.observeBatteryState(),
                networkMonitor.observeNetworkState()
            ) { battery, network ->
                DeviceHardwareState(
                    batteryPercent = battery.levelPercent,
                    isCharging = battery.isCharging,
                    batteryStatus = battery.statusText,
                    isWifiConnected = network.isWifiConnected,
                    wifiSsid = network.wifiSsid,
                    lastUpdated = System.currentTimeMillis()
                )
            }.collect { state ->
                _deviceState.value = state
            }
        }
    }

    fun getLatestHardwareState(): DeviceHardwareState {
        val bat = batteryMonitor.getCurrentBatteryState()
        val net = networkMonitor.getCurrentNetworkState()
        val state = DeviceHardwareState(
            batteryPercent = bat.levelPercent,
            isCharging = bat.isCharging,
            batteryStatus = bat.statusText,
            isWifiConnected = net.isWifiConnected,
            wifiSsid = net.wifiSsid,
            lastUpdated = System.currentTimeMillis()
        )
        _deviceState.value = state
        return state
    }
}
