package com.example.navigation

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.authentication.AuthAccountScreen
import com.example.authentication.AuthViewModel
import com.example.core.ai.AiToolRouteKey
import com.example.core.data.local.UserPreferences
import com.example.core.model.FileCategory
import com.example.core.model.QuickActionId
import com.example.core.model.WorkspaceFile
import com.example.features.ai.AiAssistantScreen
import com.example.features.ai.AiAssistantViewModel
import com.example.features.files.FileManagerScreen
import com.example.features.files.FileManagerViewModel
import com.example.features.home.HomeScreen
import com.example.features.home.HomeViewModel
import com.example.features.onboarding.FileDudeSplashScreen
import com.example.features.onboarding.OnboardingScreen
import com.example.features.settings.LegalDocumentScreen
import com.example.features.settings.SettingsScreen
import com.example.features.settings.SettingsViewModel
import com.example.features.tools.ImageStudioScreen
import com.example.features.tools.ImageStudioViewModel
import com.example.features.tools.PdfStudioScreen
import com.example.features.tools.PdfStudioViewModel
import com.example.features.tools.SmartFileReadyScreen
import com.example.features.tools.SmartFileReadyViewModel
import com.example.features.tools.ToolsHubScreen
import com.example.features.tools.VideoStudioScreen
import com.example.features.tools.VideoStudioViewModel
import com.example.reminders.DocumentWalletScreen
import com.example.reminders.DocumentWalletViewModel
import com.example.subscription.SubscriptionScreen
import com.example.ui.theme.LocalFileDudeColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object RouteSplash
@Serializable data object RouteOnboarding
@Serializable data object RouteHome
@Serializable data object RouteTools
@Serializable data object RouteFiles
@Serializable data object RouteReminders
@Serializable data object RouteSettings
@Serializable data class RouteAiAssistant(val mode: String = "CHAT", val fileId: String? = null)
@Serializable data class RouteSmartFileReady(val fileId: String? = null)
@Serializable data class RoutePdfStudio(val mode: String = "VIEW", val fileId: String? = null)
@Serializable data class RouteImageStudio(val mode: String = "STUDIO", val fileId: String? = null)
@Serializable data class RouteVideoStudio(val mode: String = "TRIM", val fileId: String? = null)
@Serializable data object RouteAuth
@Serializable data object RouteSubscription
@Serializable data class RouteLegal(val docType: String = "PRIVACY")

sealed interface IncomingAppAction {
    data class NavigateDestination(val destination: String) : IncomingAppAction
    data class OpenSharedUri(val uriString: String, val mimeType: String?) : IncomingAppAction
}

