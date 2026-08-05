@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.ui.components.collectionFolderCardImageUrl

@Composable
internal fun NetflixCollectionRail(
    railKey: String,
    collection: Collection,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    onFolderClick: (String, String) -> Unit,
    onFocusedItemChanged: (Int, String) -> Unit,
    onPendingFocusConsumed: () -> Unit,
    onFirstCardRequesterReady: (FocusRequester) -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    modifier: Modifier = Modifier
) {
    if (collection.folders.isEmpty()) return

    val density = LocalDensity.current
    val itemRequesters = remember(railKey, collection.folders.size) {
        List(collection.folders.size) { FocusRequester() }
    }

    NetflixRailScaffold(
        title = collection.title,
        subtitle = null,
        railKey = railKey,
        pendingFocusRailKey = pendingFocusRailKey,
        lastFocusedIndex = lastFocusedIndex,
        itemRequesters = itemRequesters,
        onPendingFocusConsumed = onPendingFocusConsumed,
        onFirstCardRequesterReady = onFirstCardRequesterReady,
        modifier = modifier
    ) { rowState, focusedIndex, onCardFocused ->
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(NetflixHomeSpacing.railHorizontalGap(density)),
            contentPadding = PaddingValues(
                horizontal = NetflixHomeTokens.PageHorizontalPadding,
                vertical = NetflixHomeSpacing.RailFocusPadding
            )
        ) {
            itemsIndexed(
                items = collection.folders,
                key = { _, folder -> folder.id }
            ) { index, folder ->
                val itemKey = "collection|${collection.id}|${folder.id}"
                val focused = index == focusedIndex
                NetflixMediaCard(
                    mediaKey = itemKey,
                    title = folder.title,
                    subtitle = null,
                    imageUrl = collectionFolderCardImageUrl(folder, focused),
                    width = if (focused) {
                        NetflixHomeTokens.FocusedLandscapeCardWidth
                    } else {
                        NetflixHomeTokens.LandscapeCardWidth
                    },
                    height = if (focused) {
                        NetflixHomeTokens.FocusedLandscapeCardHeight
                    } else {
                        NetflixHomeTokens.LandscapeCardHeight
                    },
                    showLabels = !folder.hideTitle,
                    showFallbackTitleWhenArtworkMissing = true,
                    focusRequester = itemRequesters[index],
                    onClick = { onFolderClick(collection.id, folder.id) },
                    onFocus = {
                        onCardFocused(index)
                        onFocusedItemChanged(index, itemKey)
                    },
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    trapLeft = index == 0,
                    trapRight = index == collection.folders.lastIndex
                )
            }
        }
    }
}
