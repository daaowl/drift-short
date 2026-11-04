package com.localshorts.app.player

import android.content.Context
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.localshorts.app.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SlotRole {
    PREV,
    CURRENT,
    NEXT
}

const val PLAYER_POOL_SIZE = 3

/**
 * Fixed mapping from a playlist index to a pool slot. Video index i always lives in slot (i mod 3),
 * so the three videos {cur-1, cur, cur+1} always occupy three distinct slots and moving by one
 * never needs a role rotation: the TextureView of a slot is never re-attached to another player.
 */
fun slotIndexFor(videoIndex: Int): Int =
    ((videoIndex % PLAYER_POOL_SIZE) + PLAYER_POOL_SIZE) % PLAYER_POOL_SIZE

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

/**
 * Strictly bounded pool of 3 ExoPlayers. Never allocates more, preventing hardware decoder
 * exhaustion while keeping previous/current/next prepared.
 */
class BoundedPlayerPool(
    private val context: Context,
    private val onFirstFrameRenderedCallback: (slotIndex: Int, videoId: String) -> Unit,
    private val onPlaybackEndedCallback: (slotIndex: Int, videoId: String) -> Unit,
    private val onPlayerErrorCallback: (slotIndex: Int, videoId: String, message: String) -> Unit
) {

    inner class ManagedSlot(val slotIndex: Int) {
        val player: ExoPlayer = createConfiguredExoPlayer()
        var boundVideo: VideoItem? = null
        var attachedTextureView: TextureView? = null
        var isFirstFrameRendered: Boolean = false
        var hasError: Boolean = false
        private var lastErrorMessage: String? = null

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

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                updateSlotSnapshot(slotIndex)
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                // Unblock any cover on a broken file; the error pill is shown instead.
                isFirstFrameRendered = true
                lastErrorMessage = error.localizedMessage ?: "Unsupported video format"
                val id = boundVideo?.stableId ?: ""
                updateSlotSnapshot(slotIndex)
                onPlayerErrorCallback(slotIndex, id, lastErrorMessage ?: "")
            }
        }

        init {
            player.addListener(listener)
        }

        fun errorText(): String? = lastErrorMessage

        fun bindVideo(video: VideoItem?, playbackSpeed: Float, resetPosition: Boolean) {
            if (video == null) {
                if (boundVideo == null) return
                boundVideo = null
                isFirstFrameRendered = false
                hasError = false
                lastErrorMessage = null
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
                boundVideo = video
                player.setPlaybackParameters(PlaybackParameters(playbackSpeed))
                if (resetPosition) {
                    player.seekTo(0L)
                }
                updateSlotSnapshot(slotIndex)
                return
            }

            boundVideo = video
            isFirstFrameRendered = false
            hasError = false
            lastErrorMessage = null
            player.playWhenReady = false
            player.volume = 0f
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setPlaybackParameters(PlaybackParameters(playbackSpeed))
            player.setMediaItem(MediaItem.fromUri(video.uri))
            player.prepare()
            player.seekTo(0L)
            updateSlotSnapshot(slotIndex)
        }

        fun attachTextureView(textureView: TextureView) {
            if (attachedTextureView === textureView) return
            attachedTextureView = textureView
            player.setVideoTextureView(textureView)
        }

        fun detachTextureView(textureView: TextureView) {
            if (attachedTextureView === textureView) {
                player.clearVideoTextureView(textureView)
                attachedTextureView = null
            }
        }

        fun release() {
            player.removeListener(listener)
            attachedTextureView?.let { player.clearVideoTextureView(it) }
            attachedTextureView = null
            player.release()
        }
    }

    private val _snapshots = MutableStateFlow<Map<Int, SlotSnapshot>>(
        (0 until PLAYER_POOL_SIZE).associateWith { SlotSnapshot(slotIndex = it) }
    )
    val snapshots: StateFlow<Map<Int, SlotSnapshot>> = _snapshots.asStateFlow()

    private val slots: List<ManagedSlot> = List(PLAYER_POOL_SIZE) { index -> ManagedSlot(index) }

    fun getSlotByIndex(slotIndex: Int): ManagedSlot =
        slots[slotIndex.coerceIn(0, PLAYER_POOL_SIZE - 1)]

    fun getSlotForVideoIndex(videoIndex: Int): ManagedSlot = slots[slotIndexFor(videoIndex)]

    fun pauseAndMuteAll() {
        slots.forEach { slot ->
            slot.player.volume = 0f
            slot.player.playWhenReady = false
            slot.player.pause()
        }
    }

    fun unbindAll() {
        slots.forEach { it.bindVideo(null, 1f, false) }
    }

    fun releaseAll() {
        slots.forEach { it.release() }
    }

    private fun updateSlotSnapshot(slotIndex: Int) {
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
                errorMessage = slot.errorText()
            ))
        }
    }

    @OptIn(UnstableApi::class)
    private fun createConfiguredExoPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1_500,
                /* maxBufferMs = */ 6_000,
                /* bufferForPlaybackMs = */ 200,
                /* bufferForPlaybackAfterRebufferMs = */ 500
            )
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
