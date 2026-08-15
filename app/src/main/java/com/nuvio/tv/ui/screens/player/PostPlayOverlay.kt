@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.theme.NuvioMotion

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R

@Composable
fun PostPlayOverlay(
    mode: PostPlayMode?,
    controlsVisible: Boolean,
    nextEpisodeFocusRequester: FocusRequester,
    progressBarFocusRequester: FocusRequester?,
    leftFocusRequester: FocusRequester?,
    onPlayNext: () -> Unit,
    onContinueStillWatching: () -> Unit,
    onDismissStillWatching: () -> Unit,
    onRateSelect: (Int) -> Unit = {},
    onRateSubmit: () -> Unit = {},
    onRateSkip: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isAutoPlay = mode is PostPlayMode.AutoPlay
    val isRatePrompt = mode is PostPlayMode.RatePrompt
    val isAutoPlayPlayable = (mode as? PostPlayMode.AutoPlay)?.nextEpisode?.hasAired == true

    var placedFocused by remember(isAutoPlay, isRatePrompt, controlsVisible) { mutableStateOf(false) }

    val transition = updateTransition(targetState = mode, label = "PostPlayMode")

    transition.AnimatedVisibility(
        visible = { it != null },
        enter = slideInHorizontally(animationSpec = tween(260), initialOffsetX = { it / 2 }) +
            fadeIn(animationSpec = tween(220)),
        exit = slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it / 2 }) +
            fadeOut(animationSpec = tween(160)),
        modifier = modifier,
    ) {
        val onCardClick: () -> Unit = when {
            isAutoPlay && isAutoPlayPlayable -> onPlayNext
            mode is PostPlayMode.StillWatching -> onContinueStillWatching
            else -> ({})
        }
        Card(
            onClick = onCardClick,
            shape = CardDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = CardDefaults.colors(
                containerColor = Color(0xE3191919),
                focusedContainerColor = Color(0xE3191919),
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, Color.White.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(14.dp),
                ),
                focusedBorder = Border(
                    border = BorderStroke(
                        if (isRatePrompt) NuvioTheme.spacing.hairline else NuvioTheme.spacing.xxs,
                        if (isRatePrompt) Color.White.copy(alpha = 0.16f) else NuvioTheme.colors.FocusRing,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ),
            ),
            scale = CardDefaults.scale(focusedScale = 1f),
            modifier = Modifier
                .width(if (isRatePrompt) 520.dp else 420.dp)
                .focusRequester(nextEpisodeFocusRequester)
                .onPlaced {
                    if (isAutoPlay && !controlsVisible && !placedFocused) {
                        placedFocused = true
                        runCatching { nextEpisodeFocusRequester.requestFocus() }
                    }
                }
                .then(
                    if (!isRatePrompt && (progressBarFocusRequester != null || leftFocusRequester != null)) {
                        Modifier.focusProperties {
                            progressBarFocusRequester?.let { down = it }
                            leftFocusRequester?.let { left = it }
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            transition.AnimatedContent(
                transitionSpec = {
                    fadeIn(animationSpec = tween(NuvioMotion.tokens.durations.fast)) togetherWith
                        fadeOut(animationSpec = tween(120))
                },
                contentKey = { it?.let { current -> current::class } },
            ) { current ->
                when (current) {
                    is PostPlayMode.AutoPlay -> AutoPlayBody(mode = current)
                    is PostPlayMode.StillWatching -> StillWatchingBody(
                        mode = current,
                        onContinue = onContinueStillWatching,
                        onDismiss = onDismissStillWatching,
                    )
                    is PostPlayMode.RatePrompt -> RatePromptBody(
                        mode = current,
                        onSelect = onRateSelect,
                        onSubmit = onRateSubmit,
                        onSkip = onRateSkip,
                    )
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun AutoPlayBody(mode: PostPlayMode.AutoPlay) {
    val nextEpisode = mode.nextEpisode
    val isPlayable = nextEpisode.hasAired
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NextEpisodeThumbnail(
            thumbnail = nextEpisode.thumbnail,
            contentDescription = stringResource(R.string.cd_next_episode_thumbnail),
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.next_episode_label),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
            Text(
                text = nextEpisodeDisplayLabel(nextEpisode),
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            val statusText = when {
                !isPlayable && !nextEpisode.unairedMessage.isNullOrBlank() -> nextEpisode.unairedMessage
                mode.searching -> stringResource(R.string.next_episode_finding_source)
                !mode.sourceName.isNullOrBlank() && mode.countdownSec != null ->
                    stringResource(R.string.next_episode_playing_via, mode.sourceName, mode.countdownSec)
                else -> null
            }
            if (statusText != null) {
                Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
                NextEpisodeStatusLine(text = statusText)
            }
        }

        Row(
            modifier = Modifier
                .padding(start = NuvioTheme.spacing.sm)
                .clip(CircleShape)
                .border(
                    BorderStroke(NuvioTheme.spacing.hairline, Color.White.copy(alpha = 0.2f)),
                    CircleShape,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isPlayable) Color.White else Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (isPlayable) {
                    stringResource(R.string.next_episode_play)
                } else {
                    stringResource(R.string.next_episode_unaired)
                },
                color = if (isPlayable) Color.White else Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}

@Composable
private fun RatePromptBody(
    mode: PostPlayMode.RatePrompt,
    onSelect: (Int) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
) {
    val scoreFocusRequesters = remember { List(10) { FocusRequester() } }
    val submitFocusRequester = remember { FocusRequester() }
    val skipFocusRequester = remember { FocusRequester() }
    var placedFocused by remember { mutableStateOf(false) }
    val selected = mode.selectedRating.coerceIn(1, 10)

    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NextEpisodeThumbnail(
                thumbnail = mode.artworkUrl,
                contentDescription = mode.title,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rate_after_watching_title),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
                Text(
                    text = mode.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!mode.subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
                    Text(
                        text = mode.subtitle,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..10).forEach { score ->
                val index = score - 1
                val selectedScore = score == selected
                Card(
                    onClick = { onSelect(score) },
                    shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    colors = CardDefaults.colors(
                        containerColor = if (selectedScore) {
                            Color.White.copy(alpha = 0.22f)
                        } else {
                            Color.White.copy(alpha = 0.06f)
                        },
                        focusedContainerColor = Color.White.copy(alpha = 0.28f),
                    ),
                    border = CardDefaults.border(
                        border = Border(
                            border = BorderStroke(
                                NuvioTheme.spacing.hairline,
                                if (selectedScore) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                            shape = RoundedCornerShape(10.dp),
                        ),
                    ),
                    scale = CardDefaults.scale(focusedScale = 1.06f),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .focusRequester(scoreFocusRequesters[index])
                        .onPlaced {
                            if (selectedScore && !placedFocused) {
                                placedFocused = true
                                runCatching { scoreFocusRequesters[index].requestFocus() }
                            }
                        }
                        .focusProperties {
                            down = submitFocusRequester
                            if (index > 0) left = scoreFocusRequesters[index - 1]
                            if (index < 9) right = scoreFocusRequesters[index + 1]
                        },
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = score.toString(),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (selectedScore) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostPlayPillButton(
                icon = Icons.Default.Check,
                iconTint = Color.White,
                label = stringResource(R.string.rate_after_watching_submit, selected),
                textColor = Color.White,
                onClick = onSubmit,
                focusRequester = submitFocusRequester,
                modifier = Modifier
                    .focusProperties {
                        up = scoreFocusRequesters[selected - 1]
                        right = skipFocusRequester
                    },
            )
            PostPlayPillButton(
                icon = Icons.Default.Close,
                iconTint = Color.White.copy(alpha = 0.65f),
                label = stringResource(R.string.rate_after_watching_skip),
                textColor = Color.White.copy(alpha = 0.72f),
                onClick = onSkip,
                focusRequester = skipFocusRequester,
                modifier = Modifier.focusProperties {
                    up = scoreFocusRequesters[selected - 1]
                    left = submitFocusRequester
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.rate_after_watching_hint),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun StillWatchingBody(
    mode: PostPlayMode.StillWatching,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val continueFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }
    var continueFocused by remember { mutableStateOf(false) }

    val nextEpisode = mode.nextEpisode
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (nextEpisode.thumbnail != null) {
            NextEpisodeThumbnail(
                thumbnail = nextEpisode.thumbnail,
                contentDescription = stringResource(R.string.cd_next_episode_thumbnail),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.still_watching_title),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
            Text(
                text = nextEpisodeDisplayLabel(nextEpisode),
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            if (mode.countdownSec != null) {
                Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
                NextEpisodeStatusLine(
                    text = stringResource(R.string.still_watching_countdown, mode.countdownSec),
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostPlayPillButton(
                icon = Icons.Default.PlayArrow,
                iconTint = Color.White,
                label = stringResource(R.string.still_watching_continue),
                textColor = Color.White,
                onClick = onContinue,
                focusRequester = continueFocusRequester,
                modifier = Modifier
                    .onPlaced {
                        if (!continueFocused) {
                            continueFocused = true
                            runCatching { continueFocusRequester.requestFocus() }
                        }
                    }
                    .focusProperties { right = exitFocusRequester },
            )
            PostPlayPillButton(
                icon = Icons.Default.Close,
                iconTint = Color.White.copy(alpha = 0.65f),
                label = stringResource(R.string.still_watching_exit),
                textColor = Color.White.copy(alpha = 0.72f),
                onClick = onDismiss,
                focusRequester = exitFocusRequester,
                modifier = Modifier.focusProperties { left = continueFocusRequester },
            )
        }
    }
}

@Composable
private fun PostPlayPillButton(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    textColor: Color,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = CircleShape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.15f),
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(NuvioTheme.spacing.hairline, Color.White.copy(alpha = 0.2f)),
                shape = CircleShape,
            ),
            focusedBorder = Border(
                border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                shape = CircleShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        modifier = modifier.focusRequester(focusRequester),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}

@Composable
private fun NextEpisodeThumbnail(
    thumbnail: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 112.dp, height = 64.dp)
            .clip(RoundedCornerShape(9.dp)),
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)),
                    ),
                ),
        )
    }
}

@Composable
private fun NextEpisodeStatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.78f),
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun nextEpisodeDisplayLabel(nextEpisode: NextEpisodeInfo): String {
    if (nextEpisode.isOtherType) return nextEpisode.title
    val context = LocalContext.current
    val code = stringResource(
        R.string.season_episode_format,
        nextEpisode.season,
        nextEpisode.episode,
    )
    val localizedTitle = nextEpisode.title.localizeEpisodeTitle(context)
    return "$code • $localizedTitle"
}
