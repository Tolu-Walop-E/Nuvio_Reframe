@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

internal data class NetflixGenreChip(
    val key: String,
    val label: String,
    val targetRailKey: String?
)

@Composable
internal fun NetflixGenreRail(
    railKey: String,
    genres: List<NetflixGenreChip>,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    onFocusedItemChanged: (Int, String) -> Unit,
    onPendingFocusConsumed: () -> Unit,
    onFirstCardRequesterReady: (FocusRequester) -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    onGenreSelected: (NetflixGenreChip) -> Unit,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) return

    val itemRequesters = remember(railKey, genres.size) { List(genres.size) { FocusRequester() } }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = lastFocusedIndex.coerceAtLeast(0))

    LaunchedEffect(itemRequesters) {
        itemRequesters.firstOrNull()?.let(onFirstCardRequesterReady)
    }

    LaunchedEffect(pendingFocusRailKey, lastFocusedIndex, itemRequesters.size) {
        if (pendingFocusRailKey != railKey || itemRequesters.isEmpty()) return@LaunchedEffect
        val targetIndex = lastFocusedIndex.coerceIn(0, itemRequesters.lastIndex)
        runCatching { rowState.scrollToItem(targetIndex) }
        runCatching { itemRequesters[targetIndex].requestFocus() }
        onPendingFocusConsumed()
    }

    LazyRow(
        modifier = modifier.padding(top = NetflixHomeSpacing.RailTopPadding),
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = NetflixHomeTokens.PageHorizontalPadding, vertical = 8.dp)
    ) {
        itemsIndexed(genres, key = { _, item -> item.key }) { index, genre ->
            NetflixGenreCard(
                label = genre.label,
                focusRequester = itemRequesters[index],
                onFocus = {
                    onFocusedItemChanged(index, genre.key)
                },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onClick = { onGenreSelected(genre) }
            )
        }
    }
}

@Composable
private fun NetflixGenreCard(
    label: String,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "netflixGenreScale"
    )
    val shape = RoundedCornerShape(13.dp)

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (keyEvent.key) {
                        Key.DirectionUp -> onMoveUp()
                        Key.DirectionDown -> onMoveDown()
                        else -> false
                    }
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (focused) 1f else 0.86f
                shadowElevation = if (focused) 14f else 2f
            }
            .size(NetflixHomeTokens.GenreCardWidth, NetflixHomeTokens.GenreCardHeight)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (focused) 0.35f else 0.20f),
                        Color.White.copy(alpha = if (focused) 0.12f else 0.06f)
                    )
                ),
                shape = shape
            )
            .border(
                width = if (focused) NetflixHomeTokens.FocusBorder else 1.dp,
                color = if (focused) NetflixHomeTokens.Focus else Color.White.copy(alpha = 0.10f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = NetflixHomeTokens.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
