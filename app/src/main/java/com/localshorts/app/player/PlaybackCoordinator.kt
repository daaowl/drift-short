package com.localshorts.app.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
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
 * Owns every ExoPlayer in the feed.
 *
 * A fixed pool of three players covers the window around the dominant page (previous, dominant,
 * next). Page i always uses player i % 3, so neighbours never share a player, and adjacent pages
 * are prepared and paused on their first frame, so a partial swipe shows real video, not black.
 *
 * Exactly one player ever has volume and playWhenReady: the dominant page's.
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
    private var released = false

    var active = -1
        private set

    /** User intent: false after tap-to-pause, while editing a thumbnail, or after Stop at the end. */
    val playing = MutableStateFlow(true)
    val progress = MutableStateFlow(0f)
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    val failed = MutableStateFlow<Set<String>>(emptySet())

    var onEndedNext: (() -> Unit)? = null
    private var backgrounded = false
    private var holdPaused = false

    // Watch counting: one count per viewing session, after 3 s of real playback
    // (or 90% of a shorter clip). Pausing, seeking and loops don't add time or counts.
    private var sessionPlayedMs = 0L
    private var sessionCounted = false
    private var lastTick = 0L

    var endBehavior: EndBehavior = EndBehavior.LOOP
        set(value) {
            field = value
            activePlayer()?.repeatMode = repeatFor(value)
        }

    var speed: Float = 1f
        set(value) {
            field = value
            players.forEach { p ->
                try {
                    p.playbackParameters = PlaybackParameters(value)
                } catch (_: Exception) {
                    // Some decoders reject unusual speeds; keep playing at the previous speed
                }
            }
        }

    init {
        players.forEach { p ->
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (released || p !== activePlayer() || state != Player.STATE_ENDED) return
                    when (endBehavior) {
                        EndBehavior.NEXT -> onEndedNext?.invoke() ?: run { playing.value = false }
                        EndBehavior.STOP -> playing.value = false // the last frame stays on screen
                        EndBehavior.LOOP -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    p.currentMediaItem?.mediaId?.let { failed.value = failed.value + it }
                }
            })
        }
        scope.launch {
            lastTick = SystemClock.elapsedRealtime()
            while (isActive && !released) {
                tick()
                delay(100)
            }
        }
    }

    private fun repeatFor(e: EndBehavior) = if (e == EndBehavior.LOOP) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

    fun playerFor(page: Int): ExoPlayer = players[page % 3]

    fun activeVideo(): VideoItem? = videos.getOrNull(active)

    private fun activePlayer(): ExoPlayer? =
        if (!released && active in videos.indices) playerFor(active) else null

    /** Makes [page] the only playing, audible page. Called mid-drag whenever dominance changes. */
    fun setActive(page: Int) {
        if (released || page == active || page !in videos.indices) return
        active = page
        for (i in page - 1..page + 1) {
            if (i !in videos.indices) continue
            val p = playerFor(i)
            val v = videos[i]
            if (p.currentMediaItem?.mediaId != v.id) {
                p.setMediaItem(MediaItem.Builder().setUri(v.uri).setMediaId(v.id).build())
                p.playWhenReady = false
                p.prepare()
            }
        }
        val current = playerFor(page)
        players.forEach { p ->
            if (p !== current) {
                p.playWhenReady = false
                p.volume = 0f
            }
        }
        current.repeatMode = repeatFor(endBehavior)
        try {
            current.playbackParameters = PlaybackParameters(speed)
        } catch (_: Exception) {
        }
        if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
        current.volume = 1f
        playing.value = true
        holdPaused = false
        current.playWhenReady = !backgrounded
        sessionPlayedMs = 0L
        sessionCounted = false
        progress.value = if (current.duration > 0) current.currentPosition.toFloat() / current.duration else 0f
    }

    /** After a swipe settles: pages that are no longer playing start from 0:00 next time. */
    fun onSettled(page: Int) {
        if (released) return
        for (i in page - 1..page + 1) {
            if (i == page || i !in videos.indices) continue
            val p = playerFor(i)
            if (p.currentMediaItem?.mediaId == videos[i].id && p.currentPosition > 0) p.seekTo(0)
        }
    }

    fun toggle() {
        val p = activePlayer() ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
            playing.value = true
            p.playWhenReady = true
            return
        }
        if (playing.value) pause() else resume()
    }

    fun pause() {
        playing.value = false
        activePlayer()?.playWhenReady = false
    }

    fun resume() {
        val p = activePlayer() ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        playing.value = true
        if (!backgrounded && !holdPaused) p.playWhenReady = true
    }

    /** Freezes playback without changing the user's play/pause intent (e.g. during close). */
    fun hold() {
        holdPaused = true
        players.forEach { it.playWhenReady = false }
    }

    fun seek(fraction: Float, exact: Boolean) {
        val p = activePlayer() ?: return
        val d = p.duration
        if (d <= 0) return
        p.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
        val ms = (fraction.coerceIn(0f, 1f) * d).toLong()
        p.seekTo(ms)
        progress.value = fraction.coerceIn(0f, 1f)
        positionMs.value = ms
    }

    fun seekToMs(ms: Long) {
        val p = activePlayer() ?: return
        p.setSeekParameters(SeekParameters.EXACT)
        p.seekTo(ms.coerceAtLeast(0))
    }

    fun positionNow(): Long = activePlayer()?.currentPosition ?: 0L

    fun onBackground() {
        backgrounded = true
        players.forEach { it.playWhenReady = false }
    }

    fun onForeground() {
        backgrounded = false
        if (playing.value && !holdPaused) activePlayer()?.playWhenReady = true
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val dt = now - lastTick
        lastTick = now
        val p = activePlayer() ?: return
        val d = p.duration
        if (d == C.TIME_UNSET || d <= 0) return
        val pos = p.currentPosition
        durationMs.value = d
        positionMs.value = pos
        progress.value = (pos.toFloat() / d).coerceIn(0f, 1f)
        if (p.isPlaying) {
            sessionPlayedMs += dt.coerceIn(0L, 500L)
            val need = minOf(3000L, (d * 0.9).toLong())
            if (!sessionCounted && sessionPlayedMs >= need) {
                sessionCounted = true
                onWatched(videos[active].id)
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        players.forEach { it.release() }
    }
}
