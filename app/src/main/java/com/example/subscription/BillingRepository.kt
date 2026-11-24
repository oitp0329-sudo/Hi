package com.example.subscription

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.core.model.PlanTier
import com.example.core.model.SubscriptionProductUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillingState(
    val planTier: PlanTier = PlanTier.FREE,
    val isConnecting: Boolean = false,
    val billingAvailable: Boolean = false,
    val products: List<SubscriptionProductUi> = emptyList(),
    val activePurchasesSummary: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val purchaseHistoryItems: List<String> = emptyList(),
    val isGracePeriodActive: Boolean = false
)

object AdConfig {
    /**
     * Advertising is intentionally disabled by default until verified AdMob App/Unit IDs
     * and Google UMP consent are configured in AndroidManifest.xml and Play Console.
     * Premium users always receive a zero-ad experience.
     */
    const val IS_ADMOB_CONFIGURED = false
}

class BillingRepository(context: Context) : PurchasesUpdatedListener {

    companion object {
        const val SKU_MONTHLY = "filedude_premium_monthly"
        const val SKU_ANNUAL = "filedude_premium_annual"
        const val SKU_LIFETIME = "filedude_premium_lifetime"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val _state = MutableStateFlow(
        BillingState(
            products = defaultUnconfiguredCatalog()
        )
    )
    val state: StateFlow<BillingState> = _state.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    fun connectAndRefresh() {
        if (billingClient.isReady) {
            queryProductsAndEntitlements()
            return
        }
        _state.update { it.copy(isConnecting = true, errorMessage = null) }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update { it.copy(isConnecting = false, billingAvailable = true) }
                    queryProductsAndEntitlements()
                } else {
                    Log.w("Billing", "Billing unavailable: ${result.debugMessage}")
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            billingAvailable = false,
                            products = defaultUnconfiguredCatalog(),
                            statusMessage = "Google Play Billing unavailable on this device/build (${result.debugMessage.ifBlank { "Play Store required" }})."
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update { it.copy(isConnecting = false, billingAvailable = false) }
            }
        })
    }

    private fun queryProductsAndEntitlements() {
        val subsList = listOf(SKU_MONTHLY, SKU_ANNUAL).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val inAppList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val resolvedProducts = mutableListOf<SubscriptionProductUi>()

        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(subsList).build()
        ) { subsResult, subsResponse ->
            if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                subsResponse.productDetailsList.forEach { pd ->
                    productDetailsMap[pd.productId] = pd
                    val offer = pd.subscriptionOfferDetails?.firstOrNull()
                    val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    resolvedProducts.add(
                        SubscriptionProductUi(
                            productId = pd.productId,
                            title = pd.title,
                            description = pd.description,
                            formattedPrice = price,
                            billingPeriodLabel = if (pd.productId == SKU_ANNUAL) "Annual" else "Monthly",
                            isConfiguredInPlay = true
                        )
                    )
                }
            }

            billingClient.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(inAppList).build()
            ) { inAppResult, inAppResponse ->
                if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    inAppResponse.productDetailsList.forEach { pd ->
                        productDetailsMap[pd.productId] = pd
                        resolvedProducts.add(
                            SubscriptionProductUi(
                                productId = pd.productId,
                                title = pd.title,
                                description = pd.description,
                                formattedPrice = pd.oneTimePurchaseOfferDetails?.formattedPrice,
                                billingPeriodLabel = "One-time Lifetime",
                                isConfiguredInPlay = true
                            )
                        )
                    }
                }

                val merged = mergeWithFallbackCatalog(resolvedProducts)
                _state.update { it.copy(products = merged) }
                refreshPurchases()
            }
        }
    }

    fun refreshPurchases() {
        if (!billingClient.isReady) {
            connectAndRefresh()
            return
        }
        val activeEntitlements = mutableListOf<Purchase>()
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { subsResult, subsPurchases ->
            if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                activeEntitlements.addAll(subsPurchases)
            }
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { inAppResult, inAppPurchases ->
                if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    activeEntitlements.addAll(inAppPurchases)
                }
                handleVerifiedPurchases(activeEntitlements)
            }
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val details = productDetailsMap[productId]
        if (!billingClient.isReady || details == null) {
            _state.update {
                it.copy(
                    errorMessage = "Product '$productId' is not configured in Google Play Console yet. Never unlock Premium without a verified Play Billing purchase."
                )
            }
            return
        }

        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                _state.update { it.copy(errorMessage = "No active subscription offer found in Play Console.") }
                return
            }
            paramsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update { it.copy(errorMessage = "Could not launch Play Billing: ${result.debugMessage}") }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    handleVerifiedPurchases(purchases)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update { it.copy(statusMessage = "Purchase cancelled.") }
            }
            else -> {
                _state.update { it.copy(errorMessage = "Billing error (${result.responseCode}): ${result.debugMessage}") }
            }
        }
    }

    private fun handleVerifiedPurchases(purchases: List<Purchase>) {
        var hasPurchasedPremium = false
        var hasPending = false
        val history = mutableListOf<String>()

        purchases.forEach { purchase ->
            val productsLabel = purchase.products.joinToString(", ")
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (purchase.products.any { it in listOf(SKU_MONTHLY, SKU_ANNUAL, SKU_LIFETIME) }) {
                        hasPurchasedPremium = true
                        history.add("$productsLabel • Active (${if (purchase.isAutoRenewing) "Auto-renewing" else "Non-renewing"})")
                        if (!purchase.isAcknowledged) {
                            scope.launch {
                                val ackParams = AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.purchaseToken)
                                    .build()
                                billingClient.acknowledgePurchase(ackParams) { ackRes ->
                                    if (ackRes.responseCode != BillingClient.BillingResponseCode.OK) {
                                        Log.w("Billing", "Ack failed: ${ackRes.debugMessage}")
                                    }
                                }
                            }
                        }
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    hasPending = true
                    history.add("$productsLabel • Pending payment confirmation")
                }
                else -> Unit
            }
        }

        _state.update {
            it.copy(
                planTier = if (hasPurchasedPremium) PlanTier.PREMIUM else PlanTier.FREE,
                activePurchasesSummary = when {
                    hasPurchasedPremium -> "Premium entitlement verified via Google Play"
                    hasPending -> "Payment pending — Premium activates once Play confirms"
                    else -> "Free Plan active"
                },
                purchaseHistoryItems = history,
                errorMessage = null
            )
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    private fun defaultUnconfiguredCatalog(): List<SubscriptionProductUi> = listOf(
        SubscriptionProductUi(
            productId = SKU_MONTHLY,
            title = "FileDude Premium Monthly",
            description = "Full PDF editing, video export presets, batch tools, cloud wallet sync & zero ads.",
            formattedPrice = null,
            billingPeriodLabel = "Monthly",
            isConfiguredInPlay = false
        ),
        SubscriptionProductUi(
            productId = SKU_ANNUAL,
            title = "FileDude Premium Annual",
            description = "Best value annual plan with full studio access, cloud sync & zero ads.",
            formattedPrice = null,
            billingPeriodLabel = "Annual",
            isConfiguredInPlay = false
        ),
        SubscriptionProductUi(
            productId = SKU_LIFETIME,
            title = "FileDude Lifetime Pro",
            description = "One-time purchase unlocking all on-device pro tools (cloud sync subject to Play terms).",
            formattedPrice = null,
            billingPeriodLabel = "One-time Lifetime",
            isConfiguredInPlay = false
        )
    )

    private fun mergeWithFallbackCatalog(resolved: List<SubscriptionProductUi>): List<SubscriptionProductUi> {
        val byId = resolved.associateBy { it.productId }
        return defaultUnconfiguredCatalog().map { fallback -> byId[fallback.productId] ?: fallback }
    }
}
