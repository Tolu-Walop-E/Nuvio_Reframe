package com.nuvio.tv.ui.screens.home.netflix

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.sync.GENRE_ROW_TARGET_CATALOG
import com.nuvio.tv.core.sync.GENRE_ROW_TARGET_COLLECTION_FOLDER
import com.nuvio.tv.core.sync.HOME_GENRES_ROW_KEY
import com.nuvio.tv.core.sync.SyncGenreRowTarget
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.ui.components.ContinueWatchingOptionsDialog
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.HomeScreenFocusState
import com.nuvio.tv.ui.screens.home.HomeRow
import com.nuvio.tv.ui.screens.home.HomeUiState
import com.nuvio.tv.ui.screens.home.contentId
import com.nuvio.tv.ui.screens.home.contentType
import com.nuvio.tv.ui.screens.home.episode
import com.nuvio.tv.ui.screens.home.season
import com.nuvio.tv.core.build.AppFeaturePolicy
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NETFLIX_TRAILER_LOG = "NetflixTrailer"

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
    onNavigateToGenre: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onGenreTargetChanged: (String, SyncGenreRowTarget?) -> Unit = { _, _ -> },
    onItemFocus: (MetaPreview) -> Unit = {},
    onPreloadAdjacentItem: (MetaPreview) -> Unit = {},
    trailerPreviewUrls: Map<String, String> = emptyMap(),
    trailerPreviewAudioUrls: Map<String, String> = emptyMap(),
    onRequestTrailerPreview: (String, String, String?, String) -> Unit = { _, _, _, _ -> },
    onSaveFocusState: (Int, Int, String?, Map<String, String>, Map<String, Int>, Int, Int) -> Unit,
    scrollToTopTrigger: Int = 0,
    pendingFocusRailKeyFromHost: String? = null,
    onPendingFocusRailKeyConsumed: () -> Unit = {},
    onRequestLazyCatalogLoad: (String) -> Unit = {},
    netflixFolderRails: Map<String, com.nuvio.tv.domain.model.CatalogRow> = emptyMap(),
    onEnsureFolderRails: (List<NetflixFolderRailRequest>) -> Unit = {}
) {
    var heroItem by remember(uiState.heroItems, uiState.catalogRows, uiState.continueWatchingItems) {
        mutableStateOf(resolveInitialHero(uiState))
    }
    var pendingHeroItem by remember { mutableStateOf(heroItem) }
    var focusedTopNavigationIndex by remember { mutableStateOf(1) }
    val topNavigationRequesters = remember { List(NETFLIX_TOP_NAV_ITEM_COUNT) { FocusRequester() } }
    val heroPrimaryRequester = remember { FocusRequester() }
    val firstCardRequestersByRail = remember { mutableStateMapOf<String, FocusRequester>() }
    val lastFocusedIndexByRail = remember { mutableStateMapOf<String, Int>() }
    val requestedTrailerKeys = remember { mutableStateMapOf<String, Boolean>() }
    val playedTrailerKeys = remember { mutableStateMapOf<String, Boolean>() }
    var pendingFocusRailKey by remember { mutableStateOf<String?>(null) }
    var railFocusJob by remember { mutableStateOf<Job?>(null) }
    var continueWatchingOptionsItem by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    var genreTargetPickerChip by remember { mutableStateOf<NetflixGenreChip?>(null) }
    var restoredSavedFocus by remember { mutableStateOf(false) }
    var previewTrailerHeroKey by remember { mutableStateOf<String?>(null) }
    var heroActionFocused by remember { mutableStateOf(false) }
    var topNavFocused by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(NetflixContentTab.HOME) }
    var lastContentRailKey by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val contentRails = remember(uiState.homeRows, uiState.catalogRows) {
        buildNetflixContentRails(uiState.homeRows, uiState.catalogRows)
    }
    val catalogEntries = remember(contentRails) {
        contentRails.mapNotNull { rail -> (rail as? NetflixHomeRail.Catalog)?.entry }
    }
    LaunchedEffect(contentRails) {
        contentRails.forEach { rail ->
            when (rail) {
                is NetflixHomeRail.Catalog -> Log.i(
                    "NuvioLibrary",
                    "catalog='${rail.entry.row.catalogName}' addon='${rail.entry.row.addonName}' type=${rail.entry.row.apiType} items=${rail.entry.row.items.size}"
                )
                is NetflixHomeRail.Collection -> Log.i(
                    "NuvioLibrary",
                    "collection='${rail.collection.title}' folders=${rail.collection.folders.size}"
                )
                else -> Unit
            }
        }
    }
    val genreChips = remember(uiState.collections, selectedTab) {
        buildGenreChipsFromCollections(uiState.collections, selectedTab)
    }
    val genreTargetOptions = remember(uiState.collections, catalogEntries, selectedTab, context) {
        buildGenreTargetOptions(uiState.collections, catalogEntries, selectedTab) { resourceId, value ->
            context.getString(resourceId, value)
        }
    }

    // Auto-map Home genre chips to their Genres-collection folders so opening
    // Action on Home still gets the full movie+series folder board.
    val autoMappedGenreKeys = remember { mutableSetOf<String>() }
    LaunchedEffect(genreChips, uiState.collections, selectedTab, uiState.genreRowTargets) {
        if (selectedTab != NetflixContentTab.HOME) return@LaunchedEffect
        genreChips.forEach { chip ->
            if (!chip.key.startsWith("genre|")) return@forEach
            if (chip.key in autoMappedGenreKeys) return@forEach
            if (uiState.genreRowTargets.containsKey(chip.key)) {
                autoMappedGenreKeys.add(chip.key)
                return@forEach
            }
            val collectionId = chip.collectionId ?: return@forEach
            val folderId = chip.folderId ?: return@forEach
            autoMappedGenreKeys.add(chip.key)
            onGenreTargetChanged(
                chip.key,
                SyncGenreRowTarget(
                    kind = GENRE_ROW_TARGET_COLLECTION_FOLDER,
                    collectionId = collectionId,
                    folderId = folderId
                )
            )
        }
    }
    val orderedContentRails = remember(
        contentRails,
        genreChips,
        uiState.homeCatalogOrderKeys,
        uiState.disabledHomeCatalogKeys
    ) {
        insertGenresRail(
            contentRails = contentRails,
            hasGenres = genreChips.isNotEmpty(),
            orderKeys = uiState.homeCatalogOrderKeys,
            disabledKeys = uiState.disabledHomeCatalogKeys
        )
    }
    val continueWatchingGenres = remember(uiState.continueWatchingItems) {
        uiState.continueWatchingItems
            .flatMap { item ->
                when (item) {
                    is ContinueWatchingItem.InProgress -> item.genres
                    is ContinueWatchingItem.NextUp -> item.info.genres
                }
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { entry -> entry.key.replaceFirstChar { it.uppercase() } }
    }
    val fanOutRequests = remember(orderedContentRails, selectedTab) {
        orderedContentRails
            .filterIsInstance<NetflixHomeRail.Collection>()
            .filter { NetflixCollectionLayout.shouldFanOut(it.collection) }
            .flatMap { rail ->
                rail.collection.folders
                    .asSequence()
                    .mapNotNull { folder ->
                        val source = NetflixCollectionLayout.pickSource(folder, selectedTab)
                            ?: return@mapNotNull null
                        NetflixFolderRailRequest(
                            railKey = NetflixCollectionLayout.railKey(rail.collection.id, folder.id, source),
                            title = folder.title,
                            source = source
                        )
                    }
                    .take(12)
                    .toList()
            }
    }
    LaunchedEffect(fanOutRequests) {
        onEnsureFolderRails(fanOutRequests)
    }
    val discoveryRails = remember(catalogEntries, continueWatchingGenres, selectedTab) {
        NetflixDiscoveryRails.build(
            rows = catalogEntries.map { it.row },
            continueWatchingGenres = continueWatchingGenres,
            tab = selectedTab
        ).map { row ->
            NetflixHomeRail.Catalog(
                NetflixCatalogEntry(row = row, railKey = "discover|${row.catalogId}")
            )
        }
    }
    val visibleRails = remember(
        uiState.continueWatchingItems,
        orderedContentRails,
        selectedTab,
        discoveryRails,
        netflixFolderRails,
        fanOutRequests
    ) {
        val expanded = expandNetflixRails(
            orderedContentRails = orderedContentRails,
            selectedTab = selectedTab,
            folderRails = netflixFolderRails
        )
        buildList {
            val genresFirst = expanded.firstOrNull() is NetflixHomeRail.Genres
            if (genresFirst) {
                add(NetflixHomeRail.Genres)
            }
            if (selectedTab == NetflixContentTab.HOME && uiState.continueWatchingItems.isNotEmpty()) {
                add(NetflixHomeRail.ContinueWatching)
            }
            addAll(if (genresFirst) expanded.drop(1) else expanded)
            addAll(discoveryRails)
        }
    }
    val railKeys = remember(visibleRails) { visibleRails.map { it.railKey } }
    val netflixTrailersEnabled = remember(
        uiState.focusedPosterBackdropTrailerEnabled
    ) {
        AppFeaturePolicy.inAppTrailerPlaybackEnabled &&
            (NetflixHomeFeature.FORCE_TRAILER_AUTOPLAY || uiState.focusedPosterBackdropTrailerEnabled)
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            railFocusJob?.cancel()
            pendingFocusRailKey = null
            runCatching { listState.scrollToItem(NETFLIX_HOME_HERO_ROW_INDEX) }
            withFrameNanos { }
            runCatching {
                topNavigationRequesters.getOrElse(focusedTopNavigationIndex) {
                    topNavigationRequesters[NETFLIX_HOME_NAV_INDEX]
                }.requestFocus()
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
        if (!netflixTrailersEnabled || listState.isScrollInProgress) {
            return@LaunchedEffect
        }
        val catalogTarget = stableHero.target as? NetflixHomeTarget.Catalog ?: return@LaunchedEffect
        val item = catalogTarget.item
        if (playedTrailerKeys[stableHero.key] == true) {
            return@LaunchedEffect
        }
        delay(NETFLIX_TRAILER_PREFETCH_DELAY_MS)
        if (heroItem?.key != stableHero.key || listState.isScrollInProgress) {
            return@LaunchedEffect
        }
        if (requestedTrailerKeys[stableHero.key] != true && trailerPreviewUrls[item.id].isNullOrBlank()) {
            Log.i(NETFLIX_TRAILER_LOG, "hero request trailer id=${item.id} title=${item.name}")
            onRequestTrailerPreview(item.id, item.name, item.releaseInfo, item.apiType)
            requestedTrailerKeys[stableHero.key] = true
        }
        previewTrailerHeroKey = stableHero.key
        Log.i(
            NETFLIX_TRAILER_LOG,
            "hero ready key=${stableHero.key} urlPresent=${!trailerPreviewUrls[item.id].isNullOrBlank()} focused=$heroActionFocused"
        )
    }

    LaunchedEffect(focusState, railKeys, heroItem?.key) {
        if (restoredSavedFocus) return@LaunchedEffect

        val focusedRowKey = focusState.focusedRowKey
        if (focusState.hasSavedFocus && focusedRowKey != null && focusedRowKey in railKeys) {
            lastFocusedIndexByRail[focusedRowKey] = focusState.focusedItemIndex
            val lazyListIndex = railKeys.indexOf(focusedRowKey) + NETFLIX_HOME_STATIC_ROW_COUNT
            runCatching { listState.scrollToItem(lazyListIndex) }
            withFrameNanos { }
            pendingFocusRailKey = focusedRowKey
            delay(NETFLIX_FOCUS_SETTLE_DELAY_MS)
            listState.scrollToItem(lazyListIndex)
            restoredSavedFocus = true
        } else if (heroItem != null && !focusState.hasSavedFocus) {
            // Only fall back to hero focus when there is no saved position;
            // otherwise wait for the saved rail to appear in railKeys (rails
            // can still be loading right after returning from another screen).
            runCatching { listState.scrollToItem(NETFLIX_HOME_HERO_ROW_INDEX) }
            withFrameNanos { }
            runCatching { heroPrimaryRequester.requestFocus() }
            delay(NETFLIX_FOCUS_SETTLE_DELAY_MS)
            restoredSavedFocus = true
        }
    }

    /**
     * Vertical target index into [railKeys], or [VERTICAL_TARGET_HERO] / [VERTICAL_TARGET_TOP_NAV].
     * Holding Up/Down coalesces into this so we never cancel mid-move and strand focus.
     */
    var queuedVerticalTarget by remember { mutableStateOf<Int?>(null) }

    fun requestTopNavFocus(): Boolean {
        railFocusJob?.cancel()
        pendingFocusRailKey = null
        queuedVerticalTarget = null
        val targetIndex = focusedTopNavigationIndex.coerceIn(0, topNavigationRequesters.lastIndex)
            .takeIf { it >= 0 }
            ?: NETFLIX_HOME_NAV_INDEX
        return runCatching {
            topNavigationRequesters.getOrElse(targetIndex) {
                topNavigationRequesters[NETFLIX_HOME_NAV_INDEX]
            }.requestFocus()
        }.isSuccess || runCatching {
            topNavigationRequesters[NETFLIX_HOME_NAV_INDEX].requestFocus()
        }.isSuccess
    }

    suspend fun focusRailNow(railKey: String): Boolean {
        val railIndex = railKeys.indexOf(railKey)
        if (railIndex < 0) return false
        val lazyListIndex = railIndex + NETFLIX_HOME_STATIC_ROW_COUNT
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == lazyListIndex }
        if (!visible) {
            runCatching { listState.scrollToItem(lazyListIndex) }
        }
        repeat(NETFLIX_FOCUS_RETRY_FRAMES) {
            withFrameNanos { }
            val lastIndex = lastFocusedIndexByRail[railKey] ?: 0
            // Prefer the rail's pending-focus path which restores last card index.
            pendingFocusRailKey = railKey
            val requester = firstCardRequestersByRail[railKey]
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                // Scaffold will refine to lastFocusedIndex via pendingFocusRailKey.
                return true
            }
            // Keep lastIndex referenced so the compiler/optimizer doesn't drop the read.
            if (lastIndex < 0) return@repeat
        }
        pendingFocusRailKey = railKey
        return false
    }

    suspend fun focusHeroNow(): Boolean {
        pendingFocusRailKey = null
        runCatching { listState.scrollToItem(NETFLIX_HOME_HERO_ROW_INDEX) }
        repeat(NETFLIX_FOCUS_RETRY_FRAMES) {
            withFrameNanos { }
            if (runCatching { heroPrimaryRequester.requestFocus() }.isSuccess) {
                return true
            }
        }
        return false
    }

    fun pumpVerticalNavigation() {
        if (railFocusJob?.isActive == true) return
        railFocusJob = coroutineScope.launch {
            while (true) {
                val target = queuedVerticalTarget ?: break
                queuedVerticalTarget = null
                when {
                    target == VERTICAL_TARGET_TOP_NAV -> {
                        requestTopNavFocus()
                        break
                    }
                    target == VERTICAL_TARGET_HERO || target < 0 -> {
                        focusHeroNow()
                    }
                    target in railKeys.indices -> {
                        focusRailNow(railKeys[target])
                    }
                    else -> break
                }
                // Brief settle so the next coalesced step lands on a real focused rail.
                delay(NETFLIX_VERTICAL_STEP_DELAY_MS)
            }
        }
    }

    fun queueVerticalTarget(target: Int): Boolean {
        queuedVerticalTarget = target
        pumpVerticalNavigation()
        return true
    }

    fun requestRailFocus(railKey: String?): Boolean {
        if (railKey == null) return true
        val railIndex = railKeys.indexOf(railKey)
        if (railIndex < 0) return false
        return queueVerticalTarget(railIndex)
    }

    fun requestHeroFocus(): Boolean = queueVerticalTarget(VERTICAL_TARGET_HERO)

    // Keep hero pinned only when focus arrives there from a settled move — never
    // animate while the user is still holding Up/Down (that used to cancel focus).
    // Also re-assert scrollOffset=0 so bring-into-view cannot eat the top gap.
    LaunchedEffect(heroActionFocused) {
        if (!heroActionFocused || railFocusJob?.isActive == true) return@LaunchedEffect
        runCatching { listState.scrollToItem(NETFLIX_HOME_HERO_ROW_INDEX, scrollOffset = 0) }
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (!heroActionFocused) return@collect
            if (index != NETFLIX_HOME_HERO_ROW_INDEX || offset != 0) {
                runCatching { listState.scrollToItem(NETFLIX_HOME_HERO_ROW_INDEX, scrollOffset = 0) }
            }
        }
    }

    var appliedTab by remember { mutableStateOf(selectedTab) }
    LaunchedEffect(selectedTab) {
        if (appliedTab == selectedTab) return@LaunchedEffect
        appliedTab = selectedTab
        railFocusJob?.cancel()
        pendingFocusRailKey = null
        val firstEntry = visibleRails.firstNotNullOfOrNull { rail ->
            (rail as? NetflixHomeRail.Catalog)?.entry?.takeIf { it.row.items.isNotEmpty() }
        }
        pendingHeroItem = if (selectedTab == NetflixContentTab.HOME) {
            resolveInitialHero(uiState)
        } else {
            firstEntry?.row?.items?.firstOrNull()?.toNetflixHeroItem(firstEntry.row.addonBaseUrl)
        } ?: pendingHeroItem
        runCatching { listState.scrollToItem(NETFLIX_HOME_HERO_ROW_INDEX) }
    }

    LaunchedEffect(pendingFocusRailKeyFromHost) {
        val target = pendingFocusRailKeyFromHost ?: return@LaunchedEffect
        onPendingFocusRailKeyConsumed()
        restoredSavedFocus = true
        requestRailFocus(target)
    }

    fun saveRailFocus(railKey: String, itemKey: String, railIndex: Int, itemIndex: Int) {
        lastFocusedIndexByRail[railKey] = itemIndex
        lastContentRailKey = railKey
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

    fun requestContentFocusFromNav(): Boolean {
        val preferred = lastContentRailKey?.takeIf { it in railKeys }
        return if (preferred != null) {
            requestRailFocus(preferred)
        } else {
            requestHeroFocus()
        }
    }

    BackHandler(enabled = true) {
        when {
            !topNavFocused -> requestTopNavFocus()
            selectedTab != NetflixContentTab.HOME -> {
                selectedTab = NetflixContentTab.HOME
                focusedTopNavigationIndex = NETFLIX_HOME_NAV_INDEX
                runCatching { topNavigationRequesters[NETFLIX_HOME_NAV_INDEX].requestFocus() }
            }
            else -> requestContentFocusFromNav()
        }
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

        // Nav is in normal layout flow (not an overlay). Overlay + LazyColumn
        // bring-into-view was scrolling the focused hero under the absolute nav.
        Column(modifier = Modifier.fillMaxSize()) {
            NetflixTopNavigation(
                itemFocusRequesters = topNavigationRequesters,
                selectedIndex = focusedTopNavigationIndex,
                onMoveDown = { requestContentFocusFromNav() },
                onFocusedIndexChanged = { focusedTopNavigationIndex = it },
                onNavFocusChanged = { topNavFocused = it },
                selectedTabIndex = selectedTab.navIndex,
                onTabSelected = { index ->
                    NetflixContentTab.fromNavIndex(index)?.let { tab -> selectedTab = tab }
                },
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                contentPadding = PaddingValues(top = NetflixHomeTokens.HeroTopGap),
                verticalArrangement = Arrangement.spacedBy(NetflixHomeTokens.RailSpacing)
            ) {
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
                        topNavigationRequester = topNavigationRequesters.getOrElse(focusedTopNavigationIndex) {
                            topNavigationRequesters[NETFLIX_HOME_NAV_INDEX]
                        },
                        primaryActionRequester = heroPrimaryRequester,
                        onMoveDownFromHero = {
                            requestRailFocus(railKeys.firstOrNull())
                        },
                        onMoveUpFromHero = {
                            requestTopNavFocus()
                        },
                        trailerPreviewUrl = heroItem?.catalogItemId()?.let { trailerPreviewUrls[it] },
                        trailerPreviewAudioUrl = heroItem?.catalogItemId()?.let { trailerPreviewAudioUrls[it] },
                        playTrailerPreview = heroActionFocused &&
                            previewTrailerHeroKey == heroItem?.key &&
                            heroItem?.key?.let { playedTrailerKeys[it] } != true &&
                            !heroItem?.catalogItemId()?.let { trailerPreviewUrls[it] }.isNullOrBlank(),
                        trailerPreviewMuted = uiState.focusedPosterBackdropTrailerMuted,
                        onTrailerEnded = {
                            heroItem?.key?.let { playedTrailerKeys[it] = true }
                            previewTrailerHeroKey = null
                        },
                        onFocusedChanged = { heroActionFocused = it },
                        onViewDetails = { target -> navigateToTargetDetails(target, onNavigateToDetail) }
                    )
                }
            }
            items(
                items = visibleRails,
                key = { rail -> rail.railKey }
            ) { rail ->
                val railKey = rail.railKey
                val railIndex = railKeys.indexOf(railKey)
                val moveUp = {
                    // Coalesce against the in-flight target so holding Up keeps climbing
                    // instead of repeatedly requesting the same previous rail.
                    val from = queuedVerticalTarget ?: railIndex
                    when {
                        from == VERTICAL_TARGET_HERO || from == VERTICAL_TARGET_TOP_NAV -> {
                            pumpVerticalNavigation()
                            true
                        }
                        from <= 0 -> queueVerticalTarget(VERTICAL_TARGET_HERO)
                        else -> queueVerticalTarget(from - 1)
                    }
                }
                val moveDown = {
                    val from = queuedVerticalTarget ?: railIndex
                    when {
                        from == VERTICAL_TARGET_HERO -> queueVerticalTarget(0)
                        from < railKeys.lastIndex -> queueVerticalTarget(from + 1)
                        else -> {
                            pumpVerticalNavigation()
                            true
                        }
                    }
                }
                val saveFocus = { itemIndex: Int, itemKey: String ->
                    if (railIndex >= 0) saveRailFocus(railKey, itemKey, railIndex, itemIndex)
                }
                val registerRequester = { requester: FocusRequester ->
                    firstCardRequestersByRail[railKey] = requester
                }

                when (rail) {
                    NetflixHomeRail.Genres -> NetflixGenreRail(
                        railKey = railKey,
                        genres = genreChips,
                        pendingFocusRailKey = pendingFocusRailKey,
                        lastFocusedIndex = lastFocusedIndexByRail[railKey] ?: 0,
                        onFocusedItemChanged = saveFocus,
                        onPendingFocusConsumed = { pendingFocusRailKey = null },
                        onFirstCardRequesterReady = registerRequester,
                        onMoveUp = moveUp,
                        onMoveDown = moveDown,
                        onGenreSelected = { genre ->
                            openGenreChip(
                                genre = genre,
                                selectedTab = selectedTab,
                                mappedTarget = uiState.genreRowTargets[genre.key],
                                onNavigateToGenre = onNavigateToGenre
                            )
                        },
                        onGenreLongPressed = { genre -> genreTargetPickerChip = genre }
                    )

                    NetflixHomeRail.ContinueWatching -> NetflixContinueWatchingRail(
                        railKey = railKey,
                        title = "Continue Watching",
                        items = uiState.continueWatchingItems,
                        pendingFocusRailKey = pendingFocusRailKey,
                        lastFocusedIndex = lastFocusedIndexByRail[railKey] ?: 0,
                        onItemClick = onContinueWatchingClick,
                        onItemLongClick = { item -> continueWatchingOptionsItem = item },
                        onItemFocused = {},
                        onFocusedItemChanged = saveFocus,
                        onPendingFocusConsumed = { pendingFocusRailKey = null },
                        onFirstCardRequesterReady = registerRequester,
                        onMoveUp = moveUp,
                        onMoveDown = moveDown
                    )

                    is NetflixHomeRail.Catalog -> {
                        val row = rail.entry.row
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
                                // Keep the hero on the lead entry for this tab;
                                // scrolling rails must not swap the showcase.
                                onItemFocus(item)
                            },
                            onItemLongClick = onCatalogItemLongPress,
                            onLoadMore = onLoadMoreCatalog,
                            onFocusedItemChanged = saveFocus,
                            onPendingFocusConsumed = { pendingFocusRailKey = null },
                            onFirstCardRequesterReady = registerRequester,
                            onMoveUp = moveUp,
                            onMoveDown = moveDown,
                            posterLabelsEnabled = uiState.posterLabelsEnabled,
                            trailerPreviewUrls = trailerPreviewUrls,
                            trailerPreviewAudioUrls = trailerPreviewAudioUrls,
                            trailerEnabled = netflixTrailersEnabled,
                            trailerMuted = uiState.focusedPosterBackdropTrailerMuted,
                            onRequestTrailerPreview = { item ->
                                Log.i(NETFLIX_TRAILER_LOG, "rail request trailer id=${item.id} title=${item.name}")
                                onRequestTrailerPreview(item.id, item.name, item.releaseInfo, item.apiType)
                            }
                        )
                    }

                    is NetflixHomeRail.Collection -> NetflixCollectionRail(
                        railKey = railKey,
                        collection = rail.collection,
                        pendingFocusRailKey = pendingFocusRailKey,
                        lastFocusedIndex = lastFocusedIndexByRail[railKey] ?: 0,
                        onFolderClick = onNavigateToFolderDetail,
                        onFocusedItemChanged = saveFocus,
                        onPendingFocusConsumed = { pendingFocusRailKey = null },
                        onFirstCardRequesterReady = registerRequester,
                        onMoveUp = moveUp,
                        onMoveDown = moveDown
                    )
                }
            }
            item(key = "bottom_padding") {
                Spacer(modifier = Modifier.height(NetflixHomeSpacing.BottomFocusClearance))
            }
            }
        }

        val optionsItem = continueWatchingOptionsItem
        if (optionsItem != null) {
            ContinueWatchingOptionsDialog(
                item = optionsItem,
                onDismiss = {
                    continueWatchingOptionsItem = null
                    requestRailFocus(NETFLIX_CONTINUE_WATCHING_RAIL_KEY)
                },
                onRemove = {
                    val currentIndex = lastFocusedIndexByRail[NETFLIX_CONTINUE_WATCHING_RAIL_KEY] ?: 0
                    val targetIndex = if (uiState.continueWatchingItems.size <= 1) {
                        null
                    } else {
                        currentIndex.coerceAtMost(uiState.continueWatchingItems.size - 2).coerceAtLeast(0)
                    }
                    targetIndex?.let {
                        lastFocusedIndexByRail[NETFLIX_CONTINUE_WATCHING_RAIL_KEY] = it
                    }
                    onRemoveContinueWatching(
                        optionsItem.contentId(),
                        optionsItem.season(),
                        optionsItem.episode(),
                        optionsItem is ContinueWatchingItem.NextUp
                    )
                    continueWatchingOptionsItem = null
                    if (targetIndex != null) {
                        requestRailFocus(NETFLIX_CONTINUE_WATCHING_RAIL_KEY)
                    }
                },
                onDetails = {
                    onNavigateToDetail(optionsItem.contentId(), optionsItem.contentType(), "")
                    continueWatchingOptionsItem = null
                },
                onStartFromBeginning = {
                    onContinueWatchingStartFromBeginning(optionsItem)
                    continueWatchingOptionsItem = null
                },
                showPlayManually = showContinueWatchingManualPlayOption,
                onPlayManually = {
                    onContinueWatchingPlayManually(optionsItem)
                    continueWatchingOptionsItem = null
                }
            )
        }

        val pickerChip = genreTargetPickerChip
        if (pickerChip != null) {
            NetflixGenreTargetDialog(
                chip = pickerChip,
                options = genreTargetOptions,
                selectedTarget = uiState.genreRowTargets[pickerChip.key],
                onSelect = { target ->
                    onGenreTargetChanged(pickerChip.key, target)
                    genreTargetPickerChip = null
                    requestRailFocus(NETFLIX_GENRE_RAIL_KEY)
                },
                onDismiss = {
                    genreTargetPickerChip = null
                    requestRailFocus(NETFLIX_GENRE_RAIL_KEY)
                }
            )
        }
    }
}

