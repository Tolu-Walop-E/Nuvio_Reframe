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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.components.TrailerPlayer
import kotlinx.coroutines.delay

@Composable
internal fun NetflixMediaCard(
    mediaKey: String,
    title: String,
    subtitle: String?,
    imageUrl: String?,
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
    /** When true, Left is consumed so focus cannot escape the rail. */
    trapLeft: Boolean = false,
    /** When true, Right is consumed so focus cannot escape the rail. */
    trapRight: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    var hasTrailerFrame by remember(trailerUrl, mediaKey) { mutableStateOf(false) }
    var trailerArmed by remember(mediaKey) { mutableStateOf(false) }
    val animatedWidth by animateDpAsState(
        targetValue = width,
        animationSpec = NetflixHomeMotion.FocusWidthAnimation,
        label = "netflixCardWidth"
    )
    val shape = RoundedCornerShape(NetflixHomeTokens.CardCornerRadius)
    val artwork = remember(mediaKey, imageUrl) {
        NetflixCardArtwork(key = "$mediaKey|${imageUrl.orEmpty()}", imageUrl = imageUrl)
    }
    val showFallbackTitle = showFallbackTitleWhenArtworkMissing && imageUrl.isNullOrBlank()
    val showText = showLabels || showFallbackTitle

    LaunchedEffect(focused, playTrailer, trailerUrl, mediaKey) {
        trailerArmed = false
        hasTrailerFrame = false
        if (!focused || !playTrailer || trailerUrl.isNullOrBlank()) {
            return@LaunchedEffect
        }
        delay(NetflixHomeTokens.TrailerStartDelayMs)
        if (focused && playTrailer) {
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
                        // At the ends of a rail, default focus search can jump into the
                        // absolute top nav and strand the cursor. Consume those edges.
                        Key.DirectionLeft -> trapLeft
                        Key.DirectionRight -> trapRight
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .clip(shape)
            .background(NetflixThemeChrome.surface)
            .border(
                width = if (focused) NetflixHomeTokens.FocusBorder else 1.dp,
                color = if (focused) NetflixThemeChrome.focus else Color.Transparent,
                shape = shape
            ),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Keep poster under the player always. TrailerPlayer fades in on first
            // frame (alpha 0→1); removing artwork earlier caused a surface flash.
            Crossfade(
                targetState = artwork,
                animationSpec = tween(durationMillis = NetflixHomeMotion.ArtworkCrossfadeDurationMs),
                label = "netflixCardArtwork"
            ) { targetArtwork ->
                if (!targetArtwork.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = targetArtwork.imageUrl,
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
                    onEnded = onTrailerEnded,
                    onFirstFrameRendered = { hasTrailerFrame = true },
                    muted = trailerMuted,
                    cropToFill = true,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(100))
                )
            }
            if (focused && !hasTrailerFrame) {
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
                // Inset from the white focus ring so the resume bar stays visible.
                val progressShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = NetflixHomeTokens.ProgressBarHorizontalInset,
                            end = NetflixHomeTokens.ProgressBarHorizontalInset,
                            bottom = NetflixHomeTokens.ProgressBarBottomInset
                        )
                        .height(NetflixHomeTokens.ProgressBarHeight)
                        .clip(progressShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NetflixHomeTokens.ProgressBarHeight)
                            .clip(progressShape)
                            .background(Color.White.copy(alpha = 0.38f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(NetflixHomeTokens.ProgressBarHeight)
                            .clip(progressShape)
                            .background(NetflixThemeChrome.progress)
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

private data class NetflixCardArtwork(
    val key: String,
    val imageUrl: String?
)
