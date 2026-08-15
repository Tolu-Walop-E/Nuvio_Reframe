package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
    /** Pack-global landscape tile scale (1 = Netflix default). */
    landscapeScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    if (collection.folders.isEmpty()) return

    val scale = landscapeScale.coerceIn(0.55f, 2.5f)
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
    ) { rowState, focusedIndex, onCardFocused, onMoveLeft, onMoveRight, railHasFocus ->
        NetflixPivotLazyRow(
            state = rowState,
            selectorVisible = railHasFocus,
            selectorWidth = NetflixHomeTokens.FocusedLandscapeCardWidth * scale,
            selectorHeight = NetflixHomeTokens.FocusedLandscapeCardHeight * scale
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
                            NetflixHomeTokens.FocusedLandscapeCardWidth * scale
                        } else {
                            NetflixHomeTokens.LandscapeCardWidth * scale
                        },
                        height = if (focused) {
                            NetflixHomeTokens.FocusedLandscapeCardHeight * scale
                        } else {
                            NetflixHomeTokens.LandscapeCardHeight * scale
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
                        onMoveLeft = onMoveLeft,
                        onMoveRight = onMoveRight,
                        showFocusBorder = false
                    )
                }
        }
    }
}
