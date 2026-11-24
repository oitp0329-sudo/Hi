package com.example.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.data.files.WorkspaceFileRepository
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeCategoryChip
import com.example.core.design.FileDudeConfirmDialog
import com.example.core.design.FileDudeEmptyState
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeSearchBar
import com.example.core.design.FileDudeSecondaryButton
import com.example.core.design.FileDudeStatusBadge
import com.example.core.design.FileDudeSuccessBanner
import com.example.core.design.FileDudeTopBar
import com.example.core.design.formatDateShort
import com.example.core.model.PlanTier
import com.example.core.model.WalletCategory
import com.example.core.model.WalletDocument
import com.example.subscription.AdPlacementLocation
import com.example.subscription.FileDudeAdContainer
import com.example.ui.theme.LocalFileDudeColors
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentWalletScreen(
    uiState: DocumentWalletUiState,
    viewModel: DocumentWalletViewModel,
    fileRepository: WorkspaceFileRepository,
    isSignedIn: Boolean,
    planTier: PlanTier = PlanTier.FREE
) {
    val context = LocalContext.current
    val ext = LocalFileDudeColors.current
    val scope = rememberCoroutineScope()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    var editingDoc by remember { mutableStateOf<WalletDocument?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var renewingDoc by remember { mutableStateOf<WalletDocument?>(null) }
    var deletingDoc by remember { mutableStateOf<WalletDocument?>(null) }

    if (renewingDoc != null) {
        val target = renewingDoc!!
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis() + 365L * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { renewingDoc = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val chosen = datePickerState.selectedDateMillis ?: (System.currentTimeMillis() + 365L * 86_400_000L)
                        viewModel.markRenewed(target, chosen, isSignedIn)
                        renewingDoc = null
                    }
                ) { Text("Mark Renewed") }
            },
            dismissButton = {
                TextButton(onClick = { renewingDoc = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (deletingDoc != null) {
        val target = deletingDoc!!
        FileDudeConfirmDialog(
            title = "Delete Document?",
            message = "Remove \"${target.title}\" and its expiry reminders from your wallet?",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteDocument(target, isSignedIn)
                deletingDoc = null
            },
            onDismiss = { deletingDoc = null }
        )
    }

    if (showAddSheet) {
        WalletEditorSheet(
            initial = editingDoc,
            onPickAttachment = { uri -> viewModel.importAttachment(uri) },
            onSave = { doc ->
                viewModel.saveDocument(doc, isSignedIn)
                showAddSheet = false
                editingDoc = null
            },
            onDismiss = {
                showAddSheet = false
                editingDoc = null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingDoc = null
                    showAddSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("wallet_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add document to wallet")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                FileDudeTopBar(
                    title = "Document Wallet & Alerts",
                    subtitle = "${uiState.documents.size} document(s) • Offline vault",
                    actions = {
                        IconButton(onClick = viewModel::toggleCalendarView) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Toggle expiry calendar grouping",
                                tint = if (uiState.calendarGroupedView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSignedIn) {
                            IconButton(onClick = { viewModel.syncCloudWalletNow(true) }) {
                                Icon(Icons.Default.CloudSync, contentDescription = "Sync wallet metadata")
                            }
                        }
                    }
                )
            }

            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    FileDudeCard(
                        modifier = Modifier.widthIn(max = 840.dp),
                        containerColor = ext.warningContainer,
                        borderColor = ext.warning.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = ext.warning)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enable Expiry Alerts", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Allow notifications so FileDude can remind you 90/30/7/1 days before documents expire.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(
                                onClick = { notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            ) {
                                Text("Allow", color = ext.darkGreenText)
                            }
                        }
                    }
                }
            }

            if (!uiState.statusMessage.isNullOrBlank()) {
                item {
                    FileDudeSuccessBanner(
                        title = "Wallet Updated",
                        subtitle = uiState.statusMessage,
                        modifier = Modifier.widthIn(max = 840.dp)
                    )
                }
            }
            if (!uiState.errorMessage.isNullOrBlank()) {
                item {
                    FileDudeErrorBanner(
                        title = "Sync Notice",
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
                    placeholder = "Search passports, licences, warranties, notes...",
                    modifier = Modifier.widthIn(max = 840.dp),
                    testTag = "wallet_search_bar"
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileDudeCategoryChip(
                        label = "All",
                        selected = uiState.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) }
                    )
                    WalletCategory.entries.forEach { cat ->
                        FileDudeCategoryChip(
                            label = cat.label,
                            selected = uiState.selectedCategory == cat,
                            onClick = { viewModel.selectCategory(cat) }
                        )
                    }
                }
            }

            if (uiState.documents.isEmpty()) {
                item {
                    FileDudeEmptyState(
                        title = "Your Document Wallet is Ready",
                        description = "Track passports, driving licences, insurance, warranties, and renewals with local attachments and WorkManager reminders.",
                        illustrationResId = R.drawable.img_empty_wallet,
                        actionLabel = "Add First Document",
                        onAction = {
                            editingDoc = null
                            showAddSheet = true
                        },
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            } else if (uiState.calendarGroupedView) {
                val now = System.currentTimeMillis()
                val groups = uiState.documents.groupBy { doc ->
                    val days = ceil((doc.expiryDateMillis - now) / 86_400_000.0).toInt()
                    when {
                        days < 0 -> "Expired"
                        days <= 7 -> "Due in 7 Days"
                        days <= 30 -> "Due in 30 Days"
                        days <= 90 -> "Due in 90 Days"
                        else -> "Later / Safe"
                    }
                }
                groups.forEach { (bucket, docs) ->
                    item {
                        Text(
                            text = "$bucket (${docs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .widthIn(max = 840.dp)
                                .fillMaxWidth()
                        )
                    }
                    items(docs, key = { it.id }) { doc ->
                        WalletDocumentCard(
                            doc = doc,
                            onEdit = { editingDoc = doc; showAddSheet = true },
                            onRenew = { renewingDoc = doc },
                            onDelete = { deletingDoc = doc },
                            onShareAttachment = { path ->
                                if (File(path).exists()) {
                                    context.startActivity(
                                        fileRepository.createShareIntent(path, "*/*", "Share Attachment")
                                    )
                                }
                            },
                            modifier = Modifier.widthIn(max = 840.dp)
                        )
                    }
                }
            } else {
                items(uiState.documents, key = { it.id }) { doc ->
                    WalletDocumentCard(
                        doc = doc,
                        onEdit = { editingDoc = doc; showAddSheet = true },
                        onRenew = { renewingDoc = doc },
                        onDelete = { deletingDoc = doc },
                        onShareAttachment = { path ->
                            if (File(path).exists()) {
                                context.startActivity(
                                    fileRepository.createShareIntent(path, "*/*", "Share Attachment")
                                )
                            }
                        },
                        modifier = Modifier.widthIn(max = 840.dp)
                    )
                }
            }

            item { FileDudeAdContainer(location = AdPlacementLocation.REMINDERS_FOOTER, planTier = planTier) }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun WalletDocumentCard(
    doc: WalletDocument,
    onEdit: () -> Unit,
    onRenew: () -> Unit,
    onDelete: () -> Unit,
    onShareAttachment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalFileDudeColors.current
    val daysLeft = ceil((doc.expiryDateMillis - System.currentTimeMillis()) / 86_400_000.0).toInt()
    val badgeColor = when {
        daysLeft < 0 -> MaterialTheme.colorScheme.errorContainer
        daysLeft <= 7 -> ext.warningContainer
        else -> ext.paleMint
    }
    val badgeTextColor = when {
        daysLeft < 0 -> MaterialTheme.colorScheme.error
        daysLeft <= 7 -> ext.warning
        else -> ext.darkGreenText
    }
    val badgeLabel = when {
        daysLeft < 0 -> "EXPIRED"
        daysLeft == 0 -> "TODAY"
        else -> "${daysLeft}d left"
    }

    FileDudeCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(doc.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${doc.category.label} • Expires ${formatDateShort(doc.expiryDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FileDudeStatusBadge(text = badgeLabel, containerColor = badgeColor, contentColor = badgeTextColor)
            }

            if (doc.notes.isNotBlank()) {
                Text(doc.notes, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "Reminders: ${doc.reminderOffsetsDays.sortedDescending().joinToString(", ") { "${it}d" }} before",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (doc.renewalHistory.isNotEmpty()) {
                Text(
                    text = "History: ${doc.renewalHistory.joinToString(" • ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.darkGreenText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileDudeSecondaryButton(
                    text = "Renewed",
                    icon = Icons.Default.CheckCircle,
                    onClick = onRenew,
                    modifier = Modifier.weight(1f)
                )
                if (!doc.attachmentPath.isNullOrBlank()) {
                    IconButton(onClick = { onShareAttachment(doc.attachmentPath) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share attachment")
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit document")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete document")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletEditorSheet(
    initial: WalletDocument?,
    onPickAttachment: suspend (android.net.Uri) -> Result<Pair<String, String>>,
    onSave: (WalletDocument) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category ?: WalletCategory.PASSPORT) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var expiryMillis by remember {
        mutableStateOf(initial?.expiryDateMillis ?: (System.currentTimeMillis() + 90L * 86_400_000L))
    }
    var attachmentPath by remember { mutableStateOf(initial?.attachmentPath) }
    var attachmentName by remember { mutableStateOf(initial?.attachmentName) }
    var offsetsCsv by remember {
        mutableStateOf((initial?.reminderOffsetsDays ?: listOf(90, 30, 7, 1)).joinToString(", "))
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                onPickAttachment(uri).onSuccess { (path, name) ->
                    attachmentPath = path
                    attachmentName = name
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = expiryMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { expiryMillis = it }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initial == null) "Add Document to Wallet" else "Edit Document",
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Document Title (e.g. Passport, Car Insurance)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Category", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WalletCategory.entries.forEach { cat ->
                    FileDudeCategoryChip(
                        label = cat.label,
                        selected = category == cat,
                        onClick = { category = cat }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Expiry Date", style = MaterialTheme.typography.labelLarge)
                    Text(formatDateShort(expiryMillis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                FileDudeSecondaryButton(
                    text = "Pick Date",
                    icon = Icons.Default.CalendarMonth,
                    onClick = { showDatePicker = true }
                )
            }
            OutlinedTextField(
                value = offsetsCsv,
                onValueChange = { offsetsCsv = it },
                label = { Text("Reminder days before expiry (e.g. 90, 30, 7, 1)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = attachmentName ?: "No local file attached (optional)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { docPicker.launch(arrayOf("application/pdf", "image/*")) }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Attach File")
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Policy or Reference #") },
                modifier = Modifier.fillMaxWidth()
            )
            FileDudePrimaryButton(
                text = "Save Document",
                enabled = title.trim().isNotEmpty(),
                onClick = {
                    val offsets = offsetsCsv.split(",")
                        .mapNotNull { it.trim().toIntOrNull()?.coerceIn(0, 365) }
                        .distinct()
                        .ifEmpty { listOf(90, 30, 7, 1) }
                    val doc = (initial ?: WalletDocument(
                        title = title.trim(),
                        category = category,
                        expiryDateMillis = expiryMillis
                    )).copy(
                        title = title.trim(),
                        category = category,
                        notes = notes.trim(),
                        expiryDateMillis = expiryMillis,
                        attachmentPath = attachmentPath,
                        attachmentName = attachmentName,
                        reminderOffsetsDays = offsets
                    )
                    onSave(doc)
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "wallet_save_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
