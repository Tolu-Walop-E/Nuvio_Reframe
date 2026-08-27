package com.nuvio.tv.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import com.nuvio.tv.core.player.LocalTrailerPlayerPool
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.data.trailer.YoutubeChunkedDataSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import com.nuvio.tv.R
import kotlinx.coroutines.delay

private const val TRAILER_LOG = "NetflixTrailer"

/** How long we wait for a real video frame before rebuilding the video surface. */
private const val FIRST_FRAME_TIMEOUT_MS = 700L
private const val SURFACE_RECOVERY_ATTEMPTS = 3
/** Ramp trailer audio after the first video frame so playback does not slam in. */
private const val AUDIO_FADE_IN_MS = 550L

/**
 * Stable identity for a trailer source.
 *
 * googlevideo serves the same media from different CDN hosts (`rr1---…` vs
 * `rr5---…`) with volatile query params, so the URL string changes while the
 * media does not. Keying playback state on the raw URL re-prepared the shared
 * player and reset the first-frame gate mid-playback, which dropped the card
 * back to its poster while audio kept running.
 */
private fun trailerMediaIdentity(videoUrl: String?, audioUrl: String?): String? {
    if (videoUrl.isNullOrBlank()) return null
    return "${stableStreamId(videoUrl)}|${audioUrl?.let { stableStreamId(it) }.orEmpty()}"
}

