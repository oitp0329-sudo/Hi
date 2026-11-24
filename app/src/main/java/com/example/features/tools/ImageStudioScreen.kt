package com.example.features.tools

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeCategoryChip
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeProcessingCard
import com.example.core.design.FileDudeSecondaryButton
import com.example.core.design.FileDudeStatusBadge
import com.example.core.design.FileDudeSuccessBanner
import com.example.core.design.FileDudeTopBar
import com.example.core.design.formatFileSize
import com.example.core.engines.ImageFilterPreset
import com.example.core.model.ImageOutputFormat
import com.example.core.model.ToolExecutionState
import com.example.ui.theme.LocalFileDudeColors
import kotlin.math.roundToInt

@Composable
fun ImageStudioScreen(
    uiState: ImageStudioUiState,
    viewModel: ImageStudioViewModel,
    fileRepository: WorkspaceFileRepository,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val ext = LocalFileDudeColors.current
    var pendingExportPath by remember { mutableStateOf<String?>(null) }

    val singleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::importPrimaryImage) }

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris -> viewModel.importMultiImages(uris) }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val src = pendingExportPath
        if (uri != null && src != null) {
            viewModel.exportToUri(src, uri)
        }
        pendingExportPath = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FileDudeTopBar(
            title = "Image Studio",
            subtitle = uiState.selectedFile?.displayName ?: "Convert, Resize, Compress, Filters & OCR",
            onBack = onBack,
            actions = {
                if (uiState.selectedFile != null && uiState.mode == ImageToolMode.STUDIO) {
                    IconButton(onClick = viewModel::undo, enabled = uiState.undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = uiState.redoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = viewModel::resetEdits) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset edits")
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
            // Tool Mode Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImageToolMode.entries.forEach { mode ->
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

            // Picker Card
            val isMultiMode = uiState.mode == ImageToolMode.COLLAGE || uiState.mode == ImageToolMode.BATCH
            FileDudeCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isMultiMode) "Selected Photos (${uiState.multiFiles.size})" else "Source Image",
                            style = MaterialTheme.typography.titleMedium
                        )
                        FileDudeSecondaryButton(
                            text = if (isMultiMode) "Pick Photos" else if (uiState.selectedFile == null) "Pick Image" else "Change",
                            icon = Icons.Default.Image,
                            onClick = {
                                if (isMultiMode) {
                                    multiImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else {
                                    singleImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            testTag = "image_pick_button"
                        )
                    }

                    if (!isMultiMode && uiState.selectedFile != null) {
                        val f = uiState.selectedFile
                        Text(
                            text = "${f.displayName} • ${formatFileSize(f.sizeBytes)} • ${uiState.imageInfo?.widthPx ?: f.widthPx}×${uiState.imageInfo?.heightPx ?: f.heightPx} px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isMultiMode) {
                        if (uiState.multiFiles.isEmpty()) {
                            Text(
                                text = if (uiState.mode == ImageToolMode.COLLAGE) "Pick 2–4 photos for your collage." else "Pick multiple images to batch process.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.multiFiles.forEach { mf ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ext.secondarySurface)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mf.displayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.removeMultiFile(mf.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove image")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Preview + Before/After Comparison
            val previewBmp = if (uiState.showBeforeOriginal) uiState.originalPreview else uiState.editedPreview
            if (!isMultiMode && previewBmp != null) {
                FileDudeCard {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileDudeStatusBadge(
                                text = if (uiState.showBeforeOriginal) "BEFORE (ORIGINAL)" else "AFTER (LIVE PREVIEW)",
                                containerColor = if (uiState.showBeforeOriginal) ext.secondarySurface else ext.paleMint,
                                contentColor = ext.darkGreenText
                            )
                            TextButton(onClick = { viewModel.toggleBeforeAfter(!uiState.showBeforeOriginal) }) {
                                Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (uiState.showBeforeOriginal) "Show Edited" else "Compare Original")
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ext.secondarySurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBmp.asImageBitmap(),
                                contentDescription = "Image studio preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }

            // Tool Controls
            val cfg = uiState.config
            when (uiState.mode) {
                ImageToolMode.STUDIO -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Crop, Rotate & Flip", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Full", "1:1 Square", "4:3", "16:9", "3:4 Portrait", "Passport 35:45").forEach { crop ->
                                    FileDudeCategoryChip(
                                        label = crop,
                                        selected = uiState.activeCropLabel == crop,
                                        onClick = { viewModel.applyCropPreset(crop) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FileDudeSecondaryButton(
                                    text = "Rotate 90°",
                                    icon = Icons.Default.RotateRight,
                                    onClick = { viewModel.updateConfig { it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f) } },
                                    modifier = Modifier.weight(1f)
                                )
                                FileDudeSecondaryButton(
                                    text = "Flip H",
                                    icon = Icons.Default.Flip,
                                    onClick = { viewModel.updateConfig { it.copy(flipHorizontal = !it.flipHorizontal) } },
                                    modifier = Modifier.weight(1f)
                                )
                                FileDudeSecondaryButton(
                                    text = "Flip V",
                                    onClick = { viewModel.updateConfig { it.copy(flipVertical = !it.flipVertical) } },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = ext.border)
                            Text("Filter Presets", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ImageFilterPreset.entries.forEach { preset ->
                                    FileDudeCategoryChip(
                                        label = preset.label,
                                        selected = cfg.filterPreset == preset,
                                        onClick = { viewModel.updateConfig { it.copy(filterPreset = preset) } }
                                    )
                                }
                            }

                            HorizontalDivider(color = ext.border)
                            Text("Adjustments", style = MaterialTheme.typography.titleMedium)
                            AdjustmentSlider("Brightness", cfg.brightness, -100f..100f) { v ->
                                viewModel.updateConfig { it.copy(brightness = v) }
                            }
                            AdjustmentSlider("Contrast", cfg.contrast, 0.5f..2.0f) { v ->
                                viewModel.updateConfig { it.copy(contrast = v) }
                            }
                            AdjustmentSlider("Saturation", cfg.saturation, 0f..2.0f) { v ->
                                viewModel.updateConfig { it.copy(saturation = v) }
                            }

                            var showAdvancedStudio by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Advanced Tools & Overlays", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { showAdvancedStudio = !showAdvancedStudio }) {
                                    Text(if (showAdvancedStudio) "Hide" else "More Options")
                                }
                            }

                            if (showAdvancedStudio) {
                                AdjustmentSlider("Exposure", cfg.exposure, -0.5f..0.5f) { v ->
                                    viewModel.updateConfig { it.copy(exposure = v) }
                                }
                                AdjustmentSlider("Sharpness", cfg.sharpness, 0f..1f) { v ->
                                    viewModel.updateConfig { it.copy(sharpness = v) }
                                }
                                AdjustmentSlider("Blur Radius", cfg.blurRadius.toFloat(), 0f..6f) { v ->
                                    viewModel.updateConfig { it.copy(blurRadius = v.roundToInt()) }
                                }

                                HorizontalDivider(color = ext.border)
                                Text("Simple Uniform Background Matte", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Samples corner backdrop colour for solid backgrounds (not AI portrait segmentation).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Replace uniform corner background")
                                    Switch(
                                        checked = cfg.removeSimpleBackground,
                                        onCheckedChange = { c -> viewModel.updateConfig { it.copy(removeSimpleBackground = c) } }
                                    )
                                }
                                if (cfg.removeSimpleBackground) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ImageStudioViewModel.MATTE_COLORS.forEach { (name, colorInt) ->
                                            FileDudeCategoryChip(
                                                label = name,
                                                selected = cfg.backgroundColor == colorInt,
                                                onClick = { viewModel.updateConfig { it.copy(backgroundColor = colorInt) } }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = ext.border)
                                Text("Text, Sticker, Watermark & Border", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = cfg.overlayText,
                                    onValueChange = { t -> viewModel.updateConfig { it.copy(overlayText = t) } },
                                    label = { Text("Caption text") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = cfg.stickerLabel,
                                    onValueChange = { t -> viewModel.updateConfig { it.copy(stickerLabel = t) } },
                                    label = { Text("Sticker badge (e.g. VERIFIED)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = cfg.watermarkText,
                                    onValueChange = { t -> viewModel.updateConfig { it.copy(watermarkText = t) } },
                                    label = { Text("Diagonal watermark") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = cfg.addShapeBadge,
                                        onCheckedChange = { b -> viewModel.updateConfig { it.copy(addShapeBadge = b) } }
                                    )
                                    Text("Add corner green circle shape badge")
                                }
                                AdjustmentSlider("Border Width (${cfg.borderWidthPx} px)", cfg.borderWidthPx.toFloat(), 0f..60f) { v ->
                                    viewModel.updateConfig { it.copy(borderWidthPx = v.roundToInt(), borderColor = AndroidColor.WHITE) }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FileDudePrimaryButton(
                            text = "Save Edited Image",
                            icon = Icons.Default.Brush,
                            enabled = uiState.selectedFile != null,
                            onClick = viewModel::saveProcessedImage,
                            modifier = Modifier.weight(1f),
                            testTag = "image_save_edited_button"
                        )
                        FileDudeSecondaryButton(
                            text = "As PDF",
                            icon = Icons.Default.PictureAsPdf,
                            enabled = uiState.selectedFile != null,
                            onClick = viewModel::convertSelectedImageToPdf
                        )
                    }
                }

                ImageToolMode.CONVERT -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Convert Format (JPG, PNG, WebP, BMP)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "HEIC photos picked via Android Photo Picker are decoded and converted cleanly to your target format.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ImageOutputFormat.entries.forEach { fmt ->
                                    FileDudeCategoryChip(
                                        label = fmt.label,
                                        selected = cfg.outputFormat == fmt,
                                        onClick = { viewModel.updateConfig { it.copy(outputFormat = fmt) } }
                                    )
                                }
                            }
                            Text("Quality: ${cfg.qualityPercent}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = cfg.qualityPercent.toFloat(),
                                onValueChange = { q -> viewModel.updateConfig { it.copy(qualityPercent = q.roundToInt()) } },
                                valueRange = 20f..100f
                            )
                            FileDudePrimaryButton(
                                text = "Convert to ${cfg.outputFormat.label}",
                                enabled = uiState.selectedFile != null,
                                onClick = viewModel::saveProcessedImage,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                ImageToolMode.RESIZE -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Resize by Pixels, % or Passport/ID", style = MaterialTheme.typography.titleMedium)
                            Text("Quick Passport / ID Size Presets (300 DPI)", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FileDudeCategoryChip(
                                    label = "35×45 mm (413×531)",
                                    selected = cfg.targetWidthPx == 413 && cfg.targetHeightPx == 531,
                                    onClick = { viewModel.applyPassportHelper(413, 531) }
                                )
                                FileDudeCategoryChip(
                                    label = "2×2 in (600×600)",
                                    selected = cfg.targetWidthPx == 600 && cfg.targetHeightPx == 600,
                                    onClick = { viewModel.applyPassportHelper(600, 600) }
                                )
                                FileDudeCategoryChip(
                                    label = "Signature (300×120)",
                                    selected = cfg.targetWidthPx == 300 && cfg.targetHeightPx == 120,
                                    onClick = { viewModel.applyPassportHelper(300, 120) }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = uiState.widthInput,
                                    onValueChange = { w -> viewModel.updateResizeFields(w, uiState.heightInput, uiState.percentInput) },
                                    label = { Text("Width (px)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = uiState.heightInput,
                                    onValueChange = { h -> viewModel.updateResizeFields(uiState.widthInput, h, uiState.percentInput) },
                                    label = { Text("Height (px)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = uiState.percentInput,
                                onValueChange = { p -> viewModel.updateResizeFields("", "", p) },
                                label = { Text("Or Scale Percentage (10–200%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FileDudePrimaryButton(
                                text = "Apply Resize & Save",
                                enabled = uiState.selectedFile != null,
                                onClick = viewModel::saveProcessedImage,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                ImageToolMode.COMPRESS -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Target File Size Compression (KB)", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("50", "100", "200", "500", "1024").forEach { kb ->
                                    FileDudeCategoryChip(
                                        label = if (kb == "1024") "1 MB" else "$kb KB",
                                        selected = uiState.targetKbText == kb,
                                        onClick = { viewModel.updateTargetKb(kb) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = uiState.targetKbText,
                                onValueChange = viewModel::updateTargetKb,
                                label = { Text("Custom Max Size (KB)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Initial Quality: ${cfg.qualityPercent}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = cfg.qualityPercent.toFloat(),
                                onValueChange = { q -> viewModel.updateConfig { it.copy(qualityPercent = q.roundToInt()) } },
                                valueRange = 20f..100f
                            )
                            FileDudePrimaryButton(
                                text = "Compress & Save Image",
                                enabled = uiState.selectedFile != null,
                                onClick = viewModel::saveProcessedImage,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                ImageToolMode.COLLAGE -> {
                    FileDudePrimaryButton(
                        text = "Create Grid Collage (${uiState.multiFiles.size} Photos)",
                        enabled = uiState.multiFiles.size in 2..4,
                        onClick = viewModel::createCollage,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ImageToolMode.OCR -> {
                    FileDudePrimaryButton(
                        text = "Extract Text (ML Kit On-Device)",
                        icon = Icons.Default.TextFields,
                        enabled = uiState.selectedFile != null,
                        onClick = viewModel::runOcr,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ImageToolMode.EXIF -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("EXIF Metadata & Privacy", style = MaterialTheme.typography.titleMedium)
                            val exif = uiState.imageInfo?.exifSummary.orEmpty()
                            if (exif.isEmpty()) {
                                Text(
                                    "No camera or GPS EXIF tags found on this image.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                exif.forEach { (k, v) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(v, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            FileDudePrimaryButton(
                                text = "Strip EXIF & Save Clean Copy",
                                icon = Icons.Default.Lock,
                                enabled = uiState.selectedFile != null,
                                onClick = viewModel::stripExif,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                ImageToolMode.BATCH -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Batch Output Format", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ImageOutputFormat.entries.forEach { fmt ->
                                    FileDudeCategoryChip(
                                        label = fmt.label,
                                        selected = cfg.outputFormat == fmt,
                                        onClick = { viewModel.updateConfig { it.copy(outputFormat = fmt) } }
                                    )
                                }
                            }
                            FileDudePrimaryButton(
                                text = "Batch Export ${uiState.multiFiles.size} Image(s)",
                                enabled = uiState.multiFiles.isNotEmpty(),
                                onClick = viewModel::runBatchExport,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Output Result Card
            when (val exec = uiState.executionState) {
                is ToolExecutionState.Processing -> {
                    FileDudeProcessingCard(
                        stage = exec.stage,
                        progressPercent = exec.progressPercent,
                        onCancel = viewModel::cancelProcessing
                    )
                }
                is ToolExecutionState.Error -> {
                    FileDudeErrorBanner(title = exec.title, message = exec.message, onDismiss = viewModel::clearMessages)
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                )
                                FileDudeSecondaryButton(
                                    text = "Copy Extracted Text",
                                    icon = Icons.Default.ContentCopy,
                                    onClick = { clipboard.setText(AnnotatedString(res.extractedText)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            res.primaryOutput?.let { out ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FileDudePrimaryButton(
                                        text = "Save Copy",
                                        icon = Icons.Default.Save,
                                        onClick = {
                                            pendingExportPath = out.localPath
                                            saveCopyLauncher.launch(out.displayName)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FileDudeSecondaryButton(
                                        text = "Share",
                                        icon = Icons.Default.Share,
                                        onClick = {
                                            context.startActivity(
                                                fileRepository.createShareIntent(out.localPath, out.mimeType, "Share Image")
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

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(String.format("%.2f", value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
