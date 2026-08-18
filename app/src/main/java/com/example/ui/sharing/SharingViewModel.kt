package com.example.ui.sharing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TrackerApp
import com.example.data.model.DeviceAccess
import com.example.data.model.DeviceInvite
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SharingUiState(
    val isConsentDialogOpen: Boolean = false,
    val isRedeemDialogOpen: Boolean = false,
    val isGeneratingInvite: Boolean = false,
    val latestInvite: DeviceInvite? = null,
    val redeemCodeInput: String = "",
    val isRedeeming: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class SharingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TrackerApp
    private val syncManager = app.firestoreSyncManager
    private val authManager = app.authManager

    val currentUser: StateFlow<UserProfile?> = authManager.currentUser
    val localDeviceName: StateFlow<String> = syncManager.localDeviceName
    val localDeviceId: StateFlow<String> = syncManager.localDeviceId
    val isSharingEnabled: StateFlow<Boolean> = syncManager.sharingEnabled
    val activeViewers: StateFlow<List<DeviceAccess>> = syncManager.activeViewers

    private val _uiState = MutableStateFlow(SharingUiState())
    val uiState: StateFlow<SharingUiState> = _uiState.asStateFlow()

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null, errorMessage = null)
    }

    fun openConsentDialog(open: Boolean) {
        _uiState.value = _uiState.value.copy(isConsentDialogOpen = open)
    }

    fun openRedeemDialog(open: Boolean) {
        _uiState.value = _uiState.value.copy(isRedeemDialogOpen = open, redeemCodeInput = "")
    }

    fun setRedeemCode(code: String) {
        _uiState.value = _uiState.value.copy(redeemCodeInput = code)
    }

    fun toggleSharing(targetState: Boolean) {
        if (targetState) {
            // Must confirm with consent dialog first
            _uiState.value = _uiState.value.copy(isConsentDialogOpen = true)
        } else {
            syncManager.setSharingEnabled(false)
            _uiState.value = _uiState.value.copy(userMessage = "Compartilhamento de localização pausado")
        }
    }

    fun confirmEnableSharing() {
        syncManager.setSharingEnabled(true)
        _uiState.value = _uiState.value.copy(
            isConsentDialogOpen = false,
            userMessage = "Compartilhamento ativado com sucesso"
        )
    }

    fun generateInviteCode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingInvite = true)
            val result = syncManager.generateInviteCode()
            result.onSuccess { invite ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingInvite = false,
                    latestInvite = invite,
                    userMessage = "Código gerado com sucesso! Válido por 10 minutos."
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingInvite = false,
                    errorMessage = err.message ?: "Erro ao gerar código de convite"
                )
            }
        }
    }

    fun redeemInviteCode() {
        val code = _uiState.value.redeemCodeInput.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRedeeming = true)
            val res = syncManager.redeemInviteCode(code)
            res.onSuccess { msg ->
                _uiState.value = _uiState.value.copy(
                    isRedeeming = false,
                    isRedeemDialogOpen = false,
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

    fun revokeViewerAccess(accessId: String, viewerEmail: String) {
        viewModelScope.launch {
            syncManager.revokeAccess(accessId)
            _uiState.value = _uiState.value.copy(
                userMessage = "Acesso revogado para $viewerEmail"
            )
        }
    }
}
