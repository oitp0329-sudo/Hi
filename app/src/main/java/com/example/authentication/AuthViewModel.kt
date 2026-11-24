package com.example.authentication

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.PreferencesRepository
import com.example.core.data.remote.CloudSyncRepository
import com.example.core.model.PlanTier
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = authRepository.authUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.currentUser())

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun attemptSilentSignIn() {
        viewModelScope.launch {
            authRepository.attemptSilentAutoSignIn()
        }
    }

    fun signInWithGoogle(activity: Activity, currentPlan: PlanTier, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            when (val outcome = authRepository.signInWithGoogleInteractive(activity)) {
                is GoogleSignInOutcome.Success -> {
                    preferencesRepository.setGuestMode(false)
                    cloudSyncRepository.upsertUserProfile(
                        displayName = outcome.user.displayName ?: "FileDude User",
                        email = outcome.user.email,
                        planTier = currentPlan,
                        cloudSyncEnabled = true
                    )
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = "Signed in as ${outcome.user.email ?: outcome.user.displayName}")
                    }
                    onSuccess()
                }
                is GoogleSignInOutcome.Cancelled -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Sign-in was cancelled. Select an account to continue or use Guest mode."
                        )
                    }
                }
                is GoogleSignInOutcome.Failure -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = outcome.message) }
                }
            }
        }
    }

    fun continueAsGuest(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(completed = true, guestMode = true)
            _uiState.update { it.copy(errorMessage = null, statusMessage = "Exploring in Guest Mode (offline-first).") }
            onDone()
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            authRepository.updateDisplayName(newName).fold(
                onSuccess = {
                    val user = authRepository.currentUser()
                    if (user != null) {
                        cloudSyncRepository.upsertUserProfile(
                            displayName = newName.trim(),
                            email = user.email,
                            planTier = PlanTier.FREE,
                            cloudSyncEnabled = true
                        )
                    }
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Profile name updated.") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Update failed.") }
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.signOut()
            preferencesRepository.setCloudSyncEnabled(false)
            _uiState.update { it.copy(isLoading = false, statusMessage = "Signed out safely.") }
        }
    }

    fun deleteAccount(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            cloudSyncRepository.deleteAllUserCloudData()
            authRepository.deleteAccount().fold(
                onSuccess = {
                    preferencesRepository.setCloudSyncEnabled(false)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Account and cloud records deleted. Local files on device remain intact."
                        )
                    }
                    onDeleted()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.localizedMessage
                                ?: "Re-authentication may be required before deleting your account. Sign out and back in, then retry."
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }
}
