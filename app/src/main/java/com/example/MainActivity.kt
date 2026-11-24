package com.example

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.configureAppCheck
import com.example.core.data.local.UserPreferences
import com.example.navigation.FileDudeAppShell
import com.example.navigation.IncomingAppAction
import com.example.ui.theme.FileDudeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer
        get() = (application as FileDudeApplication).container

    private val _incomingAction = MutableStateFlow<IncomingAppAction?>(null)
    private val incomingAction = _incomingAction.asStateFlow()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (REQUIRED): Configure App Check with launch intent before any UI or ViewModels.
        configureAppCheck(this, intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                val fadeOut = ObjectAnimator.ofFloat(splashScreenView, View.ALPHA, 1f, 0f)
                val scaleX = ObjectAnimator.ofFloat(splashScreenView.iconView, View.SCALE_X, 1f, 1.08f)
                val scaleY = ObjectAnimator.ofFloat(splashScreenView.iconView, View.SCALE_Y, 1f, 1.08f)
                AnimatorSet().apply {
                    playTogether(fadeOut, scaleX, scaleY)
                    interpolator = DecelerateInterpolator()
                    duration = 180L
                    doOnEnd { splashScreenView.remove() }
                    start()
                }
            }
        }

        handleIncomingIntent(intent)
        enableEdgeToEdge()

        setContent {
            val prefs by appContainer.preferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            val windowSizeClass = calculateWindowSizeClass(this)
            val action by incomingAction.collectAsStateWithLifecycle()

            FileDudeTheme(themeMode = prefs.themeMode) {
                FileDudeAppShell(
                    container = appContainer,
                    windowSizeClass = windowSizeClass,
                    incomingAction = action,
                    onConsumeIncomingAction = { _incomingAction.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        appContainer.billingRepository.refreshPurchases()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val tabExtra = intent.getStringExtra("navigate_tab")
        if (!tabExtra.isNullOrBlank()) {
            _incomingAction.value = IncomingAppAction.NavigateDestination(tabExtra)
            return
        }

        val dataUri = intent.data
        if (intent.action == Intent.ACTION_VIEW && dataUri?.scheme == "filedude") {
            val target = dataUri.host.orEmpty().ifBlank {
                dataUri.pathSegments.firstOrNull().orEmpty()
            }
            if (target.isNotBlank()) {
                _incomingAction.value = IncomingAppAction.NavigateDestination(target)
                return
            }
        }

        val sharedUri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> dataUri
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }

        if (sharedUri != null) {
            _incomingAction.value = IncomingAppAction.OpenSharedUri(
                uriString = sharedUri.toString(),
                mimeType = intent.type ?: contentResolver.getType(sharedUri)
            )
        }
    }
}
