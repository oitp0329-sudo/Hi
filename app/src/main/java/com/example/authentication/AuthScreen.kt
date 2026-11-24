package com.example.authentication

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeConfirmDialog
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeSecondaryButton
import com.example.core.design.FileDudeSuccessBanner
import com.example.core.design.FileDudeTopBar
import com.example.core.model.PlanTier
import com.example.ui.theme.LocalFileDudeColors
import com.google.firebase.auth.FirebaseUser

@Composable
fun AuthAccountScreen(
    currentUser: FirebaseUser?,
    uiState: AuthUiState,
    planTier: PlanTier,
    cloudSyncEnabled: Boolean,
    onGoogleSignIn: (Activity) -> Unit,
    onContinueAsGuest: () -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onToggleCloudSync: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onClearMessages: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val activity = context as? Activity
    val ext = LocalFileDudeColors.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var editedName by remember(currentUser?.displayName) {
        mutableStateOf(currentUser?.displayName.orEmpty())
    }

    if (showDeleteDialog) {
        FileDudeConfirmDialog(
            title = "Delete FileDude Account?",
            message = "This deletes your Firebase Authentication account and all synced cloud records (Document Wallet metadata and custom presets). Files stored locally on your phone are NOT deleted.",
            confirmLabel = "Delete Account",
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                onDeleteAccount()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FileDudeTopBar(
            title = if (currentUser != null) "Your Account" else "Sign In & Cloud Sync",
            subtitle = if (currentUser != null) "Signed in via Google" else "Explore as Guest or sign in",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!uiState.errorMessage.isNullOrBlank()) {
                FileDudeErrorBanner(
                    title = "Account Error",
                    message = uiState.errorMessage,
                    onDismiss = onClearMessages
                )
            }
            if (!uiState.statusMessage.isNullOrBlank()) {
                FileDudeSuccessBanner(
                    title = "Account Updated",
                    subtitle = uiState.statusMessage
                )
            }

            if (currentUser == null) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Google") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Phone OTP") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Email") })
                }

                when (selectedTab) {
                    0 -> {
                        FileDudeCard {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = ext.paleMint,
                                        shape = CircleShape,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Powered by Jetpack Credential Manager",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    "Sign in to back up Document Wallet reminders, sync custom Smart File Ready presets across devices, and restore Play Billing subscriptions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FileDudePrimaryButton(
                                    text = "Sign in with Google",
                                    icon = Icons.Default.VerifiedUser,
                                    loading = uiState.isLoading,
                                    onClick = { if (activity != null) onGoogleSignIn(activity) },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "google_sign_in_button"
                                )
                                FileDudeSecondaryButton(
                                    text = "Continue as Guest (Offline Tools)",
                                    icon = Icons.Default.Person,
                                    onClick = onContinueAsGuest,
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "guest_mode_button"
                                )
                            }
                        }
                    }

                    1 -> {
                        FileDudeCard {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Phone Number OTP Setup Required", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "Phone SMS OTP requires enabling the Phone provider in Firebase Console, adding Play Integrity / SHA-256 fingerprints, and upgrading to Firebase Blaze billing for production SMS delivery. FileDude never simulates OTP verification—please use Google Sign-In or Guest Mode until your Firebase Phone provider is enabled.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FileDudeSecondaryButton(
                                    text = "Use Google Sign-In Instead",
                                    onClick = { selectedTab = 0 },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    2 -> {
                        FileDudeCard {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Email & Password Provider Status", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "Only the Google Sign-In provider is pre-provisioned in this Firebase project. To prevent unverified credential flows or plaintext password handling, Email/Password sign-in and password reset remain inactive until enabled in the Firebase Authentication Console. See README.md for step-by-step setup.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FileDudeSecondaryButton(
                                    text = "Use Google Sign-In Instead",
                                    onClick = { selectedTab = 0 },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            } else {
                FileDudeCard {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = ext.paleMint,
                                shape = CircleShape,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUser.displayName?.ifBlank { "FileDude Member" } ?: "FileDude Member",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = currentUser.email ?: currentUser.uid,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(color = ext.paleMint, shape = MaterialTheme.shapes.small) {
                                Text(
                                    text = planTier.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.darkGreenText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FileDudeSecondaryButton(
                            text = "Save Display Name",
                            icon = Icons.Default.Edit,
                            enabled = !uiState.isLoading && editedName.isNotBlank(),
                            onClick = { onUpdateDisplayName(editedName) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                FileDudeCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Cloud Metadata Sync", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Sync wallet expiry dates & custom presets (local files never upload automatically).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = cloudSyncEnabled,
                            onCheckedChange = onToggleCloudSync
                        )
                    }
                }

                FileDudeSecondaryButton(
                    text = "Manage Subscription & Plan",
                    icon = Icons.Default.WorkspacePremium,
                    onClick = onOpenSubscription,
                    modifier = Modifier.fillMaxWidth()
                )

                FileDudeSecondaryButton(
                    text = "Sign Out",
                    icon = Icons.Default.Logout,
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "sign_out_button"
                )

                FileDudeCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Danger Zone — Account Deletion",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Permanently delete your FileDude cloud account and Firestore metadata in accordance with Google Play account deletion policies.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        FileDudeSecondaryButton(
                            text = "Delete Account & Cloud Data",
                            icon = Icons.Default.DeleteForever,
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "delete_account_button"
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FileDudeSecondaryButton(
                    text = "Privacy Policy",
                    onClick = onOpenPrivacyPolicy,
                    modifier = Modifier.weight(1f)
                )
                FileDudeSecondaryButton(
                    text = "Terms of Use",
                    onClick = onOpenTerms,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
