package com.localshorts.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.localshorts.app.data.EndBehavior
import com.localshorts.app.data.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns every ExoPlayer in the feed. A fixed pool of three players covers the window
 * (previous, current, next); page i always uses player i % 3, so adjacent pages never share one.
 * Only the active page's player has audio and playWhenReady.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackCoordinator(
    context: Context,
    private val videos: List<VideoItem>,
    scope: CoroutineScope,
    private val onWatched: (String) -> Unit,
) {
    private val players: List<ExoPlayer> = List(3) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = false
        }
    }

    var active = -1
        private set

    /** What the user wants: false after a tap-to-pause or when Stop end behavior kicks in. */
    val playing = MutableStateFlow(true)
    val progress = MutableStateFlow(0f)
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    val failed = MutableStateFlow<Set<String>>(emptySet())

    var onEndedNext: (() -> Unit)? = null
    private var counted = false
    private var backgrounded = false

    var endBehavior: EndBehavior = EndBehavior.LOOP
        set(value) {
            field = value
            activePlayer()?.repeatMode = if (value == EndBehavior.LOOP) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }

    var speed: Float = 1f
        set(value) {
            field = value
            players.forEach { it.playbackParameters = PlaybackParameters(value) }
        }

    init {
        players.forEach { p ->
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (p !== activePlayer() || state != Player.STATE_ENDED) return
                    when (endBehavior) {
                        EndBehavior.NEXT -> onEndedNext?.invoke()
                        EndBehavior.STOP -> playing.value = false // the player keeps the last frame on screen
                        EndBehavior.LOOP -> Unit
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (p === activePlayer() && reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) counted = false
                }

                override fun onPlayerError(error: PlaybackException) {
                    p.currentMediaItem?.mediaId?.let { failed.value = failed.value + it }
                }
            })
        }
        scope.launch {
            while (isActive) {
                tick()
                delay(100)
            }
        }
    }

    fun playerFor(page: Int): ExoPlayer = players[page % 3]

    private fun activePlayer(): ExoPlayer? = if (active in videos.indices) playerFor(active) else null

    /** Called whenever the dominant page changes, including mid-drag. */
    fun setActive(page: Int) {
        if (page == active || page !in videos.indices) return
        val old = active
        active = page
        // Load the window: previous, current, next
        for (i in page - 1..page + 1) {
            if (i !in videos.indices) continue
            val p = playerFor(i)
            val v = videos[i]
            if (p.currentMediaItem?.mediaId != v.id) {
                p.setMediaItem(MediaItem.Builder().setUri(v.uri).setMediaId(v.id).build())
                p.prepare()
            }
        }
        // Silence everything except the new active page
        players.forEach { p ->
            if (p !== playerFor(page)) {
                p.playWhenReady = false
                p.volume = 0f
            }
        }
        // The page we left restarts from 0:00 if the user comes back to it
        if (old in videos.indices && old != page) {
            val p = playerFor(old)
            if (p !== playerFor(page) && p.currentMediaItem?.mediaId == videos[old].id) p.seekTo(0)
        }
        val p = playerFor(page)
        p.repeatMode = if (endBehavior == EndBehavior.LOOP) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.playbackParameters = PlaybackParameters(speed)
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        p.volume = 1f
        p.playWhenReady = !backgrounded
        playing.value = true
        counted = false
        progress.value = 0f
    }

    fun toggle() {
        val p = activePlayer() ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
            p.playWhenReady = true
            playing.value = true
            return
        }
        val next = !playing.value
        playing.value = next
        p.playWhenReady = next
    }

    fun seek(fraction: Float, exact: Boolean) {
        val p = activePlayer() ?: return
        val d = p.duration
        if (d <= 0) return
        p.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
        p.seekTo((fraction.coerceIn(0f, 1f) * d).toLong())
        progress.value = fraction
    }

    fun onBackground() {
        backgrounded = true
        players.forEach { it.playWhenReady = false }
    }

    fun onForeground() {
        backgrounded = false
        if (playing.value) activePlayer()?.playWhenReady = true
    }

    private fun tick() {
        val p = activePlayer() ?: return
        val d = p.duration
        if (d <= 0) return
        val pos = p.currentPosition
        durationMs.value = d
        positionMs.value = pos
        progress.value = (pos.toFloat() / d).coerceIn(0f, 1f)
        if (!counted && p.isPlaying && pos >= minOf(3000L, d / 2)) {
            counted = true
            onWatched(videos[active].id)
        }
    }

    fun release() {
        players.forEach { it.release() }
    }
}
