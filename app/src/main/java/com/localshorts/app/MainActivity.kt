package com.localshorts.app

import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localshorts.app.model.TransitionPhase
import com.localshorts.app.ui.components.OpeningTransitionOverlay
import com.localshorts.app.ui.screens.GalleryScreen
import com.localshorts.app.ui.screens.PlayerScreen
import com.localshorts.app.ui.screens.SettingsScreen
import com.localshorts.app.ui.theme.HollowTheme
import com.localshorts.app.ui.viewmodel.MainViewModel

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
            val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
            val transitionState by viewModel.transitionUiState.collectAsStateWithLifecycle()
            val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current

            // Keep the screen awake only while a video is actively playing in the player.
            DisposableEffect(playbackState.isPlayerOpen, playbackState.isUserPaused) {
                if (playbackState.isPlayerOpen && !playbackState.isUserPaused) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Backgrounding immediately pauses and mutes every pooled player.
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> viewModel.playbackCoordinator.onAppLifecycleStarted()
                        Lifecycle.Event.ON_STOP -> viewModel.playbackCoordinator.onAppLifecycleStopped()
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

            HollowTheme(
                accentPreset = uiState.settings.accentPreset,
                gradientPreset = uiState.settings.gradientPreset,
                iconStylePreset = uiState.settings.iconStylePreset
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Gallery: stays composed under everything so scroll position, tab and
                    // thumbnail coordinates survive playback and settings.
                    GalleryScreen(
                        uiState = uiState,
                        thumbnailCache = viewModel.thumbnailCache,
                        onAddFolderClick = { folderPickerLauncher.launch(null) },
                        onSelectFolder = { viewModel.selectSavedFolder(it) },
                        onRefreshFolder = { viewModel.refreshActiveFolder() },
                        onSelectTab = { viewModel.setSelectedTab(it) },
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onCycleGridColumns = {
                            val nextCols = (uiState.settings.gridColumns % 4) + 1
                            viewModel.setGridColumns(nextCols)
                        },
                        onOpenSettings = { viewModel.openSettings() },
                        onVideoTapped = { video, bounds ->
                            viewModel.onThumbnailTapped(video, bounds)
                        },
                        onToggleFavourite = { viewModel.toggleFavourite(it) },
                        onRegisterThumbnailBounds = { id, rect ->
                            viewModel.registerThumbnailBounds(id, rect)
                        },
                        onUnregisterThumbnailBounds = { id ->
                            viewModel.unregisterThumbnailBounds(id)
                        },
                        onSaveScrollPosition = { index, offset ->
                            viewModel.saveGalleryScrollState(index, offset)
                        }
                    )

                    // 2. Player: composed as soon as a thumbnail is tapped so its TextureViews get
                    // surfaces and render the first frame while the cover is still expanding.
                    // Alpha stays > 0 while covered so the TextureView layer keeps drawing.
                    if (playbackState.isPlayerOpen) {
                        val isCovered = transitionState.phase != TransitionPhase.IDLE
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (isCovered) 0.01f else 1f)
                        ) {
                            PlayerScreen(
                                playbackState = playbackState,
                                settings = uiState.settings,
                                playbackCoordinator = viewModel.playbackCoordinator,
                                thumbnailCache = viewModel.thumbnailCache,
                                onClosePlayer = { viewModel.requestClosePlayer() },
                                onToggleFavourite = { viewModel.toggleFavourite(it) },
                                onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
                                onSelectEndBehavior = { viewModel.setEndBehavior(it) },
                                onToggleVideoInfo = { viewModel.toggleVideoInfoOverlay() },
                                onOpenSettings = { viewModel.openSettings() },
                                onSetThumbnail = { viewModel.setThumbnailFromCurrentFrame(it) },
                                onResetThumbnail = { viewModel.resetThumbnail(it) }
                            )
                        }
                    }

                    // 3. Geometry transition overlay.
                    OpeningTransitionOverlay(
                        transitionState = transitionState,
                        thumbnailCache = viewModel.thumbnailCache,
                        onOpeningGeometryFinished = { viewModel.onOpeningGeometryFinished() },
                        onCoverDismissed = { viewModel.onCoverDismissed() },
                        onClosingFinished = { viewModel.finishClosePlayer() }
                    )

                    // 4. Settings layer; closing it returns to the exact previous gallery/player state.
                    AnimatedVisibility(
                        visible = isSettingsOpen,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        SettingsScreen(
                            settings = uiState.settings,
                            onBack = { viewModel.closeSettings() },
                            onSelectAccent = { viewModel.setAccentPreset(it) },
                            onSelectGradient = { viewModel.setGradientPreset(it) },
                            onSelectIconStyle = { viewModel.setIconStylePreset(it) },
                            onSelectGridColumns = { viewModel.setGridColumns(it) },
                            onSelectDefaultSpeed = { viewModel.setPlaybackSpeed(it) },
                            onSelectEndBehavior = { viewModel.setEndBehavior(it) },
                            onSelectSwipeTransitionSpeed = { viewModel.setSwipeTransitionSpeed(it) },
                            onToggleBrightnessGesture = { viewModel.setGestureBrightnessEnabled(it) },
                            onToggleVolumeGesture = { viewModel.setGestureVolumeEnabled(it) },
                            onSelectMiddleGesture = { viewModel.setMiddleGestureAction(it) },
                            onAddFolder = { folderPickerLauncher.launch(null) },
                            onSelectActiveFolder = { viewModel.selectSavedFolder(it) },
                            onRemoveFolder = { viewModel.removeSavedFolder(it) },
                            onRefreshLibrary = { viewModel.refreshActiveFolder() }
                        )
                    }
                }
            }
        }
    }
}