private fun stableStreamId(url: String): String = runCatching {
    val uri = Uri.parse(url)
    val id = uri.getQueryParameter("id")
    val itag = uri.getQueryParameter("itag")
    if (id != null || itag != null) {
        "yt:$id:$itag"
    } else {
        "${uri.host.orEmpty()}${uri.path.orEmpty()}"
    }
}.getOrDefault(url)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TrailerPlayer(
    trailerUrl: String?,
    trailerAudioUrl: String? = null,
    isPlaying: Boolean,
    isPaused: Boolean = false,
    onEnded: () -> Unit,
    onFirstFrameRendered: () -> Unit = {},
    muted: Boolean = false,
    seekRequestToken: Int = 0,
    seekDeltaMs: Long = 0L,
    onProgressChanged: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onRemoteKey: (keyCode: Int, action: Int, repeatCount: Int) -> Boolean = { _, _, _ -> false },
    /**
     * When false (default), the embedded PlayerView never takes focus so D-pad
     * stays on the surrounding Compose surface (hero / card previews).
     * Enable only for full-screen / detail trailers that handle remote keys.
     */
    playerFocusable: Boolean = false,
    cropToFill: Boolean = false,
    overscanZoom: Float = 1f,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(animationSpec = tween(800)),
    exit: ExitTransition = fadeOut(animationSpec = tween(500)),
    trailerPlayerPool: TrailerPlayerPool? = null
) {
    // enter/exit kept for call-site API compatibility; video fade uses alpha below
    // so PlayerView can attach before prepare (avoids audio-with-no-picture).
    @Suppress("UNUSED_PARAMETER")
    val unusedEnter = enter
    @Suppress("UNUSED_PARAMETER")
    val unusedExit = exit

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activityLifecycleOwner = remember(context) { context as? androidx.lifecycle.LifecycleOwner ?: lifecycleOwner }
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentTrailerUrl by rememberUpdatedState(trailerUrl)
    val currentTrailerAudioUrl by rememberUpdatedState(trailerAudioUrl)
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnFirstFrameRendered by rememberUpdatedState(onFirstFrameRendered)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnRemoteKey by rememberUpdatedState(onRemoteKey)
    val currentMuted by rememberUpdatedState(muted)
    val zoomScale = if (cropToFill) overscanZoom.coerceAtLeast(1f) else 1f
    val mediaIdentity = remember(trailerUrl, trailerAudioUrl) {
        trailerMediaIdentity(trailerUrl, trailerAudioUrl)
    }
    var hasRenderedFirstFrame by remember(mediaIdentity) { mutableStateOf(false) }
    var surfaceAttached by remember(mediaIdentity) { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val playerAlphaState = animateFloatAsState(
        targetValue = if (isPlaying && hasRenderedFirstFrame) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "trailerFirstFrameAlpha"
    )

    val resolvedPool = trailerPlayerPool ?: LocalTrailerPlayerPool.current

    // Exclusive owner token so a previous TrailerPlayer cannot stop/clear the shared
    // ExoPlayer after focus has already moved to another card/hero surface.
    val poolOwner = remember { Any() }

    // obtain(), not acquire(): composition must not claim the shared player, or a
    // stale card recomposing steals it from whichever card is actually focused.
    val trailerPlayer = remember(mediaIdentity, resolvedPool) {
        if (mediaIdentity != null) {
            resolvedPool?.obtain()
        } else {
            null
        }
    }

    /** Point the shared player's video output back at this card's TextureView. */
    fun reattachSurface(): Boolean {
        val view = playerView ?: return false
        val player = trailerPlayer ?: return false
        if (resolvedPool?.isOwnedBy(poolOwner) == false) return false
        // clearVideoTextureView is a no-op unless this view owns the output, so
        // this cannot steal the surface from another card.
        return runCatching {
            view.player = null
            view.player = player
        }.isSuccess
    }

    fun markFirstFrame() {
        if (!hasRenderedFirstFrame) {
            hasRenderedFirstFrame = true
            android.util.Log.i(TRAILER_LOG, "first-frame owner=${poolOwner.hashCode()}")
            trailerPlayer?.let { player ->
                if (resolvedPool?.isOwnedBy(poolOwner) != false) {
                    player.volume = 0f
                }
            }
            currentOnFirstFrameRendered()
        }
    }

    fun ExoPlayer.bindTrailerMedia(videoUrl: String, audioUrl: String?) {
        if (!audioUrl.isNullOrBlank()) {
            val mediaSourceFactory = DefaultMediaSourceFactory(YoutubeChunkedDataSourceFactory())
            val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoUrl))
            val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl))
            setMediaSource(MergingMediaSource(videoSource, audioSource))
        } else {
            setMediaItem(MediaItem.fromUri(videoUrl))
        }
    }

    LaunchedEffect(trailerPlayer, muted, hasRenderedFirstFrame, isPlaying) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (resolvedPool?.isOwnedBy(poolOwner) == false) return@LaunchedEffect
        if (muted || !hasRenderedFirstFrame || !isPlaying) {
            player.volume = 0f
            return@LaunchedEffect
        }
        val steps = 16
        val stepMs = AUDIO_FADE_IN_MS / steps
        for (i in 1..steps) {
            if (currentMuted || !currentIsPlaying) {
                player.volume = 0f
                return@LaunchedEffect
            }
            if (resolvedPool?.isOwnedBy(poolOwner) == false) return@LaunchedEffect
            player.volume = i / steps.toFloat()
            delay(stepMs)
        }
        if (!currentMuted && currentIsPlaying && resolvedPool?.isOwnedBy(poolOwner) != false) {
            player.volume = 1f
        }
    }

    // Prepare only after PlayerView has attached the shared ExoPlayer. Starting
    // decode before a TextureView exists is the main cause of audio-only cards.
    // Do NOT key on muted — volume is handled separately; re-prepare was tearing
    // the Nvidia surface (audio continues, picture gone).
    var preparedMediaKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isPlaying, mediaIdentity, trailerPlayer, surfaceAttached) {
        val player = trailerPlayer ?: return@LaunchedEffect
        val videoUrl = currentTrailerUrl
        if (isPlaying && mediaIdentity != null && videoUrl != null) {
            if (!surfaceAttached) return@LaunchedEffect
            resolvedPool?.acquire(poolOwner)
            if (resolvedPool?.isOwnedBy(poolOwner) == false) return@LaunchedEffect
            if (
                preparedMediaKey == mediaIdentity &&
                player.currentMediaItem != null &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
            ) {
                player.playWhenReady = true
                return@LaunchedEffect
            }
            android.util.Log.i(
                TRAILER_LOG,
                "player-start id=$mediaIdentity muted=$muted owner=${poolOwner.hashCode()}"
            )
            preparedMediaKey = mediaIdentity
            hasRenderedFirstFrame = false
            // Take the video output before decoding: a previously focused card's
            // teardown can leave the shared player pointing at a dead texture,
            // which is exactly the audio-with-poster case.
            reattachSurface()
            // Keep silent until a real video frame so a broken surface can never
            // leak audio-only playback.
            player.volume = 0f
            player.bindTrailerMedia(videoUrl, currentTrailerAudioUrl)
            player.prepare()
            player.playWhenReady = true
        } else {
            preparedMediaKey = null
            hasRenderedFirstFrame = false
            if (resolvedPool?.isOwnedBy(poolOwner) != true) return@LaunchedEffect
            player.playWhenReady = false
            delay(150)
            if (!isPlaying && resolvedPool?.isOwnedBy(poolOwner) == true) {
                resolvedPool.stop(poolOwner)
            }
        }
    }

    // Watchdog: audio can run while our TextureView never receives frames (surface
    // handoff between cards on Shield). Rebuild the surface instead of leaving the
    // card showing its poster, and mute rather than play audio with no picture.
    LaunchedEffect(isPlaying, mediaIdentity, trailerPlayer, surfaceAttached, hasRenderedFirstFrame) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (!isPlaying || mediaIdentity == null || hasRenderedFirstFrame || !surfaceAttached) {
            return@LaunchedEffect
        }
        repeat(SURFACE_RECOVERY_ATTEMPTS) { attempt ->
            delay(FIRST_FRAME_TIMEOUT_MS)
            if (!currentIsPlaying || hasRenderedFirstFrame) return@LaunchedEffect
            if (resolvedPool?.isOwnedBy(poolOwner) == false) return@LaunchedEffect
            if (player.playbackState == Player.STATE_IDLE) return@LaunchedEffect
            android.util.Log.w(
                TRAILER_LOG,
                "no-first-frame attempt=${attempt + 1} state=${player.playbackState} " +
                    "video=${player.videoSize.width}x${player.videoSize.height} " +
                    "pos=${player.currentPosition} owner=${poolOwner.hashCode()}"
            )
            player.volume = 0f
            if (!reattachSurface()) return@LaunchedEffect
            player.playWhenReady = true
        }
        if (currentIsPlaying && !hasRenderedFirstFrame) {
            android.util.Log.w(TRAILER_LOG, "no-video-giveup owner=${poolOwner.hashCode()}")
            player.volume = 0f
        }
    }

    LaunchedEffect(trailerPlayer, cropToFill) {
        val player = trailerPlayer ?: return@LaunchedEffect
        player.videoScalingMode = if (cropToFill) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    LaunchedEffect(isPaused, trailerPlayer) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        player.playWhenReady = !isPaused
    }

    LaunchedEffect(seekRequestToken, seekDeltaMs, trailerPlayer) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (seekRequestToken <= 0) return@LaunchedEffect
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        val current = player.currentPosition
        val target = (current + seekDeltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    LaunchedEffect(trailerPlayer, isPlaying) {
        val player = trailerPlayer ?: return@LaunchedEffect
        while (isPlaying) {
            val position = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration.takeIf { it > 0 } ?: 0L
            currentOnProgressChanged(position, duration)
            delay(250)
        }
        currentOnProgressChanged(0L, 0L)
    }

    DisposableEffect(activityLifecycleOwner, trailerPlayer) {
        val player = trailerPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentOnEnded()
                }
            }

            // Only a real rendered frame reveals the player. READY + videoSize was
            // also true while decoding into a dead surface, which showed a black
            // card; the watchdog above rebuilds the surface instead, and a fresh
            // surface always reports a first frame.
            override fun onRenderedFirstFrame() {
                markFirstFrame()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w(
                    TRAILER_LOG,
                    "player-error owner=${poolOwner.hashCode()} code=${error.errorCodeName}"
                )
                player.volume = 0f
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (currentIsPlaying && !currentTrailerUrl.isNullOrBlank()) {
                        reattachSurface()
                        if (player.currentMediaItem == null) {
                            player.bindTrailerMedia(currentTrailerUrl!!, currentTrailerAudioUrl)
                            player.prepare()
                        }
                        player.playWhenReady = true
                    }
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    if (resolvedPool?.isOwnedBy(poolOwner) == true) {
                        player.playWhenReady = false
                        player.pause()
                        player.stop()
                        player.clearMediaItems()
                    }
                    // Fall back to the poster rather than a stale/black frame, and
                    // let the watchdog re-arm when we come back.
                    hasRenderedFirstFrame = false
                }
                else -> Unit
            }
        }
        player.addListener(listener)
        activityLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { activityLifecycleOwner.lifecycle.removeObserver(observer) }
            runCatching { player.removeListener(listener) }
            resolvedPool?.stop(poolOwner)
        }
    }

    // Keep PlayerView composed whenever we have a player and want playback (or are
    // fading out). Avoid AnimatedVisibility so the TextureView exists before prepare.
    if (trailerPlayer != null && (isPlaying || hasRenderedFirstFrame)) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.trailer_player_view, null) as PlayerView).apply {
                    player = trailerPlayer
                    isFocusable = playerFocusable
                    isFocusableInTouchMode = playerFocusable
                    isClickable = playerFocusable
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    if (playerFocusable) {
                        setOnKeyListener { _, keyCode, event ->
                            currentOnRemoteKey(keyCode, event.action, event.repeatCount)
                        }
                    } else {
                        setOnKeyListener(null)
                        if (isFocused) clearFocus()
                    }
                    keepScreenOn = true
                    resizeMode = if (cropToFill) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    playerView = this
                    surfaceAttached = true
                }
            },
            update = { view ->
                playerView = view
                if (resolvedPool?.isOwnedBy(poolOwner) != false && view.player !== trailerPlayer) {
                    view.player = trailerPlayer
                }
                if (view.player === trailerPlayer) {
                    surfaceAttached = true
                }
                view.isFocusable = playerFocusable
                view.isFocusableInTouchMode = playerFocusable
                view.isClickable = playerFocusable
                if (!playerFocusable && view.isFocused) {
                    view.clearFocus()
                }
                view.resizeMode = if (cropToFill) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onRelease = { view ->
                // Always detach: ExoPlayer's clearVideoTextureView only clears when
                // this view still owns the output, so this cannot wipe the surface of
                // the card that took over. Leaving stale views attached was what made
                // a dying card's texture teardown kill the focused card's picture.
                runCatching { view.player = null }
                if (playerView === view) playerView = null
                surfaceAttached = false
                view.keepScreenOn = false
            },
            modifier = modifier
                .clipToBounds()
                .then(
                    if (playerFocusable) {
                        Modifier
                    } else {
                        Modifier.focusProperties { canFocus = false }
                    }
                )
                .graphicsLayer {
                    alpha = playerAlphaState.value
                    scaleX = zoomScale
                    scaleY = zoomScale
                }
        )
    }
}
