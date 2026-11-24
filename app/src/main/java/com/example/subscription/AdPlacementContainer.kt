package com.example.subscription

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.model.PlanTier

enum class AdPlacementLocation(
    val placementKey: String,
    val bannerHeight: Dp,
    val description: String
) {
    HOME_FEED("home_feed_rect", 60.dp, "Between Tool Studios and Recent Files on Home"),
    TOOLS_HUB("tools_hub_rect", 60.dp, "Below primary studio categories on Tools Hub"),
    FILES_FOOTER("files_footer_rect", 54.dp, "Bottom of My Files list away from primary actions"),
    REMINDERS_FOOTER("reminders_footer_rect", 54.dp, "Bottom of Reminders list away from FAB")
}

object AdPlacementManager {
    /**
     * Central placement switches allow adjusting individual ad slots in future updates
     * or via Firebase Remote Config without touching screen layouts.
     */
    val enabledPlacements: MutableMap<AdPlacementLocation, Boolean> = mutableMapOf(
        AdPlacementLocation.HOME_FEED to true,
        AdPlacementLocation.TOOLS_HUB to true,
        AdPlacementLocation.FILES_FOOTER to true,
        AdPlacementLocation.REMINDERS_FOOTER to true
    )

    fun isSlotAllowed(location: AdPlacementLocation, planTier: PlanTier): Boolean {
        if (planTier == PlanTier.PREMIUM) return false
        if (!AdConfig.IS_ADMOB_CONFIGURED) return false
        return enabledPlacements[location] == true
    }
}

/**
 * Reserved rectangular advertisement container designed cleanly for Google AdMob Banner / Native ads.
 *
 * Rules enforced:
 * - Renders nothing (collapses completely to 0.dp) for Premium subscribers.
 * - Renders nothing (no fake ads or misleading ad boxes) when AdMob is not configured or unavailable.
 * - Keeps generous vertical spacing and max width constraints so ads never overlap navigation,
 *   buttons, processing progress, or destructive actions.
 */
@Composable
fun FileDudeAdContainer(
    location: AdPlacementLocation,
    planTier: PlanTier,
    modifier: Modifier = Modifier
) {
    if (!AdPlacementManager.isSlotAllowed(location, planTier)) {
        return
    }

    // When AdConfig.IS_ADMOB_CONFIGURED is set to true and Google Mobile Ads SDK + UMP consent
    // are initialized, mount the AndroidView(AdView) inside this safely padded rectangular container.
    Box(
        modifier = modifier
            .widthIn(max = 728.dp)
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(location.bannerHeight)
            .testTag("ad_slot_${location.placementKey}"),
        contentAlignment = Alignment.Center
    ) {
        // AdView host slot
    }
}
