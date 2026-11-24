package com.example.features.tools

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.engines.ImageEditConfig
import com.example.core.engines.ImageFilterPreset
import com.example.core.engines.ImageInfoResult
import com.example.core.engines.ImageProcessingEngine
import com.example.core.engines.OcrEngine
import com.example.core.engines.PdfProcessingEngine
import com.example.core.model.ImageOutputFormat
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.ToolExecutionState
import com.example.core.model.WorkspaceFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class ImageToolMode(val label: String) {
    STUDIO("Edit Studio"),
    CONVERT("Convert Format"),
    RESIZE("Resize & ID"),
    COMPRESS("KB Compress"),
    COLLAGE("Collage"),
    OCR("OCR Text"),
    EXIF("EXIF & Privacy"),
    BATCH("Batch Export");

    companion object {
        fun fromRouteKey(raw: String): ImageToolMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STUDIO
    }
}

data class ImageStudioResult(
    val title: String,
    val summary: String,
    val primaryOutput: WorkspaceFile? = null,
    val multipleOutputs: List<WorkspaceFile> = emptyList(),
    val extractedText: String? = null
)

data class ImageStudioUiState(
    val mode: ImageToolMode = ImageToolMode.STUDIO,
    val selectedFile: WorkspaceFile? = null,
    val multiFiles: List<WorkspaceFile> = emptyList(),
    val imageInfo: ImageInfoResult? = null,
    val originalPreview: Bitmap? = null,
    val editedPreview: Bitmap? = null,
    val showBeforeOriginal: Boolean = false,
    val config: ImageEditConfig = ImageEditConfig(),
    val undoStack: List<ImageEditConfig> = emptyList(),
    val redoStack: List<ImageEditConfig> = emptyList(),
    val targetKbText: String = "",
    val widthInput: String = "",
    val heightInput: String = "",
    val percentInput: String = "100",
    val activeCropLabel: String = "Full",
    val executionState: ToolExecutionState<ImageStudioResult> = ToolExecutionState.Idle,
    val infoMessage: String? = null
)

