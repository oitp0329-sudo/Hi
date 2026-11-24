package com.example.features.tools

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeCategoryChip
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeProcessingCard
import com.example.core.design.FileDudeSecondaryButton
import com.example.core.design.FileDudeSuccessBanner
import com.example.core.design.FileDudeTopBar
import com.example.core.design.formatFileSize
import com.example.core.model.ImageOutputFormat
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.ToolExecutionState
import com.example.ui.theme.LocalFileDudeColors
import kotlin.math.roundToInt

@Composable
fun PdfStudioScreen(
    uiState: PdfStudioUiState,
    viewModel: PdfStudioViewModel,
    fileRepository: WorkspaceFileRepository,
    onAskAi: (com.example.core.model.WorkspaceFile) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val ext = LocalFileDudeColors.current

    var pendingExportPath by remember { mutableStateOf<String?>(null) }

    val singlePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importPrimaryPdf) }

    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importMultipleFiles(uris, "PDFs") }

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris -> viewModel.importMultipleFiles(uris, "Images") }

    val overlayImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::importOverlayImage) }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = pendingExportPath
        if (uri != null && src != null) {
            viewModel.exportToUri(src, uri)
        }
        pendingExportPath = null
    }

    // Signature canvas state for ANNOTATE mode
    val signatureStrokes = remember { mutableStateListOf<List<Pair<Float, Float>>>() }
    var currentStroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var sigWidth by remember { mutableFloatStateOf(1f) }
    var sigHeight by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FileDudeTopBar(
            title = "PDF Studio",
            subtitle = uiState.primaryFile?.displayName ?: "27 PDF tools • Originals preserved",
            onBack = onBack,
            actions = {
                uiState.primaryFile?.let { activePdf ->
                    IconButton(onClick = { onAskAi(activePdf) }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Chat with PDF using FileDude AI")
                    }
                    IconButton(onClick = { viewModel.printCurrentPdf(context) }) {
                        Icon(Icons.Default.Print, contentDescription = "Print PDF")
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tool Mode Selector Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PdfToolMode.entries.forEach { mode ->
                    FileDudeCategoryChip(
                        label = mode.label,
                        selected = uiState.mode == mode,
                        onClick = { viewModel.selectMode(mode) }
                    )
                }
            }

            if (!uiState.infoMessage.isNullOrBlank()) {
                FileDudeSuccessBanner(title = "Notice", subtitle = uiState.infoMessage)
            }

            // Source File Selection Card
            val needsMultiInput = uiState.mode == PdfToolMode.MERGE ||
                uiState.mode == PdfToolMode.IMG2PDF ||
                (uiState.mode == PdfToolMode.METADATA && uiState.multiFiles.isNotEmpty())

            FileDudeCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (uiState.mode) {
                                PdfToolMode.MERGE -> "PDF Queue (${uiState.multiFiles.size})"
                                PdfToolMode.IMG2PDF -> "Images for PDF (${uiState.multiFiles.size})"
                                else -> "Active PDF File"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.mode == PdfToolMode.MERGE || uiState.mode == PdfToolMode.IMG2PDF) {
                            FileDudeSecondaryButton(
                                text = if (uiState.mode == PdfToolMode.IMG2PDF) "Add Images" else "Add PDFs",
                                icon = Icons.Default.Add,
                                onClick = {
                                    if (uiState.mode == PdfToolMode.IMG2PDF) {
                                        multiImagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } else {
                                        multiPdfPicker.launch(arrayOf("application/pdf"))
                                    }
                                },
                                testTag = "pdf_pick_multi_button"
                            )
                        } else {
                            FileDudeSecondaryButton(
                                text = if (uiState.primaryFile == null) "Choose PDF" else "Change PDF",
                                icon = Icons.Default.FolderOpen,
                                onClick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                testTag = "pdf_pick_single_button"
                            )
                        }
                    }

                    if (!needsMultiInput && uiState.primaryFile != null) {
                        val pf = uiState.primaryFile
                        val det = uiState.details
                        FileDudeCard(containerColor = ext.secondarySurface) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(pf.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${formatFileSize(pf.sizeBytes)} • ${det?.pageCount ?: pf.pageCount} page(s)" +
                                        if (det?.isEncrypted == true) " • Encrypted" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (uiState.mode == PdfToolMode.MERGE || uiState.mode == PdfToolMode.IMG2PDF) {
                        if (uiState.multiFiles.isEmpty()) {
                            Text(
                                text = if (uiState.mode == PdfToolMode.IMG2PDF) {
                                    "Pick 1 or more images using Android Photo Picker."
                                } else {
                                    "Pick 2 or more PDF files to combine in order."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.multiFiles.forEachIndexed { idx, mf ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ext.secondarySurface)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${idx + 1}. ${mf.displayName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.moveMultiFile(idx, -1) }, enabled = idx > 0) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveMultiFile(idx, 1) },
                                        enabled = idx < uiState.multiFiles.lastIndex
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                                    }
                                    IconButton(onClick = { viewModel.removeMultiFile(mf.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Page Preview for single-PDF modes
            val previewBitmap = uiState.currentPageBitmap
            if (uiState.primaryFile != null && previewBitmap != null &&
                uiState.mode != PdfToolMode.MERGE && uiState.mode != PdfToolMode.IMG2PDF && uiState.mode != PdfToolMode.PAGES
            ) {
                val totalPages = (uiState.details?.pageCount ?: uiState.primaryFile.pageCount).coerceAtLeast(1)
                FileDudeCard {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "PDF page ${uiState.currentPageIndex + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(290.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.goToPage(uiState.currentPageIndex - 1) },
                                enabled = uiState.currentPageIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                            }
                            Text(
                                text = "Page ${uiState.currentPageIndex + 1} of $totalPages",
                                style = MaterialTheme.typography.titleSmall
                            )
                            IconButton(
                                onClick = { viewModel.goToPage(uiState.currentPageIndex + 1) },
                                enabled = uiState.currentPageIndex < totalPages - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                            }
                        }
                    }
                }
            }

            // Mode-Specific Controls
            when (uiState.mode) {
                PdfToolMode.VIEW -> {
                    val file = uiState.primaryFile
                    val det = uiState.details
                    if (file != null) {
                        FileDudeCard {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Document Details", style = MaterialTheme.typography.titleMedium)
                                if (det != null) {
                                    Text(
                                        "Page size: ${det.firstPageWidthPt.roundToInt()} × ${det.firstPageHeightPt.roundToInt()} pt",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Form fields: ${det.formFieldNames.size} • Author: ${det.author.ifBlank { "—" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FileDudePrimaryButton(
                                        text = "Print PDF",
                                        icon = Icons.Default.Print,
                                        onClick = { viewModel.printCurrentPdf(context) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FileDudeSecondaryButton(
                                        text = "Share",
                                        icon = Icons.Default.Share,
                                        onClick = {
                                            context.startActivity(
                                                fileRepository.createShareIntent(file.localPath, "application/pdf", "Share PDF")
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                PdfToolMode.COMPRESS -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Compression Quality: ${uiState.compressQuality}%", style = MaterialTheme.typography.titleSmall)
                            Slider(
                                value = uiState.compressQuality.toFloat(),
                                onValueChange = { q -> viewModel.updateSettings { it.copy(compressQuality = q.roundToInt()) } },
                                valueRange = 25f..95f
                            )
                            Text("Render Resolution Scale: ${String.format("%.1fx", uiState.compressDpiScale)}", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = uiState.compressDpiScale,
                                onValueChange = { d -> viewModel.updateSettings { it.copy(compressDpiScale = d) } },
                                valueRange = 0.8f..2.0f
                            )
                            FileDudePrimaryButton(
                                text = "Compress PDF",
                                icon = Icons.Default.Compress,
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runCompress,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "pdf_compress_run_button"
                            )
                        }
                    }
                }

                PdfToolMode.MERGE -> {
                    FileDudePrimaryButton(
                        text = "Merge ${uiState.multiFiles.size} PDFs",
                        icon = Icons.Default.MergeType,
                        enabled = uiState.multiFiles.size >= 2,
                        onClick = viewModel::runMerge,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "pdf_merge_run_button"
                    )
                }

                PdfToolMode.SPLIT -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Split Every N Pages", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = uiState.splitPagesPerFile.toString(),
                                onValueChange = { v ->
                                    val n = v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 500) ?: 1
                                    viewModel.updateSettings { it.copy(splitPagesPerFile = n) }
                                },
                                label = { Text("Pages per split PDF") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FileDudePrimaryButton(
                                text = "Split Into Parts",
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runSplitRanges,
                                modifier = Modifier.fillMaxWidth()
                            )
                            HorizontalDivider(color = ext.border)
                            Text("Or Extract Specific Pages", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = uiState.extractPageSpec,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(extractPageSpec = v) } },
                                label = { Text("Pages (e.g. 1,3,5-7)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FileDudeSecondaryButton(
                                text = "Extract Selected Pages",
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runExtractPages,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                PdfToolMode.PAGES -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Thumbnail Page Editor", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Reorder, rotate 90°, or mark pages for deletion. Original file stays untouched.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            uiState.pageThumbnails.forEachIndexed { idx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (item.deleted) MaterialTheme.colorScheme.errorContainer else ext.secondarySurface)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.thumbnail != null) {
                                        Image(
                                            bitmap = item.thumbnail.asImageBitmap(),
                                            contentDescription = "Page ${item.originalIndex + 1}",
                                            modifier = Modifier
                                                .size(48.dp, 62.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Original Page ${item.originalIndex + 1}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = buildString {
                                                append("Rot: ${item.rotationDegrees}°")
                                                if (item.deleted) append(" • Marked for delete")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.movePageThumb(idx, -1) }, enabled = idx > 0) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move page up")
                                    }
                                    IconButton(
                                        onClick = { viewModel.movePageThumb(idx, 1) },
                                        enabled = idx < uiState.pageThumbnails.lastIndex
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move page down")
                                    }
                                    IconButton(onClick = { viewModel.rotatePageThumb(idx) }) {
                                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90 degrees")
                                    }
                                    IconButton(onClick = { viewModel.toggleDeletePageThumb(idx) }) {
                                        Icon(
                                            imageVector = if (item.deleted) Icons.Default.RestoreFromTrash else Icons.Default.Delete,
                                            contentDescription = if (item.deleted) "Restore page" else "Delete page"
                                        )
                                    }
                                }
                            }
                            FileDudePrimaryButton(
                                text = "Save Page Edits",
                                enabled = uiState.primaryFile != null && uiState.pageThumbnails.isNotEmpty(),
                                onClick = viewModel::runApplyPageEdits,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                PdfToolMode.IMG2PDF -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("PDF Page Size", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfPageSizeOption.entries.forEach { ps ->
                                    FileDudeCategoryChip(
                                        label = ps.name,
                                        selected = uiState.img2PdfPageSize == ps,
                                        onClick = { viewModel.updateSettings { it.copy(img2PdfPageSize = ps) } }
                                    )
                                }
                            }
                            FileDudePrimaryButton(
                                text = "Create PDF from ${uiState.multiFiles.size} Image(s)",
                                icon = Icons.Default.PictureAsPdf,
                                enabled = uiState.multiFiles.isNotEmpty(),
                                onClick = viewModel::runImagesToPdf,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                PdfToolMode.PDF2IMG -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Output Image Format", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(ImageOutputFormat.JPG, ImageOutputFormat.PNG).forEach { fmt ->
                                    FileDudeCategoryChip(
                                        label = fmt.label,
                                        selected = uiState.pdf2ImgFormat == fmt,
                                        onClick = { viewModel.updateSettings { it.copy(pdf2ImgFormat = fmt) } }
                                    )
                                }
                            }
                            FileDudePrimaryButton(
                                text = "Export All Pages as Images",
                                icon = Icons.Default.Image,
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runPdfToImages,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                PdfToolMode.ANNOTATE -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Annotate, Sign & Add Image (Page ${uiState.currentPageIndex + 1})", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = uiState.overlayText,
                                onValueChange = { txt -> viewModel.updateSettings { it.copy(overlayText = txt) } },
                                label = { Text("Add note / annotation text") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.highlightPage,
                                    onCheckedChange = { c -> viewModel.updateSettings { it.copy(highlightPage = c) } }
                                )
                                Text("Add yellow highlight band across upper section")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.overlayImageFile?.displayName ?: "Optional stamp / photo overlay",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        overlayImagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) { Text("Pick Image") }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Draw Signature", style = MaterialTheme.typography.titleSmall)
                                TextButton(
                                    onClick = {
                                        signatureStrokes.clear()
                                        currentStroke = emptyList()
                                    }
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear Pad")
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(1.dp, ext.border, RoundedCornerShape(12.dp))
                                    .onSizeChanged {
                                        sigWidth = it.width.toFloat().coerceAtLeast(1f)
                                        sigHeight = it.height.toFloat().coerceAtLeast(1f)
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentStroke = listOf(offset.x to offset.y)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentStroke = currentStroke + (change.position.x to change.position.y)
                                            },
                                            onDragEnd = {
                                                if (currentStroke.size >= 2) {
                                                    signatureStrokes.add(currentStroke)
                                                }
                                                currentStroke = emptyList()
                                            }
                                        )
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val all = signatureStrokes + listOf(currentStroke)
                                    all.forEach { pts ->
                                        if (pts.size >= 2) {
                                            val path = Path().apply {
                                                moveTo(pts.first().first, pts.first().second)
                                                pts.drop(1).forEach { (x, y) -> lineTo(x, y) }
                                            }
                                            drawPath(
                                                path = path,
                                                color = Color(0xFF175B43),
                                                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                            )
                                        }
                                    }
                                }
                            }
                            FileDudePrimaryButton(
                                text = "Apply Annotations & Signature",
                                icon = Icons.Default.Draw,
                                enabled = uiState.primaryFile != null,
                                onClick = {
                                    viewModel.runAnnotateAndSign(signatureStrokes.toList(), sigWidth, sigHeight)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (uiState.formValues.isNotEmpty()) {
                                HorizontalDivider(color = ext.border)
                                Text("Fill AcroForm Fields", style = MaterialTheme.typography.titleMedium)
                                uiState.formValues.forEach { (fieldName, fieldVal) ->
                                    OutlinedTextField(
                                        value = fieldVal,
                                        onValueChange = { nv ->
                                            viewModel.updateSettings { s ->
                                                s.copy(formValues = s.formValues + (fieldName to nv))
                                            }
                                        },
                                        label = { Text(fieldName) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                FileDudeSecondaryButton(
                                    text = "Save Filled Form",
                                    enabled = uiState.primaryFile != null,
                                    onClick = viewModel::runFillFormFields,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                PdfToolMode.WATERMARK -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = uiState.watermarkText,
                                onValueChange = { txt -> viewModel.updateSettings { it.copy(watermarkText = txt) } },
                                label = { Text("Watermark text (leave empty for page # only)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Opacity: ${(uiState.watermarkOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = uiState.watermarkOpacity,
                                onValueChange = { op -> viewModel.updateSettings { it.copy(watermarkOpacity = op) } },
                                valueRange = 0.1f..0.8f
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Add bottom page numbers (1 / N)")
                                Switch(
                                    checked = uiState.addPageNumbers,
                                    onCheckedChange = { c -> viewModel.updateSettings { it.copy(addPageNumbers = c) } }
                                )
                            }
                            FileDudePrimaryButton(
                                text = "Stamp Watermark & Page Numbers",
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runWatermarkAndPageNumbers,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                PdfToolMode.OCR -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("On-Device ML Kit OCR", style = MaterialTheme.typography.titleMedium)
                            Text("Max pages to scan: ${uiState.ocrMaxPages}", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = uiState.ocrMaxPages.toFloat(),
                                onValueChange = { p -> viewModel.updateSettings { it.copy(ocrMaxPages = p.roundToInt()) } },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                            FileDudePrimaryButton(
                                text = "Extract Text from PDF",
                                icon = Icons.Default.TextFields,
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runOcrPdf,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                PdfToolMode.SECURITY -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Password Protect PDF", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = uiState.userPassword,
                                onValueChange = { p -> viewModel.updateSettings { it.copy(userPassword = p) } },
                                label = { Text("User open password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = uiState.ownerPassword,
                                onValueChange = { p -> viewModel.updateSettings { it.copy(ownerPassword = p) } },
                                label = { Text("Owner password (optional)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FileDudePrimaryButton(
                                text = "Encrypt with Password",
                                icon = Icons.Default.Lock,
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runProtectPdf,
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(color = ext.border)
                            Text("Remove Password (Authorized)", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = uiState.unlockPassword,
                                onValueChange = { p -> viewModel.updateSettings { it.copy(unlockPassword = p) } },
                                label = { Text("Current authorized password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FileDudeSecondaryButton(
                                text = "Unlock PDF",
                                icon = Icons.Default.LockOpen,
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runUnlockPdf,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                PdfToolMode.METADATA -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Document Metadata", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = uiState.metaTitle,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(metaTitle = v) } },
                                label = { Text("Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = uiState.metaAuthor,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(metaAuthor = v) } },
                                label = { Text("Author") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = uiState.metaSubject,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(metaSubject = v) } },
                                label = { Text("Subject") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = uiState.metaKeywords,
                                onValueChange = { v -> viewModel.updateSettings { it.copy(metaKeywords = v) } },
                                label = { Text("Keywords") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FileDudePrimaryButton(
                                text = "Save Metadata",
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runUpdateMetadata,
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(color = ext.border)
                            Text("Page Size & Orientation", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfPageSizeOption.entries.forEach { ps ->
                                    FileDudeCategoryChip(
                                        label = ps.name,
                                        selected = uiState.targetPageSize == ps,
                                        onClick = { viewModel.updateSettings { it.copy(targetPageSize = ps) } }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Landscape orientation")
                                Switch(
                                    checked = uiState.landscapeOrientation,
                                    onCheckedChange = { c -> viewModel.updateSettings { it.copy(landscapeOrientation = c) } }
                                )
                            }
                            FileDudeSecondaryButton(
                                text = "Apply Page Size & Orientation",
                                enabled = uiState.primaryFile != null,
                                onClick = viewModel::runChangePageSize,
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(color = ext.border)
                            Text("Batch Compress Queue (${uiState.multiFiles.size} PDFs)", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FileDudeSecondaryButton(
                                    text = "Add Batch PDFs",
                                    icon = Icons.Default.Add,
                                    onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) },
                                    modifier = Modifier.weight(1f)
                                )
                                FileDudePrimaryButton(
                                    text = "Compress All",
                                    enabled = uiState.multiFiles.isNotEmpty(),
                                    onClick = viewModel::runBatchCompressPdfs,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Execution State Output Banner
            when (val exec = uiState.executionState) {
                is ToolExecutionState.Processing -> {
                    FileDudeProcessingCard(
                        stage = exec.stage,
                        progressPercent = exec.progressPercent,
                        onCancel = viewModel::cancelProcessing
                    )
                }
                is ToolExecutionState.Error -> {
                    FileDudeErrorBanner(
                        title = exec.title,
                        message = exec.message,
                        onDismiss = viewModel::clearMessages
                    )
                }
                is ToolExecutionState.Success -> {
                    val res = exec.result
                    FileDudeCard(containerColor = ext.paleMint, borderColor = MaterialTheme.colorScheme.primary) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(res.title, style = MaterialTheme.typography.titleMedium, color = ext.darkGreenText)
                            Text(res.summary, style = MaterialTheme.typography.bodySmall)
                            if (!res.extractedText.isNullOrBlank()) {
                                OutlinedTextField(
                                    value = res.extractedText,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Extracted OCR Text") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                )
                                FileDudeSecondaryButton(
                                    text = "Copy Text",
                                    icon = Icons.Default.ContentCopy,
                                    onClick = {
                                        clipboard.setText(AnnotatedString(res.extractedText))
                                        viewModel.updateSettings { it.copy(infoMessage = "Copied OCR text to clipboard.") }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            res.primaryOutput?.let { outFile ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FileDudePrimaryButton(
                                        text = "Save Copy",
                                        icon = Icons.Default.Save,
                                        onClick = {
                                            pendingExportPath = outFile.localPath
                                            saveCopyLauncher.launch(outFile.displayName)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FileDudeSecondaryButton(
                                        text = "Share",
                                        icon = Icons.Default.Share,
                                        onClick = {
                                            context.startActivity(
                                                fileRepository.createShareIntent(
                                                    outFile.localPath,
                                                    outFile.mimeType,
                                                    "Share Output"
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
