package com.example.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.data.local.PreferencesRepository
import com.example.core.data.local.UserPreferences
import com.example.core.data.remote.CloudSyncRepository
import com.example.core.design.formatFileSize
import com.example.core.model.AppThemeMode
import com.example.core.model.ImageOutputFormat
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.VideoExportPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val cacheBytes: Long = 0L,
    val totalWorkspaceBytes: Long = 0L,
    val workspaceFileCount: Int = 0,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val fileRepository: WorkspaceFileRepository,
    private val cloudSyncRepository: CloudSyncRepository
) : ViewModel() {

    private val statusFlow = MutableStateFlow<String?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferencesFlow,
        fileRepository.activeFiles,
        statusFlow,
        errorFlow
    ) { prefs, files, status, error ->
        val stats = fileRepository.computeStorageOverview(files)
        SettingsUiState(
            preferences = prefs,
            cacheBytes = stats.cacheBytes,
            totalWorkspaceBytes = stats.totalBytes,
            workspaceFileCount = stats.fileCount,
            statusMessage = status,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SettingsUiState())

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setExpiryNotifications(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setExpiryNotificationsEnabled(enabled) }
    }

    fun setCloudSync(enabled: Boolean, isSignedIn: Boolean) {
        if (enabled && !isSignedIn) {
            errorFlow.value = "Sign in with Google first before enabling cloud metadata sync."
            return
        }
        viewModelScope.launch {
            preferencesRepository.setCloudSyncEnabled(enabled)
            statusFlow.value = if (enabled) "Optional cloud wallet & preset sync enabled." else "Cloud sync paused (offline-only)."
        }
    }

    fun setAnalyticsConsent(granted: Boolean) {
        viewModelScope.launch { preferencesRepository.setAnalyticsConsent(granted) }
    }

    fun setDefaultImage(format: ImageOutputFormat, quality: Int) {
        viewModelScope.launch { preferencesRepository.setDefaultImageSettings(format, quality) }
    }

    fun setDefaultPdfPageSize(size: PdfPageSizeOption) {
        viewModelScope.launch { preferencesRepository.setDefaultPdfPageSize(size) }
    }

    fun setDefaultVideoPreset(preset: VideoExportPreset) {
        viewModelScope.launch { preferencesRepository.setDefaultVideoPreset(preset) }
    }

    fun clearTemporaryCache() {
        viewModelScope.launch {
            val cleared = fileRepository.clearTemporaryCache()
            statusFlow.value = "Cleared ${formatFileSize(cleared)} of temporary export cache."
        }
    }

    fun sendFeedback(category: String, subject: String, message: String, isSignedIn: Boolean) {
        if (subject.isBlank() || message.isBlank()) {
            errorFlow.value = "Please enter both a subject and feedback message."
            return
        }
        if (!isSignedIn) {
            errorFlow.value = "Sign in with Google to submit feedback to your Cloud Inbox (guest devices do not transmit background logs)."
            return
        }
        viewModelScope.launch {
            cloudSyncRepository.submitFeedback(category, subject, message, "1.0.0").fold(
                onSuccess = { statusFlow.value = "Feedback submitted. Thank you for helping improve FileDude!" },
                onFailure = { e -> errorFlow.value = e.localizedMessage ?: "Failed to submit feedback." }
            )
        }
    }

    fun clearMessages() {
        statusFlow.value = null
        errorFlow.value = null
    }
}
