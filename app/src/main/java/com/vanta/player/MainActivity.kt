package com.vanta.player

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vanta.player.model.TransitionPhase
import com.vanta.player.ui.components.CustomThumbnailDialog
import com.vanta.player.ui.components.OpeningTransitionOverlay
import com.vanta.player.ui.screens.GalleryScreen
import com.vanta.player.ui.screens.PlayerScreen
import com.vanta.player.ui.screens.SettingsScreen
import com.vanta.player.ui.theme.VantaTheme
import com.vanta.player.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current

            var savedPriorBrightness by remember {
                mutableFloatStateOf(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            }
            var wasBackgrounded by remember { mutableStateOf(true) }

            // Device Credential Unlock Launcher (Section 11)
            val credentialLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    viewModel.setAppLocked(false)
                    viewModel.playbackCoordinator.onAppLifecycleStarted()
                } else {
                    // Cancelled authentication exits
                    finish()
                }
            }

            // Show non-intrusive Toast messages
            LaunchedEffect(uiState.toastMessage) {
                val msg = uiState.toastMessage
                if (!msg.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearToast()
                }
            }

            // Section 5: Keep screen awake during playback; restore normal screen behaviour and prior app-window brightness on exit
            DisposableEffect(uiState.playbackState.isPlayerOpen, uiState.playbackState.isUserPaused) {
                if (uiState.playbackState.isPlayerOpen) {
                    savedPriorBrightness = window.attributes.screenBrightness
                    if (!uiState.playbackState.isUserPaused) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val params = window.attributes
                    params.screenBrightness = savedPriorBrightness
                    window.attributes = params
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Lifecycle & Phone Unlock Enforcement
            DisposableEffect(lifecycleOwner, uiState.settings.requirePhoneUnlock) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            if (wasBackgrounded && uiState.settings.requirePhoneUnlock) {
                                wasBackgrounded = false
                                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                @Suppress("DEPRECATION")
                                val intent = km?.createConfirmDeviceCredentialIntent(
                                    "Unlock Vanta",
                                    "Verify your phone lock to access your video library"
                                )
                                if (intent != null) {
                                    viewModel.setAppLocked(true)
                                    credentialLauncher.launch(intent)
                                } else {
                                    viewModel.setAppLocked(false)
                                    viewModel.playbackCoordinator.onAppLifecycleStarted()
                                }
                            } else {
                                wasBackgrounded = false
                                viewModel.playbackCoordinator.onAppLifecycleStarted()
                            }
                        }
                        Lifecycle.Event.ON_STOP -> {
                            wasBackgrounded = true
                            viewModel.playbackCoordinator.onAppLifecycleStopped()
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val folderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    viewModel.onFolderPicked(uri)
                }
            }

            VantaTheme(accentColor = uiState.settings.accentColor) {
                if (uiState.isAppLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F0F0F))
                    )
                } else {
                    val browsingKey = uiState.activeBrowsingKey
                    val savedScrollPos = remember(browsingKey) {
                        viewModel.getBrowsingScrollPosition(browsingKey)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Main Feed Layer
                        GalleryScreen(
                            uiState = uiState,
                            thumbnailCache = viewModel.thumbnailCache,
                            savedScrollPosition = savedScrollPos,
                            onSaveScrollPosition = { idx, off, anchorId, collapsed ->
                                viewModel.saveBrowsingScrollPosition(
                                    browsingKey,
                                    idx,
                                    off,
                                    anchorId,
                                    collapsed
                                )
                            },
                            onAddFolderClick = { folderPickerLauncher.launch(null) },
                            onSelectFolder = { viewModel.selectSavedFolder(it) },
                            onOpenFolderSwitcher = { viewModel.openFolderSwitcher() },
                            onCloseFolderSwitcher = { viewModel.closeFolderSwitcher() },
                            onRefreshFolder = { viewModel.refreshActiveFolder() },
                            onSelectTab = { viewModel.onTabSelected(it) },
                            onSelectSortOption = { viewModel.onSortOptionSelected(it) },
                            onOpenSearch = { viewModel.openSearch() },
                            onCloseSearch = { viewModel.closeSearch() },
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onOpenSettings = { viewModel.openSettings() },
                            onVideoTapped = { video, bounds ->
                                viewModel.onThumbnailTapped(video, bounds)
                            },
                            onToggleFavourite = { viewModel.toggleFavourite(it) },
                            onOpenCustomThumbnailEditor = {
                                viewModel.openCustomThumbnailEditor(it)
                            },
                            onRegisterThumbnailBounds = { id, rect ->
                                viewModel.registerThumbnailBounds(id, rect)
                            }
                        )

                        // 2. Fullscreen Shorts Player Layer
                        if (uiState.playbackState.isPlayerOpen) {
                            val isCoveredByOpeningOverlay =
                                uiState.transitionState.phase == TransitionPhase.OPENING ||
                                    uiState.transitionState.phase == TransitionPhase.CLOSING ||
                                    (uiState.transitionState.phase == TransitionPhase.IN_PLAYER &&
                                        !uiState.transitionState.isFirstFrameReady)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(if (isCoveredByOpeningOverlay) 0.01f else 1f)
                            ) {
                                PlayerScreen(
                                    playbackState = uiState.playbackState,
                                    settings = uiState.settings,
                                    playbackCoordinator = viewModel.playbackCoordinator,
                                    onClosePlayer = { viewModel.requestClosePlayer() },
                                    onToggleFavourite = { viewModel.toggleFavourite(it) },
                                    onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
                                    onOpenCustomThumbnailEditor = {
                                        viewModel.openCustomThumbnailEditor(it)
                                    }
                                )
                            }
                        }

                        // 3. Opening / Closing Tile-to-Fullscreen Geometry Overlay (~280ms)
                        OpeningTransitionOverlay(
                            transitionState = uiState.transitionState,
                            thumbnailCache = viewModel.thumbnailCache,
                            onOpeningAnimationFinished = {
                                viewModel.onOpeningGeometryAnimationFinished()
                            },
                            onClosingAnimationFinished = {
                                viewModel.finishClosePlayer()
                            }
                        )

                        // 4. Settings Screen Layer
                        AnimatedVisibility(
                            visible = uiState.isSettingsOpen,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            SettingsScreen(
                                settings = uiState.settings,
                                onBack = { viewModel.closeSettings() },
                                onSelectAccentColor = { viewModel.setAccentColorArgb(it) },
                                onSelectLauncherIcon = { viewModel.setLauncherIcon(it) },
                                onSelectGridColumns = { viewModel.setGridColumns(it) },
                                onSetKeepCustomFrames = { viewModel.setKeepCustomFrames(it) },
                                onApplyGlobalDefaultThumbnailSeconds = { secs, clear ->
                                    viewModel.applyGlobalDefaultThumbnailSeconds(secs, clear)
                                },
                                onSelectDefaultSpeed = { viewModel.setPlaybackSpeed(it) },
                                onSelectShortTransitionSpeed = {
                                    viewModel.setShortTransitionSpeed(it)
                                },
                                onSelectEndBehavior = { viewModel.setEndBehavior(it) },
                                onToggleBrightnessGesture = {
                                    viewModel.setGestureBrightnessEnabled(it)
                                },
                                onToggleVolumeGesture = {
                                    viewModel.setGestureVolumeEnabled(it)
                                },
                                onToggleRequirePhoneUnlock = {
                                    viewModel.setRequirePhoneUnlock(it)
                                },
                                onAddFolder = { folderPickerLauncher.launch(null) },
                                onSelectActiveFolder = { viewModel.selectSavedFolder(it) },
                                onRemoveFolder = { viewModel.removeSavedFolder(it) },
                                onRefreshLibrary = { viewModel.refreshActiveFolder() },
                                onShowMessage = { viewModel.showToast(it) }
                            )
                        }

                        // 5. Custom Video-Frame Thumbnail Editor Dialog (Section 4)
                        val editorVideo = uiState.customThumbnailEditorVideo
                        if (editorVideo != null) {
                            CustomThumbnailDialog(
                                video = editorVideo,
                                thumbnailCache = viewModel.thumbnailCache,
                                onSaveCustomTimestamp = { chosenMs ->
                                    viewModel.saveCustomThumbnailTimestamp(editorVideo, chosenMs)
                                },
                                onUseDefault = {
                                    viewModel.saveCustomThumbnailTimestamp(editorVideo, null)
                                },
                                onCancel = {
                                    viewModel.closeCustomThumbnailEditor()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
