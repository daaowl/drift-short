package com.vanta.player.player

import android.content.Context
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.vanta.player.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SlotRole {
    PREV,
    CURRENT,
    NEXT
}

data class SlotSnapshot(
    val slotIndex: Int,
    val boundVideoId: String? = null,
    val isFirstFrameRendered: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

class BoundedPlayerPool(
    private val context: Context,
    private val onFirstFrameRenderedCallback: (slotIndex: Int, videoId: String) -> Unit,
    private val onPlaybackEndedCallback: (slotIndex: Int, videoId: String) -> Unit,
    private val onPlayerErrorCallback: (slotIndex: Int, videoId: String, message: String) -> Unit
) {
    companion object {
        const val POOL_SIZE = 3
    }

    inner class ManagedSlot(val slotIndex: Int) {
        val player: ExoPlayer = createConfiguredExoPlayer()
        var boundVideo: VideoItem? = null
        var attachedTextureView: TextureView? = null
        var isFirstFrameRendered: Boolean = false
        var hasError: Boolean = false

        private val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                val id = boundVideo?.stableId ?: return
                isFirstFrameRendered = true
                updateSlotSnapshot(slotIndex)
                onFirstFrameRenderedCallback(slotIndex, id)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val id = boundVideo?.stableId
                if (playbackState == Player.STATE_ENDED && id != null) {
                    onPlaybackEndedCallback(slotIndex, id)
                }
                updateSlotSnapshot(slotIndex)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateSlotSnapshot(slotIndex)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                updateSlotSnapshot(slotIndex)
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isFirstFrameRendered = true
                val id = boundVideo?.stableId ?: ""
                updateSlotSnapshot(slotIndex, error.localizedMessage ?: "Unplayable video")
                onPlayerErrorCallback(
                    slotIndex,
                    id,
                    error.localizedMessage ?: "Unable to play this video"
                )
            }
        }

        init {
            player.addListener(listener)
        }

        fun bindVideo(video: VideoItem?, playbackSpeed: Float, resetPosition: Boolean) {
            if (video == null) {
                boundVideo = null
                isFirstFrameRendered = false
                hasError = false
                player.playWhenReady = false
                player.volume = 0f
                player.stop()
                player.clearMediaItems()
                updateSlotSnapshot(slotIndex)
                return
            }

            if (boundVideo?.stableId == video.stableId &&
                player.playbackState != Player.STATE_IDLE &&
                !hasError
            ) {
                runCatching { player.setPlaybackParameters(PlaybackParameters(playbackSpeed)) }
                if (resetPosition) {
                    player.seekTo(0L)
                }
                updateSlotSnapshot(slotIndex)
                return
            }

            boundVideo = video
            isFirstFrameRendered = false
            hasError = false
            player.playWhenReady = false
            player.volume = 0f
            player.repeatMode = Player.REPEAT_MODE_OFF
            runCatching { player.setPlaybackParameters(PlaybackParameters(playbackSpeed)) }
            player.setMediaItem(MediaItem.fromUri(video.uri))
            player.prepare()
            // Section 6: Playback always begins at the start (0ms), even if custom grid thumbnail is later
            player.seekTo(0L)
            updateSlotSnapshot(slotIndex)
        }

        fun attachTextureView(textureView: TextureView) {
            if (attachedTextureView === textureView) return
            attachedTextureView = textureView
            player.setVideoTextureView(textureView)
        }

        fun release() {
            player.removeListener(listener)
            attachedTextureView?.let { player.clearVideoTextureView(it) }
            attachedTextureView = null
            player.release()
        }
    }

    private val slots: List<ManagedSlot> = List(POOL_SIZE) { index -> ManagedSlot(index) }

    private var roleToSlotIndex = mutableMapOf(
        SlotRole.PREV to 0,
        SlotRole.CURRENT to 1,
        SlotRole.NEXT to 2
    )

    private val _snapshots = MutableStateFlow<Map<Int, SlotSnapshot>>(
        (0 until POOL_SIZE).associateWith { SlotSnapshot(slotIndex = it) }
    )
    val snapshots: StateFlow<Map<Int, SlotSnapshot>> = _snapshots.asStateFlow()

    fun getSlotForRole(role: SlotRole): ManagedSlot {
        val index = roleToSlotIndex[role] ?: 1
        return slots[index]
    }

    fun getSlotByIndex(slotIndex: Int): ManagedSlot = slots[slotIndex.coerceIn(0, POOL_SIZE - 1)]

    fun rotateForward() {
        val oldPrev = roleToSlotIndex.getValue(SlotRole.PREV)
        val oldCurrent = roleToSlotIndex.getValue(SlotRole.CURRENT)
        val oldNext = roleToSlotIndex.getValue(SlotRole.NEXT)

        roleToSlotIndex[SlotRole.PREV] = oldCurrent
        roleToSlotIndex[SlotRole.CURRENT] = oldNext
        roleToSlotIndex[SlotRole.NEXT] = oldPrev
    }

    fun rotateBackward() {
        val oldPrev = roleToSlotIndex.getValue(SlotRole.PREV)
        val oldCurrent = roleToSlotIndex.getValue(SlotRole.CURRENT)
        val oldNext = roleToSlotIndex.getValue(SlotRole.NEXT)

        roleToSlotIndex[SlotRole.NEXT] = oldCurrent
        roleToSlotIndex[SlotRole.CURRENT] = oldPrev
        roleToSlotIndex[SlotRole.PREV] = oldNext
    }

    fun pauseAndMuteAll() {
        slots.forEach { slot ->
            slot.player.volume = 0f
            slot.player.playWhenReady = false
            slot.player.pause()
        }
    }

    fun releaseAll() {
        slots.forEach { it.release() }
    }

    private fun updateSlotSnapshot(slotIndex: Int, errorMessage: String? = null) {
        val slot = slots[slotIndex]
        val p = slot.player
        _snapshots.update { current ->
            current + (slotIndex to SlotSnapshot(
                slotIndex = slotIndex,
                boundVideoId = slot.boundVideo?.stableId,
                isFirstFrameRendered = slot.isFirstFrameRendered,
                isPlaying = p.isPlaying,
                playbackState = p.playbackState,
                videoWidth = p.videoSize.width,
                videoHeight = p.videoSize.height,
                hasError = slot.hasError,
                errorMessage = errorMessage
            ))
        }
    }

    @OptIn(UnstableApi::class)
    private fun createConfiguredExoPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_500, 6_000, 200, 500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
