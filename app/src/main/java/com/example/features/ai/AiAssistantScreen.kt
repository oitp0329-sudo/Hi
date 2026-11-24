package com.example.features.ai

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.core.ai.AiExtractionResult
import com.example.core.ai.AiToolRouteKey
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeCategoryChip
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeSecondaryButton
import com.example.core.design.FileDudeStatusBadge
import com.example.core.design.FileDudeSuccessBanner
import com.example.core.design.FileDudeTopBar
import com.example.core.design.formatFileSize
import com.example.core.model.PlanTier
import com.example.core.model.WorkspaceFile
import com.example.ui.theme.LocalFileDudeColors

@Composable
fun AiAssistantScreen(
    uiState: AiAssistantUiState,
    viewModel: AiAssistantViewModel,
    onLaunchSuggestedTool: (AiToolRouteKey, WorkspaceFile?) -> Unit,
    onUpgradeClick: () -> Unit,
    onBack: () -> Unit
) {
    val primaryDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importPrimaryUri) }

    val primaryImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::importPrimaryUri) }

    val secondaryDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importSecondaryUri) }

    AiAssistantContent(
        uiState = uiState,
        onBack = onBack,
        onClearChat = viewModel::clearChat,
        onUpgradeClick = onUpgradeClick,
        onSelectMode = viewModel::selectMode,
        onClearPrimaryFile = viewModel::clearPrimaryFile,
        onPickPrimaryDoc = { primaryDocPicker.launch(arrayOf("application/pdf", "text/*", "image/*")) },
        onPickPrimaryPhoto = {
            primaryImagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onSelectPrimaryFile = viewModel::selectPrimaryFile,
        onPickSecondaryDoc = { secondaryDocPicker.launch(arrayOf("application/pdf", "text/*", "image/*")) },
        onSelectSecondaryFile = viewModel::selectSecondaryFile,
        onDismissStatus = viewModel::dismissStatus,
        onCancelTask = viewModel::cancelActiveTask,
        onUpdateChatInput = viewModel::updateChatInput,
        onSendChatQuestion = viewModel::sendChatQuestion,
        onUpdateCustomText = viewModel::updateCustomText,
        onUpdateAudienceStyle = viewModel::updateAudienceStyle,
        onRunSummarize = viewModel::runSummarize,
        onRunExtractInfo = viewModel::runExtractInfo,
        onSaveExtractedExpiry = viewModel::saveExtractedExpiryToWallet,
        onUpdateTranslationLanguage = viewModel::updateTranslationLanguage,
        onRunTranslate = viewModel::runTranslate,
        onUpdateCompareFocus = viewModel::updateCompareFocus,
        onRunCompare = viewModel::runCompare,
        onUpdateWorkflowGoal = viewModel::updateWorkflowGoal,
        onRunWorkflowSuggest = viewModel::runWorkflowSuggest,
        onLaunchSuggestedTool = onLaunchSuggestedTool
    )
}

