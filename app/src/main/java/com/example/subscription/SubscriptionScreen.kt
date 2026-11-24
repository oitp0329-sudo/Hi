package com.example.subscription

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeSecondaryButton
import com.example.core.design.FileDudeStatusBadge
import com.example.core.design.FileDudeSuccessBanner
import com.example.core.design.FileDudeTopBar
import com.example.core.model.PlanTier
import com.example.ui.theme.LocalFileDudeColors

@Composable
fun SubscriptionScreen(
    billingState: BillingState,
    onLaunchPurchase: (Activity, String) -> Unit,
    onRestorePurchases: () -> Unit,
    onClearMessages: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val activity = context as? Activity
    val ext = LocalFileDudeColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FileDudeTopBar(
            title = "FileDude Plans",
            subtitle = "Google Play Billing verification",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Current Entitlement Card
            FileDudeCard(
                containerColor = if (billingState.planTier == PlanTier.PREMIUM) ext.paleMint else MaterialTheme.colorScheme.surface,
                borderColor = if (billingState.planTier == PlanTier.PREMIUM) MaterialTheme.colorScheme.primary else ext.border
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (billingState.planTier == PlanTier.PREMIUM) Icons.Default.Verified else Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (billingState.planTier == PlanTier.PREMIUM) "Premium Active" else "Free Plan Active",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        FileDudeStatusBadge(
                            text = billingState.planTier.name,
                            containerColor = ext.paleMint,
                            contentColor = ext.darkGreenText
                        )
                    }
                    Text(
                        text = billingState.activePurchasesSummary
                            ?: "All core offline PDF, Image, Video, Smart File Ready, and Wallet tools work on Free Plan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (billingState.purchaseHistoryItems.isNotEmpty()) {
                        HorizontalDivider(color = ext.border)
                        billingState.purchaseHistoryItems.forEach { item ->
                            Text("• $item", style = MaterialTheme.typography.labelMedium, color = ext.darkGreenText)
                        }
                    }
                }
            }

            if (!billingState.statusMessage.isNullOrBlank()) {
                FileDudeSuccessBanner(title = "Play Billing Status", subtitle = billingState.statusMessage)
            }
            if (!billingState.errorMessage.isNullOrBlank()) {
                FileDudeErrorBanner(
                    title = "Play Console Notice",
                    message = billingState.errorMessage,
                    isWarning = true,
                    onDismiss = onClearMessages
                )
            }

            // Free vs Premium Comparison
            FileDudeCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What You Get", style = MaterialTheme.typography.titleMedium)
                    ComparisonRow("On-device PDF, Image & Video Studios", "Included (Free & Premium)")
                    ComparisonRow("Smart File Ready Verification", "Included (Free & Premium)")
                    ComparisonRow("FileDude AI Assistant (Chat with PDF, Summarize, Extract, Translate)", "5 requests/day Free • 200/day Premium")
                    ComparisonRow("Private Document Wallet & 90/30/7/1 Alerts", "Included Offline")
                    ComparisonRow("Priority Batch Presets & Optional Cloud Sync", "Premium")
                    ComparisonRow("Ad Experience", if (AdConfig.IS_ADMOB_CONFIGURED) "Free shows consented banner; Premium zero ads" else "Zero ads (AdMob disabled until Play IDs configured)")
                }
            }

            // Live SKUs from Play Billing
            Text("Available Google Play Plans", style = MaterialTheme.typography.titleMedium)
            billingState.products.forEach { product ->
                FileDudeCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "SKU: ${product.productId} • ${product.billingPeriodLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FileDudeStatusBadge(
                                text = product.formattedPrice ?: "Play Console Price Pending",
                                containerColor = if (product.isConfiguredInPlay) ext.paleMint else ext.secondarySurface,
                                contentColor = ext.darkGreenText
                            )
                        }
                        Text(product.description, style = MaterialTheme.typography.bodySmall)
                        FileDudePrimaryButton(
                            text = if (product.isConfiguredInPlay && product.formattedPrice != null) {
                                "Subscribe (${product.formattedPrice})"
                            } else {
                                "Continue with Google Play"
                            },
                            enabled = activity != null && !billingState.isConnecting,
                            onClick = {
                                if (activity != null) {
                                    onLaunchPurchase(activity, product.productId)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "subscribe_${product.productId}_button"
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FileDudeSecondaryButton(
                    text = "Restore Purchases",
                    icon = Icons.Default.Refresh,
                    onClick = onRestorePurchases,
                    modifier = Modifier.weight(1f),
                    testTag = "restore_purchases_button"
                )
                FileDudeSecondaryButton(
                    text = "Manage in Play",
                    icon = Icons.Default.OpenInNew,
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/account/subscriptions")
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Subscriptions auto-renew via Google Play unless cancelled at least 24 hours before the end of the billing period. Prices are fetched dynamically in your local currency from Google Play Console.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ComparisonRow(feature: String, availability: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(feature, style = MaterialTheme.typography.bodyMedium)
            Text(availability, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
