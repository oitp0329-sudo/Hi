package com.example.features.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.files.StorageOverviewStats
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.model.FileCategory
import com.example.core.model.FileSortOption
import com.example.core.model.WorkspaceFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FileManagerFilter(val label: String) {
    ALL("All"),
    RECENT("Recent"),
    PDF("PDF"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    DOCS("Docs"),
    FAVOURITES("Favourites"),
    FOLDERS("Folders"),
    DOWNLOADS("Downloads"),
    DUPLICATES("Duplicates"),
    LARGE("Large Files"),
    TRASH("Trash")
}

data class FileManagerUiState(
    val activeFilter: FileManagerFilter = FileManagerFilter.ALL,
    val selectedFolderName: String? = null,
    val availableFolders: List<String> = emptyList(),
    val searchQuery: String = "",
    val sortOption: FileSortOption = FileSortOption.DATE_DESC,
    val files: List<WorkspaceFile> = emptyList(),
    val duplicateGroups: List<List<WorkspaceFile>> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val storageStats: StorageOverviewStats = StorageOverviewStats(0L, 0L, 0L, 0L, 0L, 0L, 0),
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class FileManagerViewModel(
    private val fileRepository: WorkspaceFileRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(FileManagerFilter.ALL)
    private val folderFlow = MutableStateFlow<String?>(null)
    private val searchFlow = MutableStateFlow("")
    private val sortFlow = MutableStateFlow(FileSortOption.DATE_DESC)
    private val selectedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val statusFlow = MutableStateFlow<String?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FileManagerUiState> = combine(
        fileRepository.activeFiles,
        fileRepository.trashFiles,
        filterFlow,
        folderFlow,
        searchFlow,
        sortFlow,
        selectedIdsFlow,
        statusFlow,
        errorFlow
    ) { vals ->
        @Suppress("UNCHECKED_CAST")
        val active = vals[0] as List<WorkspaceFile>
        @Suppress("UNCHECKED_CAST")
        val trash = vals[1] as List<WorkspaceFile>
        val filter = vals[2] as FileManagerFilter
        val folder = vals[3] as String?
        val rawQuery = vals[4] as String
        val query = rawQuery.trim().lowercase()
        val sort = vals[5] as FileSortOption
        @Suppress("UNCHECKED_CAST")
        val selected = vals[6] as Set<String>
        val status = vals[7] as String?
        val error = vals[8] as String?

        val stats = fileRepository.computeStorageOverview(active)
        val folders = active.map { it.folderName }.distinct().sorted()
        val duplicates = fileRepository.findDuplicates(active)

        val baseList = when (filter) {
            FileManagerFilter.ALL -> active
            FileManagerFilter.RECENT -> active.sortedByDescending { it.lastAccessedAtMillis }.take(20)
            FileManagerFilter.PDF -> active.filter { it.category == FileCategory.PDF }
            FileManagerFilter.IMAGES -> active.filter { it.category == FileCategory.IMAGE }
            FileManagerFilter.VIDEOS -> active.filter { it.category == FileCategory.VIDEO }
            FileManagerFilter.DOCS -> active.filter { it.category == FileCategory.DOCUMENT }
            FileManagerFilter.FAVOURITES -> active.filter { it.isFavourite }
            FileManagerFilter.FOLDERS -> if (folder != null) active.filter { it.folderName == folder } else active
            FileManagerFilter.DOWNLOADS -> active.filter { it.folderName.equals("Downloads", ignoreCase = true) || it.folderName.equals("Imported", ignoreCase = true) }
            FileManagerFilter.DUPLICATES -> duplicates.flatten()
            FileManagerFilter.LARGE -> fileRepository.findLargeFiles(active, thresholdBytes = 2L * 1024L * 1024L)
            FileManagerFilter.TRASH -> trash
        }

        val searched = if (query.isEmpty()) baseList else baseList.filter {
            it.displayName.lowercase().contains(query) || it.folderName.lowercase().contains(query)
        }

        val sorted = when (sort) {
            FileSortOption.DATE_DESC -> searched.sortedByDescending { it.lastAccessedAtMillis }
            FileSortOption.DATE_ASC -> searched.sortedBy { it.lastAccessedAtMillis }
            FileSortOption.NAME_ASC -> searched.sortedBy { it.displayName.lowercase() }
            FileSortOption.SIZE_DESC -> searched.sortedByDescending { it.sizeBytes }
        }

        FileManagerUiState(
            activeFilter = filter,
            selectedFolderName = folder,
            availableFolders = folders,
            searchQuery = rawQuery,
            sortOption = sort,
            files = sorted,
            duplicateGroups = duplicates,
            selectedIds = selected,
            storageStats = stats,
            statusMessage = status,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), FileManagerUiState())

    fun selectFilter(filter: FileManagerFilter) {
        filterFlow.value = filter
        selectedIdsFlow.value = emptySet()
    }

    fun selectFolder(folderName: String?) {
        folderFlow.value = folderName
    }

    fun updateSearch(query: String) {
        searchFlow.value = query
    }

    fun updateSort(option: FileSortOption) {
        sortFlow.value = option
    }

    fun toggleSelection(fileId: String) {
        val current = selectedIdsFlow.value
        selectedIdsFlow.value = if (fileId in current) current - fileId else current + fileId
    }

    fun clearSelection() {
        selectedIdsFlow.value = emptySet()
    }

    fun selectAllVisible() {
        selectedIdsFlow.value = uiState.value.files.map { it.id }.toSet()
    }

    fun importUris(uris: List<Uri>, folderName: String = "Imported") {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            uris.forEach { uri ->
                fileRepository.importUriToWorkspace(uri, folderName).onSuccess { count++ }
            }
            statusFlow.value = if (count > 0) "Imported $count file(s) to $folderName." else null
        }
    }

    fun toggleFavourite(file: WorkspaceFile) {
        viewModelScope.launch {
            fileRepository.toggleFavourite(file.id, file.isFavourite)
        }
    }

    fun renameFile(file: WorkspaceFile, newName: String) {
        viewModelScope.launch {
            fileRepository.renameFile(file, newName).fold(
                onSuccess = { statusFlow.value = "Renamed to $newName" },
                onFailure = { e -> errorFlow.value = e.localizedMessage ?: "Rename failed." }
            )
        }
    }

    fun copyFile(file: WorkspaceFile) {
        viewModelScope.launch {
            fileRepository.copyFile(file).fold(
                onSuccess = { copied -> statusFlow.value = "Created ${copied.displayName}" },
                onFailure = { e -> errorFlow.value = e.localizedMessage ?: "Copy failed." }
            )
        }
    }

    fun moveFile(file: WorkspaceFile, folder: String) {
        viewModelScope.launch {
            fileRepository.moveFileToFolder(file.id, folder)
            statusFlow.value = "Moved ${file.displayName} to $folder."
        }
    }

    fun moveToTrash(file: WorkspaceFile) {
        viewModelScope.launch {
            fileRepository.moveToTrash(file.id)
            statusFlow.value = "Moved ${file.displayName} to Trash."
        }
    }

    fun restoreFromTrash(file: WorkspaceFile) {
        viewModelScope.launch {
            fileRepository.restoreFromTrash(file.id)
            statusFlow.value = "Restored ${file.displayName}."
        }
    }

    fun deletePermanently(file: WorkspaceFile) {
        viewModelScope.launch {
            fileRepository.deletePermanently(file)
            statusFlow.value = "Deleted ${file.displayName} permanently."
        }
    }

    fun emptyTrash() {
        val trashList = uiState.value.files
        if (uiState.value.activeFilter != FileManagerFilter.TRASH || trashList.isEmpty()) return
        viewModelScope.launch {
            fileRepository.emptyTrash(trashList)
            statusFlow.value = "Trash emptied."
        }
    }

    fun batchRenameSelected(prefix: String) {
        val cleanPrefix = prefix.trim()
        if (cleanPrefix.isEmpty()) return
        val chosen = uiState.value.files.filter { it.id in selectedIdsFlow.value }
        viewModelScope.launch {
            chosen.forEachIndexed { idx, f ->
                val ext = f.displayName.substringAfterLast('.', "")
                val newName = if (ext.isNotEmpty()) "${cleanPrefix}_${idx + 1}.$ext" else "${cleanPrefix}_${idx + 1}"
                fileRepository.renameFile(f, newName)
            }
            selectedIdsFlow.value = emptySet()
            statusFlow.value = "Batch renamed ${chosen.size} file(s)."
        }
    }

    fun batchMoveSelected(folder: String) {
        val chosen = uiState.value.files.filter { it.id in selectedIdsFlow.value }
        viewModelScope.launch {
            chosen.forEach { fileRepository.moveFileToFolder(it.id, folder) }
            selectedIdsFlow.value = emptySet()
            statusFlow.value = "Moved ${chosen.size} file(s) to $folder."
        }
    }

    fun batchTrashSelected() {
        val chosen = uiState.value.files.filter { it.id in selectedIdsFlow.value }
        viewModelScope.launch {
            chosen.forEach { fileRepository.moveToTrash(it.id) }
            selectedIdsFlow.value = emptySet()
            statusFlow.value = "Moved ${chosen.size} file(s) to Trash."
        }
    }

    fun exportToUri(sourcePath: String, destUri: Uri) {
        viewModelScope.launch {
            fileRepository.exportToUri(sourcePath, destUri).fold(
                onSuccess = { statusFlow.value = "Saved copy to chosen folder." },
                onFailure = { e -> errorFlow.value = e.localizedMessage ?: "Export failed." }
            )
        }
    }

    fun clearMessages() {
        statusFlow.value = null
        errorFlow.value = null
    }
}
