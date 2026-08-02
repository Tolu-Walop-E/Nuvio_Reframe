package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.HomeScreenFocusState
import com.nuvio.tv.ui.screens.home.HomeUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NetflixHomeContent(
    uiState: HomeUiState,
    focusState: HomeScreenFocusState,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit = {},
    onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit = {},
    showContinueWatchingManualPlayOption: Boolean = false,
    onLoadMoreCatalog: (String, String, String) -> Unit,
    onRemoveContinueWatching: (String, Int?, Int?, Boolean) -> Unit,
    isCatalogItemWatched: (MetaPreview) -> Boolean = { false },
    onCatalogItemLongPress: (MetaPreview, String) -> Unit = { _, _ -> },
    onNavigateToFolderDetail: (String, String) -> Unit = { _, _ -> },
    onItemFocus: (MetaPreview) -> Unit = {},
    onPreloadAdjacentItem: (MetaPreview) -> Unit = {},
    trailerPreviewUrls: Map<String, String> = emptyMap(),
    trailerPreviewAudioUrls: Map<String, String> = emptyMap(),
    onRequestTrailerPreview: (String, String, String?, String) -> Unit = { _, _, _, _ -> },
    onSaveFocusState: (Int, Int, String?, Map<String, String>, Map<String, Int>, Int, Int) -> Unit,
    scrollToTopTrigger: Int = 0,
    onRequestLazyCatalogLoad: (String) -> Unit = {}
) {
    var heroItem by remember(uiState.heroItems, uiState.catalogRows, uiState.continueWatchingItems) {
        mutableStateOf(resolveInitialHero(uiState))
    }
    var pendingHeroItem by remember { mutableStateOf(heroItem) }
    var focusedTopNavigationIndex by remember { mutableStateOf(1) }
    val topNavigationRequesters = remember { List(NETFLIX_TOP_NAV_ITEM_COUNT) { FocusRequester() } }
    val heroPrimaryRequester = remember { FocusRequester() }
    val heroSecondaryRequester = remember { FocusRequester() }
    val firstCardRequestersByRail = remember { mutableStateMapOf<String, FocusRequester>() }
    val lastFocusedIndexByRail = remember { mutableStateMapOf<String, Int>() }
    val requestedTrailerKeys = remember { mutableStateMapOf<String, Boolean>() }
    val playedTrailerKeys = remember { mutableStateMapOf<String, Boolean>() }
    var pendingFocusRailKey by remember { mutableStateOf<String?>(null) }
    var restoredSavedFocus by remember { mutableStateOf(false) }
    var previewTrailerHeroKey by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val genreChips = remember(uiState.catalogRows) {
        buildGenreChips(uiState.catalogRows)
    }
    val railKeys = remember(uiState.continueWatchingItems, uiState.catalogRows, genreChips) {
        buildList {
            if (genreChips.isNotEmpty()) {
                add(NETFLIX_GENRE_RAIL_KEY)
            }
            if (uiState.continueWatchingItems.isNotEmpty()) {
                add(NETFLIX_CONTINUE_WATCHING_RAIL_KEY)
            }
            uiState.catalogRows
                .filter { row -> row.items.isNotEmpty() }
                .forEach { row -> add(row.netflixRailKey()) }
        }
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            runCatching { listState.animateScrollToItem(0) }
            runCatching {
                topNavigationRequesters
                    .getOrElse(focusedTopNavigationIndex) { topNavigationRequesters[1] }
                    .requestFocus()
            }
        }
    }

    LaunchedEffect(pendingHeroItem?.key) {
        val candidate = pendingHeroItem
        delay(NETFLIX_METADATA_SETTLE_DELAY_MS)
        if (pendingHeroItem?.key == candidate?.key) {
            heroItem = candidate
        }
    }

    LaunchedEffect(heroItem?.key, listState.isScrollInProgress) {
        val stableHero = heroItem ?: run {
            previewTrailerHeroKey = null
            return@LaunchedEffect
        }
        previewTrailerHeroKey = null
        if (!uiState.focusedPosterBackdropTrailerEnabled || listState.isScrollInProgress) {
            return@LaunchedEffect
        }
        val catalogTarget = stableHero.target as? NetflixHomeTarget.Catalog ?: return@LaunchedEffect
        val item = catalogTarget.item
        if (playedTrailerKeys[stableHero.key] == true) {
            return@LaunchedEffect
        }
        delay(NETFLIX_TRAILER_SETTLE_DELAY_MS)
        if (heroItem?.key != stableHero.key || listState.isScrollInProgress) {
            return@LaunchedEffect
        }
        if (requestedTrailerKeys[stableHero.key] != true && trailerPreviewUrls[item.id].isNullOrBlank()) {
            onRequestTrailerPreview(item.id, item.name, item.releaseInfo, item.apiType)
            requestedTrailerKeys[stableHero.key] = true
        }
        previewTrailerHeroKey = stableHero.key
    }

    LaunchedEffect(focusState, railKeys) {
        val focusedRowKey = focusState.focusedRowKey
        if (!restoredSavedFocus && focusState.hasSavedFocus && focusedRowKey != null && focusedRowKey in railKeys) {
            lastFocusedIndexByRail[focusedRowKey] = focusState.focusedItemIndex
            runCatching {
                listState.scrollToItem(railKeys.indexOf(focusedRowKey) + NETFLIX_HOME_STATIC_ROW_COUNT)
            }
            pendingFocusRailKey = focusedRowKey
            restoredSavedFocus = true
        }
    }

    fun requestRailFocus(railKey: String?): Boolean {
        if (railKey == null) {
            return true
        }
        pendingFocusRailKey = railKey
        val lazyListIndex = railKeys.indexOf(railKey) + NETFLIX_HOME_STATIC_ROW_COUNT
        if (lazyListIndex >= NETFLIX_HOME_STATIC_ROW_COUNT) {
            coroutineScope.launch {
                runCatching { listState.animateScrollToItem(lazyListIndex) }
            }
            return true
        }
        return false
    }

    fun saveRailFocus(railKey: String, itemKey: String, railIndex: Int, itemIndex: Int) {
        lastFocusedIndexByRail[railKey] = itemIndex
        onSaveFocusState(
            railIndex + NETFLIX_HOME_STATIC_ROW_COUNT,
            0,
            railKey,
            mapOf(railKey to itemKey),
            emptyMap(),
            railIndex,
            itemIndex
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixHomeTokens.Background)
    ) {
        Crossfade(
            targetState = heroItem?.backdrop ?: heroItem?.poster,
            animationSpec = tween(640),
            label = "netflixPageBackdrop"
        ) { imageUrl ->
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.20f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.72f),
                                0.42f to Color.Black.copy(alpha = 0.90f),
                                1f to Color.Black
                            )
                        )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(NetflixHomeTokens.RailSpacing)
        ) {
            item(key = "top_nav") {
                NetflixTopNavigation(
                    itemFocusRequesters = topNavigationRequesters,
                    selectedIndex = focusedTopNavigationIndex,
                    downRequester = heroPrimaryRequester,
                    onFocusedIndexChanged = { focusedTopNavigationIndex = it }
                )
            }
            item(key = "hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    NetflixHero(
                        item = heroItem,
                        modifier = Modifier,
                        topNavigationRequester = topNavigationRequesters.getOrElse(focusedTopNavigationIndex) { topNavigationRequesters[1] },
                        primaryActionRequester = heroPrimaryRequester,
                        secondaryActionRequester = heroSecondaryRequester,
                        onMoveDownFromHero = {
                            requestRailFocus(railKeys.firstOrNull())
                        },
                        trailerPreviewUrl = heroItem?.catalogItemId()?.let { trailerPreviewUrls[it] },
                        trailerPreviewAudioUrl = heroItem?.catalogItemId()?.let { trailerPreviewAudioUrls[it] },
                        playTrailerPreview = previewTrailerHeroKey == heroItem?.key &&
                            heroItem?.key?.let { playedTrailerKeys[it] } != true &&
                            !heroItem?.catalogItemId()?.let { trailerPreviewUrls[it] }.isNullOrBlank(),
                        trailerPreviewMuted = uiState.focusedPosterBackdropTrailerMuted,
                        onTrailerEnded = {
                            heroItem?.key?.let { playedTrailerKeys[it] = true }
                            previewTrailerHeroKey = null
                        },
                        onViewDetails = { target -> navigateToTargetDetails(target, onNavigateToDetail) },
                        onPlay = { target ->
                            when (target) {
                                is NetflixHomeTarget.ContinueWatching -> onContinueWatchingClick(target.item)
                                is NetflixHomeTarget.Catalog -> navigateToTargetDetails(target, onNavigateToDetail)
                            }
                        }
                    )
                }
            }
            if (genreChips.isNotEmpty()) {
                item(key = NETFLIX_GENRE_RAIL_KEY) {
                    val railIndex = railKeys.indexOf(NETFLIX_GENRE_RAIL_KEY)
                    NetflixGenreRail(
                        railKey = NETFLIX_GENRE_RAIL_KEY,
                        genres = genreChips,
                        pendingFocusRailKey = pendingFocusRailKey,
                        lastFocusedIndex = lastFocusedIndexByRail[NETFLIX_GENRE_RAIL_KEY] ?: 0,
                        onFocusedItemChanged = { itemIndex, itemKey ->
                            if (railIndex >= 0) {
                                saveRailFocus(NETFLIX_GENRE_RAIL_KEY, itemKey, railIndex, itemIndex)
                            }
                        },
                        onPendingFocusConsumed = { pendingFocusRailKey = null },
                        onFirstCardRequesterReady = { requester ->
                            firstCardRequestersByRail[NETFLIX_GENRE_RAIL_KEY] = requester
                        },
                        onMoveUp = { runCatching { heroPrimaryRequester.requestFocus() }.isSuccess },
                        onMoveDown = {
                            requestRailFocus(railKeys.getOrNull(railIndex + 1))
                        },
                        onGenreSelected = { genre ->
                            requestRailFocus(genre.targetRailKey ?: railKeys.getOrNull(railIndex + 1))
                        }
                    )
                }
            }
            if (uiState.continueWatchingItems.isNotEmpty()) {
                item(key = "continue_watching") {
                val railIndex = railKeys.indexOf(NETFLIX_CONTINUE_WATCHING_RAIL_KEY)
                NetflixContinueWatchingRail(
                    railKey = NETFLIX_CONTINUE_WATCHING_RAIL_KEY,
                    title = "Continue Watching",
                    items = uiState.continueWatchingItems,
                    pendingFocusRailKey = pendingFocusRailKey,
                    lastFocusedIndex = lastFocusedIndexByRail[NETFLIX_CONTINUE_WATCHING_RAIL_KEY] ?: 0,
                    onItemClick = onContinueWatchingClick,
                    onItemFocused = { item -> pendingHeroItem = item.toNetflixHeroItem() },
                    onFocusedItemChanged = { itemIndex, itemKey ->
                        if (railIndex >= 0) {
                            saveRailFocus(NETFLIX_CONTINUE_WATCHING_RAIL_KEY, itemKey, railIndex, itemIndex)
                        }
                    },
                    onPendingFocusConsumed = { pendingFocusRailKey = null },
                    onFirstCardRequesterReady = { requester ->
                        firstCardRequestersByRail[NETFLIX_CONTINUE_WATCHING_RAIL_KEY] = requester
                    },
                    onMoveUp = { runCatching { heroPrimaryRequester.requestFocus() }.isSuccess },
                    onMoveDown = {
                        if (railIndex >= 0) {
                            requestRailFocus(railKeys.getOrNull(railIndex + 1))
                        } else {
                            false
                        }
                    }
                )
                }
            }
            items(
                items = uiState.catalogRows.filter { row -> row.items.isNotEmpty() },
                key = { row -> row.netflixRailKey() }
            ) { row ->
                val railKey = row.netflixRailKey()
                val railIndex = railKeys.indexOf(railKey)
                NetflixCatalogRail(
                    railKey = railKey,
                    row = row,
                    useLandscapeCards = uiState.modernLandscapePostersEnabled,
                    pendingFocusRailKey = pendingFocusRailKey,
                    lastFocusedIndex = lastFocusedIndexByRail[railKey] ?: 0,
                    onItemClick = { item, addonBaseUrl ->
                        onNavigateToDetail(item.id, item.apiType, addonBaseUrl)
                    },
                    onItemFocused = { item ->
                        pendingHeroItem = item.toNetflixHeroItem(row.addonBaseUrl)
                        onItemFocus(item)
                    },
                    onItemLongClick = onCatalogItemLongPress,
                    onLoadMore = onLoadMoreCatalog,
                    onFocusedItemChanged = { itemIndex, itemKey ->
                        if (railIndex >= 0) {
                            saveRailFocus(railKey, itemKey, railIndex, itemIndex)
                        }
                    },
                    onPendingFocusConsumed = { pendingFocusRailKey = null },
                    onFirstCardRequesterReady = { requester ->
                        firstCardRequestersByRail[railKey] = requester
                    },
                    onMoveUp = {
                        if (railIndex <= 0) {
                            runCatching { heroPrimaryRequester.requestFocus() }.isSuccess
                        } else {
                            requestRailFocus(railKeys.getOrNull(railIndex - 1))
                        }
                    },
                    onMoveDown = {
                        if (railIndex >= 0) {
                            requestRailFocus(railKeys.getOrNull(railIndex + 1))
                        } else {
                            false
                        }
                    },
                    posterLabelsEnabled = uiState.posterLabelsEnabled
                )
            }
            item(key = "bottom_padding") {
                Spacer(modifier = Modifier.height(42.dp))
            }
        }
    }
}

