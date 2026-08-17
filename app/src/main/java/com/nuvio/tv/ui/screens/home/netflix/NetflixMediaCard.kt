@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.nuvio.tv.ui.components.TrailerPlayer
import kotlinx.coroutines.delay

@Composable
internal fun NetflixMediaCard(
    mediaKey: String,
    title: String,
    subtitle: String?,
    imageUrl: String?,
    /**
     * When [imageUrl] is a different landscape/backdrop URL, keep showing this
     * (usually the portrait) until Coil has successfully loaded [imageUrl].
     */
    holdUntilReadyImageUrl: String? = null,
    /**
     * Stable decode/cache size for artwork, in pixels. Must match what the rail
     * prefetches ([netflixArtworkCacheKey]). The card's own measured size animates
     * on focus, so keying Coil on it produced a new cache key (and a fresh decode)
     * for every animation frame and never hit the prefetched bitmap.
     */
    artworkCacheSizePx: IntSize? = null,
    width: Dp,
    height: Dp,
    progress: Float? = null,
    showLabels: Boolean = true,
    showFallbackTitleWhenArtworkMissing: Boolean = true,
    focusRequester: FocusRequester? = null,
    trailerUrl: String? = null,
    trailerAudioUrl: String? = null,
    playTrailer: Boolean = false,
    trailerMuted: Boolean = true,
    onTrailerEnded: () -> Unit = {},
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    onMoveUp: () -> Boolean = { false },
    onMoveDown: () -> Boolean = { false },
    onMoveLeft: () -> Boolean = { false },
    onMoveRight: () -> Boolean = { false },
    /** When true, Left is consumed so focus cannot escape the rail. */
    trapLeft: Boolean = false,
    /** When true, Right is consumed so focus cannot escape the rail. */
    trapRight: Boolean = false,
    /** When false, a rail-level pivot frame draws the white selector instead. */
    showFocusBorder: Boolean = true,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    var hasTrailerFrame by remember(trailerUrl, mediaKey) { mutableStateOf(false) }
    var trailerArmed by remember(mediaKey) { mutableStateOf(false) }
    var cardSizePx by remember(mediaKey) { mutableStateOf(IntSize.Zero) }
    val animatedWidth by animateDpAsState(
        targetValue = width,
        animationSpec = NetflixHomeMotion.FocusWidthAnimation,
        label = "netflixCardWidth"
    )
    val shape = RoundedCornerShape(NetflixHomeTokens.CardCornerRadius)
    val shouldHoldPortrait = !imageUrl.isNullOrBlank() &&
        !holdUntilReadyImageUrl.isNullOrBlank() &&
        imageUrl != holdUntilReadyImageUrl
    // Prefer the rail's stable size; fall back to the measured size for callers
    // that don't pass one.
    val cacheSizePx = artworkCacheSizePx ?: cardSizePx
    val desiredImageRequest = remember(imageUrl, cacheSizePx) {
        val url = imageUrl ?: return@remember null
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                netflixArtworkCacheKey(url, cacheSizePx)?.let { key ->
                    memoryCacheKey(key)
                    diskCacheKey(url)
                    size(width = cacheSizePx.width, height = cacheSizePx.height)
                }
            }
            .build()
    }
    val desiredPainter = rememberAsyncImagePainter(model = desiredImageRequest ?: imageUrl)
    val desiredState by desiredPainter.state.collectAsState()
    val desiredReady = desiredState is AsyncImagePainter.State.Success
    val displayedImageUrl = if (shouldHoldPortrait && !desiredReady) {
        holdUntilReadyImageUrl
    } else {
        imageUrl
    }
    // Instant swap when landscape is already cached; crossfade only while waiting.
    val artworkCrossfadeMs = if (shouldHoldPortrait && !desiredReady) {
        NetflixHomeMotion.ArtworkCrossfadeDurationMs
    } else {
        0
    }
    val artwork = remember(mediaKey, displayedImageUrl) {
        NetflixCardArtwork(key = "$mediaKey|${displayedImageUrl.orEmpty()}", imageUrl = displayedImageUrl)
    }
    val displayedImageRequest = remember(displayedImageUrl, cacheSizePx, shouldHoldPortrait) {
        val url = displayedImageUrl ?: return@remember null
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                // Only use the landscape-sized cache key for the focus artwork so
                // it hits the rail prefetch; portrait holds keep the default key.
                if (!shouldHoldPortrait || displayedImageUrl == imageUrl) {
                    netflixArtworkCacheKey(url, cacheSizePx)?.let { key ->
                        memoryCacheKey(key)
                        diskCacheKey(url)
                        size(width = cacheSizePx.width, height = cacheSizePx.height)
                    }
                }
            }
            .build()
    }
    val showFallbackTitle = showFallbackTitleWhenArtworkMissing && displayedImageUrl.isNullOrBlank()
    val showText = showLabels || showFallbackTitle

    // Arm ASAP when the trailer URL is already warm; otherwise wait a short settle
    // so D-pad flits don't start the shared player. URL arrival while focused also
    // arms quickly without restarting a long timer.
    LaunchedEffect(focused, mediaKey) {
        trailerArmed = false
        hasTrailerFrame = false
        if (!focused) return@LaunchedEffect
        val cached = !trailerUrl.isNullOrBlank()
        delay(
            if (cached) {
                NetflixHomeTokens.TrailerCachedStartDelayMs
            } else {
                NetflixHomeTokens.TrailerStartDelayMs
            }
        )
        if (focused) {
            trailerArmed = true
        }
    }
    LaunchedEffect(trailerUrl, focused, mediaKey) {
        if (!focused || trailerArmed || trailerUrl.isNullOrBlank()) return@LaunchedEffect
        delay(NetflixHomeTokens.TrailerCachedStartDelayMs)
        if (focused && !trailerUrl.isNullOrBlank()) {
            trailerArmed = true
        }
    }

    val shouldPlayTrailer = focused && playTrailer && trailerArmed && !trailerUrl.isNullOrBlank()

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            onMoveUp()
                            true
                        }
                        Key.DirectionDown -> {
                            onMoveDown()
                            true
                        }
                        Key.DirectionLeft -> onMoveLeft() || trapLeft
                        Key.DirectionRight -> onMoveRight() || trapRight
                        else -> false
                    }
                }
            }
            .graphicsLayer {
                shadowElevation = if (focused) 18f else 2f
                alpha = if (focused) 1f else 0.88f
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
                if (!it.isFocused) {
                    hasTrailerFrame = false
                    trailerArmed = false
                }
            }
            .width(animatedWidth)
            .height(height)
            .onSizeChanged { cardSizePx = it }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .clip(shape)
            .background(NetflixThemeChrome.surface)
            .border(
                width = if (showFocusBorder && focused) NetflixHomeTokens.FocusBorder else 1.dp,
                color = if (showFocusBorder && focused) NetflixThemeChrome.focus else Color.Transparent,
                shape = shape
            ),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Keep poster under the player always. TrailerPlayer fades in on first
            // frame (alpha 0→1); removing artwork earlier caused a surface flash.
            // While focus swaps portrait→landscape, hold the portrait until the
            // landscape Coil request succeeds so the card never looks empty.
            Crossfade(
                targetState = artwork,
                animationSpec = tween(durationMillis = artworkCrossfadeMs),
                label = "netflixCardArtwork"
            ) { targetArtwork ->
                if (!targetArtwork.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = displayedImageRequest ?: targetArtwork.imageUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            if (shouldPlayTrailer) {
                TrailerPlayer(
                    trailerUrl = trailerUrl,
                    trailerAudioUrl = trailerAudioUrl,
                    isPlaying = shouldPlayTrailer,
                    onEnded = {
                        // Ignore ENDED from pool handoff / stop before a frame showed.
                        if (hasTrailerFrame) onTrailerEnded()
                    },
                    onFirstFrameRendered = { hasTrailerFrame = true },
                    muted = trailerMuted,
                    cropToFill = true,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(animationSpec = tween(80)),
                    exit = fadeOut(animationSpec = tween(80))
                )
            }
            // Soft focus wash only before the trailer is armed — lifting it under a
            // playing trailer caused a visible flicker into first frame.
            if (focused && !hasTrailerFrame && !trailerArmed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.06f))
                )
            }
            if (showText) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.64f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f)
                            )
                        )
                )
            }
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(NetflixHomeTokens.ProgressScrimHeight)
                ) {
                    // Soft fade so the bar stays legible on bright thumbnails.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.62f)
                                )
                            )
                    )
                    NetflixResumeBar(
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(
                                start = NetflixHomeTokens.ProgressBarHorizontalInset,
                                end = NetflixHomeTokens.ProgressBarHorizontalInset,
                                bottom = NetflixHomeTokens.ProgressBarBottomInset
                            )
                    )
                }
            }
        }
        if (showText) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = title,
                        color = NetflixThemeChrome.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (showFallbackTitle) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showLabels && !subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = NetflixThemeChrome.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Resume indicator for Continue Watching cards: a thin rounded track with a
 * solid accent fill. Inset clear of the focus ring so it stays visible while
 * the card is focused.
 */
@Composable
private fun NetflixResumeBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    // Keep a colored nub visible for barely-started items instead of a bare track.
    val fraction = progress.coerceIn(0f, 1f).coerceAtLeast(0.03f)
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .height(NetflixHomeTokens.ProgressBarHeight)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.30f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(shape)
                .background(NetflixThemeChrome.progress)
        )
    }
}

private data class NetflixCardArtwork(
    val key: String,
    val imageUrl: String?
)

/**
 * Shared Coil memory-cache key for Netflix card artwork.
 *
 * The rail prefetches neighbours with this exact key at the focused card size, so
 * the portrait→landscape swap on focus is a memory hit instead of a fresh decode.
 * Both sides must agree on the size or the prefetch is wasted.
 */
internal fun netflixArtworkCacheKey(url: String, size: IntSize): String? {
    if (size.width <= 0 || size.height <= 0) return null
    return "netflix-land|$url|${size.width}x${size.height}"
}