class ImageStudioViewModel(
    private val fileRepository: WorkspaceFileRepository,
    private val pdfEngine: PdfProcessingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageStudioUiState())
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()
    private var activeJob: Job? = null
    private var previewJob: Job? = null

    fun setInitialMode(modeKey: String, fileId: String? = null) {
        val mode = ImageToolMode.fromRouteKey(modeKey)
        _uiState.update { it.copy(mode = mode) }
        if (!fileId.isNullOrBlank()) {
            viewModelScope.launch {
                val files = fileRepository.activeFiles.first()
                files.firstOrNull { it.id == fileId }?.let { selectWorkspaceFile(it) }
            }
        }
    }

    fun selectMode(mode: ImageToolMode) {
        _uiState.update { it.copy(mode = mode, executionState = ToolExecutionState.Idle, infoMessage = null) }
    }

    fun importPrimaryImage(uri: Uri) {
        viewModelScope.launch {
            fileRepository.importUriToWorkspace(uri, folderName = "Images").fold(
                onSuccess = { imported -> selectWorkspaceFile(imported) },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            executionState = ToolExecutionState.Error(
                                "Cannot Open Image",
                                e.localizedMessage ?: "Failed to import selected image."
                            )
                        )
                    }
                }
            )
        }
    }

    fun selectWorkspaceFile(file: WorkspaceFile) {
        viewModelScope.launch {
            val diskFile = File(file.localPath)
            val info = ImageProcessingEngine.inspectImage(diskFile, file.mimeType).getOrNull()
            val origBmp = runCatching { ImageProcessingEngine.decodeSampledBitmap(diskFile, 900) }.getOrNull()
            val freshConfig = ImageEditConfig()
            _uiState.update {
                it.copy(
                    selectedFile = file,
                    imageInfo = info,
                    originalPreview = origBmp,
                    editedPreview = origBmp,
                    showBeforeOriginal = false,
                    config = freshConfig,
                    undoStack = emptyList(),
                    redoStack = emptyList(),
                    widthInput = (info?.widthPx ?: file.widthPx).takeIf { w -> w > 0 }?.toString().orEmpty(),
                    heightInput = (info?.heightPx ?: file.heightPx).takeIf { h -> h > 0 }?.toString().orEmpty(),
                    percentInput = "100",
                    activeCropLabel = "Full",
                    executionState = ToolExecutionState.Idle,
                    infoMessage = null
                )
            }
        }
    }

    fun importMultiImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = mutableListOf<WorkspaceFile>()
            uris.forEach { uri ->
                fileRepository.importUriToWorkspace(uri, folderName = "Images").onSuccess { imported.add(it) }
            }
            _uiState.update { s ->
                s.copy(
                    multiFiles = s.multiFiles + imported,
                    infoMessage = if (imported.isNotEmpty()) "Added ${imported.size} image(s)." else null
                )
            }
        }
    }

    fun removeMultiFile(id: String) {
        _uiState.update { s -> s.copy(multiFiles = s.multiFiles.filterNot { it.id == id }) }
    }

    fun updateConfig(transform: (ImageEditConfig) -> ImageEditConfig) {
        val prev = _uiState.value.config
        val next = transform(prev)
        if (prev == next) return
        _uiState.update {
            it.copy(
                config = next,
                undoStack = (it.undoStack + prev).takeLast(20),
                redoStack = emptyList()
            )
        }
        refreshPreview(next)
    }

    fun undo() {
        val stack = _uiState.value.undoStack
        if (stack.isEmpty()) return
        val previous = stack.last()
        val current = _uiState.value.config
        _uiState.update {
            it.copy(
                config = previous,
                undoStack = stack.dropLast(1),
                redoStack = (it.redoStack + current).takeLast(20)
            )
        }
        refreshPreview(previous)
    }

    fun redo() {
        val stack = _uiState.value.redoStack
        if (stack.isEmpty()) return
        val next = stack.last()
        val current = _uiState.value.config
        _uiState.update {
            it.copy(
                config = next,
                redoStack = stack.dropLast(1),
                undoStack = (it.undoStack + current).takeLast(20)
            )
        }
        refreshPreview(next)
    }

    fun resetEdits() {
        updateConfig { ImageEditConfig(outputFormat = it.outputFormat) }
        _uiState.update { it.copy(activeCropLabel = "Full", targetKbText = "", percentInput = "100") }
    }

    fun toggleBeforeAfter(showOriginal: Boolean) {
        _uiState.update { it.copy(showBeforeOriginal = showOriginal) }
    }

    fun applyCropPreset(label: String) {
        val (l, t, r, b) = when (label) {
            "1:1 Square" -> listOf(0.12f, 0.12f, 0.88f, 0.88f)
            "4:3" -> listOf(0.05f, 0.12f, 0.95f, 0.88f)
            "16:9" -> listOf(0.0f, 0.22f, 1.0f, 0.78f)
            "3:4 Portrait" -> listOf(0.14f, 0.04f, 0.86f, 0.96f)
            "Passport 35:45" -> listOf(0.15f, 0.05f, 0.85f, 0.95f)
            else -> listOf(0f, 0f, 1f, 1f)
        }
        _uiState.update { it.copy(activeCropLabel = label) }
        updateConfig {
            it.copy(cropLeftFrac = l, cropTopFrac = t, cropRightFrac = r, cropBottomFrac = b)
        }
    }

    fun applyPassportHelper(widthPx: Int, heightPx: Int) {
        _uiState.update {
            it.copy(
                widthInput = widthPx.toString(),
                heightInput = heightPx.toString(),
                percentInput = "100"
            )
        }
        updateConfig { it.copy(targetWidthPx = widthPx, targetHeightPx = heightPx, resizePercent = null) }
    }

    fun updateResizeFields(wText: String, hText: String, pctText: String) {
        _uiState.update { it.copy(widthInput = wText, heightInput = hText, percentInput = pctText) }
        val w = wText.toIntOrNull()?.takeIf { it > 0 }
        val h = hText.toIntOrNull()?.takeIf { it > 0 }
        val pct = pctText.toIntOrNull()?.takeIf { it in 10..200 && it != 100 }
        updateConfig { it.copy(targetWidthPx = w, targetHeightPx = h, resizePercent = pct) }
    }

    fun updateTargetKb(kbText: String) {
        val cleaned = kbText.filter { it.isDigit() }
        val kb = cleaned.toLongOrNull()?.takeIf { it > 0 }
        _uiState.update { it.copy(targetKbText = cleaned) }
        updateConfig { it.copy(targetMaxBytes = kb?.times(1024L)) }
    }

    private fun refreshPreview(config: ImageEditConfig) {
        val file = _uiState.value.selectedFile ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            ImageProcessingEngine.renderPreviewBitmap(File(file.localPath), config, maxPreviewDim = 760)
                .onSuccess { bmp ->
                    _uiState.update { it.copy(editedPreview = bmp) }
                }
        }
    }

    fun saveProcessedImage() {
        val file = _uiState.value.selectedFile ?: return
        val cfg = _uiState.value.config
        runTask("Process Image") {
            val base = file.displayName.substringBeforeLast('.')
            val outName = "${base}_edited.${cfg.outputFormat.extension}"
            val temp = fileRepository.createTempExportFile(outName)
            ImageProcessingEngine.processAndSave(
                input = File(file.localPath),
                output = temp,
                config = cfg,
                onProgress = { pct -> updateProgress("Applying edits & encoding...", pct) }
            ).getOrThrow()
            val saved = fileRepository.registerProcessedOutput(
                outputFile = temp,
                displayName = outName,
                mimeType = cfg.outputFormat.mimeType,
                folderName = "Images",
                toolName = "Image Studio (${cfg.outputFormat.label})",
                inputName = file.displayName,
                originalSizeBytes = file.sizeBytes
            )
            ImageStudioResult(
                title = "Image Saved",
                summary = "${saved.displayName} • ${saved.widthPx}×${saved.heightPx} px • ${saved.sizeBytes / 1024} KB (original kept safe).",
                primaryOutput = saved
            )
        }
    }

    fun convertSelectedImageToPdf() {
        val file = _uiState.value.selectedFile ?: return
        runTask("Image to PDF") {
            val outName = "${file.displayName.substringBeforeLast('.')}.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.imagesToPdf(
                imageFiles = listOf(File(file.localPath)),
                output = temp,
                pageSize = PdfPageSizeOption.A4,
                onProgress = { pct -> updateProgress("Creating PDF...", pct) }
            ).getOrThrow()
            val saved = fileRepository.registerProcessedOutput(
                outputFile = temp,
                displayName = outName,
                mimeType = "application/pdf",
                folderName = "PDFs",
                toolName = "Image to PDF",
                inputName = file.displayName,
                originalSizeBytes = file.sizeBytes
            )
            ImageStudioResult(
                title = "PDF Created",
                summary = "Saved ${saved.displayName} (${saved.pageCount} page) to My Files.",
                primaryOutput = saved
            )
        }
    }

    fun createCollage() {
        val list = _uiState.value.multiFiles
        if (list.size !in 2..4) {
            setError("Select 2 to 4 Photos", "Add between 2 and 4 images to build a grid collage.")
            return
        }
        val fmt = _uiState.value.config.outputFormat
        runTask("Collage Maker") {
            val outName = "collage_${System.currentTimeMillis().toString().takeLast(5)}.${fmt.extension}"
            val temp = fileRepository.createTempExportFile(outName)
            ImageProcessingEngine.createCollage(
                imageFiles = list.map { File(it.localPath) },
                output = temp,
                format = fmt
            ).getOrThrow()
            val saved = fileRepository.registerProcessedOutput(
                outputFile = temp,
                displayName = outName,
                mimeType = fmt.mimeType,
                folderName = "Images",
                toolName = "Collage Maker",
                inputName = "${list.size} photos",
                originalSizeBytes = list.sumOf { it.sizeBytes }
            )
            ImageStudioResult(
                title = "Collage Created",
                summary = "Combined ${list.size} photos into ${saved.displayName}.",
                primaryOutput = saved
            )
        }
    }

    fun runOcr() {
        val file = _uiState.value.selectedFile ?: return
        runTask("OCR Extract Text") {
            val text = OcrEngine.recognizeFromImageFile(File(file.localPath)).getOrThrow()
                .ifBlank { "No text detected in this image." }
            val txtName = "${file.displayName.substringBeforeLast('.')}_ocr.txt"
            val tempTxt = fileRepository.createTempExportFile(txtName).apply { writeText(text) }
            val saved = fileRepository.registerProcessedOutput(
                outputFile = tempTxt,
                displayName = txtName,
                mimeType = "text/plain",
                folderName = "Documents",
                toolName = "Image OCR",
                inputName = file.displayName,
                originalSizeBytes = file.sizeBytes
            )
            ImageStudioResult(
                title = "Text Extracted (On-Device)",
                summary = "Detected ${text.length} characters and saved $txtName.",
                primaryOutput = saved,
                extractedText = text
            )
        }
    }

    fun stripExif() {
        val file = _uiState.value.selectedFile ?: return
        val fmt = _uiState.value.config.outputFormat
        runTask("Strip EXIF Metadata") {
            val outName = "${file.displayName.substringBeforeLast('.')}_clean.${fmt.extension}"
            val temp = fileRepository.createTempExportFile(outName)
            ImageProcessingEngine.stripExifMetadata(File(file.localPath), temp, fmt, 92).getOrThrow()
            val saved = fileRepository.registerProcessedOutput(
                outputFile = temp,
                displayName = outName,
                mimeType = fmt.mimeType,
                folderName = "Images",
                toolName = "EXIF Privacy Strip",
                inputName = file.displayName,
                originalSizeBytes = file.sizeBytes
            )
            ImageStudioResult(
                title = "EXIF Metadata Stripped",
                summary = "Camera, timestamp, and GPS tags removed in ${saved.displayName}.",
                primaryOutput = saved
            )
        }
    }

    fun runBatchExport() {
        val list = _uiState.value.multiFiles
        if (list.isEmpty()) {
            setError("Batch Queue Empty", "Add images to batch convert or compress.")
            return
        }
        val cfg = _uiState.value.config
        runTask("Batch Image Export") {
            val outputs = mutableListOf<WorkspaceFile>()
            list.forEachIndexed { idx, wf ->
                updateProgress("Processing ${idx + 1}/${list.size}: ${wf.displayName}", ((idx + 1) * 100) / list.size)
                val outName = "${wf.displayName.substringBeforeLast('.')}_batch.${cfg.outputFormat.extension}"
                val temp = fileRepository.createTempExportFile(outName)
                ImageProcessingEngine.processAndSave(File(wf.localPath), temp, cfg).getOrThrow()
                outputs.add(
                    fileRepository.registerProcessedOutput(
                        outputFile = temp,
                        displayName = outName,
                        mimeType = cfg.outputFormat.mimeType,
                        folderName = "Images",
                        toolName = "Batch Image Export",
                        inputName = wf.displayName,
                        originalSizeBytes = wf.sizeBytes
                    )
                )
            }
            ImageStudioResult(
                title = "Batch Complete (${outputs.size} Images)",
                summary = "Exported all images as ${cfg.outputFormat.label} with originals preserved.",
                primaryOutput = outputs.firstOrNull(),
                multipleOutputs = outputs
            )
        }
    }

    fun exportToUri(path: String, uri: Uri) {
        viewModelScope.launch {
            fileRepository.exportToUri(path, uri).fold(
                onSuccess = { _uiState.update { it.copy(infoMessage = "Saved copy to chosen folder.") } },
                onFailure = { e -> _uiState.update { it.copy(infoMessage = e.localizedMessage ?: "Export failed.") } }
            )
        }
    }

    fun cancelProcessing() {
        activeJob?.cancel()
        _uiState.update { it.copy(executionState = ToolExecutionState.Idle, infoMessage = "Cancelled.") }
    }

    fun clearMessages() {
        _uiState.update { it.copy(infoMessage = null, executionState = ToolExecutionState.Idle) }
    }

    private fun runTask(name: String, block: suspend () -> ImageStudioResult) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(executionState = ToolExecutionState.Processing("$name...", 10)) }
            runCatching { block() }.fold(
                onSuccess = { res -> _uiState.update { it.copy(executionState = ToolExecutionState.Success(res)) } },
                onFailure = { e -> setError("$name Failed", e.localizedMessage ?: "Image processing failed.") }
            )
        }
    }

    private fun updateProgress(stage: String, pct: Int) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Processing(stage, pct)) }
    }

    private fun setError(title: String, message: String) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Error(title, message)) }
    }

    companion object {
        val MATTE_COLORS = listOf(
            "Transparent" to null,
            "White" to AndroidColor.WHITE,
            "Soft Mint" to AndroidColor.rgb(227, 242, 232),
            "Light Blue" to AndroidColor.rgb(222, 239, 252)
        )
    }
}