private const val NETFLIX_CONTINUE_WATCHING_RAIL_KEY = "continue_watching"
private const val NETFLIX_GENRE_RAIL_KEY = "genre_strip"
private const val NETFLIX_HOME_HERO_ROW_INDEX = 0
private const val NETFLIX_HOME_STATIC_ROW_COUNT = 1
private const val NETFLIX_TOP_NAV_ITEM_COUNT = 7
private const val NETFLIX_METADATA_SETTLE_DELAY_MS = 120L
private const val NETFLIX_TRAILER_PREFETCH_DELAY_MS = 120L
private const val NETFLIX_FOCUS_SETTLE_DELAY_MS = 16L
private const val NETFLIX_FOCUS_RETRY_FRAMES = 8
private const val NETFLIX_VERTICAL_STEP_DELAY_MS = 140L
private const val VERTICAL_TARGET_HERO = -1
private const val VERTICAL_TARGET_TOP_NAV = -2
private const val NETFLIX_HOME_NAV_INDEX = 1

private sealed interface NetflixHomeRail {
    val railKey: String
    val orderKey: String

    data object Genres : NetflixHomeRail {
        override val railKey: String = NETFLIX_GENRE_RAIL_KEY
        override val orderKey: String = HOME_GENRES_ROW_KEY
    }

