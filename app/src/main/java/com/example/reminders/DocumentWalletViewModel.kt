package com.example.reminders

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.data.local.PreferencesRepository
import com.example.core.model.WalletCategory
import com.example.core.model.WalletDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DocumentWalletUiState(
    val documents: List<WalletDocument> = emptyList(),
    val selectedCategory: WalletCategory? = null,
    val searchQuery: String = "",
    val calendarGroupedView: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class DocumentWalletViewModel(
    private val walletRepository: DocumentWalletRepository,
    private val fileRepository: WorkspaceFileRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val selectedCategoryFlow = MutableStateFlow<WalletCategory?>(null)
    private val searchQueryFlow = MutableStateFlow("")
    private val calendarViewFlow = MutableStateFlow(false)
    private val syncingFlow = MutableStateFlow(false)
    private val statusFlow = MutableStateFlow<String?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DocumentWalletUiState> = combine(
        walletRepository.documents,
        selectedCategoryFlow,
        searchQueryFlow,
        calendarViewFlow,
        preferencesRepository.preferencesFlow,
        syncingFlow,
        statusFlow,
        errorFlow
    ) { vals ->
        @Suppress("UNCHECKED_CAST")
        val allDocs = vals[0] as List<WalletDocument>
        val cat = vals[1] as WalletCategory?
        val query = (vals[2] as String).trim().lowercase()
        val calView = vals[3] as Boolean
        val prefs = vals[4] as com.example.core.data.local.UserPreferences
        val syncing = vals[5] as Boolean
        val status = vals[6] as String?
        val error = vals[7] as String?

        val filtered = allDocs.filter { doc ->
            val catOk = cat == null || doc.category == cat
            val qOk = query.isEmpty() ||
                doc.title.lowercase().contains(query) ||
                doc.category.label.lowercase().contains(query) ||
                doc.notes.lowercase().contains(query)
            catOk && qOk
        }.sortedBy { it.expiryDateMillis }

        DocumentWalletUiState(
            documents = filtered,
            selectedCategory = cat,
            searchQuery = vals[2] as String,
            calendarGroupedView = calView,
            cloudSyncEnabled = prefs.cloudSyncEnabled,
            isSyncing = syncing,
            statusMessage = status,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DocumentWalletUiState())

    fun selectCategory(category: WalletCategory?) {
        selectedCategoryFlow.value = category
    }

    fun updateSearch(query: String) {
        searchQueryFlow.value = query
    }

    fun toggleCalendarView() {
        calendarViewFlow.value = !calendarViewFlow.value
    }

    suspend fun importAttachment(uri: Uri): Result<Pair<String, String>> {
        return fileRepository.importAttachmentForWallet(uri)
    }

    fun saveDocument(doc: WalletDocument, isSignedIn: Boolean) {
        val shouldSync = isSignedIn && uiState.value.cloudSyncEnabled
        viewModelScope.launch {
            walletRepository.saveDocument(doc, syncToCloud = shouldSync).fold(
                onSuccess = {
                    statusFlow.value = if (shouldSync) {
                        "Saved \"${doc.title}\" & synced metadata to cloud."
                    } else {
                        "Saved \"${doc.title}\" in private device wallet."
                    }
                    errorFlow.value = null
                },
                onFailure = { e ->
                    statusFlow.value = "Saved locally; cloud sync pending: ${e.localizedMessage ?: "offline"}"
                }
            )
        }
    }

    fun markRenewed(doc: WalletDocument, newExpiryMillis: Long, isSignedIn: Boolean) {
        val shouldSync = isSignedIn && uiState.value.cloudSyncEnabled
        viewModelScope.launch {
            walletRepository.markAsRenewed(doc, newExpiryMillis, syncToCloud = shouldSync).fold(
                onSuccess = {
                    statusFlow.value = "Marked \"${doc.title}\" as renewed with new expiry date."
                },
                onFailure = { e ->
                    errorFlow.value = e.localizedMessage ?: "Could not update renewal."
                }
            )
        }
    }

    fun deleteDocument(doc: WalletDocument, isSignedIn: Boolean) {
        val shouldSync = isSignedIn && uiState.value.cloudSyncEnabled
        viewModelScope.launch {
            walletRepository.deleteDocument(doc.id, syncToCloud = shouldSync)
            statusFlow.value = "Removed \"${doc.title}\"."
        }
    }

    fun syncCloudWalletNow(isSignedIn: Boolean) {
        if (!isSignedIn) {
            errorFlow.value = "Sign in with Google first to sync wallet metadata."
            return
        }
        viewModelScope.launch {
            syncingFlow.value = true
            var syncedCount = 0
            uiState.value.documents.forEach { doc ->
                walletRepository.saveDocument(doc, syncToCloud = true).onSuccess { syncedCount++ }
            }
            walletRepository.pullCloudDocumentsIntoLocal()
            syncingFlow.value = false
            statusFlow.value = "Cloud sync checked ($syncedCount local metadata record(s) synced)."
        }
    }

    fun clearMessages() {
        statusFlow.value = null
        errorFlow.value = null
    }
}
