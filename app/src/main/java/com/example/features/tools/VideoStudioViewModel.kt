package com.example.features.tools

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.engines.VideoEditRequest
import com.example.core.engines.VideoMetadataInfo
import com.example.core.engines.VideoProcessingEngine
import com.example.core.model.ToolExecutionState
import com.example.core.model.VideoExportPreset
import com.example.core.model.WorkspaceFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class VideoToolMode(val label: String) {
    TRIM("Trim & Export"),
    COMPRESS("Compress & Size"),
    MERGE("Merge Clips"),
    TRANSFORM("Crop & Rotate"),
    EFFECTS("Speed & Overlay"),
    AUDIO("Audio Tools"),
    INFO("Thumbnail & Info"),
    BATCH("Batch Export");

    companion object {
        fun fromRouteKey(raw: String): VideoToolMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TRIM
    }
}

data class VideoStudioResult(
    val title: String,
    val summary: String,
    val primaryOutput: WorkspaceFile? = null,
    val multipleOutputs: List<WorkspaceFile> = emptyList()
)

data class VideoStudioUiState(
    val mode: VideoToolMode = VideoToolMode.TRIM,
    val selectedFile: WorkspaceFile? = null,
    val multiFiles: List<WorkspaceFile> = emptyList(),
    val metadata: VideoMetadataInfo? = null,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val preset: VideoExportPreset = VideoExportPreset.P720,
    val customBitrateKbpsText: String = "",
    val rotationDegrees: Float = 0f,
    val cropNormalized: Boolean = false,
    val muteAudio: Boolean = false,
    val speedMultiplier: Float = 1f,
    val overlayText: String = "",
    val watermarkImageFile: WorkspaceFile? = null,
    val replacementAudioFile: WorkspaceFile? = null,
    val thumbnailTimeMs: Long = 1000L,
    val executionState: ToolExecutionState<VideoStudioResult> = ToolExecutionState.Idle,
    val infoMessage: String? = null
)