    data object ContinueWatching : NetflixHomeRail {
        override val railKey: String = NETFLIX_CONTINUE_WATCHING_RAIL_KEY
        override val orderKey: String = NETFLIX_CONTINUE_WATCHING_RAIL_KEY
    }

    data class Catalog(val entry: NetflixCatalogEntry) : NetflixHomeRail {
        override val railKey: String = entry.railKey
        override val orderKey: String = entry.row.legacyKey()
    }

    data class Collection(
        val collection: com.nuvio.tv.domain.model.Collection
    ) : NetflixHomeRail {
        override val railKey: String = "collection_${collection.id}"
        override val orderKey: String = railKey
    }
}

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

private data class NetflixCatalogEntry(
    val row: com.nuvio.tv.domain.model.CatalogRow,
    val railKey: String
)

private fun buildNetflixContentRails(
    homeRows: List<HomeRow>,
    fallbackCatalogRows: List<com.nuvio.tv.domain.model.CatalogRow>
): List<NetflixHomeRail> {
    if (homeRows.isEmpty()) {
        return fallbackCatalogRows.netflixCatalogEntries().map { entry ->
            NetflixHomeRail.Catalog(entry)
        }
    }

    return homeRows.mapIndexedNotNull { index, homeRow ->
        when (homeRow) {
            is HomeRow.Catalog -> homeRow.row
                .takeIf { it.items.isNotEmpty() }
                ?.let { row ->
                    NetflixHomeRail.Catalog(
                        NetflixCatalogEntry(
                            row = row,
                            railKey = "${row.netflixRailKey()}|position|$index"
                        )
                    )
                }
            is HomeRow.CollectionRow -> homeRow.collection
                .takeIf { it.folders.isNotEmpty() }
                ?.let { collection -> NetflixHomeRail.Collection(collection) }
            is HomeRow.PlaceholderCatalog -> null
        }
    }
}

