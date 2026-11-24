package com.example.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeEmptyState
import com.example.core.design.FileDudeFileRow
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeSearchBar
import com.example.core.design.FileDudeStatusBadge
import com.example.core.design.FileDudeToolCard
import com.example.core.design.formatDateShort
import com.example.core.design.formatFileSize
import com.example.core.model.PlanTier
import com.example.core.model.QuickActionId
import com.example.core.model.WorkspaceFile
import com.example.subscription.AdPlacementLocation
import com.example.subscription.FileDudeAdContainer
import com.example.ui.theme.LocalFileDudeColors
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    userDisplayName: String?,
    planTier: PlanTier = PlanTier.FREE,
    onSearchChange: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSmartFileReady: () -> Unit,
    onOpenAiAssistant: () -> Unit = {},
    onQuickActionClick: (QuickActionId) -> Unit,
    onMoveQuickActionUp: (QuickActionId) -> Unit,
    onMoveQuickActionDown: (QuickActionId) -> Unit,
    onResetQuickActions: () -> Unit,
    onOpenPdfStudio: () -> Unit,
    onOpenImageStudio: () -> Unit,
    onOpenVideoStudio: () -> Unit,
    onOpenDocumentWallet: () -> Unit,
    onOpenToolsTab: () -> Unit,
    onOpenFilesTab: () -> Unit,
    onOpenRemindersTab: () -> Unit,
    onFileClick: (WorkspaceFile) -> Unit,
    onToggleFavourite: (WorkspaceFile) -> Unit
) {
    val ext = LocalFileDudeColors.current
    var showCustomizeActions by remember { mutableStateOf(false) }
    var showAllQuickActions by remember { mutableStateOf(false) }
    var showRecentTasksSection by remember { mutableStateOf(false) }

    if (showCustomizeActions) {
        ModalBottomSheet(
            onDismissRequest = { showCustomizeActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Customize Quick Actions", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onResetQuickActions) {
                        Text("Reset")
                    }
                }
                Text(
                    "Reorder your most-used tools on the Home Dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                uiState.quickActions.forEachIndexed { idx, action ->
                    FileDudeCard(containerColor = ext.secondarySurface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onMoveQuickActionUp(action) },
                                enabled = idx > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = { onMoveQuickActionDown(action) },
                                enabled = idx < uiState.quickActions.lastIndex
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with Logo, Greeting, Bell & Profile
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.filedude_logo),
                    contentDescription = "FileDude Logo",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val greetingName = userDisplayName?.takeIf { it.isNotBlank() } ?: "there"
                    Text(
                        text = "Hi, $greetingName",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Your Files. Ready When You Are.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (uiState.upcomingExpiries.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("${uiState.upcomingExpiries.size}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Expiry notifications"
                        )
                    }
                }
                IconButton(
                    onClick = onOpenProfile,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_profile_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ext.paleMint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User profile",
                            tint = ext.darkGreenText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Global Search Bar
        item {
            FileDudeSearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Search PDF, Image, Video tools or workspace files...",
                modifier = Modifier.widthIn(max = 840.dp),
                testTag = "home_search_bar"
            )
        }

        // Signature Feature Hero Card: Smart File Ready
        item {
            FileDudeCard(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .testTag("smart_file_ready_hero_card"),
                containerColor = ext.paleMint,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                onClick = onOpenSmartFileReady
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ext.paleMint, ext.secondarySurface)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileDudeStatusBadge(
                            text = "SIGNATURE FEATURE",
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = ext.darkGreenText
                        )
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Smart File Ready",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ext.darkGreenText
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Prepare passport photos, job applications, college & government portal uploads to exact KB, format, and pixel limits—with real pass/fail verification.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(14.dp))
                    FileDudePrimaryButton(
                        text = "Prepare & Verify a File",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = onOpenSmartFileReady,
                        testTag = "home_smart_ready_cta"
                    )
                }
            }
        }

        // Quick Actions (Clean 4-item default with Expandable View All + Customize)
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
            ) {
                SectionHeader(
                    title = "Quick Actions",
                    actionLabel = "Customize",
                    actionIcon = Icons.Default.Tune,
                    onAction = { showCustomizeActions = true }
                )
                Spacer(Modifier.height(10.dp))
                val visibleActions = if (showAllQuickActions || uiState.searchQuery.isNotBlank()) {
                    uiState.quickActions
                } else {
                    uiState.quickActions.take(4)
                }
                val rows = visibleActions.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { qa ->
                                FileDudeToolCard(
                                    title = qa.title,
                                    subtitle = qa.subtitle,
                                    icon = quickActionIcon(qa),
                                    onClick = { onQuickActionClick(qa) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "quick_action_${qa.name.lowercase()}"
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (uiState.quickActions.size > 4 && uiState.searchQuery.isBlank()) {
                        TextButton(
                            onClick = { showAllQuickActions = !showAllQuickActions },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (showAllQuickActions) "Show Fewer Actions" else "View All (${uiState.quickActions.size})")
                        }
                    }
                }
            }
        }

        // Tool Categories (PDF, Image, Video, Document Tools)
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
            ) {
                SectionHeader(
                    title = "Tool Studios",
                    actionLabel = "View All",
                    onAction = onOpenToolsTab
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        FileDudeToolCard(
                            title = "PDF Tools",
                            subtitle = "27 tools • Merge, Split, Sign, OCR, Lock",
                            icon = Icons.Default.PictureAsPdf,
                            badge = "27",
                            onClick = onOpenPdfStudio,
                            modifier = Modifier.width(196.dp),
                            testTag = "home_pdf_section_card"
                        )
                    }
                    item {
                        FileDudeToolCard(
                            title = "Image Studio",
                            subtitle = "Convert, Resize, KB Compress, Filters, EXIF",
                            icon = Icons.Default.Image,
                            badge = "Studio",
                            onClick = onOpenImageStudio,
                            modifier = Modifier.width(196.dp),
                            testTag = "home_image_section_card"
                        )
                    }
                    item {
                        FileDudeToolCard(
                            title = "Video Studio",
                            subtitle = "Trim, Merge, 360p–1080p, Speed, Audio",
                            icon = Icons.Default.VideoFile,
                            badge = "Media3",
                            onClick = onOpenVideoStudio,
                            modifier = Modifier.width(196.dp),
                            testTag = "home_video_section_card"
                        )
                    }
                    item {
                        FileDudeToolCard(
                            title = "FileDude AI",
                            subtitle = "Chat with PDF, Summarize, Extract, Translate & Compare",
                            icon = Icons.Default.AutoAwesome,
                            badge = "Gemini",
                            onClick = onOpenAiAssistant,
                            modifier = Modifier.width(196.dp),
                            testTag = "home_ai_section_card"
                        )
                    }
                    item {
                        FileDudeToolCard(
                            title = "Document Tools",
                            subtitle = "Private Wallet, Renewals & Expiry Alerts",
                            icon = Icons.Default.Badge,
                            badge = "Vault",
                            onClick = onOpenDocumentWallet,
                            modifier = Modifier.width(196.dp),
                            testTag = "home_document_section_card"
                        )
                    }
                }
            }
        }

        // Dedicated configurable rectangular advertisement space (collapses when Premium or unconfigured)
        item {
            FileDudeAdContainer(
                location = AdPlacementLocation.HOME_FEED,
                planTier = planTier
            )
        }

        // Upcoming Expiry Reminders
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
            ) {
                SectionHeader(
                    title = "Upcoming Expiry Reminders",
                    actionLabel = "View All",
                    onAction = onOpenRemindersTab
                )
                Spacer(Modifier.height(8.dp))
                if (uiState.upcomingExpiries.isEmpty()) {
                    FileDudeCard(onClick = onOpenDocumentWallet) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("No upcoming expiries", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Add passports, licences, or insurance policies to receive 90/30/7/1-day alerts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.upcomingExpiries.forEach { doc ->
                            val daysLeft = ceil((doc.expiryDateMillis - System.currentTimeMillis()) / 86_400_000.0).toInt()
                            FileDudeCard(onClick = onOpenRemindersTab) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${doc.category.label} • Expires ${formatDateShort(doc.expiryDateMillis)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FileDudeStatusBadge(
                                        text = if (daysLeft <= 0) "EXPIRED" else "${daysLeft}d left",
                                        containerColor = if (daysLeft <= 7) MaterialTheme.colorScheme.errorContainer else ext.warningContainer,
                                        contentColor = if (daysLeft <= 7) MaterialTheme.colorScheme.error else ext.warning
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Storage Overview
        item {
            FileDudeCard(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(),
                onClick = onOpenFilesTab
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Workspace Storage Overview", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = formatFileSize(uiState.storageStats.totalBytes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val total = uiState.storageStats.totalBytes.coerceAtLeast(1L).toFloat()
                    LinearProgressIndicator(
                        progress = { if (uiState.storageStats.totalBytes == 0L) 0f else (uiState.storageStats.pdfBytes / total).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = ext.paleMint
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${uiState.storageStats.fileCount} files • PDF ${formatFileSize(uiState.storageStats.pdfBytes)} • Images ${formatFileSize(uiState.storageStats.imageBytes)} • Video ${formatFileSize(uiState.storageStats.videoBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Recent Files
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
            ) {
                SectionHeader(
                    title = "Recent Files",
                    actionLabel = "View All",
                    onAction = onOpenFilesTab
                )
                Spacer(Modifier.height(8.dp))
                if (uiState.recentFiles.isEmpty()) {
                    FileDudeEmptyState(
                        title = "Your workspace is ready",
                        description = "Pick a PDF, photo, or video inside any tool or in My Files. Originals always stay safe.",
                        icon = Icons.Default.Description,
                        actionLabel = "Browse My Files",
                        onAction = onOpenFilesTab
                    )
                }
            }
        }

        items(uiState.recentFiles, key = { it.id }) { file ->
            FileDudeFileRow(
                file = file,
                onClick = { onFileClick(file) },
                onToggleFavourite = { onToggleFavourite(file) },
                modifier = Modifier.widthIn(max = 840.dp)
            )
        }

        // Favourites & Recently Completed Tasks
        if (uiState.favouriteFiles.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionHeader(title = "Favourites", actionLabel = "View All", onAction = onOpenFilesTab)
                    uiState.favouriteFiles.forEach { fav ->
                        FileDudeFileRow(
                            file = fav,
                            onClick = { onFileClick(fav) },
                            onToggleFavourite = { onToggleFavourite(fav) }
                        )
                    }
                }
            }
        }

        if (uiState.recentTasks.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionHeader(
                        title = "Recently Completed Tasks",
                        actionLabel = if (showRecentTasksSection) "Hide" else "Show (${uiState.recentTasks.size})",
                        onAction = { showRecentTasksSection = !showRecentTasksSection }
                    )
                    if (showRecentTasksSection) {
                        uiState.recentTasks.forEach { task ->
                            FileDudeCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(task.toolName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${task.inputName} → ${task.outputName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FileDudeStatusBadge(
                                        text = if (task.savedBytes > 0) "-${formatFileSize(task.savedBytes)}" else task.status,
                                        containerColor = ext.paleMint,
                                        contentColor = ext.darkGreenText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                if (actionIcon != null) {
                    Icon(actionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(actionLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun quickActionIcon(id: QuickActionId): ImageVector = when (id) {
    QuickActionId.COMPRESS_PDF -> Icons.Default.Compress
    QuickActionId.MERGE_PDF -> Icons.Default.MergeType
    QuickActionId.CONVERT_IMAGE -> Icons.Default.Transform
    QuickActionId.RESIZE_IMAGE -> Icons.Default.PhotoSizeSelectLarge
    QuickActionId.COMPRESS_IMAGE -> Icons.Default.Compress
    QuickActionId.IMAGE_TO_PDF -> Icons.Default.PictureAsPdf
    QuickActionId.EDIT_VIDEO -> Icons.Default.ContentCut
    QuickActionId.EXTRACT_TEXT -> Icons.Default.TextFields
}
