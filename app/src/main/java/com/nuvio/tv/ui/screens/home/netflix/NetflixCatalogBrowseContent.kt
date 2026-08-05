package com.nuvio.tv.ui.screens.home.netflix

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.core.build.AppFeaturePolicy
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NETFLIX_TRAILER_LOG = "NetflixTrailer"

private const val BROWSE_HERO_ROW_INDEX = 1
private const val BROWSE_STATIC_ROW_COUNT = 2
private const val BROWSE_FOCUS_SETTLE_DELAY_MS = 16L
private const val BROWSE_TRAILER_SETTLE_DELAY_MS = 120L

/**
 * Netflix-home style browse surface used for genre pills and collection folders.
 * Hero + large expanding catalogue rails; continuous D-pad Up from the hero exits.
 */
@Composable
fun NetflixCatalogBrowseContent(
    title: String,
    rows: List<CatalogRow>,
    isLoading: Boolean,
    useLandscapeCards: Boolean,
    posterLabelsEnabled: Boolean,
    trailerMuted: Boolean,
    trailerEnabled: Boolean,
    trailerPreviewUrls: Map<String, String> = emptyMap(),
    trailerPreviewAudioUrls: Map<String, String> = emptyMap(),
    onNavigateToDetail: (String, String, String) -> Unit,
    onLoadMoreCatalog: (String, String, String) -> Unit = { _, _, _ -> },
    onItemFocus: (MetaPreview) -> Unit = {},
    onCatalogItemLongPress: (MetaPreview, String) -> Unit = { _, _ -> },
    onRequestTrailerPreview: (String, String, String?, String) -> Unit = { _, _, _, _ -> },
    onExitUp: () -> Unit
) {
    BackHandler { onExitUp() }

    val displayRows = remember(rows) { rows.filter { it.items.isNotEmpty() } }
    val railKeys = remember(displayRows) {
        displayRows.mapIndexed { index, row -> "${row.netflixRailKey()}|browse|$index" }
    }

    var heroItem by remember(displayRows) {
        mutableStateOf(
            displayRows.firstOrNull()?.items?.firstOrNull()?.let { item ->
                item.toNetflixHeroItem(displayRows.first().addonBaseUrl)
            }
        )
    }
    var ambientArtUrl by remember {
        mutableStateOf(heroItem?.backdrop ?: heroItem?.poster)
    }
    val heroPrimaryRequester = remember { FocusRequester() }
    val unusedTopNavRequester = remember { FocusRequester() }
    val firstCardRequestersByRail = remember { mutableStateMapOf<String, FocusRequester>() }

    // Survive Details → Back via the Nav back-stack SavedStateHandle.
    var hasSavedFocus by rememberSaveable { mutableStateOf(false) }
    var savedRailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var savedItemIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedVerticalIndex by rememberSaveable { mutableIntStateOf(0) }

    val lastFocusedIndexByRail = remember {
        mutableStateMapOf<String, Int>().also { map ->
            val key = savedRailKey
            if (hasSavedFocus && key != null) {
                map[key] = savedItemIndex.coerceAtLeast(0)
            }
        }
    }
    val requestedTrailerKeys = remember { mutableStateMapOf<String, Boolean>() }
    val playedTrailerKeys = remember { mutableStateMapOf<String, Boolean>() }
    var pendingFocusRailKey by remember {
        mutableStateOf(savedRailKey.takeIf { hasSavedFocus })
    }
    var railFocusJob by remember { mutableStateOf<Job?>(null) }
    var previewTrailerHeroKey by remember { mutableStateOf<String?>(null) }
    var heroActionFocused by remember { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (hasSavedFocus) {
            savedVerticalIndex.coerceAtLeast(0)
        } else {
            0
        }
    )
    val coroutineScope = rememberCoroutineScope()
    val netflixTrailersEnabled = AppFeaturePolicy.inAppTrailerPlaybackEnabled &&
        (NetflixHomeFeature.FORCE_TRAILER_AUTOPLAY || trailerEnabled)

    fun saveBrowseFocus(railKey: String, itemIndex: Int, verticalIndex: Int) {
        lastFocusedIndexByRail[railKey] = itemIndex
        savedRailKey = railKey
        savedItemIndex = itemIndex
        savedVerticalIndex = verticalIndex
        hasSavedFocus = true
    }

    suspend fun focusRailNow(railKey: String): Boolean {
        val railIndex = railKeys.indexOf(railKey)
        if (railIndex < 0) return false
        val lazyListIndex = railIndex + BROWSE_STATIC_ROW_COUNT
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == lazyListIndex }
        if (!visible) {
            runCatching { listState.scrollToItem(lazyListIndex) }
        }
        pendingFocusRailKey = railKey
        repeat(6) {
            withFrameNanos { }
            if (pendingFocusRailKey != railKey) return true
        }
        return pendingFocusRailKey != railKey
    }

    fun requestRailFocus(railKey: String?): Boolean {
        if (railKey == null) return false
        if (railKeys.indexOf(railKey) < 0) return false
        railFocusJob?.cancel()
        railFocusJob = coroutineScope.launch {
            focusRailNow(railKey)
        }
        return true
    }

    fun requestHeroFocus(): Boolean {
        railFocusJob?.cancel()
        pendingFocusRailKey = null
        val heroVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == BROWSE_HERO_ROW_INDEX }
        if (heroVisible && runCatching { heroPrimaryRequester.requestFocus() }.isSuccess) {
            return true
        }
        railFocusJob = coroutineScope.launch {
            listState.scrollToItem(BROWSE_HERO_ROW_INDEX)
            heroPrimaryRequester.requestFocusAfterFrames(2)
        }
        return true
    }

    LaunchedEffect(heroItem?.key, listState.isScrollInProgress, netflixTrailersEnabled) {
        val stableHero = heroItem ?: run {
            previewTrailerHeroKey = null
            return@LaunchedEffect
        }
        previewTrailerHeroKey = null
        if (!netflixTrailersEnabled || listState.isScrollInProgress) return@LaunchedEffect
        val catalogTarget = stableHero.target as? NetflixHomeTarget.Catalog ?: return@LaunchedEffect
        val item = catalogTarget.item
        if (playedTrailerKeys[stableHero.key] == true) return@LaunchedEffect
        delay(BROWSE_TRAILER_SETTLE_DELAY_MS)
        if (heroItem?.key != stableHero.key || listState.isScrollInProgress) return@LaunchedEffect
        if (requestedTrailerKeys[stableHero.key] != true && trailerPreviewUrls[item.id].isNullOrBlank()) {
            Log.i(NETFLIX_TRAILER_LOG, "browse hero request trailer id=${item.id}")
            onRequestTrailerPreview(item.id, item.name, item.releaseInfo, item.apiType)
            requestedTrailerKeys[stableHero.key] = true
        }
        previewTrailerHeroKey = stableHero.key
    }

    LaunchedEffect(displayRows.isNotEmpty(), railKeys, initialFocusDone) {
        if (initialFocusDone || displayRows.isEmpty()) return@LaunchedEffect
        val restoreKey = savedRailKey
        if (hasSavedFocus && restoreKey != null && restoreKey in railKeys) {
            lastFocusedIndexByRail[restoreKey] = savedItemIndex.coerceAtLeast(0)
            val lazyListIndex = railKeys.indexOf(restoreKey) + BROWSE_STATIC_ROW_COUNT
            if (listState.firstVisibleItemIndex != lazyListIndex) {
                runCatching { listState.scrollToItem(lazyListIndex) }
            }
            pendingFocusRailKey = restoreKey
            repeat(6) {
                withFrameNanos { }
                if (pendingFocusRailKey != restoreKey) {
                    initialFocusDone = true
                    return@LaunchedEffect
                }
            }
            initialFocusDone = true
        } else {
            withFrameNanos { }
            requestHeroFocus()
            initialFocusDone = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixThemeChrome.background)
    ) {
        NetflixPosterBackdrop(
            imageUrl = ambientArtUrl ?: heroItem?.backdrop ?: heroItem?.poster,
            modifier = Modifier.fillMaxSize(),
            accentScrim = NetflixThemeChrome.accent
        )

        if (displayRows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    LoadingIndicator()
                } else {
                    Text(
                        text = title,
                        color = NetflixHomeTokens.TextSecondary,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(NetflixHomeTokens.RailSpacing)
        ) {
            item(key = "browse_title") {
                Text(
                    text = title,
                    color = NetflixHomeTokens.TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = NetflixHomeTokens.PageHorizontalPadding,
                        top = 28.dp,
                        end = NetflixHomeTokens.PageHorizontalPadding,
                        bottom = 8.dp
                    )
                )
            }
            item(key = "hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    val heroCatalogId = (heroItem?.target as? NetflixHomeTarget.Catalog)?.item?.id
                    NetflixHero(
                        item = heroItem,
                        topNavigationRequester = unusedTopNavRequester,
                        primaryActionRequester = heroPrimaryRequester,
                        onMoveDownFromHero = { requestRailFocus(railKeys.firstOrNull()) },
                        onMoveUpFromHero = {
                            onExitUp()
                            true
                        },
                        trailerPreviewUrl = heroCatalogId?.let { trailerPreviewUrls[it] },
                        trailerPreviewAudioUrl = heroCatalogId?.let { trailerPreviewAudioUrls[it] },
                        playTrailerPreview = heroActionFocused &&
                            previewTrailerHeroKey == heroItem?.key &&
                            heroItem?.key?.let { playedTrailerKeys[it] } != true &&
                            !heroCatalogId?.let { trailerPreviewUrls[it] }.isNullOrBlank(),
                        trailerPreviewMuted = trailerMuted,
                        onTrailerEnded = {
                            heroItem?.key?.let { playedTrailerKeys[it] = true }
                            previewTrailerHeroKey = null
                        },
                        onFocusedChanged = { heroActionFocused = it },
                        onViewDetails = { target ->
                            when (target) {
                                is NetflixHomeTarget.Catalog -> {
                                    onNavigateToDetail(
                                        target.item.id,
                                        target.item.apiType,
                                        target.addonBaseUrl
                                    )
                                }
                                is NetflixHomeTarget.ContinueWatching -> Unit
                            }
                        }
                    )
                }
            }
            itemsIndexed(
                items = displayRows,
                key = { index, _ -> railKeys[index] }
            ) { railIndex, row ->
                val railKey = railKeys[railIndex]
                NetflixCatalogRail(
                    railKey = railKey,
                    row = row,
                    useLandscapeCards = useLandscapeCards,
                    pendingFocusRailKey = pendingFocusRailKey,
                    lastFocusedIndex = lastFocusedIndexByRail[railKey] ?: 0,
                    onItemClick = { item, addonBaseUrl ->
                        onNavigateToDetail(item.id, item.apiType, addonBaseUrl)
                    },
                    onItemFocused = { item ->
                        // Hero showcase stays pinned to the lead entry; page wash
                        // follows the focused rail card.
                        ambientArtUrl = item.netflixAmbientArtUrl()
                        onItemFocus(item)
                    },
                    onItemLongClick = onCatalogItemLongPress,
                    onLoadMore = onLoadMoreCatalog,
                    onFocusedItemChanged = { index, _ ->
                        saveBrowseFocus(
                            railKey = railKey,
                            itemIndex = index,
                            verticalIndex = railIndex + BROWSE_STATIC_ROW_COUNT
                        )
                    },
                    onPendingFocusConsumed = { pendingFocusRailKey = null },
                    onFirstCardRequesterReady = { requester ->
                        firstCardRequestersByRail[railKey] = requester
                    },
                    onMoveUp = {
                        if (railIndex <= 0) requestHeroFocus()
                        else requestRailFocus(railKeys.getOrNull(railIndex - 1))
                    },
                    onMoveDown = {
                        requestRailFocus(railKeys.getOrNull(railIndex + 1))
                    },
                    posterLabelsEnabled = posterLabelsEnabled,
                    trailerPreviewUrls = trailerPreviewUrls,
                    trailerPreviewAudioUrls = trailerPreviewAudioUrls,
                    trailerEnabled = netflixTrailersEnabled,
                    trailerMuted = trailerMuted,
                    onRequestTrailerPreview = { item ->
                        Log.i(NETFLIX_TRAILER_LOG, "browse rail request trailer id=${item.id}")
                        onRequestTrailerPreview(item.id, item.name, item.releaseInfo, item.apiType)
                    }
                )
            }
            item(key = "bottom_padding") {
                Spacer(modifier = Modifier.height(NetflixHomeSpacing.BottomFocusClearance))
            }
        }
    }
}