/**
 * Fan out content collections (For You, New & Latest, Anime, …) into real title
 * rails using loaded folder catalogs. Browse hubs stay as collection showcases.
 * Movies/Shows tabs keep type-matched catalog rails only.
 */
private fun expandNetflixRails(
    orderedContentRails: List<NetflixHomeRail>,
    selectedTab: NetflixContentTab,
    folderRails: Map<String, com.nuvio.tv.domain.model.CatalogRow>
): List<NetflixHomeRail> {
    val wantedType = when (selectedTab) {
        NetflixContentTab.HOME -> null
        NetflixContentTab.MOVIES -> "movie"
        NetflixContentTab.SHOWS -> "series"
    }

    return buildList {
        orderedContentRails.forEach { rail ->
            when (rail) {
                NetflixHomeRail.Genres -> add(rail)

                is NetflixHomeRail.Catalog -> {
                    if (wantedType == null || rail.entry.row.apiType == wantedType) {
                        add(rail)
                    }
                }

                is NetflixHomeRail.Collection -> {
                    if (NetflixCollectionLayout.shouldFanOut(rail.collection)) {
                        rail.collection.folders.asSequence().take(12).forEach { folder ->
                            val source = NetflixCollectionLayout.pickSource(folder, selectedTab)
                                ?: return@forEach
                            if (wantedType != null && !source.type.equals(wantedType, ignoreCase = true)) {
                                return@forEach
                            }
                            val key = NetflixCollectionLayout.railKey(
                                collectionId = rail.collection.id,
                                folderId = folder.id,
                                source = source
                            )
                            val row = folderRails[key] ?: return@forEach
                            if (row.items.isEmpty()) return@forEach
                            add(
                                NetflixHomeRail.Catalog(
                                    NetflixCatalogEntry(
                                        row = row,
                                        railKey = key
                                    )
                                )
                            )
                        }
                    } else if (selectedTab == NetflixContentTab.HOME) {
                        add(rail)
                    }
                }

                NetflixHomeRail.ContinueWatching -> Unit
            }
        }
    }
}

