package com.example.features.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeCategoryChip
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeSecondaryButton
import com.example.core.design.FileDudeStatusBadge
import com.example.core.design.FileDudeSuccessBanner
import com.example.core.design.FileDudeTopBar
import com.example.core.design.formatFileSize
import com.example.core.model.AppThemeMode
import com.example.core.model.ImageOutputFormat
import com.example.core.model.PdfPageSizeOption
import com.example.core.model.PlanTier
import com.example.core.model.VideoExportPreset
import com.example.subscription.AdConfig
import com.example.ui.theme.LocalFileDudeColors
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    userEmail: String?,
    planTier: PlanTier,
    onOpenAccount: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenLegal: (String) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalFileDudeColors.current
    val isSignedIn = !userEmail.isNullOrBlank()
    val prefs = uiState.preferences

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showComplianceDialog by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }

    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true

    if (showFeedbackDialog) {
        var category by remember { mutableStateOf("FEATURE") }
        var subject by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Send Feedback") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("FEATURE", "BUG", "BILLING", "OTHER").forEach { cat ->
                            FileDudeCategoryChip(
                                label = cat,
                                selected = category == cat,
                                onClick = { category = cat }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Details") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendFeedback(category, subject, message, isSignedIn)
                        showFeedbackDialog = false
                    }
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showComplianceDialog) {
        AlertDialog(
            onDismissRequest = { showComplianceDialog = false },
            title = { Text("Play Store Compliance Summary") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("• Permissions: Zero broad storage permissions requested. Uses Android Photo Picker & SAF.", style = MaterialTheme.typography.bodySmall)
                    Text("• Notifications: POST_NOTIFICATIONS requested contextually in Reminders tab.", style = MaterialTheme.typography.bodySmall)
                    Text("• Offline-First Privacy: PDF, Image, OCR & Video tools run locally on device.", style = MaterialTheme.typography.bodySmall)
                    Text("• Cloud Sync: Optional metadata sync via Firestore user-owned rules; private documents stay local.", style = MaterialTheme.typography.bodySmall)
                    Text("• Billing: Real Google Play Billing v9 client; no fake unlocks.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "• Ads: ${if (AdConfig.IS_ADMOB_CONFIGURED) "Configured with UMP consent" else "Disabled cleanly until verified AdMob IDs & UMP consent are configured"}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showComplianceDialog = false }) { Text("Close") }
            }
        )
    }

    if (showFaqDialog) {
        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = { Text("FileDude FAQs") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("1. Are my original files modified?", style = MaterialTheme.typography.titleSmall)
                    Text("No. Every FileDude tool exports a new copy and leaves originals untouched.", style = MaterialTheme.typography.bodySmall)
                    Text("2. Do PDF, Image & Video tools work offline?", style = MaterialTheme.typography.titleSmall)
                    Text("Yes. PDFBox, Media3 Transformer, Bitmap processing, and ML Kit OCR run on your device.", style = MaterialTheme.typography.bodySmall)
                    Text("3. How do Smart File Ready checks work?", style = MaterialTheme.typography.titleSmall)
                    Text("After generating the file, FileDude reads the real saved bytes on disk to verify size, pixel dimensions, aspect ratio, and format.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showFaqDialog = false }) { Text("Got It") }
            }
        )
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
                title = "Settings",
                subtitle = "Account, defaults, storage & Play compliance"
            )
        }

        if (!uiState.statusMessage.isNullOrBlank()) {
            item { FileDudeSuccessBanner(title = "Updated", subtitle = uiState.statusMessage, modifier = Modifier.widthIn(max = 840.dp)) }
        }
        if (!uiState.errorMessage.isNullOrBlank()) {
            item {
                FileDudeErrorBanner(
                    title = "Notice",
                    message = uiState.errorMessage,
                    isWarning = true,
                    onDismiss = viewModel::clearMessages,
                    modifier = Modifier.widthIn(max = 840.dp)
                )
            }
        }

        // Account & Subscription
        item {
            FileDudeCard(modifier = Modifier.widthIn(max = 840.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsNavRow(
                        title = if (isSignedIn) "Account ($userEmail)" else "Account & Sign-In (Guest Mode)",
                        subtitle = if (isSignedIn) "Manage profile, sign out or delete account" else "Sign in with Google for optional wallet sync",
                        icon = Icons.Default.Person,
                        onClick = onOpenAccount,
                        testTag = "settings_account_row"
                    )
                    HorizontalDivider(color = ext.border)
                    SettingsNavRow(
                        title = "FileDude Plan (${planTier.name})",
                        subtitle = "Manage Google Play subscription & restore purchases",
                        icon = Icons.Default.Star,
                        onClick = onOpenSubscription,
                        testTag = "settings_subscription_row"
                    )
                }
            }
        }

        // Appearance & Privacy Controls
        item {
            FileDudeCard(modifier = Modifier.widthIn(max = 840.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance & Privacy", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            FileDudeCategoryChip(
                                label = mode.label,
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Expiry Reminder Notifications", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (notifGranted) "System permission granted" else "Permission not granted yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.expiryNotificationsEnabled,
                            onCheckedChange = viewModel::setExpiryNotifications
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Optional Cloud Sync (Wallet & Presets)", style = MaterialTheme.typography.bodyMedium)
                            Text("Files never upload automatically; metadata sync only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = prefs.cloudSyncEnabled,
                            onCheckedChange = { enabled -> viewModel.setCloudSync(enabled, isSignedIn) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Usage Analytics Consent", style = MaterialTheme.typography.bodyMedium)
                            Text("Disabled by default (privacy-first)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = prefs.analyticsConsentGranted,
                            onCheckedChange = viewModel::setAnalyticsConsent
                        )
                    }
                }
            }
        }

        // Tool Processing Defaults
        item {
            FileDudeCard(modifier = Modifier.widthIn(max = 840.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Default Studio Settings", style = MaterialTheme.typography.titleMedium)
                    Text("Default Image Output (${prefs.defaultImageFormat.label}, ${prefs.defaultImageQuality}%)", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageOutputFormat.entries.forEach { fmt ->
                            FileDudeCategoryChip(
                                label = fmt.label,
                                selected = prefs.defaultImageFormat == fmt,
                                onClick = { viewModel.setDefaultImage(fmt, prefs.defaultImageQuality) }
                            )
                        }
                    }
                    Slider(
                        value = prefs.defaultImageQuality.toFloat(),
                        onValueChange = { q -> viewModel.setDefaultImage(prefs.defaultImageFormat, q.roundToInt()) },
                        valueRange = 40f..100f
                    )

                    Text("Default PDF Page Size", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfPageSizeOption.entries.forEach { ps ->
                            FileDudeCategoryChip(
                                label = ps.name,
                                selected = prefs.defaultPdfPageSize == ps,
                                onClick = { viewModel.setDefaultPdfPageSize(ps) }
                            )
                        }
                    }

                    Text("Default Video Preset", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoExportPreset.entries.forEach { vp ->
                            FileDudeCategoryChip(
                                label = vp.label,
                                selected = prefs.defaultVideoPreset == vp,
                                onClick = { viewModel.setDefaultVideoPreset(vp) }
                            )
                        }
                    }
                }
            }
        }

        // Storage & Cache
        item {
            FileDudeCard(modifier = Modifier.widthIn(max = 840.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Storage & Cache", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Workspace: ${uiState.workspaceFileCount} files (${formatFileSize(uiState.totalWorkspaceBytes)}) • Temp Cache: ${formatFileSize(uiState.cacheBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FileDudeSecondaryButton(
                        text = "Clear Temporary Cache",
                        icon = Icons.Default.CleaningServices,
                        onClick = viewModel::clearTemporaryCache,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "clear_cache_button"
                    )
                }
            }
        }

        // Legal, Licences, Audit & Support
        item {
            FileDudeCard(modifier = Modifier.widthIn(max = 840.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsNavRow(
                        title = "Play Store Compliance & Permissions Audit",
                        subtitle = "Zero broad storage permissions • Photo Picker & SAF",
                        icon = Icons.Default.VerifiedUser,
                        onClick = { showComplianceDialog = true }
                    )
                    HorizontalDivider(color = ext.border)
                    SettingsNavRow(
                        title = "Privacy Policy",
                        subtitle = "How FileDude protects your local files & account data",
                        icon = Icons.Default.PrivacyTip,
                        onClick = { onOpenLegal("PRIVACY") }
                    )
                    HorizontalDivider(color = ext.border)
                    SettingsNavRow(
                        title = "Terms & Conditions",
                        subtitle = "Acceptable use, billing & user responsibilities",
                        icon = Icons.Default.Gavel,
                        onClick = { onOpenLegal("TERMS") }
                    )
                    HorizontalDivider(color = ext.border)
                    SettingsNavRow(
                        title = "Open-Source Licences",
                        subtitle = "PDFBox-Android, Media3, ML Kit, Coil, Room & Jetpack",
                        icon = Icons.Default.Security,
                        onClick = { onOpenLegal("LICENCES") }
                    )
                    HorizontalDivider(color = ext.border)
                    SettingsNavRow(
                        title = "FAQs & Help",
                        subtitle = "Answers about Smart File Ready, OCR & edits",
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        onClick = { showFaqDialog = true }
                    )
                    HorizontalDivider(color = ext.border)
                    SettingsNavRow(
                        title = "Send Feedback",
                        subtitle = "Report an issue or suggest a new portal preset",
                        icon = Icons.Default.Feedback,
                        onClick = { showFeedbackDialog = true }
                    )
                }
            }
        }

        item {
            Text(
                text = "FileDude v1.0.0 • Your Files. Ready When You Are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag.isNotEmpty()) it.testTag(testTag) else it }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LegalDocumentScreen(
    docType: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val (title, body) = when (docType.uppercase()) {
        "TERMS" -> "Terms & Conditions" to """
            FileDude — Terms & Conditions

            1. Local-First Processing
            FileDude processes PDFs, images, videos, and OCR text locally on your device. You are solely responsible for ensuring you hold rights or authorization to edit, unlock, watermark, or convert any document you import.

            2. Original Files & Backups
            FileDude creates processed copies in app storage rather than overwriting your original files directly. You can export saved files to device folders using the Android Storage Access Framework at any time.

            3. Smart File Ready Presets
            Smart File Ready helps transform and verify files against target format, file size, and pixel dimension rules. Always verify final portal acceptance directly on the destination website.

            4. Subscriptions & Google Play Billing
            Premium subscriptions and one-time Pro purchases are processed exclusively through Google Play Billing. Recurring subscriptions renew automatically unless cancelled through your Google Play account settings at least 24 hours before renewal.

            5. Account & Cloud Sync
            Signing in with Google enables optional synchronization of Document Wallet expiry metadata and custom Smart File Ready presets via Firebase Firestore. Local workspace files are not automatically uploaded.
        """.trimIndent()
        "LICENCES" -> "Open-Source Licences" to """
            FileDude uses the following open-source libraries and SDKs:

            • Apache PDFBox-Android (com.tom-roush:pdfbox-android) — Apache License 2.0
            • AndroidX Media3 Transformer & ExoPlayer (androidx.media3) — Apache License 2.0
            • Jetpack Compose, Material 3, Room, DataStore, WorkManager, Navigation & ExifInterface — Apache License 2.0
            • Coil Image Loading (io.coil-kt:coil-compose) — Apache License 2.0
            • Kotlin Coroutines & Kotlinx Serialization — Apache License 2.0
            • Google ML Kit Text Recognition — Google APIs Terms of Service
            • Google Play Billing Library & Firebase Android SDK — Google APIs / Apache 2.0
        """.trimIndent()
        else -> "Privacy Policy" to """
            FileDude — Privacy Policy

            1. Privacy-First, Offline Architecture
            FileDude is designed so your PDFs, photos, videos, and scanned documents stay on your device. PDF manipulation, image compression, video trimming, and ML Kit text recognition run on-device.

            2. Minimal Android Permissions
            FileDude does NOT request broad storage permissions (READ_EXTERNAL_STORAGE, READ_MEDIA_IMAGES, or READ_MEDIA_VIDEO). Files are opened strictly through the Android Photo Picker and Storage Access Framework only when you choose them. POST_NOTIFICATIONS is requested contextually only if you want Document Wallet expiry alerts.

            3. Optional Google Sign-In & Cloud Sync
            You may use FileDude completely offline in Guest Mode. If you sign in with Google, FileDude stores only your profile display name, email, Document Wallet expiry metadata (titles, dates, notes), custom Smart Presets, and feedback inside your user-scoped Firestore collection (users/{uid}). Attachments and edited media files remain local unless you explicitly share them.

            4. Data Deletion
            You can delete individual wallet items or delete your entire cloud account and Firestore records directly from Settings -> Account -> Delete Account. Local workspace files on your device can be deleted anytime inside My Files.
        """.trimIndent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FileDudeTopBar(title = title, onBack = onBack)
        FileDudeCard(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
