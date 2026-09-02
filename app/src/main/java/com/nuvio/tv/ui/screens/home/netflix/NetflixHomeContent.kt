package com.nuvio.tv.ui.screens.home.netflix

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.sync.GENRE_ROW_TARGET_CATALOG
import com.nuvio.tv.core.sync.GENRE_ROW_TARGET_COLLECTION_FOLDER
import com.nuvio.tv.core.sync.HOME_GENRES_ROW_KEY
import com.nuvio.tv.core.sync.SyncGenreRowTarget
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.ui.components.ContinueWatchingOptionsDialog
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.HomeScreenFocusState
import com.nuvio.tv.ui.screens.home.HomeRow
import com.nuvio.tv.ui.screens.home.HomeUiState
import com.nuvio.tv.ui.screens.home.NetflixScreenPackState
import com.nuvio.tv.core.viewpack.packOrderKeyMatchesRail
import com.nuvio.tv.core.viewpack.resolvePackHeroMeta
import com.nuvio.tv.ui.screens.home.contentId
import com.nuvio.tv.ui.screens.home.contentType
import com.nuvio.tv.ui.screens.home.episode
import com.nuvio.tv.ui.screens.home.season
import com.nuvio.tv.core.build.AppFeaturePolicy
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    onEnsureFolderRails: (List<NetflixFolderRailRequest>) -> Unit = {},
    onRailScaleChange: (String, Int) -> Unit = { _, _ -> },
    onRailTextPillsToggle: (String, Boolean) -> Unit = { _, _ -> },
    onRailFocusedInfoToggle: (String, Boolean) -> Unit = { _, _ -> },
    onRailPosterGrowToggle: (String, Boolean) -> Unit = { _, _ -> },
    onRailTrailerToggle: (String, Boolean) -> Unit = { _, _ -> },
    onCollectionExpandedToggle: (String, Boolean) -> Unit = { _, _ -> },
    selectedContentTab: NetflixContentTab = NetflixContentTab.HOME,
    onContentTabChanged: (NetflixContentTab) -> Unit = {}
) {
    var heroItem by remember(
        uiState.heroItems,
        uiState.catalogRows,
        uiState.viewPackFeaturedMeta?.id,
        uiState.viewPackHeroEnabled
    ) {
        mutableStateOf(resolveInitialHero(uiState))
    }
    var pendingHeroItem by remember { mutableStateOf(heroItem) }
    // Page wash follows focused card art, independent of the pinned hero showcase.
    var ambientArtUrl by remember {
        mutableStateOf(heroItem?.backdrop ?: heroItem?.poster)
    }
    var focusedTopNavigationIndex by remember { mutableStateOf(selectedContentTab.navIndex) }
    val topNavigationRequesters = remember { List(NETFLIX_TOP_NAV_ITEM_COUNT) { FocusRequester() } }
    val heroPrimaryRequester = remember { FocusRequester() }
    val firstCardRequestersByRail = remember { mutableStateMapOf<String, FocusRequester>() }
    // Seed focus maps from ViewModel on the first frame so Back from detail
    // already lands on the saved card instead of briefly flashing index 0.
    val seededFocusRailKey = remember(focusState.hasSavedFocus, focusState.focusedRowKey) {
        focusState.focusedRowKey.takeIf { focusState.hasSavedFocus }
    }
    val seededFocusItemIndex = remember(focusState.hasSavedFocus, focusState.focusedItemIndex) {
        if (focusState.hasSavedFocus) focusState.focusedItemIndex.coerceAtLeast(0) else 0
    }
    val lastFocusedIndexByRail = remember {
        mutableStateMapOf<String, Int>().also { map ->
            seededFocusRailKey?.let { map[it] = seededFocusItemIndex }
        }
    }
    val requestedTrailerKeys = remember { mutableStateMapOf<String, Boolean>() }
    val playedTrailerKeys = remember { mutableStateMapOf<String, Boolean>() }
    var pendingFocusRailKey by remember { mutableStateOf(seededFocusRailKey) }
    var railFocusJob by remember { mutableStateOf<Job?>(null) }
    var continueWatchingOptionsItem by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    var genreTargetPickerChip by remember { mutableStateOf<NetflixGenreChip?>(null) }
    var restoredSavedFocus by remember {
        mutableStateOf(false)
    }
    var previewTrailerHeroKey by remember { mutableStateOf<String?>(null) }
    var heroActionFocused by remember { mutableStateOf(false) }
    var topNavFocused by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(selectedContentTab) }
    var railCustomizationTarget by remember { mutableStateOf<NetflixRailCustomizationTarget?>(null) }
    var lastContentRailKey by remember {
        mutableStateOf(seededFocusRailKey)
    }
    LaunchedEffect(Unit) {
        NetflixHomeTabBridge.consume()?.let { tab ->
            selectedTab = tab
            focusedTopNavigationIndex = tab.navIndex
            onContentTabChanged(tab)
        }
    }
    LaunchedEffect(selectedContentTab) {
        if (selectedTab != selectedContentTab) {
            selectedTab = selectedContentTab
            focusedTopNavigationIndex = selectedContentTab.navIndex
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (focusState.hasSavedFocus) {
            focusState.verticalScrollIndex.coerceAtLeast(0)
        } else {
            0
        }
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val homePackActive = !uiState.activeViewPackName.isNullOrBlank()
    val tabScreenPack = when (selectedTab) {
        NetflixContentTab.HOME -> null
        NetflixContentTab.MOVIES -> uiState.moviesScreenPack
        NetflixContentTab.SHOWS -> uiState.showsScreenPack
    }
    val packActiveForTab = when (selectedTab) {
        NetflixContentTab.HOME -> homePackActive
        NetflixContentTab.MOVIES -> uiState.moviesScreenPack != null
        NetflixContentTab.SHOWS -> uiState.showsScreenPack != null
    }
    val tabOrderKeys = when {
        selectedTab == NetflixContentTab.HOME && homePackActive -> uiState.viewPackOrderKeys
        tabScreenPack != null -> tabScreenPack.orderKeys
        else -> emptyList()
    }
    val tabRowScales = tabScreenPack?.rowScales ?: uiState.viewPackRowScales
    val tabRowShowLabels = tabScreenPack?.rowShowLabels ?: uiState.viewPackRowShowLabels
    val tabRowTrailers = tabScreenPack?.rowTrailers ?: uiState.viewPackRowTrailers
    val tabHasTrailerOptIns = remember(tabRowTrailers) {
        tabRowTrailers.values.any { it }
    }
    val tabRowPosterGrow = tabScreenPack?.rowPosterGrow ?: uiState.viewPackRowPosterGrow
    val tabCatalogPosterScale = tabScreenPack?.catalogPosterScale ?: uiState.viewPackCatalogPosterScale
    val tabCollectionLandscapeScale =
        tabScreenPack?.collectionLandscapeScale ?: uiState.viewPackCollectionLandscapeScale
    val tabRailTitleScale =
        tabScreenPack?.collectionTitleScale ?: uiState.viewPackCollectionTitleScale
    val tabHeroTrailerEnabled =
        tabScreenPack?.heroTrailerEnabled ?: uiState.viewPackHeroTrailerEnabled
    val tabGenreCollectionId = when {
        selectedTab == NetflixContentTab.HOME && homePackActive -> uiState.viewPackGenreCollectionId
        tabScreenPack != null -> tabScreenPack.genreCollectionId
        else -> null
    }
    val contentRails = remember(uiState.homeRows, uiState.catalogRows, packActiveForTab) {
        buildNetflixContentRails(
            homeRows = uiState.homeRows,
            fallbackCatalogRows = uiState.catalogRows,
            keepEmptyRails = packActiveForTab
        )
    }
    // Pack/catalog rails past the first eager loads stay as blank placeholder cards
    // until something asks for lazy load — Modern home does this; Netflix must too.
    LaunchedEffect(uiState.homeRows, uiState.catalogRows) {
        uiState.homeRows.forEach { row ->
            when (row) {
                is HomeRow.PlaceholderCatalog -> onRequestLazyCatalogLoad(row.catalogKey)
                is HomeRow.Catalog -> {
                    val firstId = row.row.items.firstOrNull()?.id.orEmpty()
                    if (row.row.isLoading &&
                        (row.row.items.isEmpty() || firstId.startsWith("__placeholder_"))
                    ) {
                        onRequestLazyCatalogLoad(row.row.legacyKey())
                    }
                }
                else -> Unit
            }
        }
        uiState.catalogRows.forEach { row ->
            val firstId = row.items.firstOrNull()?.id.orEmpty()
            if (row.isLoading && firstId.startsWith("__placeholder_")) {
                onRequestLazyCatalogLoad(row.legacyKey())
            }
        }
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
    val genreChips = remember(
        uiState.genreCatalogCandidates,
        uiState.collections,
        selectedTab,
        tabGenreCollectionId
    ) {
        val collectionId = tabGenreCollectionId?.trim().orEmpty()
        if (collectionId.isNotEmpty()) {
            val collection = uiState.collections.firstOrNull { it.id == collectionId }
            if (collection != null) {
                return@remember buildGenrePillsFromCollection(collection)
            }
            emptyList()
        } else {
            buildGenreChipsFromAvailableCatalogs(
                candidates = uiState.genreCatalogCandidates,
                collections = uiState.collections,
                tab = selectedTab
            )
        }
    }
    val genreTargetOptions = remember(uiState.collections, catalogEntries, selectedTab, context) {
        buildGenreTargetOptions(uiState.collections, catalogEntries, selectedTab) { resourceId, value ->
            context.getString(resourceId, value)
        }
    }

    // Auto-sync chip destinations to the catalog that owns each genre for this tab.
    // User long-press remaps in genreRowTargets always win (never overwrite).
    LaunchedEffect(genreChips, selectedTab, uiState.genreRowTargets) {
        genreChips.forEach { chip ->
            if (!chip.key.startsWith("genre|")) return@forEach
            if (uiState.genreRowTargets.containsKey(chip.key)) return@forEach
            onGenreTargetChanged(
                chip.key,
                SyncGenreRowTarget(
                    kind = GENRE_ROW_TARGET_CATALOG,
                    addonId = chip.addonId,
                    type = chip.type,
                    catalogId = chip.catalogId
                )
            )
        }
    }
    val orderedContentRails = remember(
        contentRails,
        genreChips,
        uiState.homeCatalogOrderKeys,
        uiState.disabledHomeCatalogKeys,
        packActiveForTab,
        tabOrderKeys
    ) {
        val withGenres = insertGenresRail(
            contentRails = contentRails,
            hasGenres = genreChips.isNotEmpty(),
            orderKeys = if (packActiveForTab && tabOrderKeys.isNotEmpty()) {
                tabOrderKeys
            } else {
                uiState.homeCatalogOrderKeys
            },
            disabledKeys = if (packActiveForTab) emptySet() else uiState.disabledHomeCatalogKeys
        )
        if (packActiveForTab && tabOrderKeys.isNotEmpty()) {
            railsInPackOrder(withGenres, tabOrderKeys)
        } else {
            withGenres
        }
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
    // Active packs can author collection folders as title rails. Without a pack,
    // collections stay as normal Nuvio hubs so Remove restores the stock home.
    val fanOutRequests = remember(orderedContentRails, selectedTab, packActiveForTab) {
        emptyList<NetflixFolderRailRequest>()
    }
    LaunchedEffect(fanOutRequests) {
        onEnsureFolderRails(fanOutRequests)
    }
    val discoveryRails = remember(catalogEntries, continueWatchingGenres, selectedTab, packActiveForTab) {
        if (packActiveForTab) {
            emptyList()
        } else {
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
    }
    val visibleRails = remember(
        uiState.continueWatchingItems,
        orderedContentRails,
        selectedTab,
        discoveryRails,
        netflixFolderRails,
        fanOutRequests,
        packActiveForTab,
        tabScreenPack
    ) {
        val expanded = expandNetflixRails(
            orderedContentRails = orderedContentRails,
            selectedTab = if (packActiveForTab) NetflixContentTab.HOME else selectedTab,
            folderRails = netflixFolderRails,
            fanOutCollections = false
        )
        val fanOutCatalogIds = fanOutRequests.map { it.source.catalogId }.toSet()
        val withoutDuplicatePlaceholders = if (packActiveForTab || fanOutCatalogIds.isEmpty()) {
            expanded
        } else {
            expanded.filterNot { rail ->
                rail is NetflixHomeRail.Catalog &&
                    rail.entry.row.catalogId in fanOutCatalogIds &&
                    rail.entry.row.items.firstOrNull()?.id?.startsWith("__placeholder_") == true
            }
        }
        buildList {
            val genresFirst = expanded.firstOrNull() is NetflixHomeRail.Genres
            if (genresFirst) {
                add(NetflixHomeRail.Genres)
            }
            val cwItems = uiState.continueWatchingItems.filter { item ->
                continueWatchingMatchesTab(item, selectedTab)
            }
            val showCw = cwItems.isNotEmpty() && when {
                packActiveForTab && selectedTab == NetflixContentTab.HOME -> true
                packActiveForTab -> tabScreenPack?.hasContinueWatching == true
                selectedTab == NetflixContentTab.HOME -> true
                else -> true
            }
            if (showCw) {
                add(NetflixHomeRail.ContinueWatching)
            }
            addAll(if (genresFirst) withoutDuplicatePlaceholders.drop(1) else withoutDuplicatePlaceholders)
            addAll(discoveryRails)
        }
    }
    val railKeys = remember(visibleRails) { visibleRails.map { it.railKey } }
    val heroRowCount = if (heroItem != null) 1 else 0
    val netflixPackRequestsTrailers = packActiveForTab &&
        (tabHasTrailerOptIns || tabHeroTrailerEnabled)
    val netflixTrailersEnabled = remember(
        uiState.focusedPosterBackdropTrailerEnabled,
        netflixPackRequestsTrailers
    ) {
        AppFeaturePolicy.inAppTrailerPlaybackEnabled &&
            (
                NetflixHomeFeature.FORCE_TRAILER_AUTOPLAY ||
                    uiState.focusedPosterBackdropTrailerEnabled ||
                    netflixPackRequestsTrailers
            )
    }
    LaunchedEffect(
        netflixTrailersEnabled,
        packActiveForTab,
        tabHasTrailerOptIns,
        netflixPackRequestsTrailers,
        uiState.activeViewPackName,
        selectedTab
    ) {
        Log.d(
            NETFLIX_TRAILER_LOG,
            "netflix-home trailer-gate enabled=$netflixTrailersEnabled " +
                "packActive=$packActiveForTab hasRowOptIns=$tabHasTrailerOptIns " +
                "packRequests=$netflixPackRequestsTrailers " +
                "pack=${uiState.activeViewPackName} tab=$selectedTab"
        )
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
            if (ambientArtUrl.isNullOrBlank()) {
                ambientArtUrl = candidate?.backdrop ?: candidate?.poster
            }
        }
    }

    LaunchedEffect(
        heroItem?.key,
        listState.isScrollInProgress,
        netflixTrailersEnabled,
        uiState.activeViewPackName,
        uiState.viewPackHeroTrailerEnabled
    ) {
        val stableHero = heroItem ?: run {
            previewTrailerHeroKey = null
            return@LaunchedEffect
        }
        previewTrailerHeroKey = null
        val packBlocksHeroTrailer = !packActiveForTab || tabHeroTrailerEnabled
        if (!netflixTrailersEnabled || !packBlocksHeroTrailer || listState.isScrollInProgress) {
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
            lastFocusedIndexByRail[focusedRowKey] = focusState.focusedItemIndex.coerceAtLeast(0)
            lastContentRailKey = focusedRowKey
            val lazyListIndex = railKeys.indexOf(focusedRowKey) + heroRowCount
            if (listState.firstVisibleItemIndex != lazyListIndex) {
                runCatching { listState.scrollToItem(lazyListIndex) }
            }
            // Keep pending until the rail scaffold successfully focuses the card.
            pendingFocusRailKey = focusedRowKey
            repeat(NETFLIX_FOCUS_RETRY_FRAMES) {
                withFrameNanos { }
                if (pendingFocusRailKey != focusedRowKey) {
                    restoredSavedFocus = true
                    return@LaunchedEffect
                }
            }
            // Scaffold may still be composing — leave pending set and mark restored
            // so we do not restart with a second scroll/focus hop.
            restoredSavedFocus = true
        } else if (heroItem != null && !focusState.hasSavedFocus) {
            // Only fall back to hero focus when there is no saved position;
            // otherwise wait for the saved rail to appear in railKeys (rails
            // can still be loading right after returning from another screen).
            runCatching { listState.scrollToItem(NETFLIX_HOME_HERO_ROW_INDEX) }
            if (heroPrimaryRequester.requestFocusAfterFrames(2)) {
                restoredSavedFocus = true
            } else {
                delay(NETFLIX_FOCUS_SETTLE_DELAY_MS)
                restoredSavedFocus = true
            }
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
        val lazyListIndex = railIndex + heroRowCount
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == lazyListIndex }
        if (!visible) {
            runCatching { listState.scrollToItem(lazyListIndex) }
        }
        // Never focus card 0 then jump — scaffold restores lastFocusedIndex.
        pendingFocusRailKey = railKey
        repeat(NETFLIX_FOCUS_RETRY_FRAMES) {
            withFrameNanos { }
            if (pendingFocusRailKey != railKey) {
                return true
            }
        }
        return pendingFocusRailKey != railKey
    }

    suspend fun focusHeroNow(): Boolean {
        if (heroItem == null) {
            return railKeys.firstOrNull()?.let { focusRailNow(it) } ?: false
        }
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
        onContentTabChanged(selectedTab)
        if (appliedTab == selectedTab) return@LaunchedEffect
        appliedTab = selectedTab
        focusedTopNavigationIndex = selectedTab.navIndex
        railFocusJob?.cancel()
        pendingFocusRailKey = null
        lastContentRailKey = null
        pendingHeroItem = resolveHeroForTab(uiState, selectedTab, visibleRails) ?: pendingHeroItem
        runCatching { listState.scrollToItem(NETFLIX_HOME_HERO_ROW_INDEX) }
    }

    // When a pack hero resolves after catalogs load, pin the Netflix inset hero to it.
    LaunchedEffect(
        uiState.viewPackHeroEnabled,
        uiState.viewPackFeaturedMeta?.id,
        uiState.viewPackFeaturedAddonBaseUrl,
        uiState.moviesScreenPack?.heroDataSource,
        uiState.showsScreenPack?.heroDataSource,
        uiState.catalogRows,
        selectedTab
    ) {
        val packHero = resolveHeroForTab(uiState, selectedTab, visibleRails) ?: return@LaunchedEffect
        if (heroItem?.key != packHero.key) {
            pendingHeroItem = packHero
        }
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
            railIndex + heroRowCount,
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
        } else if (heroItem == null) {
            requestRailFocus(railKeys.firstOrNull())
        } else {
            requestHeroFocus()
        }
    }

    BackHandler(enabled = true) {
        when {
            !topNavFocused -> requestTopNavFocus()
            selectedTab != NetflixContentTab.HOME -> {
                selectedTab = NetflixContentTab.HOME
                onContentTabChanged(NetflixContentTab.HOME)
                focusedTopNavigationIndex = NETFLIX_HOME_NAV_INDEX
                runCatching { topNavigationRequesters[NETFLIX_HOME_NAV_INDEX].requestFocus() }
            }
            else -> requestContentFocusFromNav()
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
            if (visibleRails.isEmpty() && (uiState.isLoading || uiState.homeRows.any { it is HomeRow.PlaceholderCatalog })) {
                NetflixLoadingSkeletonRails(modifier = Modifier.weight(1f))
            } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                contentPadding = PaddingValues(top = NetflixHomeTokens.HeroTopGap),
                verticalArrangement = Arrangement.spacedBy(NetflixHomeTokens.RailSpacing)
            ) {
            if (heroItem != null) {
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
                                !heroItem?.catalogItemId()?.let { trailerPreviewUrls[it] }.isNullOrBlank() &&
                                (!packActiveForTab || tabHeroTrailerEnabled),
                            trailerPreviewMuted = uiState.focusedPosterBackdropTrailerMuted,
                            trailerStartDelayMs = uiState.trailerStartDelayMs,
                            onTrailerEnded = {
                                heroItem?.key?.let { playedTrailerKeys[it] = true }
                                previewTrailerHeroKey = null
                            },
                            onFocusedChanged = {
                                heroActionFocused = it
                                if (it) {
                                    ambientArtUrl = heroItem?.backdrop ?: heroItem?.poster
                                }
                            },
                            onViewDetails = { target -> navigateToTargetDetails(target, onNavigateToDetail) }
                        )
                    }
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
                        from <= 0 -> if (heroItem != null) {
                            queueVerticalTarget(VERTICAL_TARGET_HERO)
                        } else {
                            queueVerticalTarget(VERTICAL_TARGET_TOP_NAV)
                        }
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
                                onNavigateToGenre = onNavigateToGenre,
                                onNavigateToFolderDetail = onNavigateToFolderDetail
                            )
                        },
                        onGenreLongPressed = { genre -> genreTargetPickerChip = genre }
                    )

                    NetflixHomeRail.ContinueWatching -> NetflixContinueWatchingRail(
                        railKey = railKey,
                        title = "Continue Watching",
                        items = uiState.continueWatchingItems.filter { item ->
                            continueWatchingMatchesTab(item, selectedTab)
                        },
                        useEpisodeThumbnails = uiState.useEpisodeThumbnailsInCw,
                        pendingFocusRailKey = pendingFocusRailKey,
                        lastFocusedIndex = lastFocusedIndexByRail[railKey] ?: 0,
                        onItemClick = onContinueWatchingClick,
                        onItemLongClick = { item -> continueWatchingOptionsItem = item },
                        onItemFocused = { item ->
                            ambientArtUrl = item.netflixAmbientArtUrl()
                        },
                        onFocusedItemChanged = saveFocus,
                        onPendingFocusConsumed = { pendingFocusRailKey = null },
                        onFirstCardRequesterReady = registerRequester,
                        onMoveUp = moveUp,
                        onMoveDown = moveDown,
                        titleScale = if (packActiveForTab) tabRailTitleScale else 1f
                    )

                    is NetflixHomeRail.Catalog -> {
                        val row = rail.entry.row
                        val packShowMeta = tabRowShowLabels[rail.orderKey]
                        val packTrailer = tabRowTrailers[rail.orderKey] == true
                        val rowTrailerEnabled = netflixTrailersEnabled &&
                            (!packActiveForTab || packTrailer || !tabHasTrailerOptIns)
                        val packPosterGrow = tabRowPosterGrow[rail.orderKey] != false
                        LaunchedEffect(
                            railKey,
                            netflixTrailersEnabled,
                            packActiveForTab,
                            tabHasTrailerOptIns,
                            packTrailer,
                            rowTrailerEnabled
                        ) {
                            Log.d(
                                NETFLIX_TRAILER_LOG,
                                "rail trailer-gate key=$railKey catalog=${row.catalogId} " +
                                    "enabled=$rowTrailerEnabled homeGate=$netflixTrailersEnabled " +
                                    "packActive=$packActiveForTab packTrailer=$packTrailer " +
                                    "hasRowOptIns=$tabHasTrailerOptIns"
                            )
                        }
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
                                // Hero showcase stays on the lead entry; page wash
                                // follows whatever card is currently focused.
                                ambientArtUrl = item.netflixAmbientArtUrl()
                                onItemFocus(item)
                            },
                            onItemLongClick = onCatalogItemLongPress,
                            onLoadMore = onLoadMoreCatalog,
                            onFocusedItemChanged = saveFocus,
                            onPendingFocusConsumed = { pendingFocusRailKey = null },
                            onFirstCardRequesterReady = registerRequester,
                            onMoveUp = moveUp,
                            onMoveDown = moveDown,
                            onFirstItemMoveLeft = {
                                if (!uiState.viewPackCustomizationModeEnabled || !packActiveForTab) {
                                    false
                                } else {
                                    railCustomizationTarget = NetflixRailCustomizationTarget(
                                        orderKey = rail.orderKey,
                                        title = row.catalogName.replaceFirstChar { it.uppercase() },
                                        isCollection = false,
                                        collectionId = null,
                                        canBecomeTextPills = false,
                                        isTextPills = false,
                                        showFocusedInfo = packShowMeta == true,
                                        posterGrow = packPosterGrow,
                                        trailerEnabled = packTrailer,
                                        scalePercent = ((tabRowScales[rail.orderKey] ?: 1f) * 100f).roundToInt(),
                                        canExpandFolders = false,
                                        foldersExpanded = false
                                    )
                                    true
                                }
                            },
                            posterLabelsEnabled = if (packActiveForTab) {
                                packShowMeta == true
                            } else {
                                uiState.posterLabelsEnabled
                            },
                            railScale = if (packActiveForTab) {
                                (tabRowScales[rail.orderKey] ?: 1f) *
                                    tabCatalogPosterScale
                            } else {
                                1f
                            },
                            // Stock Netflix always shows the catalogue footer; packs opt in/out.
                            showFocusedMetadata = if (packActiveForTab) {
                                packShowMeta == true
                            } else {
                                true
                            },
                            posterGrow = if (packActiveForTab) packPosterGrow else true,
                            trailerPreviewUrls = trailerPreviewUrls,
                            trailerPreviewAudioUrls = trailerPreviewAudioUrls,
                            trailerEnabled = rowTrailerEnabled,
                            trailerMuted = uiState.focusedPosterBackdropTrailerMuted,
                            trailerStartDelayMs = uiState.trailerStartDelayMs,
                            onRequestTrailerPreview = { item ->
                                Log.i(NETFLIX_TRAILER_LOG, "rail request trailer id=${item.id} title=${item.name}")
                                onRequestTrailerPreview(item.id, item.name, item.releaseInfo, item.apiType)
                            },
                            allowEmpty = packActiveForTab,
                            titleScale = if (packActiveForTab) tabRailTitleScale else 1f
                        )
                    }

                    is NetflixHomeRail.Collection -> NetflixCollectionRail(
                        railKey = railKey,
                        collection = rail.collection,
                        pendingFocusRailKey = pendingFocusRailKey,
                        lastFocusedIndex = lastFocusedIndexByRail[railKey] ?: 0,
                        onFolderClick = onNavigateToFolderDetail,
                        onFocusedItemChanged = { index, key ->
                            saveFocus(index, key)
                            val folder = rail.collection.folders.getOrNull(index)
                            ambientArtUrl = folder?.coverImageUrl ?: ambientArtUrl
                        },
                        onPendingFocusConsumed = { pendingFocusRailKey = null },
                        onFirstCardRequesterReady = registerRequester,
                        onMoveUp = moveUp,
                        onMoveDown = moveDown,
                        onFirstItemMoveLeft = {
                            if (!uiState.viewPackCustomizationModeEnabled || !packActiveForTab) {
                                false
                            } else {
                                val folderKeys = rail.collection.folders.map {
                                    com.nuvio.tv.core.viewpack.packFolderOrderKey(rail.collection.id, it.id)
                                }
                                railCustomizationTarget = NetflixRailCustomizationTarget(
                                    orderKey = rail.orderKey,
                                    title = rail.collection.title,
                                    isCollection = true,
                                    collectionId = rail.collection.id,
                                    canBecomeTextPills = true,
                                    isTextPills = false,
                                    showFocusedInfo = tabRowShowLabels[rail.orderKey] == true,
                                    posterGrow = tabRowPosterGrow[rail.orderKey] != false,
                                    trailerEnabled = tabRowTrailers[rail.orderKey] == true,
                                    scalePercent = ((tabRowScales[rail.orderKey] ?: 1f) * 100f).roundToInt(),
                                    canExpandFolders = folderKeys.isNotEmpty(),
                                    foldersExpanded = folderKeys.any { it in uiState.viewPackOrderKeys }
                                )
                                true
                            }
                        },
                        landscapeScale = if (packActiveForTab) {
                            tabCollectionLandscapeScale
                        } else {
                            1f
                        },
                        titleScale = if (packActiveForTab) {
                            tabRailTitleScale
                        } else {
                            1f
                        },
                        allowEmpty = packActiveForTab
                    )
                }
            }
            item(key = "bottom_padding") {
                Spacer(modifier = Modifier.height(NetflixHomeSpacing.BottomFocusClearance))
            }
            } // LazyColumn
            } // else !skeleton
        }

        railCustomizationTarget?.let { target ->
            NetflixRailCustomizationPanel(
                target = target,
                onDismiss = { railCustomizationTarget = null },
                onScaleChange = { percent ->
                    onRailScaleChange(target.orderKey, percent)
                    railCustomizationTarget = target.copy(scalePercent = percent)
                },
                onFocusedInfoToggle = {
                    val next = !target.showFocusedInfo
                    onRailFocusedInfoToggle(target.orderKey, next)
                    railCustomizationTarget = target.copy(showFocusedInfo = next)
                },
                onPosterGrowToggle = {
                    val next = !target.posterGrow
                    onRailPosterGrowToggle(target.orderKey, next)
                    railCustomizationTarget = target.copy(posterGrow = next)
                },
                onTrailerToggle = {
                    val next = !target.trailerEnabled
                    onRailTrailerToggle(target.orderKey, next)
                    railCustomizationTarget = target.copy(trailerEnabled = next)
                },
                onTextPillsToggle = {
                    val next = !target.isTextPills
                    onRailTextPillsToggle(target.orderKey, next)
                    railCustomizationTarget = target.copy(isTextPills = next)
                },
                onExpandFoldersToggle = {
                    val collectionId = target.collectionId
                    if (collectionId != null) {
                        val next = !target.foldersExpanded
                        onCollectionExpandedToggle(collectionId, next)
                        railCustomizationTarget = target.copy(foldersExpanded = next)
                    }
                }
            )
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

private data class NetflixRailCustomizationTarget(
    val orderKey: String,
    val title: String,
    val isCollection: Boolean,
    val collectionId: String?,
    val canBecomeTextPills: Boolean,
    val isTextPills: Boolean,
    val showFocusedInfo: Boolean,
    val posterGrow: Boolean,
    val trailerEnabled: Boolean,
    val scalePercent: Int,
    val canExpandFolders: Boolean,
    val foldersExpanded: Boolean
)

@Composable
private fun NetflixRailCustomizationPanel(
    target: NetflixRailCustomizationTarget,
    onDismiss: () -> Unit,
    onScaleChange: (Int) -> Unit,
    onFocusedInfoToggle: () -> Unit,
    onPosterGrowToggle: () -> Unit,
    onTrailerToggle: () -> Unit,
    onTextPillsToggle: () -> Unit,
    onExpandFoldersToggle: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding, vertical = 84.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            onClick = {},
            modifier = Modifier.widthIn(min = 420.dp, max = 520.dp),
            colors = CardDefaults.colors(
                containerColor = NetflixThemeChrome.background.copy(alpha = 0.96f),
                focusedContainerColor = NetflixThemeChrome.background.copy(alpha = 0.96f)
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(14.dp)
                ),
                focusedBorder = Border(
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(14.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
            scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = target.title,
                    color = NetflixThemeChrome.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.home_customize_rail_hint),
                    color = NetflixThemeChrome.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { onScaleChange((target.scalePercent - 5).coerceAtLeast(55)) }) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                    }
                    Text(
                        text = stringResource(R.string.home_customize_rail_size, target.scalePercent),
                        color = NetflixThemeChrome.textPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { onScaleChange((target.scalePercent + 5).coerceAtMost(250)) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
                NetflixRailEditorButton(
                    label = stringResource(R.string.home_customize_tile_detail),
                    selected = target.showFocusedInfo,
                    onClick = onFocusedInfoToggle
                )
                NetflixRailEditorButton(
                    label = stringResource(R.string.home_customize_focus_grow),
                    selected = target.posterGrow,
                    onClick = onPosterGrowToggle
                )
                NetflixRailEditorButton(
                    label = stringResource(R.string.home_customize_trailers),
                    selected = target.trailerEnabled,
                    onClick = onTrailerToggle
                )
                if (target.canBecomeTextPills) {
                    NetflixRailEditorButton(
                        label = stringResource(R.string.home_customize_text_pills),
                        selected = target.isTextPills,
                        onClick = onTextPillsToggle
                    )
                }
                if (target.canExpandFolders) {
                    NetflixRailEditorButton(
                        label = stringResource(R.string.home_customize_expand_folders),
                        selected = target.foldersExpanded,
                        onClick = onExpandFoldersToggle
                    )
                }
                Button(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_done))
                }
            }
        }
    }
}