private fun insertGenresRail(
    contentRails: List<NetflixHomeRail>,
    hasGenres: Boolean,
    orderKeys: List<String>,
    disabledKeys: Set<String>
): List<NetflixHomeRail> {
    if (!hasGenres || HOME_GENRES_ROW_KEY in disabledKeys) return contentRails

    val explicitIndex = orderKeys.indexOf(HOME_GENRES_ROW_KEY)
    val insertionIndex = if (explicitIndex < 0) {
        0
    } else {
        val precedingKeys = orderKeys.take(explicitIndex).toSet()
        contentRails.count { rail -> rail.orderKey in precedingKeys }
    }.coerceIn(0, contentRails.size)

    return contentRails.toMutableList().apply {
        add(insertionIndex, NetflixHomeRail.Genres)
    }
}

private fun List<com.nuvio.tv.domain.model.CatalogRow>.netflixCatalogEntries(): List<NetflixCatalogEntry> {
    return filter { row -> row.items.isNotEmpty() }
        .mapIndexed { index, row ->
            NetflixCatalogEntry(
                row = row,
                railKey = "${row.netflixRailKey()}|position|$index"
            )
        }
}

private fun buildGenreChipsFromCollections(
    collections: List<com.nuvio.tv.domain.model.Collection>,
    tab: NetflixContentTab
): List<NetflixGenreChip> {
    val genresCollection = collections.firstOrNull { it.title.equals("Genres", ignoreCase = true) }
    val animeCollection = collections.firstOrNull { it.title.equals("Anime", ignoreCase = true) }
    if (genresCollection == null && animeCollection == null) {
        return emptyList()
    }

    val chips = linkedMapOf<String, NetflixGenreChip>()
    val typeSuffix = when (tab) {
        NetflixContentTab.HOME -> ""
        NetflixContentTab.MOVIES -> "|movie"
        NetflixContentTab.SHOWS -> "|series"
    }

    genresCollection?.folders?.forEach { folder ->
        val source = NetflixCollectionLayout.pickSourceStrict(folder, tab) ?: return@forEach
        val label = folder.title.trim().ifBlank { return@forEach }
        val key = "genre|${label.lowercase()}$typeSuffix"
        chips.putIfAbsent(
            key,
            NetflixGenreChip(
                key = key,
                label = label,
                catalogId = source.catalogId,
                addonId = source.addonId,
                type = source.type,
                genreFilter = source.genre?.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) },
                collectionId = genresCollection.id,
                folderId = folder.id
            )
        )
    }

    animeCollection?.let { anime ->
        val match = NetflixCollectionLayout.pickAnimeGenreSource(anime, tab) ?: return@let
        val (folder, source) = match
        val key = "genre|anime$typeSuffix"
        chips.putIfAbsent(
            key,
            NetflixGenreChip(
                key = key,
                label = "Anime",
                catalogId = source.catalogId,
                addonId = source.addonId,
                type = source.type,
                genreFilter = source.genre?.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) },
                collectionId = anime.id,
                folderId = folder.id
            )
        )
    }

    return chips.values.sortedBy { it.label.lowercase() }
}

