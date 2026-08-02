@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem

@Composable
internal fun NetflixContinueWatchingRail(
    railKey: String,
    title: String,
    items: List<ContinueWatchingItem>,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    onItemClick: (ContinueWatchingItem) -> Unit,
    onItemFocused: (ContinueWatchingItem) -> Unit,
    onFocusedItemChanged: (Int, String) -> Unit,
    onPendingFocusConsumed: () -> Unit,
    onFirstCardRequesterReady: (FocusRequester) -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    val itemRequesters = remember(railKey, items.size) { List(items.size) { FocusRequester() } }
    NetflixRailScaffold(
        title = title,
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
            itemsIndexed(items, key = { _, item -> item.netflixKey() }) { index, item ->
                val card = item.toNetflixCard()
                val itemKey = item.netflixKey()
                val focused = index == focusedIndex
                NetflixMediaCard(
                    mediaKey = itemKey,
                    title = card.title,
                    subtitle = card.subtitle,
                    imageUrl = card.imageUrl,
                    width = if (focused) NetflixHomeTokens.FocusedContinueCardWidth else NetflixHomeTokens.ContinueCardWidth,
                    height = if (focused) NetflixHomeTokens.FocusedContinueCardHeight else NetflixHomeTokens.ContinueCardHeight,
                    progress = card.progress,
                    focusRequester = itemRequesters[index],
                    onClick = { onItemClick(item) },
                    onFocus = {
                        onCardFocused(index)
                        onFocusedItemChanged(index, itemKey)
                        onItemFocused(item)
                    },
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown
                )
            }
        }
    }
}

@Composable
internal fun NetflixCatalogRail(
    railKey: String,
    row: CatalogRow,
    useLandscapeCards: Boolean,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    onItemClick: (MetaPreview, String) -> Unit,
    onItemFocused: (MetaPreview) -> Unit,
    onItemLongClick: (MetaPreview, String) -> Unit,
    onLoadMore: (String, String, String) -> Unit,
    onFocusedItemChanged: (Int, String) -> Unit,
    onPendingFocusConsumed: () -> Unit,
    onFirstCardRequesterReady: (FocusRequester) -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    posterLabelsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (row.items.isEmpty()) return
    val density = LocalDensity.current
    val itemRequesters = remember(railKey, row.items.size) { List(row.items.size) { FocusRequester() } }
    var focusedMeta by remember(row.items) { mutableStateOf(row.items.getOrNull(lastFocusedIndex)) }
    var settledMeta by remember(row.items) { mutableStateOf(focusedMeta) }

    LaunchedEffect(focusedMeta?.id) {
        val candidate = focusedMeta
        kotlinx.coroutines.delay(240L)
        if (focusedMeta?.id == candidate?.id) {
            settledMeta = candidate
        }
    }

    NetflixRailScaffold(
        title = row.catalogName.replaceFirstChar { it.uppercase() },
        subtitle = "Today's Top Picks for You",
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
                itemsIndexed(row.items, key = { index, item -> item.netflixCatalogItemKey(row, index) }) { index, item ->
                    val landscape = useLandscapeCards || item.posterShape == PosterShape.LANDSCAPE
                    val focused = index == focusedIndex
                    val itemKey = item.netflixCatalogItemKey(row, index)
                    val artwork = item.netflixCatalogueArtwork(focused = focused, preferLandscapeWhenUnfocused = landscape)
                    NetflixMediaCard(
                        mediaKey = itemKey,
                        title = item.name,
                        subtitle = item.releaseInfo,
                        imageUrl = artwork,
                        width = if (focused) geometry.focusedWidth else geometry.portraitWidth,
                        height = geometry.railHeight,
                        showLabels = posterLabelsEnabled && NetflixHomeTokens.ShowCataloguePosterLabels,
                        showFallbackTitleWhenArtworkMissing = focused,
                        focusRequester = itemRequesters[index],
                        onClick = { onItemClick(item, row.addonBaseUrl) },
                        onFocus = {
                            onCardFocused(index)
                            focusedMeta = item
                            onFocusedItemChanged(index, itemKey)
                            onItemFocused(item)
                            if (row.hasMore && index >= row.items.lastIndex - 5) {
                                onLoadMore(row.catalogId, row.addonId, row.apiType)
                            }
                        },
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onLongClick = { onItemLongClick(item, row.addonBaseUrl) }
                    )
                }
            }
        }
        Crossfade(
            targetState = settledMeta,
            animationSpec = tween(durationMillis = 200),
            label = "netflixCatalogFocusedMetadata"
        ) { item ->
            if (item != null) {
                NetflixFocusedCatalogMetadata(
                    item = item,
                    modifier = Modifier.padding(
                        start = NetflixHomeTokens.PageHorizontalPadding,
                        top = 2.dp,
                        end = NetflixHomeTokens.PageHorizontalPadding
                    )
                )
            }
        }
    }
}