@Composable
private fun NetflixRailEditorButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (selected) {
                stringResource(R.string.home_customize_enabled, label)
            } else {
                stringResource(R.string.home_customize_disabled, label)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val NETFLIX_CONTINUE_WATCHING_RAIL_KEY = "continue_watching"
private const val NETFLIX_GENRE_RAIL_KEY = "genre_strip"
private const val NETFLIX_HOME_HERO_ROW_INDEX = 0
private const val NETFLIX_HOME_STATIC_ROW_COUNT = 1
private const val NETFLIX_TOP_NAV_ITEM_COUNT = 8
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

private fun resolveHeroForTab(
    uiState: HomeUiState,
    tab: NetflixContentTab,
    visibleRails: List<NetflixHomeRail>
): NetflixHeroItem? {
    when (tab) {
        NetflixContentTab.HOME -> return resolveInitialHero(uiState)
        NetflixContentTab.MOVIES ->
            resolveScreenPackHero(uiState, uiState.moviesScreenPack)?.let { return it }
        NetflixContentTab.SHOWS ->
            resolveScreenPackHero(uiState, uiState.showsScreenPack)?.let { return it }
    }
    val firstEntry = visibleRails.firstNotNullOfOrNull { rail ->
        (rail as? NetflixHomeRail.Catalog)?.entry?.takeIf { it.row.items.isNotEmpty() }
    }
    return firstEntry?.row?.items?.firstOrNull()?.toNetflixHeroItem(firstEntry.row.addonBaseUrl)
}

private fun resolveScreenPackHero(
    uiState: HomeUiState,
    screen: NetflixScreenPackState?
): NetflixHeroItem? {
    if (screen == null || !screen.heroEnabled) return null
    val byKey = uiState.catalogRows.associateBy { it.legacyKey() }
    val meta = resolvePackHeroMeta(screen.heroDataSource, screen.orderKeys, byKey) ?: return null
    val row = byKey.values.firstOrNull { candidate ->
        candidate.items.any { it.id == meta.id && it.apiType == meta.apiType }
    }
    return meta.toNetflixHeroItem(row?.addonBaseUrl.orEmpty())
}

private fun railsInPackOrder(
    rails: List<NetflixHomeRail>,
    keys: List<String>
): List<NetflixHomeRail> {
    val unused = rails.toMutableList()
    return buildList {
        for (key in keys) {
            val exactIndex = unused.indexOfFirst { it.orderKey == key }
            val matchIndex = if (exactIndex >= 0) {
                exactIndex
            } else {
                unused.indexOfFirst { packOrderKeyMatchesRail(key, it.orderKey) }
            }
            if (matchIndex < 0) continue
            add(unused.removeAt(matchIndex))
        }
    }
}

private fun continueWatchingMatchesTab(
    item: ContinueWatchingItem,
    tab: NetflixContentTab
): Boolean {
    if (tab == NetflixContentTab.HOME) return true
    val type = item.contentType()
    return when (tab) {
        NetflixContentTab.MOVIES -> type.equals("movie", ignoreCase = true)
        NetflixContentTab.SHOWS -> type.equals("series", ignoreCase = true)
        NetflixContentTab.HOME -> true
    }
}

private fun resolveInitialHero(uiState: HomeUiState): NetflixHeroItem? {
    // View packs author the Netflix inset hero via the pack hero block —
    // prefer that over stock heroItems / first catalog.
    val packMeta = uiState.viewPackFeaturedMeta
    if (uiState.viewPackHeroEnabled && packMeta != null) {
        return packMeta.toNetflixHeroItem(uiState.viewPackFeaturedAddonBaseUrl)
    }

    val hero = uiState.heroItems.firstOrNull()
    if (hero != null) return hero.toNetflixHeroItem("")

    val catalog = uiState.catalogRows.firstOrNull { it.items.isNotEmpty() }
    val catalogItem = catalog?.items?.firstOrNull()
    if (catalog != null && catalogItem != null) {
        return catalogItem.toNetflixHeroItem(catalog.addonBaseUrl)
    }

    // Never fall back to Continue Watching — that briefly flashed CW under a
    // later hero-catalog pick on cold start.
    return null
}

private data class NetflixCatalogEntry(
    val row: com.nuvio.tv.domain.model.CatalogRow,
    val railKey: String
)

private fun buildNetflixContentRails(
    homeRows: List<HomeRow>,
    fallbackCatalogRows: List<com.nuvio.tv.domain.model.CatalogRow>,
    keepEmptyRails: Boolean = false
): List<NetflixHomeRail> {
    if (homeRows.isEmpty()) {
        return fallbackCatalogRows.netflixCatalogEntries().map { entry ->
            NetflixHomeRail.Catalog(entry)
        }
    }

    return homeRows.mapIndexedNotNull { index, homeRow ->
        when (homeRow) {
            is HomeRow.Catalog -> homeRow.row
                .takeIf { keepEmptyRails || it.items.isNotEmpty() }
                ?.let { row ->
                    NetflixHomeRail.Catalog(
                        NetflixCatalogEntry(
                            row = row,
                            railKey = "${row.netflixRailKey()}|position|$index"
                        )
                    )
                }
            is HomeRow.CollectionRow -> homeRow.collection
                .takeIf { keepEmptyRails || it.folders.isNotEmpty() }
                ?.let { collection -> NetflixHomeRail.Collection(collection) }
            is HomeRow.PlaceholderCatalog -> if (!keepEmptyRails) {
                null
            } else {
                NetflixHomeRail.Catalog(
                    NetflixCatalogEntry(
                        row = com.nuvio.tv.domain.model.CatalogRow(
                            addonId = homeRow.addonId,
                            addonName = homeRow.addonName,
                            addonBaseUrl = homeRow.addonBaseUrl,
                            catalogId = homeRow.catalogId,
                            catalogName = homeRow.displayTitle.ifBlank { homeRow.catalogName },
                            type = com.nuvio.tv.domain.model.ContentType.fromString(homeRow.apiType),
                            rawType = homeRow.apiType,
                            items = emptyList(),
                            isLoading = true,
                            hasMore = false
                        ),
                        railKey = "${homeRow.catalogKey}|position|$index"
                    )
                )
            }
        }
    }
}

/**
 * Fan out content collections (For You, New & Latest, Anime, …) into real title
 * rails using loaded folder catalogs. Browse hubs stay as collection showcases.
 * Movies/Shows tabs keep type-matched catalog rails only.
 *
 * When [fanOutCollections] is false (active Studio pack), collections stay as
 * authored hub rails — pack order is the source of truth.
 */
private fun expandNetflixRails(
    orderedContentRails: List<NetflixHomeRail>,
    selectedTab: NetflixContentTab,
    folderRails: Map<String, com.nuvio.tv.domain.model.CatalogRow>,
    fanOutCollections: Boolean = true
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
                    if (fanOutCollections && NetflixCollectionLayout.shouldFanOut(rail.collection)) {
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

/**
 * Always open the type-matched catalog via CatalogSeeAll. FolderDetail for
 * Genres boards pulls 6+ sources and feels stuck; dedicated genre catalogs
 * (genre_action_movies, etc.) load as a single rail.
 *
 * [NetflixGenreChip.genreFilter] is passed as the CatalogSeeAll genre query —
 * null for dedicated genre_* catalogs so results aren't double-filtered.
 */
private fun openGenreChip(
    genre: NetflixGenreChip,
    selectedTab: NetflixContentTab,
    mappedTarget: SyncGenreRowTarget?,
    onNavigateToGenre: (String, String, String, String?) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit
) {
    when (mappedTarget?.kind) {
        GENRE_ROW_TARGET_CATALOG -> {
            val wantedType = when (selectedTab) {
                NetflixContentTab.MOVIES -> "movie"
                NetflixContentTab.SHOWS -> "series"
                NetflixContentTab.HOME -> null
            }
            if (wantedType == null || mappedTarget.type.equals(wantedType, ignoreCase = true)) {
                onNavigateToGenre(
                    mappedTarget.catalogId,
                    mappedTarget.addonId,
                    mappedTarget.type,
                    genre.genreFilter
                )
                return
            }
        }
        else -> Unit
    }
    val collectionId = genre.collectionId
    val folderId = genre.folderId
    if (!collectionId.isNullOrBlank() && !folderId.isNullOrBlank()) {
        onNavigateToFolderDetail(collectionId, folderId)
        return
    }
    onNavigateToGenre(genre.catalogId, genre.addonId, genre.type, genre.genreFilter)
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