/**
 * Always open the type-matched catalog via CatalogSeeAll. FolderDetail for
 * Genres boards pulls 6+ sources and feels stuck; dedicated genre catalogs
 * (genre_action_movies, etc.) load as a single rail.
 */
private fun openGenreChip(
    genre: NetflixGenreChip,
    selectedTab: NetflixContentTab,
    mappedTarget: SyncGenreRowTarget?,
    onNavigateToGenre: (String, String, String, String?) -> Unit
) {
    when (mappedTarget?.kind) {
        GENRE_ROW_TARGET_CATALOG -> {
            val wanted = when (selectedTab) {
                NetflixContentTab.MOVIES -> "movie"
                NetflixContentTab.SHOWS -> "series"
                NetflixContentTab.HOME -> null
            }
            if (wanted == null || mappedTarget.type.equals(wanted, ignoreCase = true)) {
                onNavigateToGenre(
                    mappedTarget.catalogId,
                    mappedTarget.addonId,
                    mappedTarget.type,
                    genre.label
                )
                return
            }
        }
        // Folder remaps stay available via long-press, but default genre chips
        // must not open the multi-source folder board (too slow / empty-looking).
        else -> Unit
    }
    onNavigateToGenre(genre.catalogId, genre.addonId, genre.type, genre.label)
}

