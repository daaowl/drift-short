package com.localshorts.app.player

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.localshorts.app.model.EndBehavior
import com.localshorts.app.model.VideoItem
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
    val slotSnapshots: Map<Int, SlotSnapshot> = emptyMap()
) {
    val currentVideo: VideoItem?
        get() = playlist.getOrNull(currentIndex)

    val prevVideo: VideoItem?
        get() = playlist.getOrNull(currentIndex - 1)

    val nextVideo: VideoItem?
        get() = playlist.getOrNull(currentIndex + 1)

    val currentSlotIndex: Int
        get() = slotIndexFor(currentIndex)
}

/** Position/duration kept apart from [PlaybackUiState] so 10 Hz ticks only recompose the seek bar. */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Central playback coordinator owning:
 * - Active playlist and current index
 * - Adjacent video pre-warming (prev and next)
 * - Partial-swipe dominant-video switching with 53% / 47% hysteresis
 * - Strict single-player audio ownership
 * - Watch-count rule: once per viewing, after 2.5 s of continuous active playback of the current video
 */
class PlaybackCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val onWatchCountQualified: (stableVideoId: String) -> Unit
) {
    companion object {
        const val DOMINANCE_SWITCH_TO_INCOMING = 0.53f
        const val DOMINANCE_RETURN_TO_CURRENT = 0.47f
        private const val WATCH_COUNT_THRESHOLD_MS = 2_500L
    }

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

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
        onPlayerErrorCallback = { _, _, _ -> }
    )

    private var progressPollingJob: Job? = null
    private var accumulatedWatchMs: Long = 0L
    private var countedInCurrentSessionVideoId: String? = null
    private var isAppInForeground: Boolean = true

    init {
        scope.launch {
            pool.snapshots.collect { snapshots ->
                _state.update { it.copy(slotSnapshots = snapshots) }
            }
        }
    }

    fun getManagedSlot(slotIndex: Int): BoundedPlayerPool.ManagedSlot = pool.getSlotByIndex(slotIndex)

    private fun slotForRole(role: SlotRole): BoundedPlayerPool.ManagedSlot {
        val idx = _state.value.currentIndex + when (role) {
            SlotRole.PREV -> -1
            SlotRole.CURRENT -> 0
            SlotRole.NEXT -> 1
        }
        return pool.getSlotForVideoIndex(idx)
    }

    fun openPlaylistAt(
        playlist: List<VideoItem>,
        startIndex: Int,
        defaultSpeed: Float,
        endBehavior: EndBehavior
    ) {
        if (playlist.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, playlist.lastIndex)

        accumulatedWatchMs = 0L
        countedInCurrentSessionVideoId = null

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
                isStoppedAtFinalFrame = false
            )
        }
        _progress.value = PlaybackProgress(0L, playlist[safeIndex].durationMs)

        bindNeighbourhood(resetCurrentPosition = true)
        enforceDominantPlayback()
        startProgressPolling()
    }

    /** Updates favourite/watch metadata inside the open playlist without touching any player. */
    fun updatePlaylistMetadata(updatedVideosById: Map<String, VideoItem>) {
        val currentState = _state.value
        if (currentState.playlist.isEmpty()) return
        var changed = false
        val newList = currentState.playlist.map { item ->
            val updated = updatedVideosById[item.stableId]
            if (updated != null && updated != item) {
                changed = true
                updated
            } else {
                item
            }
        }
        if (changed) {
            _state.update { it.copy(playlist = newList) }
        }
    }

    /**
     * Called on every pointer move while the finger is down.
     * dragFraction > 0: dragging up (NEXT entering). dragFraction < 0: dragging down (PREV entering).
     * Magnitude is the fraction of the viewport occupied by the incoming video.
     */
    fun onContinuousDrag(dragFraction: Float) {
        val s = _state.value
        if (!s.isPlayerOpen) return

        val clamped = dragFraction.coerceIn(-1f, 1f)
        val incomingShare = abs(clamped)
        val hasNext = s.currentIndex < s.playlist.lastIndex
        val hasPrev = s.currentIndex > 0

        val nextDominant = when (s.dominantRole) {
            SlotRole.CURRENT -> when {
                clamped > 0f && hasNext && incomingShare >= DOMINANCE_SWITCH_TO_INCOMING -> SlotRole.NEXT
                clamped < 0f && hasPrev && incomingShare >= DOMINANCE_SWITCH_TO_INCOMING -> SlotRole.PREV
                else -> SlotRole.CURRENT
            }
            SlotRole.NEXT -> {
                if (clamped <= 0f || incomingShare <= DOMINANCE_RETURN_TO_CURRENT) SlotRole.CURRENT
                else SlotRole.NEXT
            }
            SlotRole.PREV -> {
                if (clamped >= 0f || incomingShare <= DOMINANCE_RETURN_TO_CURRENT) SlotRole.CURRENT
                else SlotRole.PREV
            }
        }

        val roleChanged = nextDominant != s.dominantRole
        if (roleChanged || s.dragFraction != clamped) {
            _state.update { it.copy(dragFraction = clamped, dominantRole = nextDominant) }
        }
        if (roleChanged) {
            enforceDominantPlayback()
        }
    }

    /**
     * Called when the settle animation finishes. indexDelta: +1 next, -1 previous, 0 stayed.
     */
    fun onSwipeSettled(indexDelta: Int) {
        val s = _state.value
        if (!s.isPlayerOpen) return

        val target = (s.currentIndex + indexDelta).coerceIn(0, s.playlist.lastIndex)

        if (target == s.currentIndex) {
            _state.update { it.copy(dragFraction = 0f, dominantRole = SlotRole.CURRENT) }
            // Neighbour that was briefly dominant must restart from its first frame next time.
            pool.getSlotForVideoIndex(s.currentIndex - 1).takeIf { s.currentIndex > 0 }
                ?.player?.seekTo(0L)
            pool.getSlotForVideoIndex(s.currentIndex + 1).takeIf { s.currentIndex < s.playlist.lastIndex }
                ?.player?.seekTo(0L)
            enforceDominantPlayback()
            return
        }

        accumulatedWatchMs = 0L
        countedInCurrentSessionVideoId = null

        _state.update {
            it.copy(
                currentIndex = target,
                dragFraction = 0f,
                dominantRole = SlotRole.CURRENT,
                isUserPaused = false,
                isStoppedAtFinalFrame = false
            )
        }
        _progress.value = PlaybackProgress(
            positionMs = pool.getSlotForVideoIndex(target).player.currentPosition.coerceAtLeast(0L),
            durationMs = _state.value.playlist.getOrNull(target)?.durationMs ?: 0L
        )

        // The new current video is already bound (it was the incoming slot). Only the vacated
        // neighbour slots get rebound / reset.
        bindNeighbourhood(resetCurrentPosition = false)
        enforceDominantPlayback()
    }

    fun togglePlayPause() {
        val s = _state.value
        if (!s.isPlayerOpen) return

        val currentSlot = slotForRole(SlotRole.CURRENT)
        if (s.isStoppedAtFinalFrame || currentSlot.player.playbackState == Player.STATE_ENDED) {
            currentSlot.player.seekTo(0L)
            _state.update { it.copy(isUserPaused = false, isStoppedAtFinalFrame = false) }
            enforceDominantPlayback()
            return
        }

        _state.update { it.copy(isUserPaused = !s.isUserPaused) }
        enforceDominantPlayback()
    }

    fun seekTo(positionMs: Long) {
        val currentSlot = slotForRole(SlotRole.CURRENT)
        val duration = currentSlot.player.duration.takeIf { it > 0 }
            ?: _progress.value.durationMs
        val target = positionMs.coerceIn(0L, duration.coerceAtLeast(0L))
        currentSlot.player.seekTo(target)
        _progress.update { it.copy(positionMs = target) }
        if (_state.value.isStoppedAtFinalFrame) {
            _state.update { it.copy(isStoppedAtFinalFrame = false) }
        }
        if (!_state.value.isUserPaused) {
            enforceDominantPlayback()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 2.25f)
        _state.update { it.copy(playbackSpeed = safeSpeed) }
        for (offset in -1..1) {
            val idx = _state.value.currentIndex + offset
            if (idx in _state.value.playlist.indices) {
                pool.getSlotForVideoIndex(idx).player.setPlaybackParameters(PlaybackParameters(safeSpeed))
            }
        }
    }

    fun setEndBehavior(behavior: EndBehavior) {
        _state.update { it.copy(endBehavior = behavior) }
    }

    /** Snapshot of the frame currently on screen, used as the start frame of the closing animation. */
    fun captureCurrentFrame(): Bitmap? {
        val slot = slotForRole(SlotRole.CURRENT)
        if (!slot.isFirstFrameRendered || slot.hasError) return null
        val tv = slot.attachedTextureView ?: return null
        if (!tv.isAvailable || tv.width <= 0 || tv.height <= 0) return null
        val scale = 720f / maxOf(tv.width, tv.height).toFloat()
        return runCatching {
            tv.getBitmap(
                (tv.width * scale).toInt().coerceAtLeast(1),
                (tv.height * scale).toInt().coerceAtLeast(1)
            )
        }.getOrNull()
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
        pool.unbindAll()
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

    private fun bindNeighbourhood(resetCurrentPosition: Boolean) {
        val s = _state.value
        val speed = s.playbackSpeed
        pool.getSlotForVideoIndex(s.currentIndex + 1)
            .bindVideo(s.nextVideo, speed, resetPosition = true)
        pool.getSlotForVideoIndex(s.currentIndex - 1)
            .bindVideo(s.prevVideo, speed, resetPosition = true)
        pool.getSlotForVideoIndex(s.currentIndex)
            .bindVideo(s.currentVideo, speed, resetCurrentPosition)
    }

    /**
     * At most ONE player plays and produces audio at any instant. Non-dominant players are muted
     * and paused before the dominant player is unmuted and started.
     */
    fun enforceDominantPlayback() {
        val s = _state.value
        if (!s.isPlayerOpen || !isAppInForeground) {
            pool.pauseAndMuteAll()
            return
        }

        val activeRole = s.dominantRole
        SlotRole.entries.forEach { role ->
            if (role != activeRole) {
                val slot = slotForRole(role)
                slot.player.volume = 0f
                slot.player.playWhenReady = false
                slot.player.pause()
            }
        }

        val dominantSlot = slotForRole(activeRole)
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
        val s = _state.value
        if (s.isPlayerOpen && s.currentVideo?.stableId == videoId && slotIndex == s.currentSlotIndex) {
            enforceDominantPlayback()
        }
    }

    private fun onSlotPlaybackEnded(slotIndex: Int, videoId: String) {
        val s = _state.value
        if (!s.isPlayerOpen) return
        val currentSlot = slotForRole(SlotRole.CURRENT)
        if (slotIndex != currentSlot.slotIndex || s.currentVideo?.stableId != videoId) return
        if (s.dominantRole != SlotRole.CURRENT) return

        when (s.endBehavior) {
            EndBehavior.LOOP -> {
                currentSlot.player.seekTo(0L)
                currentSlot.player.playWhenReady = true
                currentSlot.player.play()
            }
            EndBehavior.PLAY_NEXT -> {
                if (s.currentIndex < s.playlist.lastIndex) {
                    _autoAdvanceEvent.tryEmit(1)
                } else {
                    _state.update { it.copy(isStoppedAtFinalFrame = true) }
                    currentSlot.player.playWhenReady = false
                }
            }
            EndBehavior.STOP -> {
                _state.update { it.copy(isStoppedAtFinalFrame = true) }
                currentSlot.player.playWhenReady = false
                currentSlot.player.pause()
            }
        }
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            val pollIntervalMs = 100L
            while (isActive && _state.value.isPlayerOpen) {
                val s = _state.value
                val currentSlot = slotForRole(SlotRole.CURRENT)
                val player = currentSlot.player
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.takeIf { it > 0L }
                    ?: currentSlot.boundVideo?.durationMs
                    ?: 0L

                if (s.dominantRole == SlotRole.CURRENT) {
                    val next = PlaybackProgress(pos, dur)
                    if (next != _progress.value) _progress.value = next
                }

                // Watch count rule: current video dominant, actually playing, 2.5 s accumulated
                // (60% of very short clips), counted once per viewing.
                val currentVid = s.currentVideo
                if (s.dominantRole == SlotRole.CURRENT &&
                    player.isPlaying &&
                    currentVid != null &&
                    countedInCurrentSessionVideoId != currentVid.stableId
                ) {
                    accumulatedWatchMs += pollIntervalMs
                    val requiredMs = if (dur in 1L..3_500L) {
                        (dur * 0.6f).toLong().coerceAtLeast(600L)
                    } else {
                        WATCH_COUNT_THRESHOLD_MS
                    }
                    if (accumulatedWatchMs >= requiredMs) {
                        countedInCurrentSessionVideoId = currentVid.stableId
                        onWatchCountQualified(currentVid.stableId)
                    }
                }

                delay(pollIntervalMs)
            }
        }
    }
}
