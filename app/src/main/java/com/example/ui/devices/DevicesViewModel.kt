package com.example.ui.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TrackerApp
import com.example.data.model.CloudDevice
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DevicesUiState(
    val isAddDeviceDialogOpen: Boolean = false,
    val selectedDeviceForDetails: CloudDevice? = null,
    val inviteCodeInput: String = "",
    val isRedeeming: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class DevicesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TrackerApp
    private val syncManager = app.firestoreSyncManager
    private val authManager = app.authManager

    val currentUser: StateFlow<UserProfile?> = authManager.currentUser
    val localDeviceId: StateFlow<String> = syncManager.localDeviceId
    val localDeviceName: StateFlow<String> = syncManager.localDeviceName
    val isSharingEnabled: StateFlow<Boolean> = syncManager.sharingEnabled

    val myDevices: StateFlow<List<CloudDevice>> = syncManager.myDevices
    val sharedDevices: StateFlow<List<CloudDevice>> = syncManager.sharedWithMeDevices

    // All authorized devices list
    val allAuthorizedDevices: StateFlow<List<CloudDevice>> = combine(
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

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null, errorMessage = null)
    }

    fun openAddDeviceDialog(open: Boolean) {
        _uiState.value = _uiState.value.copy(isAddDeviceDialogOpen = open, inviteCodeInput = "")
    }

    fun selectDeviceForDetails(device: CloudDevice?) {
        _uiState.value = _uiState.value.copy(selectedDeviceForDetails = device)
    }

    fun setInviteCodeInput(code: String) {
        _uiState.value = _uiState.value.copy(inviteCodeInput = code)
    }

    fun saveDeviceName(newName: String) {
        syncManager.updateDeviceName(newName)
        _uiState.value = _uiState.value.copy(userMessage = "Nome atualizado para \"$newName\"")
    }

    fun redeemInviteCode() {
        val code = _uiState.value.inviteCodeInput.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRedeeming = true)
            val res = syncManager.redeemInviteCode(code)
            res.onSuccess { msg ->
                _uiState.value = _uiState.value.copy(
                    isRedeeming = false,
                    isAddDeviceDialogOpen = false,
                    userMessage = msg
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isRedeeming = false,
                    errorMessage = err.message ?: "Erro ao vincular dispositivo"
                )
            }
        }
    }

    fun addDemoDevice(name: String, type: String) {
        val demoDev = CloudDevice(
            deviceId = "DEV-${name.uppercase().take(3)}-${(1000..9999).random()}",
            ownerId = currentUser.value?.userId ?: "my_uid",
            ownerEmail = currentUser.value?.email ?: "meu.celular@gmail.com",
            name = name,
            platform = "Android",
            model = name,
            colorHex = when (type) {
                "MOTO" -> "#10B981"
                "TABLET" -> "#F59E0B"
                "CAR" -> "#EC4899"
                else -> "#00D2FF"
            },
            iconType = type,
            sharingEnabled = true,
            isOnline = true,
            batteryPercent = (65..95).random(),
            batteryLevel = (65..95).random(),
            isCharging = listOf(true, false).random(),
            batteryStatus = if (listOf(true, false).random()) "Carregando" else "Não carregando",
            wifiConnected = true,
            wifiSsid = if (type == "TABLET") "MinhaRede_5G" else "Wi-Fi Casa",
            deviceStatus = "ONLINE",
            lastSeen = System.currentTimeMillis() - ((1..8).random().toLong() * 60 * 1000L),
            lastUpdated = System.currentTimeMillis() - ((1..8).random().toLong() * 60 * 1000L),
            lastLatitude = -23.55052 + (Math.random() - 0.5) * 0.03,
            lastLongitude = -46.63330 + (Math.random() - 0.5) * 0.03,
            lastSpeedKmh = if (type == "MOTO" || type == "CAR") (30..70).random().toFloat() else 0f
        )
        val list = syncManager.sharedWithMeDevices.value.toMutableList()
        list.add(0, demoDev)
        _uiState.value = _uiState.value.copy(userMessage = "Dispositivo de teste \"$name\" adicionado!")
    }
}
