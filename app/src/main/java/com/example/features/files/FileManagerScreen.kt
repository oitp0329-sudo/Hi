package com.example.features.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeCategoryChip
import com.example.core.design.FileDudeConfirmDialog
import com.example.core.design.FileDudeEmptyState
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudeFileRow
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeSearchBar
import com.example.core.design.FileDudeSecondaryButton
import com.example.core.design.FileDudeStatusBadge
import com.example.core.design.FileDudeSuccessBanner
import com.example.core.design.FileDudeTopBar
import com.example.core.design.formatDateShort
import com.example.core.design.formatDurationMs
import com.example.core.design.formatFileSize
import com.example.core.engines.PdfProcessingEngine
import com.example.core.model.FileCategory
import com.example.core.model.FileSortOption
import com.example.core.model.PlanTier
import com.example.core.model.WorkspaceFile
import com.example.subscription.AdPlacementLocation
import com.example.subscription.FileDudeAdContainer
import com.example.ui.theme.LocalFileDudeColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    uiState: FileManagerUiState,
    viewModel: FileManagerViewModel,
    fileRepository: WorkspaceFileRepository,
    pdfEngine: PdfProcessingEngine,
    planTier: PlanTier = PlanTier.FREE,
    onOpenInStudio: (WorkspaceFile) -> Unit,
    onOpenInSmartReady: (WorkspaceFile) -> Unit,
    onOpenInAi: (WorkspaceFile) -> Unit = {}
) {
    val context = LocalContext.current
    val ext = LocalFileDudeColors.current

    var showSortMenu by remember { mutableStateOf(false) }
    var inspectedFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    var renameTarget by remember { mutableStateOf<WorkspaceFile?>(null) }
    var moveTarget by remember { mutableStateOf<WorkspaceFile?>(null) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var pendingExportFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    var multiSelectActive by remember { mutableStateOf(false) }

    val mediaImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris -> viewModel.importUris(uris, "Imported") }

    val docImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importUris(uris, "Downloads") }

    val safSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val src = pendingExportFile
        if (uri != null && src != null) {
            viewModel.exportToUri(src.localPath, uri)
        }
        pendingExportFile = null
    }

    if (renameTarget != null) {
        val target = renameTarget!!
        var input by remember { mutableStateOf(target.displayName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("New file name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameFile(target, input)
                        renameTarget = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (moveTarget != null) {
        val target = moveTarget!!
        var folderInput by remember { mutableStateOf(target.folderName) }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move to Folder") },
            text = {
                OutlinedTextField(
                    value = folderInput,
                    onValueChange = { folderInput = it },
                    label = { Text("Folder name (e.g. Work, Tax, IDs)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.moveFile(target, folderInput)
                        moveTarget = null
                    }
                ) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } }
        )
    }

    if (showBatchRenameDialog) {
        var prefix by remember { mutableStateOf("FileDude_Item") }
        AlertDialog(
            onDismissRequest = { showBatchRenameDialog = false },
            title = { Text("Batch Rename (${uiState.selectedIds.size} files)") },
            text = {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Filename prefix") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchRenameSelected(prefix)
                        showBatchRenameDialog = false
                        multiSelectActive = false
                    }
                ) { Text("Rename All") }
            },
            dismissButton = { TextButton(onClick = { showBatchRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showBatchMoveDialog) {
        var folder by remember { mutableStateOf("Work") }
        AlertDialog(
            onDismissRequest = { showBatchMoveDialog = false },
            title = { Text("Move ${uiState.selectedIds.size} files") },
            text = {
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Destination folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchMoveSelected(folder)
                        showBatchMoveDialog = false
                        multiSelectActive = false
                    }
                ) { Text("Move All") }
            },
            dismissButton = { TextButton(onClick = { showBatchMoveDialog = false }) { Text("Cancel") } }
        )
    }

    if (showEmptyTrashDialog) {
        FileDudeConfirmDialog(
            title = "Empty Trash?",
            message = "Permanently delete ${uiState.files.size} file(s) from Trash? This cannot be undone.",
            confirmLabel = "Delete Forever",
            isDestructive = true,
            onConfirm = {
                viewModel.emptyTrash()
                showEmptyTrashDialog = false
            },
            onDismiss = { showEmptyTrashDialog = false }
        )
    }

    // Preview & Details Modal Bottom Sheet
    if (inspectedFile != null) {
        val file = inspectedFile!!
        var previewBmp by remember(file.id) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(file.id) {
            previewBmp = when (file.category) {
                FileCategory.IMAGE -> BitmapFactory.decodeFile(file.localPath)
                FileCategory.PDF -> pdfEngine.renderPageBitmap(File(file.localPath), 0, 700).getOrNull()
                else -> null
            }
        }
        ModalBottomSheet(
            onDismissRequest = { inspectedFile = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(file.displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${file.category.label} • ${formatFileSize(file.sizeBytes)} • Folder: ${file.folderName} • ${formatDateShort(file.createdAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (file.durationMs > 0) {
                    Text("Duration: ${formatDurationMs(file.durationMs)} • ${file.widthPx}×${file.heightPx}", style = MaterialTheme.typography.bodySmall)
                } else if (file.widthPx > 0) {
                    Text("Dimensions: ${file.widthPx}×${file.heightPx} px", style = MaterialTheme.typography.bodySmall)
                } else if (file.pageCount > 0) {
                    Text("Pages: ${file.pageCount}", style = MaterialTheme.typography.bodySmall)
                }
                if (file.sha256.isNotBlank()) {
                    Text(
                        "SHA-256: ${file.sha256.take(18)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (previewBmp != null) {
                    Image(
                        bitmap = previewBmp!!.asImageBitmap(),
                        contentDescription = "Preview of ${file.displayName}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ext.secondarySurface),
                        contentScale = ContentScale.Fit
                    )
                }

                if (!file.isInTrash) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (file.category in listOf(FileCategory.PDF, FileCategory.IMAGE, FileCategory.VIDEO)) {
                            FileDudePrimaryButton(
                                text = "Open in Studio",
                                onClick = {
                                    inspectedFile = null
                                    onOpenInStudio(file)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (file.category in listOf(FileCategory.PDF, FileCategory.IMAGE)) {
                            FileDudeSecondaryButton(
                                text = "Smart Ready",
                                icon = Icons.Default.AutoFixHigh,
                                onClick = {
                                    inspectedFile = null
                                    onOpenInSmartReady(file)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        if (file.category in listOf(FileCategory.PDF, FileCategory.IMAGE, FileCategory.DOCUMENT)) {
                            TextButton(
                                onClick = {
                                    inspectedFile = null
                                    onOpenInAi(file)
                                }
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("Ask AI")
                            }
                        }
                        TextButton(onClick = { renameTarget = file; inspectedFile = null }) { Text("Rename") }
                        TextButton(onClick = { moveTarget = file; inspectedFile = null }) { Text("Move") }
                        TextButton(onClick = { viewModel.copyFile(file); inspectedFile = null }) { Text("Copy") }
                        TextButton(onClick = { pendingExportFile = file; safSaveLauncher.launch(file.displayName) }) { Text("Save") }
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    fileRepository.createShareIntent(file.localPath, file.mimeType, "Share File")
                                )
                            }
                        ) { Text("Share") }
                    }
                    TextButton(
                        onClick = { viewModel.moveToTrash(file); inspectedFile = null }
                    ) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FileDudePrimaryButton(
                            text = "Restore",
                            icon = Icons.Default.RestoreFromTrash,
                            onClick = { viewModel.restoreFromTrash(file); inspectedFile = null },
                            modifier = Modifier.weight(1f)
                        )
                        FileDudeSecondaryButton(
                            text = "Delete Forever",
                            icon = Icons.Default.DeleteForever,
                            onClick = { viewModel.deletePermanently(file); inspectedFile = null },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            FileDudeTopBar(
                title = "My Files",
                subtitle = "${uiState.storageStats.fileCount} files • ${formatFileSize(uiState.storageStats.totalBytes)}",
                actions = {
                    IconButton(onClick = { multiSelectActive = !multiSelectActive; if (!multiSelectActive) viewModel.clearSelection() }) {
                        Icon(
                            Icons.Default.CheckBox,
                            contentDescription = "Toggle multi-select",
                            tint = if (multiSelectActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort files")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            FileSortOption.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    onClick = {
                                        viewModel.updateSort(opt)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        // Import & Storage Analyser Strip
        item {
            FileDudeCard(modifier = Modifier.widthIn(max = 840.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        FileDudePrimaryButton(
                            text = "Import Photos/Videos",
                            icon = Icons.Default.Image,
                            onClick = {
                                mediaImporter.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "files_import_media_button"
                        )
                        FileDudeSecondaryButton(
                            text = "Import Docs/PDF",
                            icon = Icons.Default.FolderOpen,
                            onClick = { docImporter.launch(arrayOf("application/pdf", "image/*", "video/*", "text/*")) },
                            modifier = Modifier.weight(1f),
                            testTag = "files_import_doc_button"
                        )
                    }
                    val st = uiState.storageStats
                    val total = st.totalBytes.coerceAtLeast(1L).toFloat()
                    LinearProgressIndicator(
                        progress = { if (st.totalBytes == 0L) 0f else ((st.pdfBytes + st.imageBytes + st.videoBytes) / total).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = ext.paleMint
                    )
                    Text(
                        text = "PDF ${formatFileSize(st.pdfBytes)} • Images ${formatFileSize(st.imageBytes)} • Video ${formatFileSize(st.videoBytes)} • Cache ${formatFileSize(st.cacheBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!uiState.statusMessage.isNullOrBlank()) {
            item { FileDudeSuccessBanner(title = "Updated", subtitle = uiState.statusMessage, modifier = Modifier.widthIn(max = 840.dp)) }
        }
        if (!uiState.errorMessage.isNullOrBlank()) {
            item {
                FileDudeErrorBanner(
                    title = "Action Failed",
                    message = uiState.errorMessage,
                    onDismiss = viewModel::clearMessages,
                    modifier = Modifier.widthIn(max = 840.dp)
                )
            }
        }

        item {
            FileDudeSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::updateSearch,
                placeholder = "Search workspace files or folders...",
                modifier = Modifier.widthIn(max = 840.dp),
                testTag = "files_search_bar"
            )
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileManagerFilter.entries.forEach { filter ->
                    FileDudeCategoryChip(
                        label = filter.label,
                        selected = uiState.activeFilter == filter,
                        onClick = { viewModel.selectFilter(filter) }
                    )
                }
            }
        }

        // Folder Chips when FOLDERS filter active
        if (uiState.activeFilter == FileManagerFilter.FOLDERS && uiState.availableFolders.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileDudeCategoryChip(
                        label = "All Folders",
                        selected = uiState.selectedFolderName == null,
                        onClick = { viewModel.selectFolder(null) }
                    )
                    uiState.availableFolders.forEach { fName ->
                        FileDudeCategoryChip(
                            label = fName,
                            selected = uiState.selectedFolderName == fName,
                            onClick = { viewModel.selectFolder(fName) }
                        )
                    }
                }
            }
        }

        // Multi-Select Batch Action Toolbar
        if (multiSelectActive) {
            item {
                FileDudeCard(
                    modifier = Modifier.widthIn(max = 840.dp),
                    containerColor = ext.paleMint
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${uiState.selectedIds.size} selected", style = MaterialTheme.typography.titleSmall, color = ext.darkGreenText)
                        Row {
                            TextButton(onClick = viewModel::selectAllVisible) { Text("All") }
                            IconButton(
                                onClick = { showBatchRenameDialog = true },
                                enabled = uiState.selectedIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Batch rename")
                            }
                            IconButton(
                                onClick = { showBatchMoveDialog = true },
                                enabled = uiState.selectedIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = "Batch move")
                            }
                            IconButton(
                                onClick = { viewModel.batchTrashSelected(); multiSelectActive = false },
                                enabled = uiState.selectedIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Batch move to trash")
                            }
                        }
                    }
                }
            }
        }

        if (uiState.activeFilter == FileManagerFilter.TRASH && uiState.files.isNotEmpty()) {
            item {
                Row(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showEmptyTrashDialog = true }) {
                        Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (uiState.activeFilter == FileManagerFilter.DUPLICATES) {
            if (uiState.duplicateGroups.isEmpty()) {
                item {
                    FileDudeEmptyState(
                        title = "No Duplicate Files Found",
                        description = "FileDude checks SHA-256 checksums across your workspace and found all files are unique.",
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            } else {
                item {
                    FileDudeStatusBadge(
                        text = "${uiState.duplicateGroups.size} exact SHA-256 duplicate group(s) found",
                        containerColor = ext.warningContainer,
                        contentColor = ext.warning
                    )
                }
            }
        }

        if (uiState.files.isEmpty() && uiState.activeFilter != FileManagerFilter.DUPLICATES) {
            item {
                FileDudeEmptyState(
                    title = "No files in ${uiState.activeFilter.label}",
                    description = "Use Import Photos/Videos or Import Docs/PDF above, or process a file in any tool studio.",
                    icon = Icons.Default.FolderOpen,
                    modifier = Modifier.widthIn(max = 600.dp)
                )
            }
        } else {
            items(uiState.files, key = { it.id }) { file ->
                val selected = file.id in uiState.selectedIds
                FileDudeFileRow(
                    file = file,
                    selected = selected,
                    onClick = {
                        if (multiSelectActive) viewModel.toggleSelection(file.id) else inspectedFile = file
                    },
                    onToggleFavourite = if (!file.isInTrash) ({ viewModel.toggleFavourite(file) }) else null,
                    modifier = Modifier.widthIn(max = 840.dp)
                )
            }
        }

        item { FileDudeAdContainer(location = AdPlacementLocation.FILES_FOOTER, planTier = planTier) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
