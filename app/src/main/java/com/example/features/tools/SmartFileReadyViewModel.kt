package com.example.features.tools

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.data.local.SmartPresetDao
import com.example.core.data.local.SmartPresetEntity
import com.example.core.data.remote.CloudSyncRepository
import com.example.core.engines.SmartFileReadyEngine
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.SmartPreset
import com.example.core.model.SmartReadyResult
import com.example.core.model.SmartTargetFormat
import com.example.core.model.ToolExecutionState
import com.example.core.model.WorkspaceFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class SmartFileReadyUiState(
    val presets: List<SmartPreset> = emptyList(),
    val selectedPreset: SmartPreset? = null,
    val editableSpec: SmartPreset = SmartPreset(name = "Custom Requirements", description = "Define your exact target rules"),
    val selectedFile: WorkspaceFile? = null,
    val executionState: ToolExecutionState<SmartReadyResult> = ToolExecutionState.Idle,
    val infoMessage: String? = null
)

class SmartFileReadyViewModel(
    private val presetDao: SmartPresetDao,
    private val fileRepository: WorkspaceFileRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val engine: SmartFileReadyEngine
) : ViewModel() {

    private val selectedPresetFlow = MutableStateFlow<SmartPreset?>(null)
    private val editableSpecFlow = MutableStateFlow(
        SmartPreset(
            id = "custom_active",
            name = "Custom Requirements",
            description = "Configure exact KB limit, dimensions, and format."
        )
    )
    private val selectedFileFlow = MutableStateFlow<WorkspaceFile?>(null)
    private val executionFlow = MutableStateFlow<ToolExecutionState<SmartReadyResult>>(ToolExecutionState.Idle)
    private val infoMessageFlow = MutableStateFlow<String?>(null)
    private var activeJob: Job? = null

    val uiState: StateFlow<SmartFileReadyUiState> = combine(
        presetDao.observeAll(),
        selectedPresetFlow,
        editableSpecFlow,
        selectedFileFlow,
        executionFlow,
        infoMessageFlow
    ) { vals ->
        @Suppress("UNCHECKED_CAST")
        val entities = vals[0] as List<SmartPresetEntity>
        val presets = entities.map { it.toDomain() }
        val chosen = (vals[1] as SmartPreset?) ?: presets.firstOrNull()
        val spec = vals[2] as SmartPreset
        val file = vals[3] as WorkspaceFile?
        @Suppress("UNCHECKED_CAST")
        val exec = vals[4] as ToolExecutionState<SmartReadyResult>
        val info = vals[5] as String?

        SmartFileReadyUiState(
            presets = presets,
            selectedPreset = chosen,
            editableSpec = if (vals[1] == null && chosen != null) chosen else spec,
            selectedFile = file,
            executionState = exec,
            infoMessage = info
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SmartFileReadyUiState())

    fun selectPreset(preset: SmartPreset) {
        selectedPresetFlow.value = preset
        editableSpecFlow.value = preset
        executionFlow.value = ToolExecutionState.Idle
    }

    fun updateSpec(transform: (SmartPreset) -> SmartPreset) {
        val updated = transform(editableSpecFlow.value)
        editableSpecFlow.value = updated
        if (selectedPresetFlow.value == null && uiState.value.presets.isNotEmpty()) {
            selectedPresetFlow.value = uiState.value.presets.first()
        }
    }

    fun importFileFromUri(uri: Uri) {
        viewModelScope.launch {
            fileRepository.importUriToWorkspace(uri, folderName = "SmartReady").fold(
                onSuccess = { imported ->
                    selectedFileFlow.value = imported
                    executionFlow.value = ToolExecutionState.Ready("File ready for processing")
                },
                onFailure = { e ->
                    executionFlow.value = ToolExecutionState.Error(
                        "Unable to Load File",
                        e.localizedMessage ?: "Failed to import selected file."
                    )
                }
            )
        }
    }

    fun selectWorkspaceFile(file: WorkspaceFile) {
        selectedFileFlow.value = file
        executionFlow.value = ToolExecutionState.Ready("File ready for processing")
    }

    fun runProcessing() {
        val file = selectedFileFlow.value ?: run {
            executionFlow.value = ToolExecutionState.Error("No File Selected", "Pick an image or PDF first.")
            return
        }
        val spec = editableSpecFlow.value
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            executionFlow.value = ToolExecutionState.Processing("Preparing output...", 5)
            val base = file.displayName.substringBeforeLast('.')
            val outName = "${base}_ready.${spec.targetFormat.extension}"
            val tempOut = fileRepository.createTempExportFile(outName)

            engine.processAndValidate(
                input = file,
                preset = spec,
                outputFile = tempOut,
                onProgress = { pct ->
                    executionFlow.value = ToolExecutionState.Processing(
                        stage = if (pct < 90) "Optimizing to requirements..." else "Verifying output file...",
                        progressPercent = pct
                    )
                }
            ).fold(
                onSuccess = { report ->
                    val savedFile = fileRepository.registerProcessedOutput(
                        outputFile = File(report.outputPath),
                        displayName = outName,
                        mimeType = report.outputMimeType,
                        folderName = "SmartReady",
                        toolName = "Smart File Ready (${spec.name})",
                        inputName = file.displayName,
                        originalSizeBytes = file.sizeBytes
                    )
                    executionFlow.value = ToolExecutionState.Success(
                        report.copy(outputPath = savedFile.localPath, outputName = savedFile.displayName)
                    )
                },
                onFailure = { e ->
                    executionFlow.value = ToolExecutionState.Error(
                        title = "Preparation Failed",
                        message = e.localizedMessage ?: "Unable to transform file to these requirements."
                    )
                }
            )
        }
    }

    fun cancelProcessing() {
        activeJob?.cancel()
        executionFlow.value = ToolExecutionState.Idle
        infoMessageFlow.value = "Processing cancelled. Original file untouched."
    }

    fun saveCustomPreset(customName: String, syncToCloud: Boolean) {
        val trimmed = customName.trim()
        if (trimmed.isEmpty()) {
            infoMessageFlow.value = "Enter a preset name first."
            return
        }
        viewModelScope.launch {
            val newPreset = editableSpecFlow.value.copy(
                id = "custom_${UUID.randomUUID().toString().take(8)}",
                name = trimmed,
                description = "Custom saved preset",
                isBuiltIn = false
            )
            presetDao.upsert(SmartPresetEntity.fromDomain(newPreset))
            selectedPresetFlow.value = newPreset
            editableSpecFlow.value = newPreset
            if (syncToCloud) {
                cloudSyncRepository.syncSmartPreset(newPreset)
            }
            infoMessageFlow.value = "Saved custom preset \"$trimmed\"."
        }
    }

    fun deleteCustomPreset(preset: SmartPreset) {
        if (preset.isBuiltIn) return
        viewModelScope.launch {
            presetDao.deleteCustomById(preset.id)
            infoMessageFlow.value = "Deleted preset \"${preset.name}\"."
        }
    }

    fun exportToUri(path: String, destUri: Uri) {
        viewModelScope.launch {
            fileRepository.exportToUri(path, destUri).fold(
                onSuccess = { infoMessageFlow.value = "Saved verified file to chosen folder." },
                onFailure = { e -> infoMessageFlow.value = e.localizedMessage ?: "Save failed." }
            )
        }
    }

    fun clearMessage() {
        infoMessageFlow.value = null
    }
}
