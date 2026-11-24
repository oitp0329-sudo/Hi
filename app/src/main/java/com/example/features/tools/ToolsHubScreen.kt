package com.example.features.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeSearchBar
import com.example.core.design.FileDudeToolCard
import com.example.core.design.FileDudeTopBar
import com.example.core.model.PlanTier
import com.example.subscription.AdPlacementLocation
import com.example.subscription.FileDudeAdContainer
import com.example.ui.theme.LocalFileDudeColors

private data class ToolTileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String? = null,
    val onClick: () -> Unit
)

@Composable
fun ToolsHubScreen(
    planTier: PlanTier = PlanTier.FREE,
    onOpenSmartFileReady: () -> Unit,
    onOpenAiMode: (String) -> Unit = {},
    onOpenPdfMode: (String) -> Unit,
    onOpenImageMode: (String) -> Unit,
    onOpenVideoMode: (String) -> Unit,
    onOpenDocumentWallet: () -> Unit
) {
    val ext = LocalFileDudeColors.current
    var query by remember { mutableStateOf("") }

    val pdfTools = remember {
        listOf(
            ToolTileSpec("PDF Viewer & Print", "Read, inspect & print PDFs", Icons.Default.PictureAsPdf, "Free") { onOpenPdfMode("VIEW") },
            ToolTileSpec("Compress PDF", "Reduce PDF file size", Icons.Default.Compress) { onOpenPdfMode("COMPRESS") },
            ToolTileSpec("Merge PDF", "Combine multiple PDFs", Icons.Default.MergeType) { onOpenPdfMode("MERGE") },
            ToolTileSpec("Split & Extract", "Split ranges or extract pages", Icons.Default.ContentCut) { onOpenPdfMode("SPLIT") },
            ToolTileSpec("Page Editor", "Reorder, rotate & delete pages", Icons.Default.ViewAgenda) { onOpenPdfMode("PAGES") },
            ToolTileSpec("Image to PDF", "Convert photos into PDF", Icons.Default.Image) { onOpenPdfMode("IMG2PDF") },
            ToolTileSpec("PDF to JPG / PNG", "Export pages as images", Icons.Default.Transform) { onOpenPdfMode("PDF2IMG") },
            ToolTileSpec("Sign, Annotate & Fill", "Sign, highlight, text & forms", Icons.Default.Draw) { onOpenPdfMode("ANNOTATE") },
            ToolTileSpec("Watermark & Numbers", "Stamp text & page numbers", Icons.Default.TextFields) { onOpenPdfMode("WATERMARK") },
            ToolTileSpec("OCR PDF", "Extract text from scanned pages", Icons.Default.TextFields, "ML Kit") { onOpenPdfMode("OCR") },
            ToolTileSpec("Protect & Unlock", "Encrypt or remove auth password", Icons.Default.Lock) { onOpenPdfMode("SECURITY") },
            ToolTileSpec("Metadata, Size & Batch", "Edit info, A4/Letter & batch", Icons.Default.FolderOpen) { onOpenPdfMode("METADATA") }
        )
    }

    val imageTools = remember {
        listOf(
            ToolTileSpec("Image Studio", "Filters, crop, adjust & compare", Icons.Default.Brush, "Live") { onOpenImageMode("STUDIO") },
            ToolTileSpec("Convert Format", "JPG, PNG, WebP & BMP", Icons.Default.Transform) { onOpenImageMode("CONVERT") },
            ToolTileSpec("Resize & Passport", "Exact px, %, or ID specs", Icons.Default.PhotoSizeSelectLarge) { onOpenImageMode("RESIZE") },
            ToolTileSpec("Target KB Compress", "Hit exact KB / MB file limits", Icons.Default.Compress) { onOpenImageMode("COMPRESS") },
            ToolTileSpec("Collage Maker", "Combine 2–4 photos in grid", Icons.Default.Filter) { onOpenImageMode("COLLAGE") },
            ToolTileSpec("OCR Text Extract", "On-device ML Kit recognition", Icons.Default.TextFields, "ML Kit") { onOpenImageMode("OCR") },
            ToolTileSpec("EXIF & Privacy Strip", "Inspect or remove camera EXIF", Icons.Default.Lock) { onOpenImageMode("EXIF") },
            ToolTileSpec("Batch Image Export", "Convert or resize multiple", Icons.Default.FolderOpen) { onOpenImageMode("BATCH") }
        )
    }

    val videoTools = remember {
        listOf(
            ToolTileSpec("Trim & Cut Video", "Timeline handles & export", Icons.Default.ContentCut, "Media3") { onOpenVideoMode("TRIM") },
            ToolTileSpec("Compress & Resize", "360p, 480p, 720p, 1080p", Icons.Default.Compress) { onOpenVideoMode("COMPRESS") },
            ToolTileSpec("Merge Videos", "Concatenate multiple clips", Icons.Default.MergeType) { onOpenVideoMode("MERGE") },
            ToolTileSpec("Crop & Rotate", "Frame crop & 90/180/270 rot", Icons.Default.PhotoSizeSelectLarge) { onOpenVideoMode("TRANSFORM") },
            ToolTileSpec("Speed & Overlays", "0.5x–2.0x speed & watermark", Icons.Default.Speed) { onOpenVideoMode("EFFECTS") },
            ToolTileSpec("Extract / Mute / Audio", "Extract M4A, mute or replace", Icons.Default.MusicNote) { onOpenVideoMode("AUDIO") },
            ToolTileSpec("Thumbnail & Info", "Extract frame & inspect codec", Icons.Default.VideoFile) { onOpenVideoMode("INFO") },
            ToolTileSpec("Batch Video Export", "Queue multiple clips safely", Icons.Default.FolderOpen) { onOpenVideoMode("BATCH") }
        )
    }

    val aiTools = remember {
        listOf(
            ToolTileSpec("Chat with PDF & Files", "Ask questions about documents", Icons.Default.AutoAwesome, "Gemini") { onOpenAiMode("CHAT") },
            ToolTileSpec("Summarize & Explain", "Key points & plain English", Icons.Default.TextFields) { onOpenAiMode("SUMMARIZE") },
            ToolTileSpec("Extract Info & Translate", "Dates, IDs, or 9+ languages", Icons.Default.Transform) { onOpenAiMode("EXTRACT") },
            ToolTileSpec("Compare & Workflow", "Diff two docs or suggest steps", Icons.Default.MergeType) { onOpenAiMode("COMPARE") }
        )
    }

    val q = query.trim().lowercase()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            FileDudeTopBar(
                title = "All FileDude Tools",
                subtitle = "Offline-first PDF, Image, Video & Document Studios"
            )
        }

        item {
            FileDudeSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Filter tools (e.g. Merge, OCR, Passport, Trim)...",
                modifier = Modifier.widthIn(max = 840.dp),
                testTag = "tools_search_bar"
            )
        }

        if (q.isEmpty() || "smart file ready passport job".contains(q)) {
            item {
                FileDudeCard(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .testTag("tools_smart_ready_card"),
                    containerColor = ext.paleMint,
                    onClick = onOpenSmartFileReady
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart File Ready", style = MaterialTheme.typography.titleMedium, color = ext.darkGreenText)
                            Text(
                                "Auto-prepare & verify documents for forms, visas, jobs & portals.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        @Composable
        fun renderSection(sectionTitle: String, items: List<ToolTileSpec>) {
            val filtered = if (q.isEmpty()) items else items.filter {
                it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q)
            }
            if (filtered.isEmpty()) return
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(sectionTitle, style = MaterialTheme.typography.titleMedium)
                filtered.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { spec ->
                            FileDudeToolCard(
                                title = spec.title,
                                subtitle = spec.subtitle,
                                icon = spec.icon,
                                badge = spec.badge,
                                onClick = spec.onClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        item { renderSection("FileDude AI Assistant", aiTools) }
        item { renderSection("PDF Toolkit (27 Tools)", pdfTools) }
        item { FileDudeAdContainer(location = AdPlacementLocation.TOOLS_HUB, planTier = planTier) }
        item { renderSection("Image Editing Studio", imageTools) }
        item { renderSection("Video Editing Studio", videoTools) }

        if (q.isEmpty() || "wallet document expiry reminder".contains(q)) {
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Document Tools", style = MaterialTheme.typography.titleMedium)
                    FileDudeToolCard(
                        title = "Private Document Wallet",
                        subtitle = "Store IDs, insurance, certificates & schedule 90/30/7/1-day expiry alerts.",
                        icon = Icons.Default.Badge,
                        badge = "Offline Vault",
                        onClick = onOpenDocumentWallet,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}
