package com.example.features.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.engines.PdfDocumentDetails
import com.example.core.engines.PdfPageEditSpec
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

enum class PdfToolMode(val label: String) {
    VIEW("View & Print"),
    COMPRESS("Compress"),
    MERGE("Merge"),
    SPLIT("Split & Extract"),
    PAGES("Page Editor"),
    IMG2PDF("Image to PDF"),
    PDF2IMG("PDF to Images"),
    ANNOTATE("Annotate & Sign"),
    WATERMARK("Watermark & #"),
    OCR("OCR Text"),
    SECURITY("Password Lock"),
    METADATA("Info, Size & Batch");

    companion object {
        fun fromRouteKey(raw: String): PdfToolMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: VIEW
    }
}

data class PdfPageThumbnailItem(
    val originalIndex: Int,
    val rotationDegrees: Int = 0,
    val deleted: Boolean = false,
    val thumbnail: Bitmap? = null
)

data class PdfStudioResult(
    val title: String,
    val summary: String,
    val primaryOutput: WorkspaceFile? = null,
    val multipleOutputs: List<WorkspaceFile> = emptyList(),
    val extractedText: String? = null
)

data class PdfStudioUiState(
    val mode: PdfToolMode = PdfToolMode.VIEW,
    val primaryFile: WorkspaceFile? = null,
    val multiFiles: List<WorkspaceFile> = emptyList(),
    val details: PdfDocumentDetails? = null,
    val currentPageIndex: Int = 0,
    val currentPageBitmap: Bitmap? = null,
    val pageThumbnails: List<PdfPageThumbnailItem> = emptyList(),
    val executionState: ToolExecutionState<PdfStudioResult> = ToolExecutionState.Idle,
    val infoMessage: String? = null,
    val compressQuality: Int = 72,
    val compressDpiScale: Float = 1.2f,
    val splitPagesPerFile: Int = 1,
    val extractPageSpec: String = "1",
    val img2PdfPageSize: PdfPageSizeOption = PdfPageSizeOption.A4,
    val pdf2ImgFormat: ImageOutputFormat = ImageOutputFormat.JPG,
    val watermarkText: String = "CONFIDENTIAL",
    val watermarkOpacity: Float = 0.25f,
    val addPageNumbers: Boolean = true,
    val overlayText: String = "",
    val highlightPage: Boolean = false,
    val overlayImageFile: WorkspaceFile? = null,
    val formValues: Map<String, String> = emptyMap(),
    val userPassword: String = "",
    val ownerPassword: String = "",
    val unlockPassword: String = "",
    val metaTitle: String = "",
    val metaAuthor: String = "",
    val metaSubject: String = "",
    val metaKeywords: String = "",
    val targetPageSize: PdfPageSizeOption = PdfPageSizeOption.A4,
    val landscapeOrientation: Boolean = false,
    val ocrMaxPages: Int = 5
)