private fun buildGenreTargetOptions(
    collections: List<com.nuvio.tv.domain.model.Collection>,
    catalogEntries: List<NetflixCatalogEntry>,
    tab: NetflixContentTab,
    formatSubtitle: (Int, String) -> String
): List<NetflixGenreTargetOption> {
    val wantedType = when (tab) {
        NetflixContentTab.MOVIES -> "movie"
        NetflixContentTab.SHOWS -> "series"
        NetflixContentTab.HOME -> null
    }
    val catalogOptions = catalogEntries
        .map { it.row }
        .filter { row -> wantedType == null || row.apiType.equals(wantedType, ignoreCase = true) }
        .distinctBy { it.legacyKey() }
        .map { row ->
            NetflixGenreTargetOption(
                key = "catalog|${row.legacyKey()}",
                title = row.catalogName,
                subtitle = formatSubtitle(R.string.genre_target_catalog_subtitle, row.addonName),
                target = SyncGenreRowTarget(
                    kind = GENRE_ROW_TARGET_CATALOG,
                    addonId = row.addonId,
                    type = row.apiType,
                    catalogId = row.catalogId
                )
            )
        }
    val collectionOptions = collections.flatMap { collection ->
        collection.folders.mapNotNull { folder ->
            if (wantedType != null &&
                NetflixCollectionLayout.pickSourceStrict(folder, tab) == null
            ) {
                return@mapNotNull null
            }
            NetflixGenreTargetOption(
                key = "collection|${collection.id}|${folder.id}",
                title = folder.title,
                subtitle = formatSubtitle(R.string.genre_target_collection_subtitle, collection.title),
                target = SyncGenreRowTarget(
                    kind = GENRE_ROW_TARGET_COLLECTION_FOLDER,
                    collectionId = collection.id,
                    folderId = folder.id
                )
            )
        }
    }
    return catalogOptions + collectionOptions
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
