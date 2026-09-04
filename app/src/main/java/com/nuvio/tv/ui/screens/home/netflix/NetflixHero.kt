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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
    /** Focus dwell before the trailer is allowed to start. */
    trailerStartDelayMs: Int = NetflixHomeTokens.TrailerStartDelayMs,
    onTrailerEnded: () -> Unit,
    onFocusedChanged: (Boolean) -> Unit = {},
    onViewDetails: (NetflixHomeTarget) -> Unit
) {
    val shape = RoundedCornerShape(NetflixHomeTokens.HeroCornerRadius)
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var hasTrailerFrame by remember(item?.key) { mutableStateOf(false) }
    var trailerArmed by remember(item?.key) { mutableStateOf(false) }

    // Settle window measured from focus. Arming without a URL is harmless:
    // playback still waits for one.
    LaunchedEffect(focused, item?.key, trailerStartDelayMs) {
        trailerArmed = false
        hasTrailerFrame = false
        if (!focused) return@LaunchedEffect
        delay(trailerStartDelayMs.coerceAtLeast(0).toLong())
        if (focused) {
            trailerArmed = true
        }
    }

    val shouldPlayTrailer = focused && playTrailerPreview && trailerArmed && !trailerPreviewUrl.isNullOrBlank()
    val hideHeroCopy = shouldPlayTrailer && hasTrailerFrame

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    Box(
        modifier = modifier
            .height(NetflixHomeTokens.heroHeightFor(screenHeightDp))
            .fillMaxWidth()
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
                        // Netflix keeps a strong left gradient on the billboard whether
                        // or not it is focused. The old near-transparent focused scrim
                        // left the copy sitting on bare artwork.
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.74f),
                                0.40f to Color.Black.copy(alpha = 0.42f),
                                0.70f to Color.Transparent
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.46f)
                            )
                        )
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
                    .fillMaxWidth(0.58f)
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Black.copy(alpha = 0.66f)
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
                        .fillMaxWidth(0.52f)
                        .padding(start = 34.dp, top = 20.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // The logo stays over a playing trailer — dropping it left a bare
                    // "View Details" button in an empty billboard.
                    Crossfade(
                        targetState = item.logo,
                        animationSpec = tween(360),
                        label = "netflixHeroLogo"
                    ) { logo ->
                            if (!logo.isNullOrBlank()) {
                                AsyncImage(
                                    model = remember(logo) {
                                        NetflixLogoArtwork.request(context, logo)
                                    },
                                    contentDescription = item.title,
                                    modifier = Modifier
                                        .fillMaxWidth(0.82f)
                                        .height(76.dp),
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.BottomStart,
                                    filterQuality = FilterQuality.High
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
                    if (!hideHeroCopy) {
                        Spacer(modifier = Modifier.height(14.dp))
                        NetflixHeroFacts(item)
                        if (!item.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = item.description,
                                color = NetflixHomeTokens.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
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

/** Netflix keeps the billboard middot line short; 4 is what fits the copy column. */
private const val MAX_HERO_FACTS = 4

@Composable
private fun NetflixHeroFacts(item: NetflixHeroItem) {
    // Blank facts would still draw their leading middot, leaving a dangling
    // separator. More than [MAX_HERO_FACTS] overflows the copy column and the
    // squeezed tail renders as a bare ellipsis.
    val facts = buildList {
        item.rating?.takeIf { it.isNotBlank() }?.let { add("IMDb $it") }
        item.year?.takeIf { it.isNotBlank() }?.let(::add)
        item.certification?.takeIf { it.isNotBlank() }?.let(::add)
        netflixRuntimeLabel(item.runtime)?.let(::add)
        item.genres.firstOrNull { it.isNotBlank() }?.let(::add)
    }.take(MAX_HERO_FACTS)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        facts.forEachIndexed { index, fact ->
            if (index > 0) {
                Text(
                    text = "•",
                    color = NetflixHomeTokens.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = fact,
                color = NetflixHomeTokens.TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