class PdfStudioViewModel(
    private val fileRepository: WorkspaceFileRepository,
    private val pdfEngine: PdfProcessingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfStudioUiState())
    val uiState: StateFlow<PdfStudioUiState> = _uiState.asStateFlow()
    private var activeJob: Job? = null

    fun setInitialMode(modeKey: String, fileId: String? = null) {
        val mode = PdfToolMode.fromRouteKey(modeKey)
        _uiState.update { it.copy(mode = mode) }
        if (!fileId.isNullOrBlank()) {
            viewModelScope.launch {
                val files = fileRepository.activeFiles.first()
                files.firstOrNull { it.id == fileId }?.let { selectPrimaryFile(it) }
            }
        }
    }

    fun selectMode(mode: PdfToolMode) {
        _uiState.update { it.copy(mode = mode, executionState = ToolExecutionState.Idle, infoMessage = null) }
    }

    fun importPrimaryPdf(uri: Uri) {
        viewModelScope.launch {
            fileRepository.importUriToWorkspace(uri, folderName = "PDFs").fold(
                onSuccess = { imported -> selectPrimaryFile(imported) },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            executionState = ToolExecutionState.Error(
                                "Failed to Open PDF",
                                e.localizedMessage ?: "Unable to read selected file."
                            )
                        )
                    }
                }
            )
        }
    }

    fun selectPrimaryFile(file: WorkspaceFile) {
        _uiState.update {
            it.copy(
                primaryFile = file,
                currentPageIndex = 0,
                executionState = ToolExecutionState.Idle,
                infoMessage = null
            )
        }
        loadInspectionAndPage(file, pageIndex = 0)
    }

    fun importMultipleFiles(uris: List<Uri>, folder: String = "PDFs") {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = mutableListOf<WorkspaceFile>()
            uris.forEach { uri ->
                fileRepository.importUriToWorkspace(uri, folderName = folder).onSuccess { imported.add(it) }
            }
            _uiState.update { state ->
                state.copy(
                    multiFiles = state.multiFiles + imported,
                    infoMessage = if (imported.isNotEmpty()) "Added ${imported.size} file(s) to queue." else null
                )
            }
        }
    }

    fun importOverlayImage(uri: Uri) {
        viewModelScope.launch {
            fileRepository.importUriToWorkspace(uri, folderName = "Images").onSuccess { img ->
                _uiState.update { it.copy(overlayImageFile = img, infoMessage = "Selected overlay image: ${img.displayName}") }
            }
        }
    }

    fun removeMultiFile(id: String) {
        _uiState.update { s -> s.copy(multiFiles = s.multiFiles.filterNot { it.id == id }) }
    }

    fun moveMultiFile(index: Int, delta: Int) {
        val list = _uiState.value.multiFiles.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        _uiState.update { it.copy(multiFiles = list) }
    }

    fun clearMultiFiles() {
        _uiState.update { it.copy(multiFiles = emptyList()) }
    }

    private fun loadInspectionAndPage(file: WorkspaceFile, pageIndex: Int) {
        viewModelScope.launch {
            val diskFile = File(file.localPath)
            val details = pdfEngine.inspectPdf(diskFile).getOrNull()
            val bmp = pdfEngine.renderPageBitmap(diskFile, pageIndex, targetWidthPx = 920).getOrNull()
            val thumbs = if (details != null && details.pageCount in 1..24) {
                (0 until details.pageCount).map { idx ->
                    val thumbBmp = pdfEngine.renderPageBitmap(diskFile, idx, targetWidthPx = 220).getOrNull()
                    PdfPageThumbnailItem(originalIndex = idx, thumbnail = thumbBmp)
                }
            } else {
                val count = (details?.pageCount ?: file.pageCount).coerceAtLeast(1)
                (0 until count).map { PdfPageThumbnailItem(originalIndex = it) }
            }

            _uiState.update { s ->
                s.copy(
                    details = details,
                    currentPageIndex = pageIndex,
                    currentPageBitmap = bmp,
                    pageThumbnails = thumbs,
                    metaTitle = details?.title.orEmpty(),
                    metaAuthor = details?.author.orEmpty(),
                    metaSubject = details?.subject.orEmpty(),
                    metaKeywords = details?.keywords.orEmpty(),
                    formValues = details?.formFieldNames?.associateWith { s.formValues[it].orEmpty() } ?: emptyMap()
                )
            }
        }
    }

    fun goToPage(newIndex: Int) {
        val file = _uiState.value.primaryFile ?: return
        val maxPages = (_uiState.value.details?.pageCount ?: file.pageCount).coerceAtLeast(1)
        val clamped = newIndex.coerceIn(0, maxPages - 1)
        viewModelScope.launch {
            val bmp = pdfEngine.renderPageBitmap(File(file.localPath), clamped, targetWidthPx = 920).getOrNull()
            _uiState.update { it.copy(currentPageIndex = clamped, currentPageBitmap = bmp) }
        }
    }

    fun rotatePageThumb(index: Int) {
        _uiState.update { s ->
            val list = s.pageThumbnails.toMutableList()
            if (index in list.indices) {
                val curr = list[index]
                list[index] = curr.copy(rotationDegrees = (curr.rotationDegrees + 90) % 360)
            }
            s.copy(pageThumbnails = list)
        }
    }

    fun toggleDeletePageThumb(index: Int) {
        _uiState.update { s ->
            val list = s.pageThumbnails.toMutableList()
            if (index in list.indices) {
                val curr = list[index]
                val keptCount = list.count { !it.deleted }
                if (!curr.deleted && keptCount <= 1) {
                    return@update s.copy(infoMessage = "At least one page must remain in the PDF.")
                }
                list[index] = curr.copy(deleted = !curr.deleted)
            }
            s.copy(pageThumbnails = list)
        }
    }

    fun movePageThumb(index: Int, delta: Int) {
        _uiState.update { s ->
            val list = s.pageThumbnails.toMutableList()
            val target = index + delta
            if (index !in list.indices || target !in list.indices) return@update s
            val item = list.removeAt(index)
            list.add(target, item)
            s.copy(pageThumbnails = list)
        }
    }

    fun updateSettings(transform: (PdfStudioUiState) -> PdfStudioUiState) {
        _uiState.update(transform)
    }

    fun runCompress() {
        val file = _uiState.value.primaryFile ?: return
        val s = _uiState.value
        runPdfTask("Compress PDF", file) {
            val outName = "${file.displayName.substringBeforeLast('.')}_compressed.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.compressPdf(
                input = File(file.localPath),
                output = temp,
                qualityPercent = s.compressQuality,
                targetDpiScale = s.compressDpiScale,
                onProgress = { pct -> updateProgress("Compressing pages...", pct) }
            ).getOrThrow()
            val saved = registerPdf(temp, outName, "Compress PDF", file.displayName, file.sizeBytes)
            PdfStudioResult(
                title = "PDF Compressed",
                summary = "Original: ${file.sizeBytes / 1024} KB → New: ${saved.sizeBytes / 1024} KB (original kept intact).",
                primaryOutput = saved
            )
        }
    }

    fun runMerge() {
        val list = _uiState.value.multiFiles
        if (list.size < 2) {
            setError("Need More PDFs", "Select at least two PDF files to merge.")
            return
        }
        runPdfTask("Merge PDF", list.first()) {
            val outName = "merged_${System.currentTimeMillis().toString().takeLast(5)}.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.mergePdfs(
                inputs = list.map { File(it.localPath) },
                output = temp,
                onProgress = { pct -> updateProgress("Merging ${list.size} PDFs...", pct) }
            ).getOrThrow()
            val totalOrig = list.sumOf { it.sizeBytes }
            val saved = registerPdf(temp, outName, "Merge PDF", "${list.size} files", totalOrig)
            PdfStudioResult(
                title = "PDFs Merged",
                summary = "Combined ${list.size} PDFs into ${saved.displayName} (${saved.pageCount} pages).",
                primaryOutput = saved
            )
        }
    }

    fun runSplitRanges() {
        val file = _uiState.value.primaryFile ?: return
        val perFile = _uiState.value.splitPagesPerFile.coerceAtLeast(1)
        runPdfTask("Split PDF", file) {
            val base = file.displayName.substringBeforeLast('.')
            val tempDir = fileRepository.createTempExportFile("split_dir").apply { mkdirs() }
            val parts = pdfEngine.splitPdfIntoRanges(File(file.localPath), tempDir, base, perFile).getOrThrow()
            val savedParts = parts.map { part ->
                registerPdf(part, part.name, "Split PDF", file.displayName, file.sizeBytes)
            }
            PdfStudioResult(
                title = "Split Complete",
                summary = "Created ${savedParts.size} separate PDF file(s) in your workspace.",
                primaryOutput = savedParts.firstOrNull(),
                multipleOutputs = savedParts
            )
        }
    }

    fun runExtractPages() {
        val file = _uiState.value.primaryFile ?: return
        val pages = parsePageSpec(_uiState.value.extractPageSpec)
        if (pages.isEmpty()) {
            setError("Invalid Page Range", "Enter page numbers such as 1,3,5-7.")
            return
        }
        runPdfTask("Extract PDF Pages", file) {
            val outName = "${file.displayName.substringBeforeLast('.')}_extracted.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.extractPages(File(file.localPath), temp, pages).getOrThrow()
            val saved = registerPdf(temp, outName, "Extract Pages", file.displayName, file.sizeBytes)
            PdfStudioResult(
                title = "Pages Extracted",
                summary = "Saved ${saved.pageCount} page(s) to ${saved.displayName}.",
                primaryOutput = saved
            )
        }
    }

    fun runApplyPageEdits() {
        val file = _uiState.value.primaryFile ?: return
        val specs = _uiState.value.pageThumbnails.map {
            PdfPageEditSpec(it.originalIndex, it.rotationDegrees, !it.deleted)
        }
        runPdfTask("Edit PDF Pages", file) {
            val outName = "${file.displayName.substringBeforeLast('.')}_edited.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.applyPageEdits(File(file.localPath), temp, specs).getOrThrow()
            val saved = registerPdf(temp, outName, "Page Editor", file.displayName, file.sizeBytes)
            PdfStudioResult(
                title = "Page Edits Saved",
                summary = "Saved reordered/rotated PDF (${saved.pageCount} pages).",
                primaryOutput = saved
            )
        }
    }

    fun runImagesToPdf() {
        val imgs = _uiState.value.multiFiles
        if (imgs.isEmpty()) {
            setError("No Images Selected", "Pick one or more images to convert to PDF.")
            return
        }
        val pageSize = _uiState.value.img2PdfPageSize
        runPdfTask("Image to PDF", imgs.first()) {
            val outName = "scan_${System.currentTimeMillis().toString().takeLast(5)}.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.imagesToPdf(
                imageFiles = imgs.map { File(it.localPath) },
                output = temp,
                pageSize = pageSize,
                onProgress = { pct -> updateProgress("Building PDF pages...", pct) }
            ).getOrThrow()
            val saved = registerPdf(temp, outName, "Image to PDF", "${imgs.size} image(s)", imgs.sumOf { it.sizeBytes })
            PdfStudioResult(
                title = "PDF Created from Images",
                summary = "Created ${saved.displayName} (${saved.pageCount} pages).",
                primaryOutput = saved
            )
        }
    }

    fun runPdfToImages() {
        val file = _uiState.value.primaryFile ?: return
        val fmt = _uiState.value.pdf2ImgFormat
        runPdfTask("PDF to Images", file) {
            val base = file.displayName.substringBeforeLast('.')
            val tempDir = fileRepository.createTempExportFile("pdf2img_dir").apply { mkdirs() }
            val outs = pdfEngine.pdfToImages(
                input = File(file.localPath),
                outputDir = tempDir,
                baseName = base,
                format = fmt,
                onProgress = { pct -> updateProgress("Rendering pages...", pct) }
            ).getOrThrow()
            val savedImgs = outs.map { f ->
                fileRepository.registerProcessedOutput(
                    outputFile = f,
                    displayName = f.name,
                    mimeType = fmt.mimeType,
                    folderName = "Images",
                    toolName = "PDF to ${fmt.label}",
                    inputName = file.displayName,
                    originalSizeBytes = file.sizeBytes
                )
            }
            PdfStudioResult(
                title = "Exported ${savedImgs.size} Image(s)",
                summary = "All pages exported as ${fmt.label} to My Files.",
                primaryOutput = savedImgs.firstOrNull(),
                multipleOutputs = savedImgs
            )
        }
    }

    fun runWatermarkAndPageNumbers() {
        val file = _uiState.value.primaryFile ?: return
        val s = _uiState.value
        runPdfTask("Watermark PDF", file) {
            val outName = "${file.displayName.substringBeforeLast('.')}_stamped.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.addWatermarkAndPageNumbers(
                input = File(file.localPath),
                output = temp,
                watermarkText = s.watermarkText,
                addPageNumbers = s.addPageNumbers,
                opacity = s.watermarkOpacity
            ).getOrThrow()
            val saved = registerPdf(temp, outName, "Watermark & Numbers", file.displayName, file.sizeBytes)
            PdfStudioResult(
                title = "Watermark Applied",
                summary = "Saved ${saved.displayName} with stamp and page numbering.",
                primaryOutput = saved
            )
        }
    }

    fun runAnnotateAndSign(signatureStrokes: List<List<Pair<Float, Float>>>, canvasWidth: Float, canvasHeight: Float) {
        val file = _uiState.value.primaryFile ?: return
        val s = _uiState.value
        runPdfTask("Sign & Annotate PDF", file) {
            val sigBitmap = if (signatureStrokes.isNotEmpty() && canvasWidth > 0f && canvasHeight > 0f) {
                renderSignatureBitmap(signatureStrokes, canvasWidth, canvasHeight)
            } else null

            try {
                val outName = "${file.displayName.substringBeforeLast('.')}_signed.pdf"
                val temp = fileRepository.createTempExportFile(outName)
                pdfEngine.addAnnotationOrSignature(
                    input = File(file.localPath),
                    output = temp,
                    pageIndex = s.currentPageIndex,
                    overlayText = s.overlayText,
                    highlightText = s.highlightPage,
                    signatureBitmap = sigBitmap,
                    overlayImageFile = s.overlayImageFile?.let { File(it.localPath) }
                ).getOrThrow()
                val saved = registerPdf(temp, outName, "Sign & Annotate PDF", file.displayName, file.sizeBytes)
                PdfStudioResult(
                    title = "Annotation & Signature Saved",
                    summary = "Applied edits on page ${s.currentPageIndex + 1} into ${saved.displayName}.",
                    primaryOutput = saved
                )
            } finally {
                sigBitmap?.recycle()
            }
        }
    }

    fun runFillFormFields() {
        val file = _uiState.value.primaryFile ?: return
        val values = _uiState.value.formValues
        if (values.isEmpty()) {
            setError("No AcroForm Fields", "This PDF has no detected fillable AcroForm fields.")
            return
        }
        runPdfTask("Fill PDF Form", file) {
            val outName = "${file.displayName.substringBeforeLast('.')}_filled.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.fillFormFields(File(file.localPath), temp, values).getOrThrow()
            val saved = registerPdf(temp, outName, "Fill PDF Form", file.displayName, file.sizeBytes)
            PdfStudioResult(
                title = "Form Fields Saved",
                summary = "Updated ${values.size} AcroForm field(s) in ${saved.displayName}.",
                primaryOutput = saved
            )
        }
    }

    fun runOcrPdf() {
        val file = _uiState.value.primaryFile ?: return
        val maxPages = _uiState.value.ocrMaxPages
        runPdfTask("OCR PDF", file) {
            val text = pdfEngine.extractOcrTextFromPdf(
                input = File(file.localPath),
                maxPages = maxPages,
                onProgress = { pct -> updateProgress("Running ML Kit OCR...", pct) }
            ).getOrThrow()
            val txtName = "${file.displayName.substringBeforeLast('.')}_ocr.txt"
            val tempTxt = fileRepository.createTempExportFile(txtName).apply { writeText(text) }
            val savedTxt = fileRepository.registerProcessedOutput(
                outputFile = tempTxt,
                displayName = txtName,
                mimeType = "text/plain",
                folderName = "Documents",
                toolName = "OCR PDF",
                inputName = file.displayName,
                originalSizeBytes = file.sizeBytes
            )
            PdfStudioResult(
                title = "OCR Extraction Complete",
                summary = "Extracted ${text.length} characters and saved $txtName.",
                primaryOutput = savedTxt,
                extractedText = text
            )
        }
    }

    fun runProtectPdf() {
        val file = _uiState.value.primaryFile ?: return
        val s = _uiState.value
        if (s.userPassword.isBlank()) {
            setError("Password Required", "Enter a user password to encrypt this PDF.")
            return
        }
        runPdfTask("Protect PDF", file) {
            val outName = "${file.displayName.substringBeforeLast('.')}_protected.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.protectPdfWithPassword(File(file.localPath), temp, s.userPassword, s.ownerPassword).getOrThrow()
            val saved = registerPdf(temp, outName, "Protect PDF", file.displayName, file.sizeBytes)
            PdfStudioResult(
                title = "PDF Encrypted (128-bit)",
                summary = "Saved password-protected PDF as ${saved.displayName}.",
                primaryOutput = saved
            )
        }
    }

    fun runUnlockPdf() {
        val file = _uiState.value.primaryFile ?: return
        val pwd = _uiState.value.unlockPassword
        if (pwd.isBlank()) {
            setError("Authorized Password Needed", "You must enter the authorized password to remove encryption.")
            return
        }
        runPdfTask("Unlock PDF", file) {
            val outName = "${file.displayName.substringBeforeLast('.')}_unlocked.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.removePdfPassword(File(file.localPath), temp, pwd).getOrThrow()
            val saved = registerPdf(temp, outName, "Remove PDF Password", file.displayName, file.sizeBytes)
            PdfStudioResult(
                title = "Password Removed",
                summary = "Unlocked copy saved as ${saved.displayName}.",
                primaryOutput = saved
            )
        }
    }

    fun runUpdateMetadata() {
        val file = _uiState.value.primaryFile ?: return
        val s = _uiState.value
        runPdfTask("Update PDF Metadata", file) {
            val outName = "${file.displayName.substringBeforeLast('.')}_meta.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.updateMetadata(
                File(file.localPath),
                temp,
                s.metaTitle,
                s.metaAuthor,
                s.metaSubject,
                s.metaKeywords
            ).getOrThrow()
            val saved = registerPdf(temp, outName, "PDF Metadata", file.displayName, file.sizeBytes)
            PdfStudioResult(
                title = "Metadata Updated",
                summary = "Updated Title/Author/Subject/Keywords in ${saved.displayName}.",
                primaryOutput = saved
            )
        }
    }

    fun runChangePageSize() {
        val file = _uiState.value.primaryFile ?: return
        val s = _uiState.value
        runPdfTask("Resize PDF Pages", file) {
            val outName = "${file.displayName.substringBeforeLast('.')}_${s.targetPageSize.name.lowercase()}.pdf"
            val temp = fileRepository.createTempExportFile(outName)
            pdfEngine.changePageSizeAndOrientation(
                File(file.localPath),
                temp,
                s.targetPageSize,
                s.landscapeOrientation
            ).getOrThrow()
            val saved = registerPdf(temp, outName, "Page Size & Orientation", file.displayName, file.sizeBytes)
            PdfStudioResult(
                title = "Page Layout Updated",
                summary = "Saved ${saved.displayName} with ${s.targetPageSize.name} (${if (s.landscapeOrientation) "Landscape" else "Portrait"}).",
                primaryOutput = saved
            )
        }
    }

    fun runBatchCompressPdfs() {
        val list = _uiState.value.multiFiles
        if (list.isEmpty()) {
            setError("Batch Queue Empty", "Add 1 or more PDFs to the batch queue first.")
            return
        }
        val quality = _uiState.value.compressQuality
        runPdfTask("Batch Compress PDFs", list.first()) {
            val outputs = mutableListOf<WorkspaceFile>()
            list.forEachIndexed { idx, wf ->
                updateProgress("Compressing ${idx + 1}/${list.size}: ${wf.displayName}", ((idx + 1) * 100) / list.size)
                val outName = "${wf.displayName.substringBeforeLast('.')}_compressed.pdf"
                val temp = fileRepository.createTempExportFile(outName)
                pdfEngine.compressPdf(File(wf.localPath), temp, quality).getOrThrow()
                outputs.add(registerPdf(temp, outName, "Batch Compress PDF", wf.displayName, wf.sizeBytes))
            }
            PdfStudioResult(
                title = "Batch Complete (${outputs.size} PDFs)",
                summary = "Compressed all queued PDFs while keeping originals intact.",
                primaryOutput = outputs.firstOrNull(),
                multipleOutputs = outputs
            )
        }
    }

    fun printCurrentPdf(activityContext: Context) {
        val file = (_uiState.value.executionState as? ToolExecutionState.Success)?.result?.primaryOutput
            ?: _uiState.value.primaryFile
            ?: return
        pdfEngine.launchSystemPrint(activityContext, File(file.localPath), file.displayName)
    }

    fun exportToUri(path: String, destUri: Uri) {
        viewModelScope.launch {
            fileRepository.exportToUri(path, destUri).fold(
                onSuccess = { _uiState.update { it.copy(infoMessage = "Saved file copy to chosen location.") } },
                onFailure = { e -> _uiState.update { it.copy(infoMessage = e.localizedMessage ?: "Export failed.") } }
            )
        }
    }

    fun cancelProcessing() {
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                executionState = ToolExecutionState.Idle,
                infoMessage = "Cancelled. Original PDFs remain untouched."
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(infoMessage = null, executionState = ToolExecutionState.Idle) }
    }

    private fun runPdfTask(taskName: String, sourceFile: WorkspaceFile, block: suspend () -> PdfStudioResult) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(executionState = ToolExecutionState.Processing("$taskName...", 10))
            }
            runCatching { block() }.fold(
                onSuccess = { res ->
                    _uiState.update { it.copy(executionState = ToolExecutionState.Success(res)) }
                },
                onFailure = { e ->
                    viewModelScope.launch {
                        fileRepository.recordTaskEvent(taskName, sourceFile.displayName, "-", "FAILED")
                    }
                    setError("$taskName Failed", e.localizedMessage ?: "Unable to process PDF.")
                }
            )
        }
    }

    private suspend fun registerPdf(
        tempFile: File,
        displayName: String,
        toolName: String,
        inputName: String,
        originalSize: Long
    ): WorkspaceFile = fileRepository.registerProcessedOutput(
        outputFile = tempFile,
        displayName = displayName,
        mimeType = "application/pdf",
        folderName = "PDFs",
        toolName = toolName,
        inputName = inputName,
        originalSizeBytes = originalSize
    )

    private fun updateProgress(stage: String, pct: Int) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Processing(stage, pct)) }
    }

    private fun setError(title: String, msg: String) {
        _uiState.update { it.copy(executionState = ToolExecutionState.Error(title, msg)) }
    }

    private fun parsePageSpec(spec: String): List<Int> {
        val out = mutableListOf<Int>()
        spec.split(",").forEach { token ->
            val t = token.trim()
            if (t.contains("-")) {
                val parts = t.split("-")
                val a = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val b = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (a != null && b != null && a <= b) {
                    for (p in a..b) out.add(p)
                }
            } else {
                t.toIntOrNull()?.let { out.add(it) }
            }
        }
        return out.filter { it >= 1 }.distinct()
    }

    private fun renderSignatureBitmap(
        strokes: List<List<Pair<Float, Float>>>,
        srcW: Float,
        srcH: Float
    ): Bitmap {
        val bmp = Bitmap.createBitmap(420, 180, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.TRANSPARENT)
        val canvas = AndroidCanvas(bmp)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(23, 91, 67)
            strokeWidth = 6f
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
        val sx = 420f / srcW
        val sy = 180f / srcH
        strokes.forEach { pts ->
            if (pts.size >= 2) {
                val path = AndroidPath()
                path.moveTo(pts.first().first * sx, pts.first().second * sy)
                pts.drop(1).forEach { (x, y) -> path.lineTo(x * sx, y * sy) }
                canvas.drawPath(path, paint)
            }
        }
        return bmp
    }
}
