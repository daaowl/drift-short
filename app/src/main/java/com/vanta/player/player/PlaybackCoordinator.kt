package com.vanta.player.player

import android.content.Context
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.vanta.player.model.EndBehavior
import com.vanta.player.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

data class PlaybackUiState(
    val isPlayerOpen: Boolean = false,
    val playlist: List<VideoItem> = emptyList(),
    val currentIndex: Int = 0,
    val dominantRole: SlotRole = SlotRole.CURRENT,
    val dragFraction: Float = 0f,
    val isUserPaused: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val endBehavior: EndBehavior = EndBehavior.LOOP,
    val isStoppedAtFinalFrame: Boolean = false,
    val currentPositionMs: Long = 0L,
    val currentDurationMs: Long = 0L,
    val currentSlotIndex: Int = 1,
    val prevSlotIndex: Int = 0,
    val nextSlotIndex: Int = 2,
    val slotSnapshots: Map<Int, SlotSnapshot> = emptyMap(),
    val speedErrorMessage: String? = null
) {
    val canSwipeAdjacent: Boolean
        get() = playlist.size > 1

    val currentVideo: VideoItem?
        get() = playlist.getOrNull(currentIndex)

    // Section 6 & 8: Wrap around at collection ends; handle 1-video collection without invalid adjacent page
    val prevIndex: Int?
        get() = if (playlist.size <= 1) null else (currentIndex - 1 + playlist.size) % playlist.size

    val nextIndex: Int?
        get() = if (playlist.size <= 1) null else (currentIndex + 1) % playlist.size

    val prevVideo: VideoItem?
        get() = prevIndex?.let { playlist.getOrNull(it) }

    val nextVideo: VideoItem?
        get() = nextIndex?.let { playlist.getOrNull(it) }
}

class PlaybackCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val onWatchCountQualified: (stableVideoId: String) -> Unit
) {
    companion object {
        // Section 6: During a held swipe, the video occupying MORE THAN HALF the screen plays UNMUTED.
        const val DOMINANCE_THRESHOLD = 0.505f
        private const val WATCH_COUNT_REAL_PLAYBACK_MS = 3_000L
    }

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _autoAdvanceEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val autoAdvanceEvent: SharedFlow<Int> = _autoAdvanceEvent.asSharedFlow()

    val pool = BoundedPlayerPool(
        context = context,
        onFirstFrameRenderedCallback = { slotIndex, videoId ->
            onSlotFirstFrameRendered(slotIndex, videoId)
        },
        onPlaybackEndedCallback = { slotIndex, videoId ->
            onSlotPlaybackEnded(slotIndex, videoId)
        },
        onPlayerErrorCallback = { _, _, _ ->
            syncPoolSnapshots()
        }
    )

    private var progressPollingJob: Job? = null
    private var accumulatedEffectiveWatchMs: Double = 0.0
    private var countedInCurrentSession: Boolean = false
    private var isAppInForeground: Boolean = true

    init {
        scope.launch {
            pool.snapshots.collect { snapshots ->
                _state.update { it.copy(slotSnapshots = snapshots) }
            }
        }
    }

    fun getManagedSlot(slotIndex: Int): BoundedPlayerPool.ManagedSlot {
        return pool.getSlotByIndex(slotIndex)
    }

    fun openPlaylistAt(
        playlist: List<VideoItem>,
        startIndex: Int,
        defaultSpeed: Float,
        endBehavior: EndBehavior
    ) {
        if (playlist.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, playlist.lastIndex)

        accumulatedEffectiveWatchMs = 0.0
        countedInCurrentSession = false

        _state.update {
            it.copy(
                isPlayerOpen = true,
                playlist = playlist,
                currentIndex = safeIndex,
                dominantRole = SlotRole.CURRENT,
                dragFraction = 0f,
                isUserPaused = false,
                playbackSpeed = defaultSpeed,
                endBehavior = endBehavior,
                isStoppedAtFinalFrame = false,
                currentPositionMs = 0L,
                currentDurationMs = playlist[safeIndex].durationMs,
                speedErrorMessage = null
            )
        }

        bindAllThreeRoles(resetCurrentPosition = true)
        enforceDominantPlayback()
        startProgressPolling()
    }

    fun updatePlaylistMetadata(updatedVideosById: Map<String, VideoItem>) {
        val currentState = _state.value
        if (currentState.playlist.isEmpty()) return
        val newList = currentState.playlist.map { item ->
            updatedVideosById[item.stableId] ?: item
        }
        _state.update { it.copy(playlist = newList) }
    }

    fun onContinuousDrag(dragFraction: Float) {
        val s = _state.value
        if (!s.isPlayerOpen) return
        if (!s.canSwipeAdjacent) {
            _state.update { it.copy(dragFraction = 0f, dominantRole = SlotRole.CURRENT) }
            return
        }

        val clampedFraction = dragFraction.coerceIn(-1f, 1f)
        val incomingShare = abs(clampedFraction)

        val nextDominant = when {
            clampedFraction > 0f && incomingShare > DOMINANCE_THRESHOLD -> SlotRole.NEXT
            clampedFraction < 0f && incomingShare > DOMINANCE_THRESHOLD -> SlotRole.PREV
            else -> SlotRole.CURRENT
        }

        val roleChanged = nextDominant != s.dominantRole
        _state.update {
            it.copy(
                dragFraction = clampedFraction,
                dominantRole = nextDominant
            )
        }

        if (roleChanged) {
            enforceDominantPlayback()
        }
    }

    fun onSwipeSettled(indexDelta: Int) {
        val s = _state.value
        if (!s.isPlayerOpen) return

        if (indexDelta == 0 || !s.canSwipeAdjacent) {
            _state.update {
                it.copy(
                    dragFraction = 0f,
                    dominantRole = SlotRole.CURRENT
                )
            }
            pool.getSlotForRole(SlotRole.PREV).player.seekTo(0L)
            pool.getSlotForRole(SlotRole.NEXT).player.seekTo(0L)
            enforceDominantPlayback()
            return
        }

        val targetIndex = if (indexDelta > 0) {
            s.nextIndex ?: s.currentIndex
        } else {
            s.prevIndex ?: s.currentIndex
        }

        if (indexDelta > 0) {
            pool.rotateForward()
        } else {
            pool.rotateBackward()
        }

        // Section 9: Revisiting a video starts another eligible viewing session
        accumulatedEffectiveWatchMs = 0.0
        countedInCurrentSession = false

        val newCurrentSlot = pool.getSlotForRole(SlotRole.CURRENT)
        val newPrevSlot = pool.getSlotForRole(SlotRole.PREV)
        val newNextSlot = pool.getSlotForRole(SlotRole.NEXT)

        _state.update {
            it.copy(
                currentIndex = targetIndex,
                dragFraction = 0f,
                dominantRole = SlotRole.CURRENT,
                isUserPaused = false,
                isStoppedAtFinalFrame = false,
                currentSlotIndex = newCurrentSlot.slotIndex,
                prevSlotIndex = newPrevSlot.slotIndex,
                nextSlotIndex = newNextSlot.slotIndex,
                currentPositionMs = newCurrentSlot.player.currentPosition.coerceAtLeast(0L),
                currentDurationMs = it.playlist.getOrNull(targetIndex)?.durationMs ?: 0L
            )
        }

        val speed = _state.value.playbackSpeed
        newPrevSlot.bindVideo(_state.value.prevVideo, speed, resetPosition = true)
        newNextSlot.bindVideo(_state.value.nextVideo, speed, resetPosition = true)

        enforceDominantPlayback()
    }

    fun togglePlayPause() {
        val s = _state.value
        if (!s.isPlayerOpen) return

        val currentSlot = pool.getSlotForRole(SlotRole.CURRENT)
        // Section 5: Play after a stopped ending restarts from the beginning
        if (s.isStoppedAtFinalFrame || currentSlot.player.playbackState == Player.STATE_ENDED) {
            currentSlot.player.seekTo(0L)
            _state.update {
                it.copy(
                    isUserPaused = false,
                    isStoppedAtFinalFrame = false
                )
            }
            enforceDominantPlayback()
            return
        }

        val nowPaused = !s.isUserPaused
        _state.update { it.copy(isUserPaused = nowPaused) }
        enforceDominantPlayback()
    }

    fun pauseExplicitly() {
        if (!_state.value.isPlayerOpen) return
        _state.update { it.copy(isUserPaused = true) }
        enforceDominantPlayback()
    }

    fun seekTo(positionMs: Long) {
        val currentSlot = pool.getSlotForRole(SlotRole.CURRENT)
        val duration = currentSlot.player.duration.takeIf { it > 0 }
            ?: _state.value.currentDurationMs
        val target = positionMs.coerceIn(0L, duration.coerceAtLeast(0L))
        currentSlot.player.seekTo(target)
        _state.update {
            it.copy(
                currentPositionMs = target,
                isStoppedAtFinalFrame = false
            )
        }
        if (!_state.value.isUserPaused) {
            enforceDominantPlayback()
        }
    }

    fun setPlaybackSpeed(speed: Float): Boolean {
        val safeSpeed = speed.coerceIn(0.25f, 2.25f)
        return try {
            SlotRole.entries.forEach { role ->
                pool.getSlotForRole(role).player.setPlaybackParameters(PlaybackParameters(safeSpeed))
            }
            _state.update { it.copy(playbackSpeed = safeSpeed, speedErrorMessage = null) }
            true
        } catch (e: Exception) {
            _state.update {
                it.copy(speedErrorMessage = "Device decoder does not support ${safeSpeed}× playback speed.")
            }
            false
        }
    }

    fun setEndBehavior(behavior: EndBehavior) {
        _state.update { it.copy(endBehavior = behavior) }
    }

    fun pauseForClosingOrBackground() {
        pool.pauseAndMuteAll()
    }

    fun onAppLifecycleStarted() {
        isAppInForeground = true
        if (_state.value.isPlayerOpen) {
            enforceDominantPlayback()
            startProgressPolling()
        }
    }

    fun onAppLifecycleStopped() {
        isAppInForeground = false
        pool.pauseAndMuteAll()
        progressPollingJob?.cancel()
    }

    fun closePlayer() {
        progressPollingJob?.cancel()
        pool.pauseAndMuteAll()
        _state.update {
            it.copy(
                isPlayerOpen = false,
                dragFraction = 0f,
                dominantRole = SlotRole.CURRENT,
                isUserPaused = false,
                isStoppedAtFinalFrame = false
            )
        }
    }

    fun release() {
        progressPollingJob?.cancel()
        pool.releaseAll()
    }

    private fun bindAllThreeRoles(resetCurrentPosition: Boolean) {
        val s = _state.value
        val prevSlot = pool.getSlotForRole(SlotRole.PREV)
        val currentSlot = pool.getSlotForRole(SlotRole.CURRENT)
        val nextSlot = pool.getSlotForRole(SlotRole.NEXT)

        currentSlot.bindVideo(s.currentVideo, s.playbackSpeed, resetCurrentPosition)
        prevSlot.bindVideo(s.prevVideo, s.playbackSpeed, resetPosition = true)
        nextSlot.bindVideo(s.nextVideo, s.playbackSpeed, resetPosition = true)

        _state.update {
            it.copy(
                currentSlotIndex = currentSlot.slotIndex,
                prevSlotIndex = prevSlot.slotIndex,
                nextSlotIndex = nextSlot.slotIndex
            )
        }
    }

    fun enforceDominantPlayback() {
        val s = _state.value
        if (!s.isPlayerOpen || !isAppInForeground) {
            pool.pauseAndMuteAll()
            return
        }

        val activeRole = s.dominantRole
        SlotRole.entries.forEach { role ->
            if (role != activeRole) {
                val slot = pool.getSlotForRole(role)
                slot.player.volume = 0f
                slot.player.playWhenReady = false
                slot.player.pause()
            }
        }

        val dominantSlot = pool.getSlotForRole(activeRole)
        val shouldPlay = !s.isUserPaused &&
            !(activeRole == SlotRole.CURRENT && s.isStoppedAtFinalFrame) &&
            dominantSlot.boundVideo != null

        if (shouldPlay) {
            dominantSlot.player.volume = 1f
            dominantSlot.player.playWhenReady = true
            if (dominantSlot.player.playbackState == Player.STATE_IDLE && dominantSlot.boundVideo != null) {
                dominantSlot.player.prepare()
            }
            dominantSlot.player.play()
        } else {
            dominantSlot.player.volume = 0f
            dominantSlot.player.playWhenReady = false
            dominantSlot.player.pause()
        }
    }

    private fun onSlotFirstFrameRendered(slotIndex: Int, videoId: String) {
        syncPoolSnapshots()
        val s = _state.value
        if (s.isPlayerOpen && s.currentVideo?.stableId == videoId && slotIndex == s.currentSlotIndex) {
            enforceDominantPlayback()
        }
    }

    private fun onSlotPlaybackEnded(slotIndex: Int, videoId: String) {
        val s = _state.value
        if (!s.isPlayerOpen) return
        val currentSlot = pool.getSlotForRole(SlotRole.CURRENT)
        if (slotIndex != currentSlot.slotIndex || s.currentVideo?.stableId != videoId) return

        when (s.endBehavior) {
            EndBehavior.LOOP -> {
                currentSlot.player.seekTo(0L)
                currentSlot.player.playWhenReady = true
                currentSlot.player.play()
            }
            EndBehavior.PLAY_NEXT -> {
                if (s.canSwipeAdjacent) {
                    _autoAdvanceEvent.tryEmit(1)
                } else {
                    // Single video collection wraparound restarts cleanly
                    currentSlot.player.seekTo(0L)
                    currentSlot.player.playWhenReady = true
                    currentSlot.player.play()
                }
            }
            EndBehavior.STOP -> {
                _state.update { it.copy(isStoppedAtFinalFrame = true) }
                currentSlot.player.playWhenReady = false
                currentSlot.player.pause()
            }
        }
    }

    private fun syncPoolSnapshots() {
        _state.update { it.copy(slotSnapshots = pool.snapshots.value) }
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            val pollIntervalMs = 100L
            while (isActive && _state.value.isPlayerOpen) {
                val s = _state.value
                val dominantSlot = pool.getSlotForRole(s.dominantRole)
                val player = dominantSlot.player
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.takeIf { it > 0L }
                    ?: dominantSlot.boundVideo?.durationMs
                    ?: 0L

                if (s.dominantRole == SlotRole.CURRENT) {
                    _state.update {
                        it.copy(
                            currentPositionMs = pos,
                            currentDurationMs = dur
                        )
                    }
                }

                // Section 9 Watch Count Rule:
                // Count a view after 3 seconds of actual playback, or 90% of a shorter video's
                // playback duration, accounting for selected playback speed. At most one count per viewing session.
                val currentVid = s.currentVideo
                if (s.dominantRole == SlotRole.CURRENT &&
                    player.isPlaying &&
                    !s.isUserPaused &&
                    currentVid != null &&
                    !countedInCurrentSession
                ) {
                    accumulatedEffectiveWatchMs += pollIntervalMs.toDouble()
                    val speed = s.playbackSpeed.coerceAtLeast(0.25f).toDouble()
                    val effectiveVideoDurationWallMs = if (dur > 0L) dur.toDouble() / speed else 3_000.0
                    val requiredWallMs = minOf(
                        WATCH_COUNT_REAL_PLAYBACK_MS.toDouble(),
                        (effectiveVideoDurationWallMs * 0.90).coerceAtLeast(300.0)
                    )
                    if (accumulatedEffectiveWatchMs >= requiredWallMs) {
                        countedInCurrentSession = true
                        onWatchCountQualified(currentVid.stableId)
                    }
                }

                delay(pollIntervalMs)
            }
        }
    }
}
