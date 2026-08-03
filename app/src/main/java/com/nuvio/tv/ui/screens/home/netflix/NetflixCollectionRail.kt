@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
        subtitle = when {
            collection.title.equals("Streaming Services", ignoreCase = true) ->
                "Jump into a service · ${collection.folders.size} boards"
            collection.title.equals("Studios & Labels", ignoreCase = true) ->
                "Browse by studio · ${collection.folders.size} labels"
            collection.title.equals("Actors", ignoreCase = true) ->
                "People to follow · ${collection.folders.size} names"
            collection.title.equals("Directors", ignoreCase = true) ->
                "Auteur shelves · ${collection.folders.size} directors"
            collection.title.equals("Film Collections", ignoreCase = true) ->
                "Franchises & universes · ${collection.folders.size} sets"
            collection.title.equals("By Decade", ignoreCase = true) ->
                "Time travel · ${collection.folders.size} eras"
            collection.title.equals("Genres", ignoreCase = true) ->
                "Genre boards · ${collection.folders.size} moods"
            else -> "Browse hub · ${collection.folders.size} curated sets"
        },
        railKey = railKey,
        pendingFocusRailKey = pendingFocusRailKey,
        lastFocusedIndex = lastFocusedIndex,
        itemRequesters = itemRequesters,
        onPendingFocusConsumed = onPendingFocusConsumed,
        onFirstCardRequesterReady = onFirstCardRequesterReady,
        modifier = modifier
    ) { rowState, focusedIndex, onCardFocused ->
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val usableWidth = (maxWidth - (NetflixHomeTokens.PageHorizontalPadding * 2))
                .coerceAtLeast(0.dp)
            val geometry = remember(usableWidth, density) {
                NetflixHomeDimensions.catalogueRailGeometry(usableWidth, density)
            }
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
                        width = if (focused) geometry.focusedWidth else geometry.portraitWidth,
                        height = geometry.railHeight,
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
}
