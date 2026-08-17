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
import android.view.LayoutInflater
import android.view.ViewGroup
import com.nuvio.tv.R
import kotlinx.coroutines.delay

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
    val zoomScale = if (cropToFill) overscanZoom.coerceAtLeast(1f) else 1f
    var hasRenderedFirstFrame by remember(trailerUrl) { mutableStateOf(false) }
    var surfaceAttached by remember(trailerUrl) { mutableStateOf(false) }
    val playerAlphaState = animateFloatAsState(
        targetValue = if (isPlaying && hasRenderedFirstFrame) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "trailerFirstFrameAlpha"
    )

    val resolvedPool = trailerPlayerPool ?: LocalTrailerPlayerPool.current

    // Exclusive owner token so a previous TrailerPlayer cannot stop/clear the shared
    // ExoPlayer after focus has already moved to another card/hero surface.
    val poolOwner = remember { Any() }

    val trailerPlayer = remember(trailerUrl, resolvedPool) {
        if (trailerUrl != null) {
            resolvedPool?.acquire(poolOwner)
        } else {
            null
        }
    }

    fun markFirstFrame() {
        if (!hasRenderedFirstFrame) {
            hasRenderedFirstFrame = true
            android.util.Log.i("NetflixTrailer", "first-frame owner=${poolOwner.hashCode()}")
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

    LaunchedEffect(trailerPlayer, muted, cropToFill) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (resolvedPool?.isOwnedBy(poolOwner) == false) return@LaunchedEffect
        player.volume = if (muted) 0f else 1f
        player.videoScalingMode = if (cropToFill) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    // Prepare only after PlayerView has attached the shared ExoPlayer. Starting
    // decode before a TextureView exists is the main cause of audio-only cards.
    // Do NOT key on muted — volume is handled separately; re-prepare was tearing
    // the Nvidia surface (audio continues, picture gone).
    var preparedMediaKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isPlaying, trailerUrl, trailerAudioUrl, trailerPlayer, surfaceAttached) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (isPlaying && trailerUrl != null) {
            if (!surfaceAttached) return@LaunchedEffect
            resolvedPool?.acquire(poolOwner)
            if (resolvedPool?.isOwnedBy(poolOwner) == false) return@LaunchedEffect
            val mediaKey = "$trailerUrl|${trailerAudioUrl.orEmpty()}"
            if (
                preparedMediaKey == mediaKey &&
                player.currentMediaItem != null &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
            ) {
                player.playWhenReady = true
                if (!hasRenderedFirstFrame && player.videoSize.width > 0) {
                    markFirstFrame()
                }
                return@LaunchedEffect
            }
            android.util.Log.i(
                "NetflixTrailer",
                "player-start url=${trailerUrl.take(64)} muted=$muted owner=${poolOwner.hashCode()}"
            )
            preparedMediaKey = mediaKey
            hasRenderedFirstFrame = false
            player.volume = if (muted) 0f else 1f
            player.bindTrailerMedia(trailerUrl, trailerAudioUrl)
            player.prepare()
            player.playWhenReady = true
            delay(200)
            if (
                resolvedPool?.isOwnedBy(poolOwner) == true &&
                currentIsPlaying &&
                !hasRenderedFirstFrame &&
                player.playbackState == Player.STATE_READY &&
                player.videoSize.width > 0
            ) {
                markFirstFrame()
            }
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
                if (
                    playbackState == Player.STATE_READY &&
                    player.videoSize.width > 0 &&
                    currentIsPlaying
                ) {
                    markFirstFrame()
                }
            }

            override fun onRenderedFirstFrame() {
                markFirstFrame()
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && currentIsPlaying && player.playbackState == Player.STATE_READY) {
                    markFirstFrame()
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (currentIsPlaying && !currentTrailerUrl.isNullOrBlank()) {
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
                    surfaceAttached = true
                }
            },
            update = { view ->
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
                // Critical: only clear the shared ExoPlayer from THIS view when we
                // still own the pool. Otherwise a previous card's dispose wipes the
                // new card's TextureView and leaves audio with a blank poster.
                if (view.player === trailerPlayer &&
                    (resolvedPool == null || resolvedPool.isOwnedBy(poolOwner))
                ) {
                    view.player = null
                }
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
