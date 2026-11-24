package com.example.features.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.SmartTargetFormat
import com.example.core.model.ToolExecutionState
import com.example.ui.theme.LocalFileDudeColors
import kotlin.math.roundToInt

@Composable
fun SmartFileReadyScreen(
    uiState: SmartFileReadyUiState,
    fileRepository: WorkspaceFileRepository,
    cloudSyncEnabled: Boolean,
    onSelectPreset: (com.example.core.model.SmartPreset) -> Unit,
    onUpdateSpec: ((com.example.core.model.SmartPreset) -> com.example.core.model.SmartPreset) -> Unit,
    onImportUri: (Uri) -> Unit,
    onRunProcessing: () -> Unit,
    onCancelProcessing: () -> Unit,
    onSaveCustomPreset: (String, Boolean) -> Unit,
    onExportToUri: (String, Uri) -> Unit,
    onClearMessage: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalFileDudeColors.current

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var pendingExportPath by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onImportUri) }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportUri) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val src = pendingExportPath
        if (uri != null && src != null) {
            onExportToUri(src, uri)
        }
        pendingExportPath = null
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Custom Preset") },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text("Preset name (e.g. Visa Portal 150KB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCustomPreset(presetNameInput, cloudSyncEnabled)
                        presetNameInput = ""
                        showSavePresetDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FileDudeTopBar(
            title = "Smart File Ready",
            subtitle = "Prepare & verify exact portal requirements",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!uiState.infoMessage.isNullOrBlank()) {
                FileDudeSuccessBanner(
                    title = "Updated",
                    subtitle = uiState.infoMessage
                )
            }

            // Step 1: Select File
            FileDudeCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Select Source File", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FileDudeSecondaryButton(
                            text = "Pick Photo",
                            icon = Icons.Default.Image,
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "smart_pick_photo_button"
                        )
                        FileDudeSecondaryButton(
                            text = "Pick PDF / Doc",
                            icon = Icons.Default.Description,
                            onClick = {
                                docPicker.launch(arrayOf("application/pdf", "image/*"))
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "smart_pick_doc_button"
                        )
                    }

                    val file = uiState.selectedFile
                    if (file != null) {
                        FileDudeCard(containerColor = ext.secondarySurface) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(file.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = buildString {
                                        append("Original: ${formatFileSize(file.sizeBytes)}")
                                        append(" • ${file.mimeType}")
                                        if (file.widthPx > 0) append(" • ${file.widthPx}×${file.heightPx} px")
                                        if (file.pageCount > 0) append(" • ${file.pageCount} page(s)")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Step 2: Choose Preset
            FileDudeCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. Choose Requirement Preset", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showSavePresetDialog = true }) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Preset")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.presets.forEach { preset ->
                            FileDudeCategoryChip(
                                label = preset.name,
                                selected = uiState.selectedPreset?.id == preset.id,
                                onClick = { onSelectPreset(preset) }
                            )
                        }
                    }

                    Text(
                        text = uiState.editableSpec.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Step 3: Configure Requirements
            val spec = uiState.editableSpec
            FileDudeCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("3. Configure Exact Limits", style = MaterialTheme.typography.titleMedium)

                    Text("Target Format", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmartTargetFormat.entries.forEach { fmt ->
                            FileDudeCategoryChip(
                                label = fmt.label,
                                selected = spec.targetFormat == fmt,
                                onClick = { onUpdateSpec { it.copy(targetFormat = fmt) } }
                            )
                        }
                    }

                    val maxKbText = (spec.maxSizeBytes / 1024L).toString()
                    OutlinedTextField(
                        value = maxKbText,
                        onValueChange = { raw ->
                            val kb = raw.filter { ch -> ch.isDigit() }.toLongOrNull()?.coerceIn(10L, 512_000L)
                            if (kb != null) {
                                onUpdateSpec { it.copy(maxSizeBytes = kb * 1024L) }
                            }
                        },
                        label = { Text("Maximum File Size (KB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (spec.targetFormat == SmartTargetFormat.PDF) {
                        Text("PDF Page Size", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PdfPageSizeOption.entries.forEach { ps ->
                                FileDudeCategoryChip(
                                    label = ps.name,
                                    selected = spec.pdfPageSize == ps,
                                    onClick = { onUpdateSpec { it.copy(pdfPageSize = ps) } }
                                )
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = if (spec.minWidthPx > 0) spec.minWidthPx.toString() else "",
                                onValueChange = { v ->
                                    onUpdateSpec { it.copy(minWidthPx = v.toIntOrNull()?.coerceAtLeast(0) ?: 0) }
                                },
                                label = { Text("Min W (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = if (spec.maxWidthPx > 0) spec.maxWidthPx.toString() else "",
                                onValueChange = { v ->
                                    onUpdateSpec { it.copy(maxWidthPx = v.toIntOrNull()?.coerceAtLeast(0) ?: 0) }
                                },
                                label = { Text("Max W (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = if (spec.minHeightPx > 0) spec.minHeightPx.toString() else "",
                                onValueChange = { v ->
                                    onUpdateSpec { it.copy(minHeightPx = v.toIntOrNull()?.coerceAtLeast(0) ?: 0) }
                                },
                                label = { Text("Min H (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = if (spec.maxHeightPx > 0) spec.maxHeightPx.toString() else "",
                                onValueChange = { v ->
                                    onUpdateSpec { it.copy(maxHeightPx = v.toIntOrNull()?.coerceAtLeast(0) ?: 0) }
                                },
                                label = { Text("Max H (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = spec.aspectRatioLabel,
                            onValueChange = { ratio -> onUpdateSpec { it.copy(aspectRatioLabel = ratio.take(12)) } },
                            label = { Text("Aspect Ratio (optional, e.g. 35:45 or 1:1)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Target Quality: ${spec.qualityPercent}%", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = spec.qualityPercent.toFloat(),
                        onValueChange = { v -> onUpdateSpec { it.copy(qualityPercent = v.roundToInt()) } },
                        valueRange = 25f..100f
                    )
                }
            }

            // Step 4: Proposed Changes Summary & Action
            if (uiState.selectedFile != null) {
                FileDudeCard(containerColor = ext.secondarySurface) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Proposed Changes", style = MaterialTheme.typography.titleSmall, color = ext.darkGreenText)
                        Text(
                            "• Convert/optimize ${uiState.selectedFile.displayName} (${formatFileSize(uiState.selectedFile.sizeBytes)}) → ${spec.targetFormat.label} ≤ ${formatFileSize(spec.maxSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "• Preserve original file untouched and verify every output constraint on disk.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            when (val exec = uiState.executionState) {
                is ToolExecutionState.Processing -> {
                    FileDudeProcessingCard(
                        stage = exec.stage,
                        progressPercent = exec.progressPercent,
                        onCancel = onCancelProcessing
                    )
                }
                is ToolExecutionState.Error -> {
                    FileDudeErrorBanner(
                        title = exec.title,
                        message = exec.message,
                        onRetry = onRunProcessing,
                        onDismiss = onClearMessage
                    )
                }
                is ToolExecutionState.Success -> {
                    val report = exec.result
                    FileDudeCard(
                        containerColor = if (report.allPassed) ext.paleMint else ext.warningContainer,
                        borderColor = if (report.allPassed) MaterialTheme.colorScheme.primary else ext.warning
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (report.allPassed) "All Requirements Verified" else "Some Limits Could Not Be Met",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (report.allPassed) ext.darkGreenText else ext.warning
                                )
                                FileDudeStatusBadge(
                                    text = if (report.allPassed) "PASS" else "CHECK",
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = if (report.allPassed) MaterialTheme.colorScheme.primary else ext.warning
                                )
                            }
                            Text(
                                text = "Output: ${report.outputName} (${formatFileSize(report.outputSizeBytes)}, was ${formatFileSize(report.originalSizeBytes)})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            HorizontalDivider(color = ext.border)
                            report.checks.forEach { chk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = if (chk.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = if (chk.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(chk.ruleName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "Expected: ${chk.expected} • Actual: ${chk.actual}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (chk.passed) "PASS" else "FAIL",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (chk.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FileDudePrimaryButton(
                                    text = "Save Copy",
                                    icon = Icons.Default.Save,
                                    onClick = {
                                        pendingExportPath = report.outputPath
                                        saveLauncher.launch(report.outputName)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                FileDudeSecondaryButton(
                                    text = "Share",
                                    icon = Icons.Default.Share,
                                    onClick = {
                                        context.startActivity(
                                            fileRepository.createShareIntent(
                                                report.outputPath,
                                                report.outputMimeType,
                                                "Share Verified File"
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                else -> Unit
            }

            FileDudePrimaryButton(
                text = "Prepare & Verify File",
                icon = Icons.Default.AutoFixHigh,
                enabled = uiState.selectedFile != null && uiState.executionState !is ToolExecutionState.Processing,
                onClick = onRunProcessing,
                modifier = Modifier.fillMaxWidth(),
                testTag = "smart_prepare_verify_button"
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
