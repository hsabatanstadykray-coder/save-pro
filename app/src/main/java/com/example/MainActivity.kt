package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppLanguage
import com.example.data.model.DownloadStatus
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MediaSaverTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UiToast

object AppRoutes {
    const val HOME = "home"
    const val DOWNLOADS = "downloads"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            MediaSaverApp(
                viewModel = viewModel,
                onReadClipboard = {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { shared ->
                viewModel.handleSharedIncomingUrl(shared)
            }
        }
    }
}

@Composable
fun MediaSaverApp(
    viewModel: MainViewModel,
    onReadClipboard: () -> String?
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val detectedPlatform by viewModel.detectedPlatform.collectAsStateWithLifecycle()
    val resolveState by viewModel.resolveUiState.collectAsStateWithLifecycle()
    val clipboardCandidate by viewModel.clipboardCandidateUrl.collectAsStateWithLifecycle()
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val savedCount by viewModel.savedCount.collectAsStateWithLifecycle()
    val usageInfo by viewModel.usageInfo.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()

    val strings = if (settings.language == AppLanguage.ARABIC) ArabicStrings else EnglishStrings
    val layoutDirection = if (settings.language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr

    val activeDownloadsCount = remember(downloadTasks) {
        downloadTasks.count {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.inspectClipboardText(onReadClipboard())
        viewModel.refreshCacheSize()
    }

    MediaSaverTheme(themeMode = settings.themeMode) {
        CompositionLocalProvider(
            LocalAppStrings provides strings,
            LocalLayoutDirection provides layoutDirection
        ) {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.HOME
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(strings) {
                viewModel.toastFlow.collect { toast ->
                    val message = when (toast) {
                        UiToast.DownloadStarted -> strings.downloadStartedToast
                        is UiToast.DownloadCompleted -> String.format(strings.downloadCompletedToast, toast.title)
                        is UiToast.DownloadFailed -> String.format(strings.downloadFailedToast, toast.reason)
                        UiToast.LinkCopied -> strings.linkCopiedToast
                        UiToast.CacheCleared -> strings.cacheClearedToast
                        UiToast.HistoryCleared -> strings.historyClearedToast
                        UiToast.ClipboardEmpty -> strings.clipboardEmptyToast
                        UiToast.DuplicateRequest -> strings.duplicateRequestToast
                        UiToast.FileMissing -> strings.fileMissingError
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }

            val navItems = listOf(
                NavDestinationItem(AppRoutes.HOME, strings.navHome, Icons.Rounded.Home, Icons.Outlined.Home),
                NavDestinationItem(
                    AppRoutes.DOWNLOADS,
                    strings.navDownloads,
                    Icons.Rounded.Download,
                    Icons.Outlined.Download,
                    badgeCount = activeDownloadsCount
                ),
                NavDestinationItem(AppRoutes.LIBRARY, strings.navLibrary, Icons.Rounded.Folder, Icons.Outlined.Folder),
                NavDestinationItem(AppRoutes.HISTORY, strings.navHistory, Icons.Rounded.History, Icons.Outlined.History),
                NavDestinationItem(AppRoutes.SETTINGS, strings.navSettings, Icons.Rounded.Settings, Icons.Outlined.Settings)
            )

            fun navigateToTab(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            var showSplash by rememberSaveable { mutableStateOf(true) }

            Crossfade(
                targetState = showSplash,
                animationSpec = tween(durationMillis = 380),
                label = "splash_crossfade"
            ) { isSplash ->
                if (isSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useNavRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                navItems.forEach { item ->
                                    val selected = currentRoute == item.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToTab(item.route) },
                                        modifier = Modifier.testTag("nav_${item.route}"),
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (item.badgeCount > 0) {
                                                        Badge { Text(item.badgeCount.toString()) }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            }
                                        },
                                        label = { Text(item.label, maxLines = 1) }
                                    )
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
                        if (useNavRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                navItems.forEach { item ->
                                    val selected = currentRoute == item.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateToTab(item.route) },
                                        modifier = Modifier.testTag("rail_${item.route}"),
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (item.badgeCount > 0) {
                                                        Badge { Text(item.badgeCount.toString()) }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            }
                                        },
                                        label = { Text(item.label, maxLines = 1) }
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = AppRoutes.HOME,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(AppRoutes.HOME) {
                                HomeScreen(
                                    urlInput = urlInput,
                                    detectedPlatform = detectedPlatform,
                                    resolveState = resolveState,
                                    clipboardCandidateUrl = clipboardCandidate,
                                    savedCount = savedCount,
                                    activeDownloadsCount = activeDownloadsCount,
                                    usageInfo = usageInfo,
                                    recentMedia = libraryItems,
                                    onUrlChange = viewModel::onUrlChanged,
                                    onPasteClick = viewModel::pasteUrlDirect,
                                    onClearClick = viewModel::clearUrlInput,
                                    onAnalyzeClick = { viewModel.analyzeUrl() },
                                    onAcceptClipboard = viewModel::acceptClipboardCandidate,
                                    onDismissClipboard = viewModel::dismissClipboardCandidate,
                                    onSelectOption = viewModel::selectMediaOption,
                                    onStartDownload = {
                                        viewModel.startSelectedDownload()
                                        navigateToTab(AppRoutes.DOWNLOADS)
                                    },
                                    onDismissResolveSheet = viewModel::dismissResolveSheet,
                                    onOpenSettings = { navigateToTab(AppRoutes.SETTINGS) },
                                    onOpenLibrary = { navigateToTab(AppRoutes.LIBRARY) },
                                    onOpenDownloads = { navigateToTab(AppRoutes.DOWNLOADS) }
                                )
                            }
                            composable(AppRoutes.DOWNLOADS) {
                                DownloadsScreen(
                                    tasks = downloadTasks,
                                    onPause = viewModel::pauseDownload,
                                    onResume = viewModel::resumeDownload,
                                    onRetry = viewModel::retryDownload,
                                    onCancel = viewModel::cancelDownload,
                                    onDismiss = viewModel::dismissDownloadTask
                                )
                            }
                            composable(AppRoutes.LIBRARY) {
                                LibraryScreen(
                                    items = libraryItems,
                                    isMediaAccessible = viewModel::isMediaAccessible,
                                    onRename = viewModel::renameMedia,
                                    onDelete = viewModel::deleteMedia,
                                    onLinkCopied = viewModel::notifyLinkCopied
                                )
                            }
                            composable(AppRoutes.HISTORY) {
                                HistoryScreen(
                                    items = historyItems,
                                    onDownloadAgain = { url ->
                                        navigateToTab(AppRoutes.HOME)
                                        viewModel.analyzeUrl(url)
                                    },
                                    onDeleteItem = viewModel::deleteHistoryItem,
                                    onClearAll = viewModel::clearAllHistory
                                )
                            }
                            composable(AppRoutes.SETTINGS) {
                                SettingsScreen(
                                    settings = settings,
                                    usageInfo = usageInfo,
                                    cacheSizeBytes = cacheSizeBytes,
                                    onThemeChange = viewModel::setThemeMode,
                                    onLanguageChange = viewModel::setLanguage,
                                    onQualityChange = viewModel::setDefaultQuality,
                                    onSaveLocationChange = viewModel::setSaveLocation,
                                    onNotificationsChange = viewModel::setNotificationsEnabled,
                                    onAutoClipboardChange = viewModel::setAutoClipboardEnabled,
                                    onClearCache = viewModel::clearCache,
                                    onClearHistory = viewModel::clearAllHistory,
                                    onRefreshUsage = viewModel::refreshUsageInfo
                                )
                            }
                        }
                    }
                }
                    }
                }
            }
        }
    }
}