class VideoStudioViewModel(
    private val fileRepository: WorkspaceFileRepository,
    private val videoEngine: VideoProcessingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoStudioUiState())
    val uiState: StateFlow<VideoStudioUiState> = _uiState.asStateFlow()
    private var activeJob: Job? = null

    fun setInitialMode(modeKey: String, fileId: String? = null) {
        _uiState.update { it.copy(mode = VideoToolMode.fromRouteKey(modeKey)) }
        if (!fileId.isNullOrBlank()) {
            viewModelScope.launch {
                val files = fileRepository.activeFiles.first()
                files.firstOrNull { it.id == fileId }?.let { selectWorkspaceFile(it) }
            }
        }
    }

    fun selectMode(mode: VideoToolMode) {
        _uiState.update { it.copy(mode = mode, executionState = ToolExecutionState.Idle, infoMessage = null) }
    }

    fun importPrimaryVideo(uri: Uri) {
        viewModelScope.launch {
            fileRepository.importUriToWorkspace(uri, folderName = "Videos").fold(
                onSuccess = { imported -> selectWorkspaceFile(imported) },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            executionState = ToolExecutionState.Error(
                                "Cannot Open Video",
                                e.localizedMessage ?: "Failed to read selected video."
                            )
                        )
                    }
                }
            )
        }
    }

    fun selectWorkspaceFile(file: WorkspaceFile) {
        viewModelScope.launch {
            val info = videoEngine.inspectVideo(File(file.localPath)).getOrNull()
            val duration = info?.durationMs ?: file.durationMs
            _uiState.update {
                it.copy(
                    selectedFile = file,
                    metadata = info,
                    trimStartMs = 0L,
                    trimEndMs = duration.coerceAtLeast(1000L),
                    thumbnailTimeMs = (duration / 4L).coerceAtLeast(0L),
                    executionState = ToolExecutionState.Idle,
                    infoMessage = null
                )
            }
        }
    }

    fun importMultipleVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = mutableListOf<WorkspaceFile>()
            uris.forEach { uri ->
                fileRepository.importUriToWorkspace(uri, folderName = "Videos").onSuccess { imported.add(it) }
            }
            _uiState.update { s ->
                s.copy(
                    multiFiles = s.multiFiles + imported,
                    infoMessage = if (imported.isNotEmpty()) "Added ${imported.size} video(s)." else null
                )
            }
        }
    }

    fun importWatermarkImage(uri: Uri) {
        viewModelScope.launch {
            fileRepository.importUriToWorkspace(uri, folderName = "Images").onSuccess { img ->
                _uiState.update { it.copy(watermarkImageFile = img, infoMessage = "Watermark selected: ${img.displayName}") }
            }
        }
    }

    fun importReplacementAudio(uri: Uri) {
        viewModelScope.launch {
            fileRepository.importUriToWorkspace(uri, folderName = "Audio").onSuccess { aud ->
                _uiState.update { it.copy(replacementAudioFile = aud, infoMessage = "Audio track selected: ${aud.displayName}") }
            }
        }
    }

    fun moveMultiVideo(index: Int, delta: Int) {
        val list = _uiState.value.multiFiles.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        _uiState.update { it.copy(multiFiles = list) }
    }

    fun removeMultiVideo(id: String) {
        _uiState.update { s -> s.copy(multiFiles = s.multiFiles.filterNot { it.id == id }) }
    }

    fun updateSettings(transform: (VideoStudioUiState) -> VideoStudioUiState) {
        _uiState.update(transform)
    }

    fun runVideoExport(extractAudioOnly: Boolean = false) {
        val file = _uiState.value.selectedFile ?: return
        val s = _uiState.value
        val customBps = s.customBitrateKbpsText.toIntOrNull()?.takeIf { it > 50 }?.times(1000) ?: 0
        val extName = if (extractAudioOnly) "m4a" else "mp4"
        val mime = if (extractAudioOnly) "audio/mp4" else "video/mp4"
        val folder = if (extractAudioOnly) "Documents" else "Videos"
        val suffix = if (extractAudioOnly) "_audio" else "_edited"

        val request = VideoEditRequest(
            trimStartMs = s.trimStartMs,
            trimEndMs = s.trimEndMs,
            preset = s.preset,
            customBitrateBps = customBps,
            rotationDegrees = s.rotationDegrees,
            cropNormalized = s.cropNormalized,
            muteAudio = s.muteAudio,
            extractAudioOnly = extractAudioOnly,
            speedMultiplier = s.speedMultiplier,
            overlayText = s.overlayText,
            watermarkBitmapFile = s.watermarkImageFile?.let { File(it.localPath) },
            replacementAudioFile = s.replacementAudioFile?.let { File(it.localPath) }
        )

        runTask(if (extractAudioOnly) "Extract Audio" else "Export Video") {
            val outName = "${file.displayName.substringBeforeLast('.')}$suffix.$extName"
            val temp = fileRepository.createTempExportFile(outName)
            videoEngine.exportVideo(
                inputFile = File(file.localPath),
                outputFile = temp,
                request = request,
                onProgress = { pct -> updateProgress(if (extractAudioOnly) "Extracting audio..." else "Encoding video...", pct) }
            ).getOrThrow()

            val saved = fileRepository.registerProcessedOutput(
                outputFile = temp,
                displayName = outName,
                mimeType = mime,
                folderName = folder,
                toolName = if (extractAudioOnly) "Extract Audio" else "Video Studio (${s.preset.label})",
                inputName = file.displayName,
                originalSizeBytes = file.sizeBytes
            )
            VideoStudioResult(
                title = if (extractAudioOnly) "Audio Extracted" else "Video Exported",
                summary = "${saved.displayName} • ${saved.sizeBytes / 1024} KB (original kept safe).",
                primaryOutput = saved
            )
        }
    }

    fun runMergeVideos() {
        val list = _uiState.value.multiFiles
        if (list.size < 2) {
            setError("Select 2+ Clips", "Add at least two videos to merge.")
            return
        }
        val preset = _uiState.value.preset
        runTask("Merge Videos") {
            val outName = "merged_${System.currentTimeMillis().toString().takeLast(5)}.mp4"
            val temp = fileRepository.createTempExportFile(outName)
            videoEngine.mergeVideos(
                inputs = list.map { File(it.localPath) },
                outputFile = temp,
                preset = preset,
                onProgress = { pct -> updateProgress("Merging ${list.size} clips...", pct) }
            ).getOrThrow()
            val saved = fileRepository.registerProcessedOutput(
                outputFile = temp,
                displayName = outName,
                mimeType = "video/mp4",
                folderName = "Videos",
                toolName = "Merge Videos",
                inputName = "${list.size} clips",
                originalSizeBytes = list.sumOf { it.sizeBytes }
            )
            VideoStudioResult(
                title = "Clips Merged",
                summary = "Combined ${list.size} clips into ${saved.displayName}.",
                primaryOutput = saved
            )
        }
    }

    fun runExtractThumbnail() {
        val file = _uiState.value.selectedFile ?: return
        val atMs = _uiState.value.thumbnailTimeMs
        runTask("Extract Video Thumbnail") {
            val outName = "${file.displayName.substringBeforeLast('.')}_thumb.jpg"
            val temp = fileRepository.createTempExportFile(outName)
            videoEngine.extractThumbnail(File(file.localPath), temp, atMs).getOrThrow()
            val saved = fileRepository.registerProcessedOutput(
                outputFile = temp,
                displayName = outName,
                mimeType = "image/jpeg",
                folderName = "Images",
                toolName = "Video Thumbnail",
                inputName = file.displayName,
                originalSizeBytes = file.sizeBytes
            )
            VideoStudioResult(
                title = "Thumbnail Extracted",
                summary = "Saved frame at ${atMs / 1000.0}s as ${saved.displayName}.",
                primaryOutput = saved
            )
        }
    }

    fun runBatchExport() {
        val list = _uiState.value.multiFiles
        if (list.isEmpty()) {
            setError("Batch Queue Empty", "Pick videos to batch export.")
            return
        }
        val s = _uiState.value
        val req = VideoEditRequest(
            preset = s.preset,
            muteAudio = s.muteAudio,
            rotationDegrees = s.rotationDegrees
        )
        runTask("Batch Video Export") {
            val outputs = mutableListOf<WorkspaceFile>()
            list.forEachIndexed { idx, wf ->
                val outName = "${wf.displayName.substringBeforeLast('.')}_${s.preset.name.lowercase()}.mp4"
                val temp = fileRepository.createTempExportFile(outName)
                videoEngine.exportVideo(File(wf.localPath), temp, req) { pct ->
                    val overall = ((idx * 100) + pct) / list.size
                    updateProgress("Exporting ${idx + 1}/${list.size}: ${wf.displayName}", overall)
                }.getOrThrow()
                outputs.add(
                    fileRepository.registerProcessedOutput(
                        outputFile = temp,
                        displayName = outName,
                        mimeType = "video/mp4",
                        folderName = "Videos",
                        toolName = "Batch Video Export",
                        inputName = wf.displayName,
                        originalSizeBytes = wf.sizeBytes
                    )
                )
            }
            VideoStudioResult(
                title = "Batch Complete (${outputs.size} Videos)",
                summary = "Exported all queued videos at ${s.preset.label}.",
                primaryOutput = outputs.firstOrNull(),
                multipleOutputs = outputs
            )
        }
    }

    fun exportToUri(path: String, uri: Uri) {
        viewModelScope.launch {
            fileRepository.exportToUri(path, uri).fold(
                onSuccess = { _uiState.update { it.copy(infoMessage = "Saved copy to chosen location.") } },
                onFailure = { e -> _uiState.update { it.copy(infoMessage = e.localizedMessage ?: "Export failed.") } }
            )
        }
    }

    fun cancelProcessing() {
        activeJob?.cancel()
        _uiState.update { it.copy(executionState = ToolExecutionState.Idle, infoMessage = "Export cancelled cleanly.") }
    }

    fun clearMessages() {
        _uiState.update { it.copy(infoMessage = null, executionState = ToolExecutionState.Idle) }
    }

    private fun runTask(name: String, block: suspend () -> VideoStudioResult) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(executionState = ToolExecutionState.Processing("$name...", 5)) }
            runCatching { block() }.fold(
                onSuccess = { res -> _uiState.update { it.copy(executionState = ToolExecutionState.Success(res)) } },
                onFailure = { e -> setError("$name Failed", e.localizedMessage ?: "Video export failed on this device codec.") }
            )
        }
    }

    private fun updateProgress(stage: String, pct: Int) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Processing(stage, pct)) }
    }

    private fun setError(title: String, msg: String) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Error(title, msg)) }
    }
}
