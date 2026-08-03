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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import kotlin.math.floor

@Composable
internal fun NetflixContinueWatchingRail(
    railKey: String,
    title: String,
    items: List<ContinueWatchingItem>,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    onItemClick: (ContinueWatchingItem) -> Unit,
    onItemLongClick: (ContinueWatchingItem) -> Unit,
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
    var focusedItem by remember(items) { mutableStateOf(items.getOrNull(lastFocusedIndex.coerceIn(0, items.lastIndex))) }
    var settledItem by remember(items) { mutableStateOf(focusedItem) }

    LaunchedEffect(focusedItem?.netflixKey()) {
        val candidate = focusedItem
        kotlinx.coroutines.delay(220L)
        if (focusedItem?.netflixKey() == candidate?.netflixKey()) {
            settledItem = candidate
        }
    }

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
                    height = if (focused) {
                        NetflixHomeTokens.FocusedContinueCardHeight
                    } else {
                        NetflixHomeTokens.ContinueCardHeight
                    },
                    progress = card.progress,
                    showLabels = false,
                    showFallbackTitleWhenArtworkMissing = false,
                    focusRequester = itemRequesters[index],
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    onFocus = {
                        onCardFocused(index)
                        focusedItem = item
                        onFocusedItemChanged(index, itemKey)
                        onItemFocused(item)
                    },
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    trapLeft = index == 0,
                    trapRight = index == items.lastIndex
                )
            }
        }
        Box(
            modifier = Modifier
                .height(NetflixHomeSpacing.ContinueMetadataHeight)
                .padding(
                    start = NetflixHomeTokens.PageHorizontalPadding,
                    top = 8.dp,
                    end = NetflixHomeTokens.PageHorizontalPadding
                )
        ) {
            Crossfade(
                targetState = settledItem ?: focusedItem,
                animationSpec = tween(durationMillis = 200),
                label = "netflixContinueWatchingMetadata"
            ) { item ->
                item?.let { NetflixContinueWatchingMetadata(item = it) }
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
    trailerPreviewUrls: Map<String, String> = emptyMap(),
    trailerPreviewAudioUrls: Map<String, String> = emptyMap(),
    trailerEnabled: Boolean = false,
    trailerMuted: Boolean = true,
    onRequestTrailerPreview: (MetaPreview) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (row.items.isEmpty()) return
    val density = LocalDensity.current
    val itemRequesters = remember(railKey, row.items.size) { List(row.items.size) { FocusRequester() } }
    var focusedMeta by remember(row.items) { mutableStateOf(row.items.getOrNull(lastFocusedIndex)) }
    var settledMeta by remember(row.items) { mutableStateOf(focusedMeta) }
    val playedTrailerIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(focusedMeta?.id) {
        val candidate = focusedMeta
        if (candidate != null && trailerEnabled) {
            onRequestTrailerPreview(candidate)
        }
        kotlinx.coroutines.delay(80L)
        if (focusedMeta?.id == candidate?.id) {
            settledMeta = candidate
        }
    }

    NetflixRailScaffold(
        title = row.catalogName.replaceFirstChar { it.uppercase() },
        subtitle = null,
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
                    val trailerUrl = trailerPreviewUrls[item.id]
                    // Do not gate on rail focusedIndex — every visible rail keeps a
                    // "focused" index, which would start multiple TrailerPlayers and
                    // fight over the shared ExoPlayer. NetflixMediaCard already gates
                    // on its own real focus state.
                    val playTrailer = trailerEnabled &&
                        playedTrailerIds[item.id] != true &&
                        !trailerUrl.isNullOrBlank()
                    if (focused && trailerEnabled) {
                        android.util.Log.i(
                            "NetflixTrailer",
                            "card-focus id=${item.id} urlPresent=${!trailerUrl.isNullOrBlank()}"
                        )
                    }
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
                        trailerUrl = trailerUrl,
                        trailerAudioUrl = trailerPreviewAudioUrls[item.id],
                        playTrailer = playTrailer,
                        trailerMuted = trailerMuted,
                        onTrailerEnded = { playedTrailerIds[item.id] = true },
                        onClick = { onItemClick(item, row.addonBaseUrl) },
                        onFocus = {
                            onCardFocused(index)
                            focusedMeta = item
                            onFocusedItemChanged(index, itemKey)
                            onItemFocused(item)
                            if (trailerEnabled) {
                                onRequestTrailerPreview(item)
                            }
                            if (row.hasMore && index >= row.items.lastIndex - 5) {
                                onLoadMore(row.catalogId, row.addonId, row.apiType)
                            }
                        },
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        trapLeft = index == 0,
                        trapRight = index == row.items.lastIndex,
                        onLongClick = { onItemLongClick(item, row.addonBaseUrl) }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .height(NetflixHomeSpacing.FocusedMetadataHeight)
                .padding(
                    start = NetflixHomeTokens.PageHorizontalPadding,
                    top = 12.dp,
                    end = NetflixHomeTokens.PageHorizontalPadding
                )
        ) {
            Crossfade(
                targetState = settledMeta,
                animationSpec = tween(durationMillis = 200),
                label = "netflixCatalogFocusedMetadata"
            ) { item ->
                if (item != null) {
                    NetflixFocusedCatalogMetadata(
                        item = item,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
internal fun NetflixRailScaffold(
    title: String,
    subtitle: String? = null,
    railKey: String,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    itemRequesters: List<FocusRequester>,
    onPendingFocusConsumed: () -> Unit,
    onFirstCardRequesterReady: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LazyListState, Int, (Int) -> Unit) -> Unit
) {
    var focusedIndex by remember(railKey, itemRequesters.size) {
        mutableStateOf(lastFocusedIndex.coerceIn(0, itemRequesters.lastIndex))
    }
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
        focusedIndex = targetIndex
        runCatching { itemRequesters[targetIndex].requestFocus() }
        onPendingFocusConsumed()
    }

    Column(
        modifier = modifier
            .padding(top = NetflixHomeSpacing.RailTopPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    color = NetflixHomeTokens.TextPrimary,
                    style = NetflixHomeTypography.RowTitle,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = NetflixHomeTokens.TextSecondary,
                        style = NetflixHomeTypography.RowSubtitle,
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth(0.62f)) {
        val density = LocalDensity.current
        val synopsisLineHeight = with(density) {
            NetflixHomeTypography.Synopsis.lineHeight.toDp()
        }
        val metadataLineHeight = with(density) {
            NetflixHomeTypography.Metadata.lineHeight.toDp()
        }
        val synopsisGap = 8.dp
        val availableForSynopsis = (maxHeight - metadataLineHeight - synopsisGap)
            .coerceAtLeast(0.dp)
        val maxSynopsisLines = floor(
            availableForSynopsis / synopsisLineHeight.coerceAtLeast(1.dp)
        ).toInt().coerceIn(3, 7)

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item.imdbRating?.let {
                    Text(
                        text = "${String.format("%.0f", it * 10)}%",
                        color = Color(0xFF31D76B),
                        style = NetflixHomeTypography.Metadata,
                        fontWeight = FontWeight.Bold
                    )
                }
                item.metadataFacts().forEach { fact ->
                    Text(
                        text = fact,
                        color = NetflixHomeTokens.TextPrimary,
                        style = NetflixHomeTypography.Metadata,
                        maxLines = 1
                    )
                }
            }
            if (!item.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(synopsisGap))
                Text(
                    text = item.description,
                    color = NetflixHomeTokens.TextSecondary,
                    style = NetflixHomeTypography.Synopsis,
                    maxLines = maxSynopsisLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NetflixContinueWatchingMetadata(
    item: ContinueWatchingItem,
    modifier: Modifier = Modifier
) {
    val card = item.toNetflixCard()
    Column(modifier = modifier.fillMaxWidth(0.52f)) {
        Text(
            text = card.title,
            color = NetflixHomeTokens.TextPrimary,
            style = NetflixHomeTypography.ContinueTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!card.subtitle.isNullOrBlank()) {
            Text(
                text = card.subtitle,
                color = NetflixHomeTokens.TextSecondary,
                style = NetflixHomeTypography.ContinueSecondary,
                maxLines = 1,
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

private fun MetaPreview.metadataFacts(): List<String> {
    return buildList {
        releaseInfo?.let { value ->
            Regex("""\b(19|20)\d{2}\b""").find(value)?.value?.let(::add)
        }
        ageRating?.takeIf { it.isNotBlank() }?.let(::add)
        genres.take(2)
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?.let(::add)
        runtime?.takeIf { it.isNotBlank() && it != "0 min" }?.let(::add)
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