@Composable
fun AiAssistantContent(
    uiState: AiAssistantUiState,
    onBack: () -> Unit,
    onClearChat: () -> Unit,
    onUpgradeClick: () -> Unit,
    onSelectMode: (AiMode) -> Unit,
    onClearPrimaryFile: () -> Unit,
    onPickPrimaryDoc: () -> Unit,
    onPickPrimaryPhoto: () -> Unit,
    onSelectPrimaryFile: (WorkspaceFile) -> Unit,
    onPickSecondaryDoc: () -> Unit,
    onSelectSecondaryFile: (WorkspaceFile) -> Unit,
    onDismissStatus: () -> Unit,
    onCancelTask: () -> Unit,
    onUpdateChatInput: (String) -> Unit,
    onSendChatQuestion: (String?) -> Unit,
    onUpdateCustomText: (String) -> Unit,
    onUpdateAudienceStyle: (String) -> Unit,
    onRunSummarize: () -> Unit,
    onRunExtractInfo: () -> Unit,
    onSaveExtractedExpiry: (AiExtractionResult) -> Unit,
    onUpdateTranslationLanguage: (String) -> Unit,
    onRunTranslate: () -> Unit,
    onUpdateCompareFocus: (String) -> Unit,
    onRunCompare: () -> Unit,
    onUpdateWorkflowGoal: (String) -> Unit,
    onRunWorkflowSuggest: () -> Unit,
    onLaunchSuggestedTool: (AiToolRouteKey, WorkspaceFile?) -> Unit
) {
    BackHandler(onBack = onBack)

    val ext = LocalFileDudeColors.current
    val clipboard = LocalClipboardManager.current

    var pickingWorkspaceForSecondary by remember { mutableStateOf(false) }
    var showWorkspacePicker by remember { mutableStateOf(false) }

    if (showWorkspacePicker) {
        AlertDialog(
            onDismissRequest = { showWorkspacePicker = false },
            title = { Text(if (pickingWorkspaceForSecondary) "Pick Document B" else "Pick Workspace File") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.workspaceFiles.isEmpty()) {
                        Text(
                            "No files imported into FileDude yet. Use Choose Doc or Choose Photo to import a file.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        uiState.workspaceFiles.take(20).forEach { wf ->
                            FileDudeCard(
                                containerColor = ext.secondarySurface,
                                onClick = {
                                    if (pickingWorkspaceForSecondary) {
                                        onSelectSecondaryFile(wf)
                                    } else {
                                        onSelectPrimaryFile(wf)
                                    }
                                    showWorkspacePicker = false
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(wf.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${wf.category.label} • ${formatFileSize(wf.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWorkspacePicker = false }) { Text("Close") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FileDudeTopBar(
            title = "FileDude AI Assistant",
            subtitle = "Chat with PDFs, summarize, extract, translate & compare",
            onBack = onBack,
            actions = {
                if (uiState.mode == AiMode.CHAT && uiState.chatMessages.isNotEmpty()) {
                    IconButton(onClick = onClearChat) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear chat history")
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
            // Usage Quota & Tier Status Card
            FileDudeCard(
                containerColor = if (uiState.planTier == PlanTier.PREMIUM) ext.paleMint else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (uiState.planTier == PlanTier.PREMIUM) {
                                    "Premium AI • ${uiState.usedToday} / ${uiState.dailyLimit} today"
                                } else {
                                    "Free AI • ${uiState.usedToday} / ${uiState.dailyLimit} daily requests"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Powered by Firebase AI Logic (no client API key exposed)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (uiState.planTier == PlanTier.FREE) {
                        TextButton(onClick = onUpgradeClick, modifier = Modifier.testTag("ai_upgrade_button")) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Upgrade")
                        }
                    } else {
                        FileDudeStatusBadge("PRO", ext.secondarySurface, ext.darkGreenText)
                    }
                }
            }

            // Mode Tabs Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiMode.entries.forEach { mode ->
                    FileDudeCategoryChip(
                        label = mode.label,
                        selected = uiState.mode == mode,
                        onClick = { onSelectMode(mode) }
                    )
                }
            }

            // Primary Document / Media Context Selector
            FileDudeCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.mode == AiMode.COMPARE) "Document A" else "Document / Image Context",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.primaryFile != null) {
                            IconButton(onClick = onClearPrimaryFile) {
                                Icon(Icons.Default.Close, contentDescription = "Detach document")
                            }
                        }
                    }

                    if (uiState.primaryFile != null) {
                        val pf = uiState.primaryFile
                        FileDudeCard(containerColor = ext.secondarySurface) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pf.displayName, style = MaterialTheme.typography.titleSmall)
                                    val textCount = uiState.primaryPayload?.extractedText?.length ?: 0
                                    val pageBmpCount = uiState.primaryPayload?.previewBitmaps?.size ?: 0
                                    Text(
                                        text = "${pf.category.label} • ${formatFileSize(pf.sizeBytes)}" +
                                            if (textCount > 0) " • $textCount chars parsed" else if (pageBmpCount > 0) " • $pageBmpCount visual page(s) attached" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.isPreparingFile) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FileDudeSecondaryButton(
                            text = "Choose PDF/Doc",
                            icon = Icons.Default.FolderOpen,
                            onClick = onPickPrimaryDoc,
                            modifier = Modifier.weight(1f),
                            testTag = "ai_pick_doc_button"
                        )
                        FileDudeSecondaryButton(
                            text = "Choose Photo",
                            icon = Icons.Default.Image,
                            onClick = onPickPrimaryPhoto,
                            modifier = Modifier.weight(1f),
                            testTag = "ai_pick_image_button"
                        )
                    }
                    if (uiState.workspaceFiles.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                pickingWorkspaceForSecondary = false
                                showWorkspacePicker = true
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Pick from Workspace (${uiState.workspaceFiles.size})")
                        }
                    }
                }
            }

            // Secondary Document for Compare mode
            if (uiState.mode == AiMode.COMPARE) {
                FileDudeCard {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Document B (Compare Against)", style = MaterialTheme.typography.titleMedium)
                        if (uiState.secondaryFile != null) {
                            val sf = uiState.secondaryFile
                            FileDudeCard(containerColor = ext.secondarySurface) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(sf.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text("${sf.category.label} • ${formatFileSize(sf.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FileDudeSecondaryButton(
                                text = "Choose Document B",
                                icon = Icons.Default.FolderOpen,
                                onClick = onPickSecondaryDoc,
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.workspaceFiles.isNotEmpty()) {
                                FileDudeSecondaryButton(
                                    text = "From Workspace",
                                    onClick = {
                                        pickingWorkspaceForSecondary = true
                                        showWorkspacePicker = true
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // State Feedback Banners (Offline / Limit / Error / Success / Loading / Streaming)
            when (val t = uiState.taskState) {
                is AiTaskState.Offline -> {
                    FileDudeCard(containerColor = ext.warningContainer) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, contentDescription = null, tint = ext.warning)
                            Spacer(Modifier.width(10.dp))
                            Text(t.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is AiTaskState.LimitReached -> {
                    FileDudeCard(containerColor = ext.paleMint) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Daily Free AI Limit Reached", style = MaterialTheme.typography.titleSmall, color = ext.darkGreenText)
                            Text(
                                "You have used all ${uiState.dailyLimit} Free AI requests for today. Upgrade to Premium for expanded daily AI limits and ad-free tools, or return tomorrow when the quota resets.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            FileDudePrimaryButton(
                                text = "Upgrade to FileDude Premium",
                                icon = Icons.Default.Star,
                                onClick = onUpgradeClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                is AiTaskState.Error -> {
                    FileDudeErrorBanner(
                        title = "AI Notice",
                        message = t.message,
                        onDismiss = onDismissStatus
                    )
                }
                is AiTaskState.Loading -> {
                    FileDudeCard(containerColor = ext.secondarySurface) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Analyzing with Gemini via Firebase AI Logic...", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = onCancelTask) { Text("Cancel") }
                        }
                    }
                }
                else -> {}
            }

            if (!uiState.statusMessage.isNullOrBlank()) {
                FileDudeSuccessBanner(title = "Saved", subtitle = uiState.statusMessage)
            }

            // Active Mode UI
            when (uiState.mode) {
                AiMode.CHAT -> {
                    // Quick Prompt Chips
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Summarize this document in 5 bullets",
                            "What deadlines or amounts are listed?",
                            "Explain any complex or legal terms",
                            "Check if anything important is missing"
                        ).forEach { prompt ->
                            FileDudeCategoryChip(
                                label = prompt,
                                selected = false,
                                onClick = { onSendChatQuestion(prompt) }
                            )
                        }
                    }

                    if (uiState.chatMessages.isEmpty() && uiState.taskState !is AiTaskState.Streaming) {
                        FileDudeCard(containerColor = ext.secondarySurface) {
                            Text(
                                text = if (uiState.primaryFile != null) {
                                    "Ready to answer questions about ${uiState.primaryFile.displayName}. Ask anything below!"
                                } else {
                                    "Attach a PDF, photo, or text file above—or ask any document formatting, writing, or portal requirement question."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.chatMessages.forEach { msg ->
                                FileDudeCard(
                                    containerColor = if (msg.isUser) ext.paleMint else MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (msg.isUser) "You" else "FileDude AI",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (msg.isUser) ext.darkGreenText else MaterialTheme.colorScheme.primary
                                            )
                                            if (!msg.isUser) {
                                                IconButton(
                                                    onClick = { clipboard.setText(AnnotatedString(msg.text)) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy AI response", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                        Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (uiState.taskState is AiTaskState.Streaming) {
                                FileDudeCard(containerColor = MaterialTheme.colorScheme.surface) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text("FileDude AI (streaming…)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(4.dp))
                                        Text(uiState.taskState.partialText, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.chatInput,
                            onValueChange = onUpdateChatInput,
                            label = { Text("Ask about this PDF or document...") },
                            modifier = Modifier.weight(1f).testTag("ai_chat_input"),
                            maxLines = 3
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSendChatQuestion(null) }
                                .testTag("ai_chat_send_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send question",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                AiMode.SUMMARIZE -> {
                    OptionalTextPasteField(uiState.customTextInput, onUpdateCustomText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Clear & Concise", "Simple / Plain English", "Detailed Clauses").forEach { style ->
                            FileDudeCategoryChip(
                                label = style,
                                selected = uiState.audienceStyle == style,
                                onClick = { onUpdateAudienceStyle(style) }
                            )
                        }
                    }
                    FileDudePrimaryButton(
                        text = "Summarize & Explain",
                        icon = Icons.Default.AutoAwesome,
                        onClick = onRunSummarize,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "ai_summarize_button"
                    )
                    uiState.summaryResult?.let { res ->
                        FileDudeCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(res.title, style = MaterialTheme.typography.titleMedium)
                                Text(res.executiveSummary, style = MaterialTheme.typography.bodyMedium)
                                HorizontalDivider(color = ext.border)
                                Text("Simple Explanation", style = MaterialTheme.typography.titleSmall, color = ext.darkGreenText)
                                Text(res.simpleExplanation, style = MaterialTheme.typography.bodySmall)
                                if (res.keyPoints.isNotEmpty()) {
                                    Text("Key Points", style = MaterialTheme.typography.titleSmall)
                                    res.keyPoints.forEach { pt -> Text("• $pt", style = MaterialTheme.typography.bodySmall) }
                                }
                                if (res.actionItems.isNotEmpty()) {
                                    Text("Action Items", style = MaterialTheme.typography.titleSmall)
                                    res.actionItems.forEach { act -> Text("✓ $act", style = MaterialTheme.typography.bodySmall, color = ext.darkGreenText) }
                                }
                            }
                        }
                    }
                }

                AiMode.EXTRACT -> {
                    OptionalTextPasteField(uiState.customTextInput, onUpdateCustomText)
                    FileDudePrimaryButton(
                        text = "Extract Key Information",
                        icon = Icons.Default.AutoAwesome,
                        onClick = onRunExtractInfo,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "ai_extract_button"
                    )
                    uiState.extractionResult?.let { extRes ->
                        FileDudeCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(extRes.detectedTitle, style = MaterialTheme.typography.titleMedium)
                                        Text(extRes.documentType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = {
                                            val copyBody = extRes.fields.joinToString("\n") { "${it.label}: ${it.value}" }
                                            clipboard.setText(AnnotatedString(copyBody))
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy extracted fields")
                                    }
                                }
                                if (extRes.summaryNote.isNotBlank()) {
                                    Text(extRes.summaryNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider(color = ext.border)
                                extRes.fields.forEach { field ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(field.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.4f))
                                        Text(field.value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.6f))
                                    }
                                }
                                if (extRes.detectedExpiryIso.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    FileDudeSecondaryButton(
                                        text = "Add Expiry (${extRes.detectedExpiryIso}) to Wallet",
                                        icon = Icons.Default.AddAlert,
                                        onClick = { onSaveExtractedExpiry(extRes) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                AiMode.TRANSLATE -> {
                    OptionalTextPasteField(uiState.customTextInput, onUpdateCustomText)
                    Text("Target Language", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("English", "Spanish", "French", "German", "Hindi", "Portuguese", "Arabic", "Japanese", "Chinese").forEach { lang ->
                            FileDudeCategoryChip(
                                label = lang,
                                selected = uiState.translationLanguage == lang,
                                onClick = { onUpdateTranslationLanguage(lang) }
                            )
                        }
                    }
                    FileDudePrimaryButton(
                        text = "Translate to ${uiState.translationLanguage}",
                        icon = Icons.Default.AutoAwesome,
                        onClick = onRunTranslate,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "ai_translate_button"
                    )
                    if (uiState.translationOutput.isNotBlank()) {
                        FileDudeCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Translated (${uiState.translationLanguage})", style = MaterialTheme.typography.titleSmall)
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.translationOutput)) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy translation")
                                    }
                                }
                                Text(uiState.translationOutput, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                AiMode.COMPARE -> {
                    OutlinedTextField(
                        value = uiState.compareFocusArea,
                        onValueChange = onUpdateCompareFocus,
                        label = { Text("Focus area (optional, e.g. Pricing, Dates, Clauses)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FileDudePrimaryButton(
                        text = "Compare Document A vs B",
                        icon = Icons.Default.AutoAwesome,
                        onClick = onRunCompare,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "ai_compare_button"
                    )
                    if (uiState.compareOutput.isNotBlank()) {
                        FileDudeCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Comparison Report", style = MaterialTheme.typography.titleMedium)
                                Text(uiState.compareOutput, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                AiMode.WORKFLOW -> {
                    OutlinedTextField(
                        value = uiState.workflowGoal,
                        onValueChange = onUpdateWorkflowGoal,
                        label = { Text("What are you preparing this file for? (e.g. Visa, Job portal, Email)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FileDudePrimaryButton(
                        text = "Suggest FileDude Workflow",
                        icon = Icons.Default.AutoAwesome,
                        onClick = onRunWorkflowSuggest,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "ai_workflow_button"
                    )
                    uiState.workflowResult?.let { wfRes ->
                        FileDudeCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("AI File Assessment", style = MaterialTheme.typography.titleMedium)
                                Text(wfRes.assessment, style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider(color = ext.border)
                                wfRes.steps.forEach { step ->
                                    FileDudeCard(containerColor = ext.secondarySurface) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(step.toolKey.label, style = MaterialTheme.typography.titleSmall)
                                                Text(step.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            TextButton(
                                                onClick = { onLaunchSuggestedTool(step.toolKey, uiState.primaryFile) }
                                            ) {
                                                Text("Open")
                                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OptionalTextPasteField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Or paste text directly (optional if file is attached)") },
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
    )
}
