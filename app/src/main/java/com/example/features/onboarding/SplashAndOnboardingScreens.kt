package com.example.features.onboarding

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.design.FileDudeCard
import com.example.core.design.FileDudeErrorBanner
import com.example.core.design.FileDudePrimaryButton
import com.example.core.design.FileDudeSecondaryButton
import com.example.ui.theme.LocalFileDudeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FileDudeSplashScreen(
    isDataReady: Boolean = true,
    skipDelay: Boolean = false,
    onFinished: () -> Unit
) {
    val ext = LocalFileDudeColors.current
    var visible by remember { mutableStateOf(false) }
    var minDelayElapsed by remember { mutableStateOf(skipDelay) }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "splash_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 260),
        label = "splash_alpha"
    )

    LaunchedEffect(skipDelay) {
        visible = true
        if (!skipDelay) {
            delay(650L)
            minDelayElapsed = true
        }
    }

    LaunchedEffect(minDelayElapsed, isDataReady) {
        if (minDelayElapsed && isDataReady) {
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(ext.paleMint.copy(alpha = 0.55f), MaterialTheme.colorScheme.background),
                    radius = 950f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("filedude_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
                .padding(32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, ext.border)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.filedude_logo),
                    contentDescription = "FileDude Logo",
                    modifier = Modifier
                        .padding(10.dp)
                        .size(84.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "FileDude",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Your Files. Ready When You Are.",
                style = MaterialTheme.typography.bodyLarge,
                color = ext.darkGreenText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = ext.paleMint
            )
        }
    }
}

private data class OnboardingPageData(
    val title: String,
    val description: String,
    val badge: String,
    val imageRes: Int
)

@Composable
fun OnboardingScreen(
    isAuthLoading: Boolean,
    authError: String?,
    onGoogleSignIn: (Activity) -> Unit,
    onContinueAsGuest: () -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val ext = LocalFileDudeColors.current

    val pages = remember {
        listOf(
            OnboardingPageData(
                title = "PDF, Image & Video Studio",
                description = "Compress, merge, split, annotate, sign PDFs, adjust photos, extract text with OCR, and trim or export videos right on your device.",
                badge = "All-in-One Toolkit",
                imageRes = R.drawable.img_onboarding_studio
            ),
            OnboardingPageData(
                title = "Smart File Ready",
                description = "Match strict portal limits for passport photos, job applications, college admissions, and government forms—verified with a real pass/fail report.",
                badge = "Signature Feature",
                imageRes = R.drawable.img_onboarding_smart_ready
            ),
            OnboardingPageData(
                title = "Offline-First & Private Wallet",
                description = "Your files stay on your phone by default, originals are preserved, and your Document Wallet alerts you 90, 30, 7, and 1 day before expiry.",
                badge = "Privacy First",
                imageRes = R.drawable.img_empty_wallet
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.filedude_logo),
                    contentDescription = "FileDude Logo",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.size(10.dp))
                Text("FileDude", style = MaterialTheme.typography.titleLarge)
            }
            TextButton(
                onClick = onContinueAsGuest,
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text("Skip", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (!authError.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            FileDudeErrorBanner(
                title = "Sign-In Notice",
                message = authError,
                onDismiss = onDismissError,
                modifier = Modifier.widthIn(max = 600.dp)
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = ext.paleMint,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = page.badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = ext.darkGreenText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Image(
                    painter = painterResource(id = page.imageRes),
                    contentDescription = page.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Page indicator dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pages.size) { i ->
                val active = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .size(width = if (active) 24.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else ext.border)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        FileDudeCard(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            containerColor = ext.secondarySurface
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "Offline-first by design. Permissions are requested only when you pick a feature that needs them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (pagerState.currentPage < pages.lastIndex) {
                FileDudePrimaryButton(
                    text = "Next",
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "onboarding_next_button"
                )
            } else {
                FileDudePrimaryButton(
                    text = "Continue with Google",
                    icon = Icons.Default.VerifiedUser,
                    loading = isAuthLoading,
                    onClick = {
                        if (activity != null) onGoogleSignIn(activity)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "onboarding_google_button"
                )
            }

            FileDudeSecondaryButton(
                text = "Continue as Guest (Explore Offline)",
                onClick = onContinueAsGuest,
                modifier = Modifier.fillMaxWidth(),
                testTag = "onboarding_guest_button"
            )
        }
    }
}