@Composable
private fun NetflixRailScaffold(
    title: String,
    subtitle: String? = null,
    railKey: String,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    itemRequesters: List<FocusRequester>,
    onPendingFocusConsumed: () -> Unit,
    onFirstCardRequesterReady: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LazyListState, Int?, (Int) -> Unit) -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    var lastBroughtIntoViewIndex by remember { mutableStateOf<Int?>(null) }
    val horizontalListState = rememberLazyListState(
        initialFirstVisibleItemIndex = lastFocusedIndex.coerceAtLeast(0)
    )

    LaunchedEffect(itemRequesters) {
        itemRequesters.firstOrNull()?.let(onFirstCardRequesterReady)
    }

    LaunchedEffect(pendingFocusRailKey, itemRequesters.size, lastFocusedIndex) {
        if (pendingFocusRailKey != railKey || itemRequesters.isEmpty()) {
            return@LaunchedEffect
        }
        val targetIndex = lastFocusedIndex.coerceIn(0, itemRequesters.lastIndex)
        runCatching { horizontalListState.scrollToItem(targetIndex) }
        runCatching { itemRequesters[targetIndex].requestFocus() }
        bringIntoViewRequester.bringIntoView()
        onPendingFocusConsumed()
    }

    LaunchedEffect(focusedIndex) {
        val targetIndex = focusedIndex ?: return@LaunchedEffect
        if (lastBroughtIntoViewIndex == targetIndex) {
            return@LaunchedEffect
        }
        bringIntoViewRequester.bringIntoView()
        lastBroughtIntoViewIndex = targetIndex
    }

    Column(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(NetflixHomeTokens.Accent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = NetflixHomeTokens.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = NetflixHomeTokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        content(horizontalListState, focusedIndex) { index -> focusedIndex = index }
    }
}

@Composable
private fun NetflixFocusedCatalogMetadata(
    item: MetaPreview,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(0.52f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.imdbRating?.let {
                Text(
                    text = "${String.format("%.0f", it * 10)}%",
                    color = Color(0xFF31D76B),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            item.releaseInfo?.let { fact ->
                Text(text = fact.take(4), color = NetflixHomeTokens.TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            item.genres.firstOrNull()?.let { genre ->
                Text(text = genre, color = NetflixHomeTokens.TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            item.runtime?.let { runtime ->
                Text(text = runtime, color = NetflixHomeTokens.TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!item.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                color = NetflixHomeTokens.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class NetflixCardData(
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val progress: Float?
)

private fun ContinueWatchingItem.netflixKey(): String {
    return when (this) {
        is ContinueWatchingItem.InProgress -> "progress|${progress.contentId}|${progress.videoId}"
        is ContinueWatchingItem.NextUp -> "next|${info.contentId}|${info.videoId}"
    }
}

internal fun CatalogRow.netflixRailKey(): String {
    return "catalog|$addonId|$addonBaseUrl|$apiType|$catalogId"
}

private fun MetaPreview.netflixCatalogItemKey(row: CatalogRow, index: Int): String {
    return "${row.addonId}|${row.catalogId}|$apiType|$id|$index"
}

private fun MetaPreview.netflixCatalogueArtwork(
    focused: Boolean,
    preferLandscapeWhenUnfocused: Boolean
): String? {
    return if (focused) {
        background ?: landscapePoster ?: poster
    } else if (preferLandscapeWhenUnfocused) {
        background ?: landscapePoster ?: poster
    } else {
        poster ?: background ?: landscapePoster
    }
}

private fun ContinueWatchingItem.toNetflixCard(): NetflixCardData {
    return when (this) {
        is ContinueWatchingItem.InProgress -> NetflixCardData(
            title = progress.name,
            subtitle = listOfNotNull(
                progress.season?.let { season -> progress.episode?.let { episode -> "S${season}:E${episode}" } },
                progress.episodeTitle
            ).firstOrNull(),
            imageUrl = episodeThumbnail ?: progress.backdrop ?: progress.poster,
            progress = progress.progressPercentage
        )

        is ContinueWatchingItem.NextUp -> NetflixCardData(
            title = info.name,
            subtitle = "S${info.season}:E${info.episode}" + (info.episodeTitle?.let { "  $it" } ?: ""),
            imageUrl = info.thumbnail ?: info.backdrop ?: info.poster,
            progress = null
        )
    }
}
