@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.graphics.Color
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
import android.view.KeyEvent as AndroidKeyEvent
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.util.rememberLongPressKeyTracker

internal data class NetflixGenreChip(
    val key: String,
    val label: String,
    val catalogId: String,
    val addonId: String,
    val type: String,
    val genreFilter: String?,
    /** When set, Home can open the full collection folder (movie + series tabs). */
    val collectionId: String? = null,
    val folderId: String? = null
)

/**
 * Genre rail in the scrollable home stack (after Continue Watching).
 * Styling matches Netflix category tiles: dark-glass rounded rectangles + thick focus ring.
 */
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
    onFirstItemMoveLeft: () -> Boolean = { false },
    onGenreSelected: (NetflixGenreChip) -> Unit,
    onGenreLongPressed: (NetflixGenreChip) -> Unit,
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
        if (rowState.firstVisibleItemIndex != targetIndex) {
            runCatching { rowState.scrollToItem(targetIndex) }
        }
        if (itemRequesters[targetIndex].requestFocusAfterFrames(2)) {
            onPendingFocusConsumed()
        }
    }

    LazyRow(
        modifier = modifier.padding(top = NetflixHomeSpacing.RailTopPadding),
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = NetflixHomeTokens.PageHorizontalPadding)
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
                onMoveLeft = if (index == 0) onFirstItemMoveLeft else null,
                trapLeft = index == 0,
                trapRight = index == genres.lastIndex,
                onClick = { onGenreSelected(genre) },
                onLongClick = { onGenreLongPressed(genre) }
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
    onMoveLeft: (() -> Boolean)? = null,
    trapLeft: Boolean = false,
    trapRight: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Netflix category tiles: rounded rectangles, not capsules.
    val shape = RoundedCornerShape(NetflixHomeTokens.GenreTileCorner)
    val longPressKeyTracker = rememberLongPressKeyTracker()
    val ringWidth = NetflixHomeTokens.FocusBorder

    Box(
        modifier = Modifier
            .height(NetflixHomeTokens.GenrePillHeight)
            .wrapContentWidth()
            .defaultMinSize(minWidth = NetflixHomeTokens.GenreTileMinWidth)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                    onLongClick()
                    return@onPreviewKeyEvent true
                }
                if (longPressKeyTracker.handle(native, ::isGenreSelectKey, onLongClick)) {
                    return@onPreviewKeyEvent true
                }
                when (keyEvent.key) {
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter -> {
                        if (keyEvent.type == KeyEventType.KeyUp) onClick()
                        true
                    }

                    Key.DirectionUp -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            onMoveUp()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            onMoveDown()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> if (keyEvent.type == KeyEventType.KeyDown && trapLeft) {
                        onMoveLeft?.invoke() ?: true
                    } else {
                        false
                    }
                    Key.DirectionRight -> keyEvent.type == KeyEventType.KeyDown && trapRight
                    else -> false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .background(
                // Dark glass fill — Netflix category chips sit as muted charcoal tiles.
                color = if (focused) {
                    Color(0xFF3A3A3A)
                } else {
                    Color(0xFF2A2A2A).copy(alpha = 0.92f)
                },
                shape = shape
            )
            .border(
                width = ringWidth,
                color = if (focused) {
                    NetflixThemeChrome.focus
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = NetflixHomeTokens.GenreTileHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = NetflixThemeChrome.textPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun isGenreSelectKey(keyCode: Int): Boolean {
    return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
}
