package com.example.ui.account

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TrackerApp
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountUiState(
    val isLoginDialogVisible: Boolean = false,
    val isDeleteConfirmVisible: Boolean = false,
    val isEditingDeviceName: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TrackerApp
    private val authManager = app.authManager
    private val syncManager = app.firestoreSyncManager

    val currentUser: StateFlow<UserProfile?> = authManager.currentUser
    val authLoading: StateFlow<Boolean> = authManager.authLoading
    val localDeviceId: StateFlow<String> = syncManager.localDeviceId
    val localDeviceName: StateFlow<String> = syncManager.localDeviceName
    val isSharingEnabled: StateFlow<Boolean> = syncManager.sharingEnabled

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null, errorMessage = null)
    }

    fun showLoginDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(isLoginDialogVisible = show)
    }

    fun showDeleteConfirm(show: Boolean) {
        _uiState.value = _uiState.value.copy(isDeleteConfirmVisible = show)
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            val res = authManager.signInWithGoogle(context)
            res.onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    isLoginDialogVisible = false,
                    userMessage = "Conectado como ${user.name}!"
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = err.message ?: "Erro ao entrar com o Google"
                )
            }
        }
    }

    fun signInWithEmail(name: String, email: String) {
        viewModelScope.launch {
            authManager.signInWithDemoAccount(email, name)
            _uiState.value = _uiState.value.copy(
                isLoginDialogVisible = false,
                userMessage = "Conta conectada com sucesso!"
            )
        }
    }

    fun signOut(context: Context?) {
        viewModelScope.launch {
            authManager.signOut(context)
            _uiState.value = _uiState.value.copy(userMessage = "Sessão encerrada com sucesso")
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            authManager.deleteAccountPermanently()
            _uiState.value = _uiState.value.copy(
                isDeleteConfirmVisible = false,
                userMessage = "Conta e histórico remoto excluídos com sucesso"
            )
        }
    }

    fun saveDeviceName(newName: String) {
        syncManager.updateDeviceName(newName)
        _uiState.value = _uiState.value.copy(userMessage = "Nome do dispositivo atualizado para \"$newName\"")
    }
}