private const val NETFLIX_CONTINUE_WATCHING_RAIL_KEY = "continue_watching"
private const val NETFLIX_GENRE_RAIL_KEY = "genre_strip"
private const val NETFLIX_HOME_STATIC_ROW_COUNT = 2
private const val NETFLIX_TOP_NAV_ITEM_COUNT = 7
private const val NETFLIX_METADATA_SETTLE_DELAY_MS = 240L
private const val NETFLIX_TRAILER_SETTLE_DELAY_MS = 1_000L

private fun resolveInitialHero(uiState: HomeUiState): NetflixHeroItem? {
    val hero = uiState.heroItems.firstOrNull()
    if (hero != null) return hero.toNetflixHeroItem("")

    val catalog = uiState.catalogRows.firstOrNull { it.items.isNotEmpty() }
    val catalogItem = catalog?.items?.firstOrNull()
    if (catalog != null && catalogItem != null) {
        return catalogItem.toNetflixHeroItem(catalog.addonBaseUrl)
    }

    return uiState.continueWatchingItems.firstOrNull()?.toNetflixHeroItem()
}

private fun buildGenreChips(rows: List<com.nuvio.tv.domain.model.CatalogRow>): List<NetflixGenreChip> {
    val chips = linkedMapOf<String, NetflixGenreChip>()
    rows.filter { it.items.isNotEmpty() }.forEach { row ->
        val rowKey = row.netflixRailKey()
        val rowLabel = row.catalogName.trim().replaceFirstChar { it.uppercase() }
        if (rowLabel.isNotBlank()) {
            chips.putIfAbsent(
                rowLabel.lowercase(),
                NetflixGenreChip(
                    key = "row|${rowKey}",
                    label = rowLabel,
                    targetRailKey = rowKey
                )
            )
        }
        row.items.asSequence()
            .flatMap { item -> item.genres.asSequence() }
            .map { genre -> genre.trim() }
            .filter { genre -> genre.length in 3..18 }
            .forEach { genre ->
                chips.putIfAbsent(
                    genre.lowercase(),
                    NetflixGenreChip(
                        key = "genre|${genre.lowercase()}",
                        label = genre.replaceFirstChar { it.uppercase() },
                        targetRailKey = rowKey
                    )
                )
            }
    }
    return chips.values.take(10)
}

private fun navigateToTargetDetails(
    target: NetflixHomeTarget,
    onNavigateToDetail: (String, String, String) -> Unit
) {
    when (target) {
        is NetflixHomeTarget.Catalog -> {
            onNavigateToDetail(target.item.id, target.item.apiType, target.addonBaseUrl)
        }
        is NetflixHomeTarget.ContinueWatching -> {
            when (val item = target.item) {
                is ContinueWatchingItem.InProgress -> {
                    onNavigateToDetail(item.progress.contentId, item.progress.contentType, "")
                }
                is ContinueWatchingItem.NextUp -> {
                    onNavigateToDetail(item.info.contentId, item.info.contentType, "")
                }
            }
        }
    }
}

private fun NetflixHeroItem.catalogItemId(): String? {
    return (target as? NetflixHomeTarget.Catalog)?.item?.id
}