private data class TopLevelTab(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_LEVEL_TABS = listOf(
    TopLevelTab("Home", RouteHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    TopLevelTab("Tools", RouteTools, Icons.Filled.Build, Icons.Outlined.Build, "nav_tab_tools"),
    TopLevelTab("My Files", RouteFiles, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_tab_files"),
    TopLevelTab("Reminders", RouteReminders, Icons.Filled.Notifications, Icons.Outlined.Notifications, "nav_tab_reminders"),
    TopLevelTab("Settings", RouteSettings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
)

@Composable
fun FileDudeAppShell(
    container: AppContainer,
    windowSizeClass: WindowSizeClass,
    incomingAction: IncomingAppAction? = null,
    onConsumeIncomingAction: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val scope = rememberCoroutineScope()

    var preferencesLoaded by remember { mutableStateOf(false) }
    val preferences by remember {
        container.preferencesRepository.preferencesFlow
    }.collectAsStateWithLifecycle(initialValue = UserPreferences())

    LaunchedEffect(Unit) {
        container.preferencesRepository.preferencesFlow.first()
        preferencesLoaded = true
    }

    val billingState by container.billingRepository.state.collectAsStateWithLifecycle()

    val authViewModel: AuthViewModel = viewModel(
        factory = singleFactory {
            AuthViewModel(
                container.authRepository,
                container.cloudSyncRepository,
                container.preferencesRepository
            )
        }
    )
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        authViewModel.attemptSilentSignIn()
    }

    val isTopLevelRoute = currentDest?.let { dest ->
        dest.hasRoute(RouteHome::class) ||
            dest.hasRoute(RouteTools::class) ||
            dest.hasRoute(RouteFiles::class) ||
            dest.hasRoute(RouteReminders::class) ||
            dest.hasRoute(RouteSettings::class)
    } ?: false

    val useNavigationRail = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val ext = LocalFileDudeColors.current

    fun navigateTopTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateBackSafely() {
        val popped = navController.popBackStack()
        if (!popped) {
            navigateTopTab(RouteHome)
        }
    }

    fun openFileInAppropriateStudio(file: WorkspaceFile) {
        when (file.category) {
            FileCategory.PDF -> navController.navigate(RoutePdfStudio(mode = "VIEW", fileId = file.id))
            FileCategory.IMAGE -> navController.navigate(RouteImageStudio(mode = "STUDIO", fileId = file.id))
            FileCategory.VIDEO -> navController.navigate(RouteVideoStudio(mode = "TRIM", fileId = file.id))
            else -> navController.navigate(RouteAiAssistant(mode = "CHAT", fileId = file.id))
        }
    }

    fun handleQuickAction(action: QuickActionId) {
        when (action) {
            QuickActionId.COMPRESS_PDF -> navController.navigate(RoutePdfStudio("COMPRESS"))
            QuickActionId.MERGE_PDF -> navController.navigate(RoutePdfStudio("MERGE"))
            QuickActionId.CONVERT_IMAGE -> navController.navigate(RouteImageStudio("CONVERT"))
            QuickActionId.RESIZE_IMAGE -> navController.navigate(RouteImageStudio("RESIZE"))
            QuickActionId.COMPRESS_IMAGE -> navController.navigate(RouteImageStudio("COMPRESS"))
            QuickActionId.IMAGE_TO_PDF -> navController.navigate(RoutePdfStudio("IMG2PDF"))
            QuickActionId.EDIT_VIDEO -> navController.navigate(RouteVideoStudio("TRIM"))
            QuickActionId.EXTRACT_TEXT -> navController.navigate(RouteImageStudio("OCR"))
        }
    }

    fun launchAiSuggestedTool(toolKey: AiToolRouteKey, attachedFile: WorkspaceFile?) {
        val fileId = attachedFile?.id
        when (toolKey) {
            AiToolRouteKey.SMART_FILE_READY -> navController.navigate(RouteSmartFileReady(fileId = fileId))
            AiToolRouteKey.PDF_COMPRESS -> navController.navigate(RoutePdfStudio("COMPRESS", fileId))
            AiToolRouteKey.PDF_OCR -> navController.navigate(RoutePdfStudio("OCR", fileId))
            AiToolRouteKey.PDF_TO_IMAGES -> navController.navigate(RoutePdfStudio("PDF2IMG", fileId))
            AiToolRouteKey.PDF_SECURITY -> navController.navigate(RoutePdfStudio("SECURITY", fileId))
            AiToolRouteKey.IMAGE_COMPRESS -> navController.navigate(RouteImageStudio("COMPRESS", fileId))
            AiToolRouteKey.IMAGE_RESIZE -> navController.navigate(RouteImageStudio("RESIZE", fileId))
            AiToolRouteKey.IMAGE_EXIF -> navController.navigate(RouteImageStudio("EXIF", fileId))
            AiToolRouteKey.IMAGE_TO_PDF -> navController.navigate(RoutePdfStudio("IMG2PDF", fileId))
            AiToolRouteKey.VIDEO_COMPRESS -> navController.navigate(RouteVideoStudio("COMPRESS", fileId))
        }
    }

    // Handle notifications, filedude:// deep links, and external Open With / Share intents
    LaunchedEffect(incomingAction, preferencesLoaded) {
        val action = incomingAction ?: return@LaunchedEffect
        if (!preferencesLoaded) return@LaunchedEffect

        // Ensure Home is on backstack cleanly when arriving from splash
        if (currentDest?.hasRoute(RouteSplash::class) == true) {
            navController.navigate(RouteHome) {
                popUpTo(RouteSplash) { inclusive = true }
            }
        }

        when (action) {
            is IncomingAppAction.NavigateDestination -> {
                when (action.destination.lowercase()) {
                    "reminders", "wallet" -> navigateTopTab(RouteReminders)
                    "files", "myfiles" -> navigateTopTab(RouteFiles)
                    "tools" -> navigateTopTab(RouteTools)
                    "settings" -> navigateTopTab(RouteSettings)
                    "ai", "assistant" -> navController.navigate(RouteAiAssistant("CHAT"))
                    "smartready", "smart_file_ready" -> navController.navigate(RouteSmartFileReady())
                    "pdf" -> navController.navigate(RoutePdfStudio("VIEW"))
                    "image" -> navController.navigate(RouteImageStudio("STUDIO"))
                    "video" -> navController.navigate(RouteVideoStudio("TRIM"))
                    "subscription", "premium" -> navController.navigate(RouteSubscription)
                    "auth", "account" -> navController.navigate(RouteAuth)
                    else -> navigateTopTab(RouteHome)
                }
            }
            is IncomingAppAction.OpenSharedUri -> {
                scope.launch {
                    val uri = runCatching { Uri.parse(action.uriString) }.getOrNull()
                    if (uri != null) {
                        container.workspaceFileRepository.importUriToWorkspace(uri, "Shared")
                            .onSuccess { imported ->
                                openFileInAppropriateStudio(imported)
                            }
                    }
                }
            }
        }
        onConsumeIncomingAction()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (isTopLevelRoute && !useNavigationRail) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = ext.border)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        TOP_LEVEL_TABS.forEach { tab ->
                            val selected = currentDest?.hierarchy?.any {
                                when (tab.route) {
                                    is RouteHome -> it.hasRoute(RouteHome::class)
                                    is RouteTools -> it.hasRoute(RouteTools::class)
                                    is RouteFiles -> it.hasRoute(RouteFiles::class)
                                    is RouteReminders -> it.hasRoute(RouteReminders::class)
                                    else -> it.hasRoute(RouteSettings::class)
                                }
                            } ?: false
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = ext.paleMint,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isTopLevelRoute && useNavigationRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    TOP_LEVEL_TABS.forEach { tab ->
                        val selected = currentDest?.hierarchy?.any {
                            when (tab.route) {
                                is RouteHome -> it.hasRoute(RouteHome::class)
                                is RouteTools -> it.hasRoute(RouteTools::class)
                                is RouteFiles -> it.hasRoute(RouteFiles::class)
                                is RouteReminders -> it.hasRoute(RouteReminders::class)
                                else -> it.hasRoute(RouteSettings::class)
                            }
                        } ?: false
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = ext.paleMint,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                VerticalDivider(thickness = 1.dp, color = ext.border, modifier = Modifier.fillMaxHeight())
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = RouteSplash,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    composable<RouteSplash> {
                        FileDudeSplashScreen(
                            isDataReady = preferencesLoaded,
                            skipDelay = incomingAction != null,
                            onFinished = {
                                if (incomingAction == null) {
                                    val next: Any = if (preferences.onboardingCompleted || currentUser != null) {
                                        RouteHome
                                    } else {
                                        RouteOnboarding
                                    }
                                    navController.navigate(next) {
                                        popUpTo(RouteSplash) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable<RouteOnboarding> {
                        OnboardingScreen(
                            isAuthLoading = authUiState.isLoading,
                            authError = authUiState.errorMessage,
                            onGoogleSignIn = { act: Activity ->
                                authViewModel.signInWithGoogle(act, billingState.planTier) {
                                    navController.navigate(RouteHome) {
                                        popUpTo(RouteOnboarding) { inclusive = true }
                                    }
                                }
                            },
                            onContinueAsGuest = {
                                authViewModel.continueAsGuest {
                                    navController.navigate(RouteHome) {
                                        popUpTo(RouteOnboarding) { inclusive = true }
                                    }
                                }
                            },
                            onDismissError = authViewModel::clearMessages
                        )
                    }

                    composable<RouteHome> {
                        val homeVm: HomeViewModel = viewModel(
                            factory = singleFactory {
                                HomeViewModel(
                                    container.workspaceFileRepository,
                                    container.documentWalletRepository,
                                    container.preferencesRepository
                                )
                            }
                        )
                        val homeState by homeVm.uiState.collectAsStateWithLifecycle()
                        HomeScreen(
                            uiState = homeState,
                            userDisplayName = currentUser?.displayName,
                            planTier = billingState.planTier,
                            onSearchChange = homeVm::onSearchQueryChange,
                            onOpenNotifications = { navigateTopTab(RouteReminders) },
                            onOpenProfile = { navController.navigate(RouteAuth) },
                            onOpenSmartFileReady = { navController.navigate(RouteSmartFileReady()) },
                            onOpenAiAssistant = { navController.navigate(RouteAiAssistant("CHAT")) },
                            onQuickActionClick = ::handleQuickAction,
                            onMoveQuickActionUp = homeVm::moveQuickActionUp,
                            onMoveQuickActionDown = homeVm::moveQuickActionDown,
                            onResetQuickActions = homeVm::resetQuickActions,
                            onOpenPdfStudio = { navController.navigate(RoutePdfStudio("VIEW")) },
                            onOpenImageStudio = { navController.navigate(RouteImageStudio("STUDIO")) },
                            onOpenVideoStudio = { navController.navigate(RouteVideoStudio("TRIM")) },
                            onOpenDocumentWallet = { navigateTopTab(RouteReminders) },
                            onOpenToolsTab = { navigateTopTab(RouteTools) },
                            onOpenFilesTab = { navigateTopTab(RouteFiles) },
                            onOpenRemindersTab = { navigateTopTab(RouteReminders) },
                            onFileClick = ::openFileInAppropriateStudio,
                            onToggleFavourite = homeVm::toggleFavourite
                        )
                    }

                    composable<RouteTools> {
                        ToolsHubScreen(
                            planTier = billingState.planTier,
                            onOpenSmartFileReady = { navController.navigate(RouteSmartFileReady()) },
                            onOpenAiMode = { mode -> navController.navigate(RouteAiAssistant(mode)) },
                            onOpenPdfMode = { mode -> navController.navigate(RoutePdfStudio(mode)) },
                            onOpenImageMode = { mode -> navController.navigate(RouteImageStudio(mode)) },
                            onOpenVideoMode = { mode -> navController.navigate(RouteVideoStudio(mode)) },
                            onOpenDocumentWallet = { navigateTopTab(RouteReminders) }
                        )
                    }

                    composable<RouteFiles> {
                        val filesVm: FileManagerViewModel = viewModel(
                            factory = singleFactory { FileManagerViewModel(container.workspaceFileRepository) }
                        )
                        val filesState by filesVm.uiState.collectAsStateWithLifecycle()
                        FileManagerScreen(
                            uiState = filesState,
                            viewModel = filesVm,
                            fileRepository = container.workspaceFileRepository,
                            pdfEngine = container.pdfEngine,
                            planTier = billingState.planTier,
                            onOpenInStudio = ::openFileInAppropriateStudio,
                            onOpenInSmartReady = { wf -> navController.navigate(RouteSmartFileReady(fileId = wf.id)) },
                            onOpenInAi = { wf -> navController.navigate(RouteAiAssistant(mode = "CHAT", fileId = wf.id)) }
                        )
                    }

                    composable<RouteReminders> {
                        val walletVm: DocumentWalletViewModel = viewModel(
                            factory = singleFactory {
                                DocumentWalletViewModel(
                                    container.documentWalletRepository,
                                    container.workspaceFileRepository,
                                    container.preferencesRepository
                                )
                            }
                        )
                        val walletState by walletVm.uiState.collectAsStateWithLifecycle()
                        DocumentWalletScreen(
                            uiState = walletState,
                            viewModel = walletVm,
                            fileRepository = container.workspaceFileRepository,
                            isSignedIn = currentUser != null,
                            planTier = billingState.planTier
                        )
                    }

                    composable<RouteSettings> {
                        val settingsVm: SettingsViewModel = viewModel(
                            factory = singleFactory {
                                SettingsViewModel(
                                    container.preferencesRepository,
                                    container.workspaceFileRepository,
                                    container.cloudSyncRepository
                                )
                            }
                        )
                        val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
                        SettingsScreen(
                            uiState = settingsState,
                            viewModel = settingsVm,
                            userEmail = currentUser?.email,
                            planTier = billingState.planTier,
                            onOpenAccount = { navController.navigate(RouteAuth) },
                            onOpenSubscription = { navController.navigate(RouteSubscription) },
                            onOpenLegal = { type -> navController.navigate(RouteLegal(type)) }
                        )
                    }

                    composable<RouteAiAssistant> { entry ->
                        val args = entry.toRoute<RouteAiAssistant>()
                        val aiVm: AiAssistantViewModel = viewModel(
                            factory = singleFactory {
                                AiAssistantViewModel(
                                    container.aiRepository,
                                    container.workspaceFileRepository,
                                    container.preferencesRepository,
                                    container.documentWalletRepository
                                )
                            }
                        )
                        LaunchedEffect(args.mode, args.fileId) {
                            aiVm.setInitialArgs(args.mode, args.fileId)
                        }
                        LaunchedEffect(billingState.planTier) {
                            aiVm.updatePlanTier(billingState.planTier)
                        }
                        val aiState by aiVm.uiState.collectAsStateWithLifecycle()
                        AiAssistantScreen(
                            uiState = aiState,
                            viewModel = aiVm,
                            onLaunchSuggestedTool = ::launchAiSuggestedTool,
                            onUpgradeClick = { navController.navigate(RouteSubscription) },
                            onBack = ::navigateBackSafely
                        )
                    }

                    composable<RouteSmartFileReady> { entry ->
                        val args = entry.toRoute<RouteSmartFileReady>()
                        val smartVm: SmartFileReadyViewModel = viewModel(
                            factory = singleFactory {
                                SmartFileReadyViewModel(
                                    container.database.smartPresetDao(),
                                    container.workspaceFileRepository,
                                    container.cloudSyncRepository,
                                    container.smartFileReadyEngine
                                )
                            }
                        )
                        LaunchedEffect(args.fileId) {
                            if (!args.fileId.isNullOrBlank()) {
                                val files = container.workspaceFileRepository.activeFiles.first()
                                files.firstOrNull { it.id == args.fileId }?.let { smartVm.selectWorkspaceFile(it) }
                            }
                        }
                        val smartState by smartVm.uiState.collectAsStateWithLifecycle()
                        SmartFileReadyScreen(
                            uiState = smartState,
                            fileRepository = container.workspaceFileRepository,
                            cloudSyncEnabled = currentUser != null && preferences.cloudSyncEnabled,
                            onSelectPreset = smartVm::selectPreset,
                            onUpdateSpec = smartVm::updateSpec,
                            onImportUri = smartVm::importFileFromUri,
                            onRunProcessing = smartVm::runProcessing,
                            onCancelProcessing = smartVm::cancelProcessing,
                            onSaveCustomPreset = smartVm::saveCustomPreset,
                            onExportToUri = smartVm::exportToUri,
                            onClearMessage = smartVm::clearMessage,
                            onBack = ::navigateBackSafely
                        )
                    }

                    composable<RoutePdfStudio> { entry ->
                        val args = entry.toRoute<RoutePdfStudio>()
                        val pdfVm: PdfStudioViewModel = viewModel(
                            factory = singleFactory {
                                PdfStudioViewModel(container.workspaceFileRepository, container.pdfEngine)
                            }
                        )
                        LaunchedEffect(args.mode, args.fileId) {
                            pdfVm.setInitialMode(args.mode, args.fileId)
                        }
                        val pdfState by pdfVm.uiState.collectAsStateWithLifecycle()
                        PdfStudioScreen(
                            uiState = pdfState,
                            viewModel = pdfVm,
                            fileRepository = container.workspaceFileRepository,
                            onAskAi = { wf -> navController.navigate(RouteAiAssistant("CHAT", wf.id)) },
                            onBack = ::navigateBackSafely
                        )
                    }

                    composable<RouteImageStudio> { entry ->
                        val args = entry.toRoute<RouteImageStudio>()
                        val imgVm: ImageStudioViewModel = viewModel(
                            factory = singleFactory {
                                ImageStudioViewModel(container.workspaceFileRepository, container.pdfEngine)
                            }
                        )
                        LaunchedEffect(args.mode, args.fileId) {
                            imgVm.setInitialMode(args.mode, args.fileId)
                        }
                        val imgState by imgVm.uiState.collectAsStateWithLifecycle()
                        ImageStudioScreen(
                            uiState = imgState,
                            viewModel = imgVm,
                            fileRepository = container.workspaceFileRepository,
                            onBack = ::navigateBackSafely
                        )
                    }

                    composable<RouteVideoStudio> { entry ->
                        val args = entry.toRoute<RouteVideoStudio>()
                        val vidVm: VideoStudioViewModel = viewModel(
                            factory = singleFactory {
                                VideoStudioViewModel(container.workspaceFileRepository, container.videoEngine)
                            }
                        )
                        LaunchedEffect(args.mode, args.fileId) {
                            vidVm.setInitialMode(args.mode, args.fileId)
                        }
                        val vidState by vidVm.uiState.collectAsStateWithLifecycle()
                        VideoStudioScreen(
                            uiState = vidState,
                            viewModel = vidVm,
                            fileRepository = container.workspaceFileRepository,
                            onBack = ::navigateBackSafely
                        )
                    }

                    composable<RouteAuth> {
                        AuthAccountScreen(
                            currentUser = currentUser,
                            uiState = authUiState,
                            planTier = billingState.planTier,
                            cloudSyncEnabled = preferences.cloudSyncEnabled,
                            onGoogleSignIn = { act ->
                                authViewModel.signInWithGoogle(act, billingState.planTier)
                            },
                            onContinueAsGuest = {
                                authViewModel.continueAsGuest { navigateBackSafely() }
                            },
                            onUpdateDisplayName = authViewModel::updateDisplayName,
                            onToggleCloudSync = { enabled ->
                                if (currentUser != null) {
                                    scope.launch {
                                        container.preferencesRepository.setCloudSyncEnabled(enabled)
                                    }
                                }
                            },
                            onSignOut = authViewModel::signOut,
                            onDeleteAccount = {
                                authViewModel.deleteAccount { navigateBackSafely() }
                            },
                            onOpenSubscription = { navController.navigate(RouteSubscription) },
                            onOpenPrivacyPolicy = { navController.navigate(RouteLegal("PRIVACY")) },
                            onOpenTerms = { navController.navigate(RouteLegal("TERMS")) },
                            onClearMessages = authViewModel::clearMessages,
                            onBack = ::navigateBackSafely
                        )
                    }

                    composable<RouteSubscription> {
                        SubscriptionScreen(
                            billingState = billingState,
                            onLaunchPurchase = container.billingRepository::launchPurchase,
                            onRestorePurchases = container.billingRepository::refreshPurchases,
                            onClearMessages = container.billingRepository::clearMessages,
                            onBack = ::navigateBackSafely
                        )
                    }

                    composable<RouteLegal> { entry ->
                        val args = entry.toRoute<RouteLegal>()
                        LegalDocumentScreen(
                            docType = args.docType,
                            onBack = ::navigateBackSafely
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun singleFactory(
    creator: () -> ViewModel
): ViewModelProvider.Factory = remember {
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
    }
}
