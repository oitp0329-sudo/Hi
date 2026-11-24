package com.example.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.files.StorageOverviewStats
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.data.local.PreferencesRepository
import com.example.core.model.ProcessingTaskRecord
import com.example.core.model.QuickActionId
import com.example.core.model.WalletDocument
import com.example.core.model.WorkspaceFile
import com.example.reminders.DocumentWalletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val searchQuery: String = "",
    val quickActions: List<QuickActionId> = QuickActionId.entries.toList(),
    val recentFiles: List<WorkspaceFile> = emptyList(),
    val favouriteFiles: List<WorkspaceFile> = emptyList(),
    val upcomingExpiries: List<WalletDocument> = emptyList(),
    val recentTasks: List<ProcessingTaskRecord> = emptyList(),
    val storageStats: StorageOverviewStats = StorageOverviewStats(0, 0, 0, 0, 0, 0, 0)
)

class HomeViewModel(
    private val fileRepository: WorkspaceFileRepository,
    walletRepository: DocumentWalletRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val storageStatsFlow = MutableStateFlow(StorageOverviewStats(0, 0, 0, 0, 0, 0, 0))

    init {
        viewModelScope.launch {
            fileRepository.activeFiles.collect { files ->
                storageStatsFlow.value = fileRepository.computeStorageOverview(files)
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        searchQueryFlow,
        preferencesRepository.preferencesFlow,
        fileRepository.activeFiles,
        fileRepository.favouriteFiles,
        walletRepository.documents,
        fileRepository.recentTasks,
        storageStatsFlow
    ) { values ->
        val query = values[0] as String
        val prefs = values[1] as com.example.core.data.local.UserPreferences
        @Suppress("UNCHECKED_CAST")
        val files = values[2] as List<WorkspaceFile>
        @Suppress("UNCHECKED_CAST")
        val favs = values[3] as List<WorkspaceFile>
        @Suppress("UNCHECKED_CAST")
        val docs = values[4] as List<WalletDocument>
        @Suppress("UNCHECKED_CAST")
        val tasks = values[5] as List<ProcessingTaskRecord>
        val storage = values[6] as StorageOverviewStats

        val q = query.trim().lowercase()
        val filteredFiles = if (q.isEmpty()) files else files.filter { it.displayName.lowercase().contains(q) }
        val filteredActions = if (q.isEmpty()) prefs.quickActionsOrder else prefs.quickActionsOrder.filter {
            it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q)
        }
        val upcoming = docs.filter { !it.isRenewed }.take(4)

        HomeUiState(
            searchQuery = query,
            quickActions = filteredActions,
            recentFiles = filteredFiles.take(5),
            favouriteFiles = favs.take(4),
            upcomingExpiries = upcoming,
            recentTasks = tasks.take(4),
            storageStats = storage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HomeUiState()
    )

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun moveQuickActionUp(action: QuickActionId) {
        val current = uiState.value.quickActions.toMutableList()
        val idx = current.indexOf(action)
        if (idx > 0) {
            current[idx] = current[idx - 1]
            current[idx - 1] = action
            viewModelScope.launch { preferencesRepository.setQuickActionsOrder(current) }
        }
    }

    fun moveQuickActionDown(action: QuickActionId) {
        val current = uiState.value.quickActions.toMutableList()
        val idx = current.indexOf(action)
        if (idx in 0 until current.lastIndex) {
            current[idx] = current[idx + 1]
            current[idx + 1] = action
            viewModelScope.launch { preferencesRepository.setQuickActionsOrder(current) }
        }
    }

    fun resetQuickActions() {
        viewModelScope.launch {
            preferencesRepository.setQuickActionsOrder(QuickActionId.entries.toList())
        }
    }

    fun toggleFavourite(file: WorkspaceFile) {
        viewModelScope.launch {
            fileRepository.toggleFavourite(file.id, file.isFavourite)
        }
    }
}
