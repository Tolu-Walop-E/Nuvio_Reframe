package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.components.TrailerPlayer
import kotlinx.coroutines.delay

@Composable
internal fun NetflixHero(
    item: NetflixHeroItem?,
    modifier: Modifier = Modifier,
    topNavigationRequester: FocusRequester,
    primaryActionRequester: FocusRequester,
    onMoveDownFromHero: () -> Boolean,
    onMoveUpFromHero: (() -> Boolean)? = null,
    trailerPreviewUrl: String?,
    trailerPreviewAudioUrl: String?,
    playTrailerPreview: Boolean,
    trailerPreviewMuted: Boolean,
    onTrailerEnded: () -> Unit,
    onFocusedChanged: (Boolean) -> Unit = {},
    onViewDetails: (NetflixHomeTarget) -> Unit
) {
    val shape = RoundedCornerShape(NetflixHomeTokens.HeroCornerRadius)
    var focused by remember { mutableStateOf(false) }
    var hasTrailerFrame by remember(item?.key) { mutableStateOf(false) }
    var trailerArmed by remember(item?.key) { mutableStateOf(false) }

    // Arm ASAP when the trailer URL is already warm; otherwise wait a short settle.
    LaunchedEffect(focused, item?.key) {
        trailerArmed = false
        hasTrailerFrame = false
        if (!focused) return@LaunchedEffect
        val cached = !trailerPreviewUrl.isNullOrBlank()
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
    LaunchedEffect(trailerPreviewUrl, focused, item?.key) {
        if (!focused || trailerArmed || trailerPreviewUrl.isNullOrBlank()) return@LaunchedEffect
        delay(NetflixHomeTokens.TrailerCachedStartDelayMs)
        if (focused && !trailerPreviewUrl.isNullOrBlank()) {
            trailerArmed = true
        }
    }

    val shouldPlayTrailer = focused && playTrailerPreview && trailerArmed && !trailerPreviewUrl.isNullOrBlank()
    val hideHeroCopy = shouldPlayTrailer && hasTrailerFrame

    Box(
        modifier = modifier
            .height(NetflixHomeTokens.HeroHeight)
            .fillMaxWidth(0.96f)
            .clip(shape)
            .background(NetflixHomeTokens.SurfaceRaised)
            .border(
                width = if (focused) NetflixHomeTokens.FocusBorder else 1.dp,
                color = if (focused) NetflixThemeChrome.focus else Color.White.copy(alpha = 0.10f),
                shape = shape
            )
    ) {
        // Media layer — never focusable. Keys are owned by the overlay below so
        // Down/Up keep working while a trailer is playing.
        Box(modifier = Modifier.fillMaxSize()) {
            // Keep backdrop under the player; TrailerPlayer fades in on first frame.
            Crossfade(
                targetState = item?.backdrop ?: item?.poster,
                animationSpec = tween(180),
                label = "netflixHeroBackdrop"
            ) { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item?.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (focused) 1f else 0.88f },
                    contentScale = ContentScale.Crop
                )
            }
            if (shouldPlayTrailer) {
                TrailerPlayer(
                    trailerUrl = trailerPreviewUrl,
                    trailerAudioUrl = trailerPreviewAudioUrl,
                    isPlaying = shouldPlayTrailer,
                    onEnded = onTrailerEnded,
                    onFirstFrameRendered = { hasTrailerFrame = true },
                    muted = trailerPreviewMuted,
                    cropToFill = true,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(100))
                )
            }
        }

        // Soft scrim only while copy is visible; full-bleed trailer when text is hidden.
        if (!hideHeroCopy) {
            if (focused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.32f),
                                0.42f to Color.Black.copy(alpha = 0.08f),
                                0.62f to Color.Transparent
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.40f)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.05f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.88f),
                                0.38f to Color.Black.copy(alpha = 0.58f),
                                1f to Color.Black.copy(alpha = 0.06f)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.16f),
                                0.72f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.64f)
                            )
                        )
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.42f)
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f)
                        )
                    )
            )
        }

        // Focus + key overlay sits above the PlayerView so D-pad never gets stuck
        // inside the AndroidView while a trailer is playing.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(primaryActionRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                onMoveUpFromHero?.invoke()
                                    ?: runCatching { topNavigationRequester.requestFocus() }.isSuccess
                            }

                            Key.DirectionDown -> onMoveDownFromHero()
                            else -> false
                        }
                    }
                }
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusedChanged(it.isFocused)
                    if (!it.isFocused) hasTrailerFrame = false
                }
                .clickable(enabled = item != null) {
                    item?.let { onViewDetails(it.target) }
                }
        ) {
            if (item != null) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.50f)
                        .padding(start = 38.dp, top = 28.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (!hideHeroCopy) {
                        Crossfade(
                            targetState = item.logo,
                            animationSpec = tween(360),
                            label = "netflixHeroLogo"
                        ) { logo ->
                            if (!logo.isNullOrBlank()) {
                                AsyncImage(
                                    model = logo,
                                    contentDescription = item.title,
                                    modifier = Modifier
                                        .fillMaxWidth(0.72f)
                                        .height(82.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    text = item.title,
                                    color = NetflixHomeTokens.TextPrimary,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        NetflixHeroFacts(item)
                        if (!item.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = item.description,
                                color = NetflixHomeTokens.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(22.dp))
                    }
                    NetflixHeroButton(
                        label = "View Details",
                        primary = true,
                        focused = focused
                    )
                }
            }
        }
    }
}

@Composable
private fun NetflixHeroFacts(item: NetflixHeroItem) {
    val facts = buildList {
        item.rating?.let { add("IMDb $it") }
        item.year?.let { add(it) }
        item.certification?.let { add(it) }
        if (item.genres.isNotEmpty()) add(item.genres.joinToString(" / "))
        item.runtime?.let { add(it) }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        facts.forEachIndexed { index, fact ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(3.dp)
                        .background(NetflixHomeTokens.TextMuted, RoundedCornerShape(50))
                )
            }
            Text(
                text = fact,
                color = NetflixHomeTokens.TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NetflixHeroButton(
    label: String,
    primary: Boolean,
    focused: Boolean
) {
    val shape = RoundedCornerShape(7.dp)
    Text(
        text = label,
        modifier = Modifier
            .clip(shape)
            .background(if (primary) NetflixHomeTokens.TextPrimary else Color.White.copy(alpha = 0.16f))
            .border(
                width = if (focused) NetflixHomeTokens.FocusBorder else 1.dp,
                color = if (focused && primary) Color.Black else if (focused) NetflixThemeChrome.focus else Color.Transparent,
                shape = shape
            )
            .padding(horizontal = 22.dp, vertical = 11.dp),
        color = if (primary) Color.Black else NetflixHomeTokens.TextPrimary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
    )

}
