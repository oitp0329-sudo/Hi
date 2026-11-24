package com.example.features.tools

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeCategoryChip
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeProcessingCard
import com.example.core.design.FileDudeSecondaryButton
import com.example.core.design.FileDudeSuccessBanner
import com.example.core.design.FileDudeTopBar
import com.example.core.design.formatDurationMs
import com.example.core.design.formatFileSize
import com.example.core.model.ToolExecutionState
import com.example.core.model.VideoExportPreset
import com.example.ui.theme.LocalFileDudeColors
import java.io.File
import kotlin.math.roundToLong

@Composable
fun VideoStudioScreen(
    uiState: VideoStudioUiState,
    viewModel: VideoStudioViewModel,
    fileRepository: WorkspaceFileRepository,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val ext = LocalFileDudeColors.current
    var pendingExportPath by remember { mutableStateOf<String?>(null) }

    val singleVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::importPrimaryVideo) }

    val multiVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> viewModel.importMultipleVideos(uris) }

    val watermarkImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::importWatermarkImage) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importReplacementAudio) }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val src = pendingExportPath
        if (uri != null && src != null) {
            viewModel.exportToUri(src, uri)
        }
        pendingExportPath = null
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    val activeVideoPath = uiState.selectedFile?.localPath
    LaunchedEffect(activeVideoPath) {
        if (!activeVideoPath.isNullOrBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(activeVideoPath))))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FileDudeTopBar(
            title = "Video Studio",
            subtitle = uiState.selectedFile?.displayName ?: "Media3 hardware-accelerated processing",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoToolMode.entries.forEach { mode ->
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

            val isMultiMode = uiState.mode == VideoToolMode.MERGE || uiState.mode == VideoToolMode.BATCH
            FileDudeCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isMultiMode) "Video Queue (${uiState.multiFiles.size})" else "Source Video",
                            style = MaterialTheme.typography.titleMedium
                        )
                        FileDudeSecondaryButton(
                            text = if (isMultiMode) "Pick Clips" else if (uiState.selectedFile == null) "Pick Video" else "Change",
                            icon = Icons.Default.VideoFile,
                            onClick = {
                                if (isMultiMode) {
                                    multiVideoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                } else {
                                    singleVideoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            },
                            testTag = "video_pick_button"
                        )
                    }

                    if (!isMultiMode && uiState.selectedFile != null) {
                        val f = uiState.selectedFile
                        val meta = uiState.metadata
                        Text(
                            text = "${f.displayName} • ${formatFileSize(f.sizeBytes)} • ${meta?.widthPx ?: f.widthPx}×${meta?.heightPx ?: f.heightPx} • ${formatDurationMs(meta?.durationMs ?: f.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isMultiMode) {
                        if (uiState.multiFiles.isEmpty()) {
                            Text(
                                "Pick 2 or more clips for merge or batch export.",
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
                                    Text("${idx + 1}. ${mf.displayName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.moveMultiVideo(idx, -1) }, enabled = idx > 0) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move clip up")
                                    }
                                    IconButton(onClick = { viewModel.moveMultiVideo(idx, 1) }, enabled = idx < uiState.multiFiles.lastIndex) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move clip down")
                                    }
                                    IconButton(onClick = { viewModel.removeMultiVideo(mf.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove clip")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ExoPlayer Preview
            if (!isMultiMode && uiState.selectedFile != null) {
                FileDudeCard {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        update = { view ->
                            if (view.player !== exoPlayer) {
                                view.player = exoPlayer
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }

            // Export Quality Preset Selector (shared across most modes)
            if (uiState.mode in listOf(VideoToolMode.TRIM, VideoToolMode.COMPRESS, VideoToolMode.MERGE, VideoToolMode.BATCH)) {
                FileDudeCard {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Export Resolution Preset", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoExportPreset.entries.forEach { preset ->
                                FileDudeCategoryChip(
                                    label = preset.label,
                                    selected = uiState.preset == preset,
                                    onClick = { viewModel.updateSettings { it.copy(preset = preset) } }
                                )
                            }
                        }
                    }
                }
            }

            when (uiState.mode) {
                VideoToolMode.TRIM -> {
                    val maxDur = (uiState.metadata?.durationMs ?: uiState.selectedFile?.durationMs ?: 10_000L).coerceAtLeast(1000L)
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Trim Range", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Start: ${formatDurationMs(uiState.trimStartMs)}  •  End: ${formatDurationMs(uiState.trimEndMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val startF = uiState.trimStartMs.toFloat().coerceIn(0f, maxDur.toFloat())
                            val endF = uiState.trimEndMs.toFloat().coerceIn(startF, maxDur.toFloat())
                            RangeSlider(
                                value = startF..endF,
                                onValueChange = { range ->
                                    viewModel.updateSettings {
                                        it.copy(
                                            trimStartMs = range.start.roundToLong(),
                                            trimEndMs = range.endInclusive.roundToLong()
                                        )
                                    }
                                },
                                valueRange = 0f..maxDur.toFloat()
                            )
                            FileDudePrimaryButton(
                                text = "Trim & Export MP4",
                                enabled = uiState.selectedFile != null,
                                onClick = { viewModel.runVideoExport(extractAudioOnly = false) },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "video_trim_export_button"
                            )
                        }
                    }
                }

                VideoToolMode.COMPRESS -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Compression & Custom Bitrate", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = uiState.customBitrateKbpsText,
                                onValueChange = { t -> viewModel.updateSettings { it.copy(customBitrateKbpsText = t.filter { ch -> ch.isDigit() }) } },
                                label = { Text("Custom Bitrate (kbps, leave empty for preset)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FileDudePrimaryButton(
                                text = "Compress & Resize Video",
                                enabled = uiState.selectedFile != null,
                                onClick = { viewModel.runVideoExport(extractAudioOnly = false) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                VideoToolMode.MERGE -> {
                    FileDudePrimaryButton(
                        text = "Merge ${uiState.multiFiles.size} Clips",
                        icon = Icons.Default.MergeType,
                        enabled = uiState.multiFiles.size >= 2,
                        onClick = viewModel::runMergeVideos,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                VideoToolMode.TRANSFORM -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Rotation & Center Crop", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(0f, 90f, 180f, 270f).forEach { deg ->
                                    FileDudeCategoryChip(
                                        label = "${deg.toInt()}°",
                                        selected = uiState.rotationDegrees == deg,
                                        onClick = { viewModel.updateSettings { it.copy(rotationDegrees = deg) } }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.cropNormalized,
                                    onCheckedChange = { c -> viewModel.updateSettings { it.copy(cropNormalized = c) } }
                                )
                                Text("Center 75% frame crop")
                            }
                            FileDudePrimaryButton(
                                text = "Apply Transform & Export",
                                enabled = uiState.selectedFile != null,
                                onClick = { viewModel.runVideoExport(extractAudioOnly = false) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                VideoToolMode.EFFECTS -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Playback Speed (${uiState.speedMultiplier}x)", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                    FileDudeCategoryChip(
                                        label = "${spd}x",
                                        selected = uiState.speedMultiplier == spd,
                                        onClick = { viewModel.updateSettings { it.copy(speedMultiplier = spd) } }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = uiState.overlayText,
                                onValueChange = { txt -> viewModel.updateSettings { it.copy(overlayText = txt) } },
                                label = { Text("Text overlay stamp") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.watermarkImageFile?.displayName ?: "Optional image watermark",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        watermarkImagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) { Text("Pick Image") }
                            }
                            FileDudePrimaryButton(
                                text = "Apply Speed & Overlays",
                                enabled = uiState.selectedFile != null,
                                onClick = { viewModel.runVideoExport(extractAudioOnly = false) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                VideoToolMode.AUDIO -> {
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Audio Controls", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Mute original audio track")
                                Switch(
                                    checked = uiState.muteAudio,
                                    onCheckedChange = { m -> viewModel.updateSettings { it.copy(muteAudio = m) } }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.replacementAudioFile?.displayName ?: "No replacement audio track selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                                    Text("Pick Audio")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FileDudePrimaryButton(
                                    text = "Export Video",
                                    enabled = uiState.selectedFile != null,
                                    onClick = { viewModel.runVideoExport(extractAudioOnly = false) },
                                    modifier = Modifier.weight(1f)
                                )
                                FileDudeSecondaryButton(
                                    text = "Extract M4A",
                                    icon = Icons.Default.MusicNote,
                                    enabled = uiState.selectedFile != null,
                                    onClick = { viewModel.runVideoExport(extractAudioOnly = true) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                VideoToolMode.INFO -> {
                    val meta = uiState.metadata
                    FileDudeCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Video Metadata & Thumbnail", style = MaterialTheme.typography.titleMedium)
                            if (meta != null) {
                                Text("Codec / MIME: ${meta.mimeType} • Rotation: ${meta.rotationDegrees}°", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Resolution: ${meta.widthPx}×${meta.heightPx} • Bitrate: ${meta.bitrateBps / 1000} kbps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val maxDur = (meta?.durationMs ?: 10_000L).coerceAtLeast(1000L)
                            Text("Frame Position: ${formatDurationMs(uiState.thumbnailTimeMs)}", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = uiState.thumbnailTimeMs.toFloat().coerceIn(0f, maxDur.toFloat()),
                                onValueChange = { v -> viewModel.updateSettings { it.copy(thumbnailTimeMs = v.roundToLong()) } },
                                valueRange = 0f..maxDur.toFloat()
                            )
                            FileDudePrimaryButton(
                                text = "Extract Thumbnail Frame (JPG)",
                                icon = Icons.Default.PhotoCamera,
                                enabled = uiState.selectedFile != null,
                                onClick = viewModel::runExtractThumbnail,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Honest Video-to-GIF Capability Notice
                    FileDudeErrorBanner(
                        title = "Video-to-GIF Notice",
                        message = "FileDude uses Android Media3 hardware encoding (H.264 MP4). Direct animated .GIF raster encoding is not included offline; use 360p muted MP4 export or JPG frame extraction above instead.",
                        isWarning = true
                    )
                }

                VideoToolMode.BATCH -> {
                    FileDudePrimaryButton(
                        text = "Batch Export ${uiState.multiFiles.size} Video(s)",
                        enabled = uiState.multiFiles.isNotEmpty(),
                        onClick = viewModel::runBatchExport,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

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
                                                fileRepository.createShareIntent(out.localPath, out.mimeType, "Share Output")
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
