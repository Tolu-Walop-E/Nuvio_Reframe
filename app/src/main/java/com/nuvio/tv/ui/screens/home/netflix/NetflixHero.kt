package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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

@Composable
internal fun NetflixHero(
    item: NetflixHeroItem?,
    modifier: Modifier = Modifier,
    topNavigationRequester: FocusRequester,
    primaryActionRequester: FocusRequester,
    onMoveDownFromHero: () -> Boolean,
    trailerPreviewUrl: String?,
    trailerPreviewAudioUrl: String?,
    playTrailerPreview: Boolean,
    trailerPreviewMuted: Boolean,
    onTrailerEnded: () -> Unit,
    onViewDetails: (NetflixHomeTarget) -> Unit
) {
    val shape = RoundedCornerShape(NetflixHomeTokens.HeroCornerRadius)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var hasTrailerFrame by remember(trailerPreviewUrl) { mutableStateOf(false) }

    LaunchedEffect(item?.key) {
        hasTrailerFrame = false
    }

    Box(
        modifier = modifier
            .height(NetflixHomeTokens.HeroHeight)
            .fillMaxWidth(0.96f)
            .bringIntoViewRequester(bringIntoViewRequester)
            .clip(shape)
            .background(NetflixHomeTokens.SurfaceRaised)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    ) {
        Crossfade(
            targetState = item?.backdrop ?: item?.poster,
            animationSpec = tween(520),
            label = "netflixHeroBackdrop"
        ) { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = item?.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        AnimatedVisibility(
            visible = playTrailerPreview && !trailerPreviewUrl.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(360)),
            exit = fadeOut(animationSpec = tween(260))
        ) {
            TrailerPlayer(
                trailerUrl = trailerPreviewUrl,
                trailerAudioUrl = trailerPreviewAudioUrl,
                isPlaying = playTrailerPreview && !trailerPreviewUrl.isNullOrBlank(),
                onEnded = onTrailerEnded,
                onFirstFrameRendered = { hasTrailerFrame = true },
                muted = trailerPreviewMuted,
                cropToFill = true,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(animationSpec = tween(320)),
                exit = fadeOut(animationSpec = tween(240))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.94f),
                        0.38f to Color.Black.copy(alpha = if (hasTrailerFrame) 0.70f else 0.58f),
                        1f to Color.Black.copy(alpha = if (hasTrailerFrame) 0.18f else 0.06f)
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

        if (item != null) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.50f)
                    .padding(start = 38.dp, top = 28.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
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
                NetflixHeroButton(
                    label = "View Details",
                    primary = true,
                    focusRequester = primaryActionRequester,
                    onFocus = { bringIntoViewRequester.bringIntoViewSafely() },
                    onMoveUp = {
                        runCatching { topNavigationRequester.requestFocus() }.isSuccess
                    },
                    onMoveDown = onMoveDownFromHero,
                    onClick = { onViewDetails(item.target) }
                )
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
    focusRequester: FocusRequester,
    onFocus: suspend () -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Text(
        text = label,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            onMoveUp()
                        }

                        Key.DirectionDown -> {
                            onMoveDown()
                        }

                        else -> false
                    }
                }
            }
            .onFocusChanged {
                focused = it.isFocused
            }
            .clip(shape)
            .background(if (primary) NetflixHomeTokens.TextPrimary else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .border(
                width = if (focused) NetflixHomeTokens.FocusBorder else 1.dp,
                color = if (focused) NetflixHomeTokens.Focus else Color.Transparent,
                shape = shape
            )
            .padding(horizontal = 22.dp, vertical = 11.dp),
        color = if (primary) Color.Black else NetflixHomeTokens.TextPrimary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
    )

    LaunchedEffect(focused) {
        if (focused) onFocus()
    }
}

private suspend fun BringIntoViewRequester.bringIntoViewSafely() {
    runCatching { bringIntoView() }
}
