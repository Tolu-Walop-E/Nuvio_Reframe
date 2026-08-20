package com.nuvio.tv.ui.screens.home

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.catalogRowStableKey
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.domain.model.mergeCatalogPage
import com.nuvio.tv.domain.model.nextCatalogSkip
import com.nuvio.tv.domain.model.skipStep
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.model.supportsExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PLACEHOLDER_IMAGE_URL
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import com.nuvio.tv.core.util.filterReleasedItems
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class CatalogUpdateResult(
    val displayRows: List<CatalogRow>,
    val heroItems: List<com.nuvio.tv.domain.model.MetaPreview>,
    val gridItems: List<GridItem>,
    val fullRows: List<CatalogRow>
)

@OptIn(FlowPreview::class)
internal fun HomeViewModel.observeCollectionsPipeline() {
    viewModelScope.launch {
        collectionsDataStore.collections
            .distinctUntilChanged()
            .debounce(300)
            .collectLatest { collections ->
                // Deduplicate by collection ID (keep last occurrence) to prevent
                // duplicate LazyColumn keys when users import overlapping collections.
                collectionsCache = collections.associateBy { it.id }.values.toList()
                _uiState.update { state -> state.copy(collections = collectionsCache) }
                rebuildCatalogOrder(addonsCache)
                ensureActivePackCatalogsLoaded()
                scheduleUpdateCatalogRows()
            }
    }
}

internal fun HomeViewModel.loadHomeCatalogOrderPreferencePipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.homeCatalogOrderKeys.collectLatest { keys ->
            homeCatalogOrderKeys = keys
            _uiState.update { state -> state.copy(homeCatalogOrderKeys = keys) }
            rebuildCatalogOrder(addonsCache)
            scheduleUpdateCatalogRows()
        }
    }
}

internal fun HomeViewModel.loadFollowAddonsOrderPipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.followAddonsOrder.collectLatest { enabled ->
            followAddonsOrderEnabled = enabled
            rebuildCatalogOrder(addonsCache)
            scheduleUpdateCatalogRows()
        }
    }
}

internal fun HomeViewModel.loadActiveViewPackPipeline() {
    viewModelScope.launch {
        maybeImportPendingViewPackFile()
        combine(
            layoutPreferenceDataStore.activeViewPackJson,
            layoutPreferenceDataStore.viewPackRotationState
        ) { json, rotationState -> json to rotationState }.collectLatest { (json, rotationState) ->
            if (json.isNullOrBlank()) {
                activeViewPackOrderKeys = null
                activeViewPackRowScales = emptyMap()
                activeViewPackRowShowLabels = emptyMap()
                activeViewPackRowTrailers = emptyMap()
                activeViewPackRowPosterGrow = emptyMap()
                activeViewPackCatalogRefs = emptyMap()
                activeViewPackCollectionHubRefs = emptyMap()
                activeViewPackHeroDataSource = null
                moviesViewPackOrderKeys = null
                showsViewPackOrderKeys = null
                _uiState.update { state ->
                    if (state.activeViewPackName == null &&
                        !state.activeViewPackRotateEnabled &&
                        state.viewPackRowShowLabels.isEmpty() &&
                        !state.viewPackHeroEnabled &&
                        state.moviesScreenPack == null &&
                        state.showsScreenPack == null
                    ) {
                        state
                    } else {
                        state.copy(
                            activeViewPackName = null,
                            activeViewPackRotateEnabled = false,
                            viewPackOrderKeys = emptyList(),
                            viewPackRowScales = emptyMap(),
                            viewPackRowShowLabels = emptyMap(),
                            viewPackRowTrailers = emptyMap(),
                            viewPackRowPosterGrow = emptyMap(),
                            viewPackCatalogPosterScale = 1f,
                            viewPackCollectionLandscapeScale = 1f,
                            viewPackHeroEnabled = false,
                            viewPackHeroTrailerEnabled = false,
                            viewPackHeroLabel = "Featured",
                            viewPackHeroDataSource = null,
                            viewPackFeaturedPreview = null,
                            viewPackFeaturedMeta = null,
                            viewPackFeaturedAddonBaseUrl = "",
                            viewPackFeaturedHeightPx = null,
                            moviesScreenPack = null,
                            showsScreenPack = null,
                            viewPackGenreCollectionId = null
                        )
                    }
                }
                rebuildCatalogOrder(addonsCache)
                scheduleUpdateCatalogRows()
                return@collectLatest
            }
            try {
                val parsed = com.nuvio.tv.core.viewpack.parseViewPackJson(json)
                val rotation = com.nuvio.tv.core.viewpack.rotateUnlockedBlocks(parsed, rotationState)
                if (rotation.state != rotationState) {
                    // Persisting re-emits and this block runs again with the settled state.
                    layoutPreferenceDataStore.setViewPackRotationState(rotation.state)
                }
                val rotated = parsed.copy(blocks = rotation.blocks)
                val moviesPack = rotated.moviesScreen
                val showsPack = rotated.showsScreen
                activeViewPackOrderKeys = com.nuvio.tv.core.viewpack.homeOrderKeysFromPack(rotated)
                activeViewPackRowScales = com.nuvio.tv.core.viewpack.homeRowScalesFromPack(rotated)
                activeViewPackRowShowLabels = com.nuvio.tv.core.viewpack.homeRowShowLabelsFromPack(rotated)
                activeViewPackRowTrailers = com.nuvio.tv.core.viewpack.homeRowTrailersFromPack(rotated)
                activeViewPackRowPosterGrow = com.nuvio.tv.core.viewpack.homeRowPosterGrowFromPack(rotated)
                activeViewPackCatalogRefs = com.nuvio.tv.core.viewpack.mergePackCatalogRefs(
                    rotated,
                    moviesPack,
                    showsPack
                )
                activeViewPackCollectionHubRefs =
                    com.nuvio.tv.core.viewpack.mergePackCollectionHubRefs(
                        rotated,
                        moviesPack,
                        showsPack
                    )
                activeViewPackHeroDataSource = com.nuvio.tv.core.viewpack.packHeroDataSource(rotated)
                moviesViewPackOrderKeys = moviesPack?.let {
                    com.nuvio.tv.core.viewpack.homeOrderKeysFromPack(it)
                }
                showsViewPackOrderKeys = showsPack?.let {
                    com.nuvio.tv.core.viewpack.homeOrderKeysFromPack(it)
                }
                // Expanded folder / catalog rails need their backing catalogs fetched
                // even when those catalogs are not in the default home set.
                val collectionsById = collectionsCache.associateBy { it.id }
                listOfNotNull(rotated, moviesPack, showsPack).forEach { pack ->
                    com.nuvio.tv.core.viewpack.packFolderCatalogRefs(
                        pack = pack,
                        collectionsById = collectionsById
                    ).values.forEach { ref ->
                        ensureCatalogLoaded(
                            ref.addonId,
                            ref.type,
                            ref.catalogId,
                            extraArgs = folderCatalogExtraArgs(ref.genre)
                        )
                    }
                }
                activeViewPackCatalogRefs.values.forEach { ref ->
                    val resolved = resolvedPackCatalogForOrderKey(ref.orderKey) ?: ref
                    ensureCatalogLoaded(
                        resolved.addonId,
                        resolved.type,
                        resolved.catalogId
                    )
                }
                _uiState.update { state ->
                    state.copy(
                        activeViewPackName = rotated.name,
                        activeViewPackRotateEnabled = rotated.rotateUnlocked,
                        viewPackOrderKeys = activeViewPackOrderKeys.orEmpty(),
                        viewPackRowScales = activeViewPackRowScales,
                        viewPackRowShowLabels = activeViewPackRowShowLabels,
                        viewPackRowTrailers = activeViewPackRowTrailers,
                        viewPackRowPosterGrow = activeViewPackRowPosterGrow,
                        viewPackCatalogPosterScale =
                            com.nuvio.tv.core.viewpack.normalizePackCardScale(
                                rotated.catalogPosterScale
                            ),
                        viewPackCollectionLandscapeScale =
                            com.nuvio.tv.core.viewpack.normalizePackCardScale(
                                rotated.collectionLandscapeScale
                            ),
                        viewPackHeroEnabled = com.nuvio.tv.core.viewpack.packHasHero(rotated),
                        viewPackHeroTrailerEnabled =
                            com.nuvio.tv.core.viewpack.packHeroTrailerEnabled(rotated),
                        viewPackHeroLabel = com.nuvio.tv.core.viewpack.packHeroLabel(rotated),
                        viewPackHeroDataSource = activeViewPackHeroDataSource,
                        viewPackFeaturedHeightPx = com.nuvio.tv.core.viewpack.packHeroHeightPx(rotated),
                        moviesScreenPack = moviesPack?.toNetflixScreenPackState(),
                        showsScreenPack = showsPack?.toNetflixScreenPackState(),
                        viewPackGenreCollectionId =
                            com.nuvio.tv.core.viewpack.packGenreCollectionId(rotated)
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "Invalid active view pack", e)
                activeViewPackOrderKeys = null
                activeViewPackRowScales = emptyMap()
                activeViewPackRowShowLabels = emptyMap()
                activeViewPackRowTrailers = emptyMap()
                activeViewPackRowPosterGrow = emptyMap()
                activeViewPackCatalogRefs = emptyMap()
                activeViewPackCollectionHubRefs = emptyMap()
                activeViewPackHeroDataSource = null
                moviesViewPackOrderKeys = null
                showsViewPackOrderKeys = null
                _uiState.update { state ->
                    state.copy(
                        activeViewPackName = null,
                        activeViewPackRotateEnabled = false,
                        viewPackOrderKeys = emptyList(),
                        viewPackRowScales = emptyMap(),
                        viewPackRowShowLabels = emptyMap(),
                        viewPackRowTrailers = emptyMap(),
                        viewPackRowPosterGrow = emptyMap(),
                        viewPackCatalogPosterScale = 1f,
                        viewPackCollectionLandscapeScale = 1f,
                        viewPackHeroEnabled = false,
                        viewPackHeroTrailerEnabled = false,
                        viewPackHeroLabel = "Featured",
                        viewPackHeroDataSource = null,
                        viewPackFeaturedPreview = null,
                        viewPackFeaturedMeta = null,
                        viewPackFeaturedAddonBaseUrl = "",
                        viewPackFeaturedHeightPx = null,
                        moviesScreenPack = null,
                        showsScreenPack = null,
                        viewPackGenreCollectionId = null
                    )
                }
            }
            rebuildCatalogOrder(addonsCache)
            scheduleUpdateCatalogRows()
        }
    }
}

/** One-shot import: push `current.view.json` into the app external files dir (adb / Send-to-TV). */
private suspend fun HomeViewModel.maybeImportPendingViewPackFile() {
    val dir = appContext.getExternalFilesDir(null) ?: return
    val candidates = listOf(
        java.io.File(dir, "current.view.json"),
        java.io.File(dir, "import.view.json")
    )
    val file = candidates.firstOrNull { it.exists() && it.canRead() } ?: return
    try {
        val text = file.readText().trim()
        if (text.isEmpty()) return
        val pack = com.nuvio.tv.core.viewpack.parseViewPackJson(text)
        layoutPreferenceDataStore.setActiveViewPackJson(
            com.nuvio.tv.core.viewpack.serializeViewPackJson(pack)
        )
        val done = java.io.File(dir, "${file.name}.imported")
        if (done.exists()) done.delete()
        if (!file.renameTo(done)) {
            file.delete()
        }
        android.util.Log.i("HomeViewModel", "Imported view pack from ${file.name}: ${pack.name}")
    } catch (e: Exception) {
        android.util.Log.w("HomeViewModel", "Failed to import pending view pack file", e)
    }
}

internal fun HomeViewModel.loadDisabledHomeCatalogPreferencePipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.disabledHomeCatalogKeys.collectLatest { keys ->
            val newKeys = keys.toSet()
            if (newKeys == disabledHomeCatalogKeys) return@collectLatest
            disabledHomeCatalogKeys = newKeys
            _uiState.update { state -> state.copy(disabledHomeCatalogKeys = newKeys) }
            rebuildCatalogOrder(addonsCache)
            if (addonsCache.isNotEmpty()) {
                loadAllCatalogsPipeline(addonsCache)
            } else {
                scheduleUpdateCatalogRows()
            }
        }
    }
}

internal fun HomeViewModel.loadGenreRowTargetsPipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.genreRowTargets
            .distinctUntilChanged()
            .collectLatest { targets ->
                _uiState.update { state -> state.copy(genreRowTargets = targets) }
            }
    }
}

internal fun HomeViewModel.loadCustomCatalogTitlesPipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.customCatalogTitles.collectLatest { titles ->
            customCatalogTitles = titles
            scheduleUpdateCatalogRows()
        }
    }
}

internal fun HomeViewModel.observeTmdbSettingsPipeline() {
    viewModelScope.launch {
        tmdbSettingsDataStore.settings
            .distinctUntilChanged()
            .collectLatest { settings ->
                val languageChanged = currentTmdbSettings.language != settings.language
                val releaseDatesChanged = currentTmdbSettings.useReleaseDates != settings.useReleaseDates
                currentTmdbSettings = settings
                val tmdbEnabledForLayout = settings.enabled &&
                    (_uiState.value.homeLayout != HomeLayout.MODERN || settings.modernHomeEnabled)
                val enrichEnabled = tmdbEnabledForLayout || externalMetaPrefetchEnabled
                _uiState.update { it.copy(heroEnrichmentEnabled = enrichEnabled) }
                if (languageChanged || releaseDatesChanged) {
                    // Allow re-enrichment with the updated TMDB metadata selection on next focus.
                    prefetchedTmdbIds.clear()
                    prefetchedExternalMetaIds.clear()
                    backgroundMetaPrefetchedIds.clear()
                    _enrichedPreviews.value = emptyMap()
                    _lastEnrichedPreview.value = null
                }
                scheduleUpdateCatalogRows()
            }
    }
}

@OptIn(FlowPreview::class)
internal fun HomeViewModel.observeInstalledAddonsPipeline() {
    viewModelScope.launch {
        addonRepository.getInstalledAddons()
            .distinctUntilChanged()
            .collectLatest { installedAddons ->
                val addons = installedAddons.enabledAddons()
                addonsCache = addons
                _uiState.update {
                    it.copy(
                        genreCatalogCandidates =
                            com.nuvio.tv.ui.screens.home.netflix.buildGenreCatalogCandidates(addons)
                    )
                }
                loadAllCatalogsPipeline(addons)
            }
    }
}

internal suspend fun HomeViewModel.loadAllCatalogsPipeline(
    addons: List<Addon>,
    forceReload: Boolean = false
) {
    val signature = buildHomeCatalogLoadSignature(addons)
    val hasActiveLoads = synchronized(activeCatalogLoadJobs) { activeCatalogLoadJobs.any { it.isActive } }
    if (!forceReload &&
        signature == activeCatalogLoadSignature &&
        (hasActiveLoads || hasAnyCatalogRows())
    ) {
        return
    }

    activeCatalogLoadSignature = signature
    catalogsLoadInProgress = true
    catalogLoadGeneration += 1
    val generation = catalogLoadGeneration
    cancelInFlightCatalogLoads()

    // On reload (not first load), keep existing UI data visible while new
    // catalogs load in the background to avoid a flash of empty content.
    val isReload = _uiState.value.catalogRows.isNotEmpty() || _uiState.value.homeRows.isNotEmpty()
    if (!isReload) {
        _uiState.update { it.copy(isLoading = true, error = null, installedAddonsCount = addons.size) }
        synchronized(catalogStateLock) {
            catalogOrder.clear()
        }
        clearCatalogData()
    } else {
        _uiState.update { it.copy(error = null, installedAddonsCount = addons.size) }
    }
    posterStatusReconcileJob?.cancel()
    reconcilePosterStatusObserversPipeline(emptyList())
    _fullCatalogRows.value = emptyList()
    hasRenderedFirstCatalog = false
    trailerPreviewLoadingIds.clear()
    trailerPreviewNegativeCache.clear()
    trailerPreviewNegativeCacheTimestamps.clear()
    trailerPreviewUrlsState.clear()
    trailerPreviewAudioUrlsState.clear()
    activeTrailerPreviewItemId = null
    trailerPreviewRequestVersion = 0L
    prefetchedExternalMetaIds.clear()
    backgroundMetaPrefetchedIds.clear()
    externalMetaPrefetchInFlightIds.clear()
    externalMetaPrefetchJob?.cancel()
    pendingExternalMetaPrefetchItemId = null
    prefetchedTmdbIds.clear()
    tmdbEnrichFocusJob?.cancel()
    pendingTmdbEnrichItemId = null
    lastHeroEnrichmentSignature = null
    lastHeroEnrichedItems = emptyList()
    heroItemOrder = emptyList()

    try {
        if (addons.isEmpty()) {
            catalogsLoadInProgress = false
            _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.home_error_no_addons)) }
            return
        }

        rebuildCatalogOrder(addons)

        // Hero has its own catalog sources (heroCatalogKeys) configured
        // independently in Layout Settings.  When the user has explicitly
        // selected hero catalogs, load those even if they are disabled from
        // home rows.  When no hero catalogs are selected, the hero simply
        // piggybacks on whatever home catalogs are loaded — if none are
        // loaded, the hero has no data and won't render.
        val heroCatalogSet = currentHeroCatalogKeys.toSet()
        val hasHeroSelections = heroCatalogSet.isNotEmpty()

        if (isCatalogOrderEmpty() && !hasHeroSelections) {
            catalogsLoadInProgress = false
            _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.home_error_no_catalog_addons)) }
            return
        }

        val catalogsToLoad = addons.flatMap { addon ->
            addon.catalogs
                .filterNot {
                    !it.shouldShowOnHome() || isCatalogDisabled(
                        addonBaseUrl = addon.baseUrl,
                        addonId = addon.id,
                        type = it.apiType,
                        catalogId = it.id,
                        catalogName = it.name
                    )
                }
                .map { catalog -> addon to catalog }
        }

        // Load hero-selected catalogs even if disabled from home rows —
        // the hero has its own catalog source independent of home rows.
        val alreadyLoadingKeys = catalogsToLoad.map { (addon, catalog) ->
            catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
        }.toSet()
        val heroOnlyCatalogs = if (hasHeroSelections) {
            addons.flatMap { addon ->
                addon.catalogs
                    .filter { catalog ->
                        val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
                        key in heroCatalogSet && key !in alreadyLoadingKeys && !catalog.isSearchOnlyCatalog()
                    }
                    .map { catalog -> addon to catalog }
            }
        } else {
            emptyList()
        }

        val packCatalogsToLoad = packCatalogLoadPairs()
        val allCatalogsToLoad = catalogsToLoad + heroOnlyCatalogs + packCatalogsToLoad
        if (allCatalogsToLoad.isEmpty()) {
            // No home catalogs and no hero catalogs to load —
            // but collections may still exist to render.
            catalogsLoadInProgress = false
            if (hasCatalogOrderEntries()) {
                scheduleUpdateCatalogRows()
            } else {
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.home_error_no_catalog_addons)) }
            }
            return
        }

        // ── Lazy loading: split into eager and deferred ──
        val heroOnlyKeys = heroOnlyCatalogs.map { (addon, catalog) ->
            catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
        }.toSet()

        // Build display title helper (respects custom titles)
        val titlesSnapshot = customCatalogTitles
        val showTypeSuffix = _uiState.value.catalogTypeSuffixEnabled
        val strTypeMovie = appContext.getString(R.string.type_movie)
        val strTypeSeries = appContext.getString(R.string.type_series)
        fun displayTitle(addon: Addon, catalog: CatalogDescriptor): String {
            val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
            val custom = titlesSnapshot[key]
            val baseName = if (!custom.isNullOrBlank()) custom else catalog.name
            val catalogName = baseName.replaceFirstChar { it.uppercase() }
            if (!showTypeSuffix) return catalogName
            val typeLabel = when (catalog.apiType.lowercase()) {
                "movie" -> strTypeMovie.ifBlank { catalog.apiType.replaceFirstChar { it.uppercase() } }
                "series" -> strTypeSeries.ifBlank { catalog.apiType.replaceFirstChar { it.uppercase() } }
                else -> catalog.apiType.replaceFirstChar { it.uppercase() }
            }
            return "$catalogName - $typeLabel"
        }

        // Determine which home catalogs to load eagerly vs lazily.
        // Grid layout loads all catalogs eagerly since it doesn't support
        // placeholder shimmer rows — all content must be available upfront.
        // Wait for layout preferences if not yet ready, to avoid wrong eager/lazy split.
        if (!_uiState.value.layoutPreferencesReady) {
            _uiState.first { it.layoutPreferencesReady }
        }
        val packActive = activeViewPackOrderKeys != null &&
            _uiState.value.homeLayout != HomeLayout.GRID &&
            _uiState.value.homeLayout != HomeLayout.CLASSIC
        val isGridLayout = _uiState.value.homeLayout == HomeLayout.GRID
        val eagerHomeCatalogs = when {
            isGridLayout -> catalogsToLoad
            packActive && packCatalogsToLoad.isNotEmpty() -> packCatalogsToLoad
            else -> catalogsToLoad.take(eagerCatalogLoadCount)
        }
        val lazyHomeCatalogs = when {
            isGridLayout -> emptyList()
            packActive && packCatalogsToLoad.isNotEmpty() -> emptyList()
            else -> catalogsToLoad.drop(eagerCatalogLoadCount)
        }

        // Build placeholder descriptors for lazy catalogs
        synchronized(catalogStateLock) {
            pendingLazyCatalogs.clear()
            placeholderDescriptors.clear()
        }
        lazyLoadRequestedKeys.clear()
        emptyCatalogRetryKeys.clear()

        (eagerHomeCatalogs + lazyHomeCatalogs).forEach { (addon, catalog) ->
            val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
            synchronized(catalogStateLock) {
                placeholderDescriptors.add(
                    HomeViewModel.PlaceholderDescriptor(
                        catalogKey = key,
                        addonId = addon.id,
                        addonName = addon.displayName,
                        addonBaseUrl = addon.baseUrl,
                        catalogId = catalog.id,
                        catalogName = catalog.name,
                        apiType = catalog.apiType,
                        displayTitle = displayTitle(addon, catalog)
                    )
                )
            }
        }

        lazyHomeCatalogs.forEach { (addon, catalog) ->
            val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
            synchronized(catalogStateLock) {
                pendingLazyCatalogs[key] = addon to catalog
            }
        }

        Log.d(HomeViewModel.TAG,
            "Lazy loading: eager=${eagerHomeCatalogs.size} lazy=${lazyHomeCatalogs.size}"
        )

        val eagerCatalogs = eagerHomeCatalogs + heroOnlyCatalogs
        pendingCatalogLoads = eagerCatalogs.size
        eagerCatalogs.forEach { (addon, catalog) ->
            loadCatalogPipeline(addon, catalog, generation)
        }
        ensureActivePackCatalogsLoaded()

        // Immediately schedule an update so placeholder rows appear in the UI
        // while catalogs are still loading.
        scheduleUpdateCatalogRows()

        // Safety flush: if catalogs trickle in slowly (e.g., slow addons),
        // ensure the user sees whatever content is available within a
        // reasonable window, even if not all catalogs have completed yet.
        if (eagerCatalogs.size > 1) {
            viewModelScope.launch {
                delay(800L)
                if (pendingCatalogLoads > 0 && hasAnyCatalogRows()) {
                    Log.d(HomeViewModel.TAG, "Safety flush: pending=$pendingCatalogLoads — forcing UI update")
                    scheduleUpdateCatalogRows()
                }
            }
        }
    } catch (e: Exception) {
        catalogsLoadInProgress = false
        _uiState.update { it.copy(isLoading = false, error = e.message) }
    }
}

/**
 * Additively loads hero-selected catalogs that are not already in [catalogsMap].
 * Unlike [loadAllCatalogsPipeline] this does NOT clear existing state — it only
 * fills in missing hero catalog data so the hero section can render.
 *
 * Called from the presentation pipeline when [currentHeroCatalogKeys] arrives
 * after the initial catalog load (due to the layout preference debounce).
 */
internal fun HomeViewModel.loadHeroCatalogsPipeline() {
    val heroCatalogKeys = currentHeroCatalogKeys
    if (heroCatalogKeys.isEmpty() || addonsCache.isEmpty()) return

    val heroCatalogSet = heroCatalogKeys.toSet()
    val alreadyLoadedKeys = snapshotCatalogKeys()
    val missingHeroKeys = heroCatalogSet - alreadyLoadedKeys
    if (missingHeroKeys.isEmpty()) {
        // All hero catalogs already loaded — just refresh presentation
        scheduleUpdateCatalogRows()
        return
    }

    val heroToLoad = addonsCache.flatMap { addon ->
        addon.catalogs
            .filter { catalog ->
                val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
                key in missingHeroKeys && !catalog.isSearchOnlyCatalog()
            }
            .map { catalog -> addon to catalog }
    }

    if (heroToLoad.isEmpty()) {
        scheduleUpdateCatalogRows()
        return
    }

    val generation = catalogLoadGeneration
    pendingCatalogLoads += heroToLoad.size
    heroToLoad.forEach { (addon, catalog) ->
        loadCatalogPipeline(addon, catalog, generation)
    }
}

internal fun HomeViewModel.loadCatalogPipeline(
    addon: Addon,
    catalog: CatalogDescriptor,
    generation: Long,
    extraArgs: Map<String, String> = emptyMap()
) {
    val loadJob = viewModelScope.launch {
        var hasCountedCompletion = false
        catalogLoadSemaphore.withPermit {
            if (generation != catalogLoadGeneration) return@withPermit
            val supportsSkip = catalog.supportsExtra("skip")
            val skipStep = catalog.skipStep()
            Log.d(
                HomeViewModel.TAG,
                "Loading home catalog addonId=${addon.id} addonName=${addon.name} type=${catalog.apiType} catalogId=${catalog.id} catalogName=${catalog.name} supportsSkip=$supportsSkip skipStep=$skipStep"
            )
            catalogRepository.getCatalog(
                addonBaseUrl = addon.baseUrl,
                addonId = addon.id,
                addonName = addon.displayName,
                catalogId = catalog.id,
                catalogName = catalog.name,
                type = catalog.apiType,
                skip = 0,
                skipStep = skipStep,
                extraArgs = extraArgs,
                supportsSkip = supportsSkip
            ).collect { result ->
                if (generation != catalogLoadGeneration) return@collect
                when (result) {
                    is NetworkResult.Success -> {
                        val key = catalogKey(
                            addonId = addon.id,
                            type = catalog.apiType,
                            catalogId = catalog.id
                        )
                        replaceCatalogRow(key, result.data)
                        // Remove placeholder descriptor now that real data is available
                        synchronized(catalogStateLock) {
                            placeholderDescriptors.removeAll { it.catalogKey == key }
                        }
                        if (!hasCountedCompletion) {
                            pendingCatalogLoads = (pendingCatalogLoads - 1).coerceAtLeast(0)
                            hasCountedCompletion = true
                        }
                        Log.d(
                            HomeViewModel.TAG,
                            "Home catalog loaded addonId=${addon.id} type=${catalog.apiType} catalogId=${catalog.id} items=${result.data.items.size} pending=$pendingCatalogLoads"
                        )
                        if (pendingCatalogLoads == 0) {
                            catalogsLoadInProgress = false
                        }
                        // Batch updates: only trigger a UI rebuild when all
                        // eager catalogs have completed, or let the debounce
                        // in scheduleUpdateCatalogRows coalesce intermediate
                        // arrivals.  When pending == 0 we always flush.
                        if (pendingCatalogLoads == 0) {
                            scheduleUpdateCatalogRows()
                        } else if (!hasRenderedFirstCatalog) {
                            // First content arriving — show it quickly so the
                            // user sees something beyond placeholders.
                            scheduleUpdateCatalogRows()
                        }
                        // Otherwise, let the next completion or the final
                        // pendingCatalogLoads==0 trigger the update.
                    }
                    is NetworkResult.Error -> {
                        val errorKey = catalogKey(
                            addonId = addon.id,
                            type = catalog.apiType,
                            catalogId = catalog.id
                        )
                        // Remove placeholder on error so it doesn't show forever
                        synchronized(catalogStateLock) {
                            placeholderDescriptors.removeAll { it.catalogKey == errorKey }
                        }
                        if (!hasCountedCompletion) {
                            pendingCatalogLoads = (pendingCatalogLoads - 1).coerceAtLeast(0)
                            hasCountedCompletion = true
                        }
                        Log.w(
                            HomeViewModel.TAG,
                            "Home catalog failed addonId=${addon.id} type=${catalog.apiType} catalogId=${catalog.id} code=${result.code} message=${result.message}"
                        )
                        if (pendingCatalogLoads == 0) {
                            catalogsLoadInProgress = false
                        }
                        // Same batching logic as success path.
                        if (pendingCatalogLoads == 0 || !hasRenderedFirstCatalog) {
                            scheduleUpdateCatalogRows()
                        }
                    }
                    NetworkResult.Loading -> {
                        /* Handled by individual row */
                    }
                }
            }
        }
    }
    registerCatalogLoadJob(loadJob)
}

internal fun HomeViewModel.loadMoreCatalogItemsPipeline(catalogId: String, addonId: String, type: String) {
    val key = catalogKey(addonId = addonId, type = type, catalogId = catalogId)
    val currentRow = readCatalogRow(key)

    if (currentRow == null) {
        return
    }

    if (currentRow.isLoading || !currentRow.hasMore) {
        return
    }
    if (key in _loadingCatalogs.value) {
        return
    }

    updateCatalogRow(key) { it.copy(isLoading = true) }
    _loadingCatalogs.update { it + key }

    viewModelScope.launch {
        val addon = addonsCache.find { it.id == addonId }
        if (addon == null) {
            return@launch
        }

        val nextSkip = currentRow.nextCatalogSkip()
        catalogRepository.getCatalog(
            addonBaseUrl = addon.baseUrl,
            addonId = addon.id,
            addonName = addon.displayName,
            catalogId = catalogId,
            catalogName = currentRow.catalogName,
            type = currentRow.apiType,
            skip = nextSkip,
            skipStep = currentRow.skipStep,
            supportsSkip = currentRow.supportsSkip
        ).collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    updateCatalogRow(key) { latestRow ->
                        val mergedRow = latestRow.mergeCatalogPage(result.data)
                        mergedRow
                    }
                    _loadingCatalogs.update { it - key }
                    scheduleUpdateCatalogRows()
                }
                is NetworkResult.Error -> {
                    updateCatalogRow(key) { it.copy(isLoading = false) }
                    _loadingCatalogs.update { it - key }
                    scheduleUpdateCatalogRows()
                }
                NetworkResult.Loading -> { }
            }
        }
    }
}

internal suspend fun HomeViewModel.updateCatalogRowsPipeline() {
    val (orderedKeys, catalogSnapshot) = snapshotCatalogState()
    val collectionsSnapshot = collectionsCache.associateBy { "collection_${it.id}" }
    val heroCatalogKeys = currentHeroCatalogKeys
    val currentLayout = _uiState.value.homeLayout
    val currentGridItems = _uiState.value.gridItems
    val heroSectionEnabled = _uiState.value.heroSectionEnabled
    val hideUnreleased = _uiState.value.hideUnreleasedContent
    val titlesSnapshot = customCatalogTitles

    val (displayRows, baseHeroItems, baseGridItems, fullRowsFiltered) = withContext(Dispatchers.Default) {
        val rawRows = orderedKeys.mapNotNull { key ->
            val row = catalogSnapshot[key] ?: return@mapNotNull null
            val custom = titlesSnapshot[key]
            if (!custom.isNullOrBlank()) row.copy(catalogName = custom) else row
        }
        val orderedRows = if (hideUnreleased) {
            val today = LocalDate.now()
            rawRows.map { it.filterReleasedItems(today) }
        } else {
            rawRows
        }
        // Catalogs loaded on demand (genre chips, remaps) live in catalogsMap but
        // may not be in home order — still expose them via fullCatalogRows so
        // CatalogSeeAll can render after ensureCatalogLoaded.
        val orderedKeySet = orderedKeys.toSet()
        val extraLoadedRows = catalogSnapshot.keys
            .asSequence()
            .filter { it !in orderedKeySet }
            .mapNotNull { key ->
                val row = catalogSnapshot[key] ?: return@mapNotNull null
                if (row.items.isEmpty()) return@mapNotNull null
                val custom = titlesSnapshot[key]
                if (!custom.isNullOrBlank()) row.copy(catalogName = custom) else row
            }
            .toList()
        val extraRowsFiltered = if (hideUnreleased) {
            val today = LocalDate.now()
            extraLoadedRows.map { it.filterReleasedItems(today) }
        } else {
            extraLoadedRows
        }
        val fullRowsForBrowse = orderedRows + extraRowsFiltered
        val selectedHeroCatalogSet = heroCatalogKeys.toSet()
        val selectedHeroRows = if (selectedHeroCatalogSet.isNotEmpty()) {
            // Include hero catalogs from ordered rows
            val fromOrdered = orderedRows.filter { row ->
                val key = row.legacyKey()
                key in selectedHeroCatalogSet
            }
            // Also include hero catalogs loaded but not in catalog order
            // (e.g., catalogs disabled from home rows but selected for hero)
            val heroOnlyRows = selectedHeroCatalogSet
                .filter { it !in orderedKeySet }
                .mapNotNull { catalogSnapshot[it] }
            val heroOnlyFiltered = if (hideUnreleased) {
                val today = LocalDate.now()
                heroOnlyRows.map { it.filterReleasedItems(today) }
            } else {
                heroOnlyRows
            }
            fromOrdered + heroOnlyFiltered
        } else {
            emptyList()
        }
        fun stableHeroCandidates(row: CatalogRow, candidates: kotlin.collections.Collection<MetaPreview>): List<MetaPreview> {
            return candidates.sortedWith(
                compareBy<MetaPreview> { stableHeroSortKey(row, it) }
                    .thenBy { it.id }
            )
        }
        fun slotShuffled(rows: List<CatalogRow>, filter: (MetaPreview) -> Boolean, currentOrder: List<String>): List<MetaPreview> {
            val totalCatalogs = rows.size.coerceAtLeast(1)
            val baseSlot = 7 / totalCatalogs
            val remainder = 7 % totalCatalogs
            val seen = mutableSetOf<String>()
            val result = mutableListOf<MetaPreview>()
            rows.forEachIndexed { index, row ->
                val slot = baseSlot + if (index < remainder) 1 else 0
                val existing = currentOrder.filter { id -> row.items.any { it.id == id } }
                val byId = row.items.filter(filter).associateBy { it.id }
                val ordered = existing.mapNotNull { byId[it] }
                val new = stableHeroCandidates(
                    row = row,
                    candidates = byId.values.filter { it.id !in existing }
                )
                // Filter out duplicates but keep taking until slot is filled
                val unique = (ordered + new).filter { seen.add(it.id) }
                result += unique.take(slot)
            }
            return result
        }

        val currentHeroOrder = heroItemOrder

        val heroItemsFromSelectedCatalogs = slotShuffled(
            selectedHeroRows, { it.hasHeroArtwork() }, currentHeroOrder
        )
        val fallbackHeroItemsFromSelectedCatalogs = slotShuffled(
            selectedHeroRows, { true }, currentHeroOrder
        )
        // When orderedRows is empty (all catalogs disabled), include any
        // hero-only loaded catalogs as fallback hero sources.
        val allHeroFallbackRows = if (orderedRows.isNotEmpty()) {
            orderedRows
        } else {
            val nonOrderedRows = catalogSnapshot.keys
                .filter { it !in orderedKeySet }
                .mapNotNull { catalogSnapshot[it] }
            if (hideUnreleased) {
                val today = LocalDate.now()
                nonOrderedRows.map { it.filterReleasedItems(today) }
            } else {
                nonOrderedRows
            }
        }
        val fallbackHeroItemsWithArtwork = slotShuffled(
            allHeroFallbackRows, { it.hasHeroArtwork() }, currentHeroOrder
        )

        val computedHeroItems = when {
            heroItemsFromSelectedCatalogs.isNotEmpty() -> heroItemsFromSelectedCatalogs
            fallbackHeroItemsFromSelectedCatalogs.isNotEmpty() -> fallbackHeroItemsFromSelectedCatalogs
            fallbackHeroItemsWithArtwork.isNotEmpty() -> fallbackHeroItemsWithArtwork
            else -> emptyList()
        }

        val computedDisplayRows = orderedRows.map { row ->
            val shouldKeepFullRowInModern = currentLayout == HomeLayout.MODERN
            val gridTruncateLimit = 24
            if (row.items.size > gridTruncateLimit && !shouldKeepFullRowInModern) {
                val key = row.legacyKey()
                val cachedEntry = getTruncatedRowCacheEntry(key)
                if (cachedEntry != null && cachedEntry.sourceRow === row) {
                    cachedEntry.truncatedRow
                } else {
                    val truncatedRow = row.copy(
                        items = row.items.take(gridTruncateLimit),
                        hasMore = true
                    )
                    putTruncatedRowCacheEntry(
                        key,
                        HomeViewModel.TruncatedRowCacheEntry(
                            sourceRow = row,
                            truncatedRow = truncatedRow
                        )
                    )
                    truncatedRow
                }
            } else {
                val key = row.legacyKey()
                removeTruncatedRowCacheEntry(key)
                row
            }
        }

        CatalogUpdateResult(computedDisplayRows, computedHeroItems, emptyList(), fullRowsForBrowse)
    }

    _fullCatalogRows.update { rows ->
        if (rows == fullRowsFiltered) rows else fullRowsFiltered
    }

    heroItemOrder = baseHeroItems.map { it.id }

    val (computedHomeRows, nextGridItems) = withContext(Dispatchers.Default) {
        val computedHomeRows = buildList {
            val displayRowsByKey = displayRows.associateBy { it.legacyKey() }
            // Build a lookup of placeholder descriptors by key for lazy catalogs
            val placeholdersByKey = synchronized(catalogStateLock) {
                placeholderDescriptors.associateBy { it.catalogKey }
            }
            val addedCollectionIds = mutableSetOf<String>()
            val packActive = activeViewPackOrderKeys != null
            collectionsCache.forEach { collection ->
                val key = "collection_${collection.id}"
            if (!packActive && collection.pinToTop && key !in disabledHomeCatalogKeys && addedCollectionIds.add(collection.id)) {
                add(HomeRow.CollectionRow(collection))
            }
        }
        val packFolderRefs = if (packActive) {
            val refs = LinkedHashMap<String, com.nuvio.tv.core.viewpack.PackFolderCatalogRef>()
            for (key in orderedKeys) {
                if (!com.nuvio.tv.core.viewpack.isPackFolderOrderKey(key) || key in refs) continue
                for (collection in collectionsCache) {
                    for (folder in collection.folders) {
                        val candidate = com.nuvio.tv.core.viewpack.packFolderOrderKey(
                            collection.id,
                            folder.id
                        )
                        if (candidate != key) continue
                        val source = folder.sources
                            .filterIsInstance<com.nuvio.tv.domain.model.AddonCatalogCollectionSource>()
                            .firstOrNull()
                            ?: folder.catalogSources.firstOrNull()?.let { legacy ->
                                com.nuvio.tv.domain.model.AddonCatalogCollectionSource(
                                    addonId = legacy.addonId,
                                    type = legacy.type,
                                    catalogId = legacy.catalogId,
                                    genre = legacy.genre
                                )
                            }
                            ?: continue
                        refs[key] = com.nuvio.tv.core.viewpack.PackFolderCatalogRef(
                            orderKey = key,
                            collectionId = collection.id,
                            folderId = folder.id,
                            folderTitle = folder.title,
                            addonId = source.addonId,
                            type = source.type,
                            catalogId = source.catalogId,
                            genre = source.genre
                        )
                    }
                }
            }
            refs
        } else {
            emptyMap()
        }

        for (key in orderedKeys) {
            // With an active pack the order already IS the explicit allow-list,
            // so the per-rail disabled preference must not drop pack rails.
            if (!packActive && key in disabledHomeCatalogKeys) continue
            val collectionEntry = collectionsSnapshot[key]
            if (collectionEntry != null) {
                if ((packActive || !collectionEntry.pinToTop) && addedCollectionIds.add(collectionEntry.id)) {
                    add(HomeRow.CollectionRow(collectionEntry))
                }
            } else {
                    val folderRef = packFolderRefs[key]
                    val packCatalog = if (packActive) resolvedPackCatalogForOrderKey(key) else null
                    val catalogLookupKey = folderRef?.catalogOrderKey ?: key
                    val packRailTitle = folderRef?.folderTitle?.takeIf { it.isNotBlank() }
                        ?: packCatalog?.label
                    val catalogRow = displayRowsByKey[catalogLookupKey]?.let { row ->
                        if (packRailTitle != null) row.copy(catalogName = packRailTitle) else row
                    }
                    if (catalogRow != null && catalogRow.items.isNotEmpty()) {
                        add(HomeRow.Catalog(catalogRow))
                    } else if (catalogRow != null && packActive) {
                        add(HomeRow.Catalog(catalogRow))
                    } else {
                        val placeholder = placeholdersByKey[catalogLookupKey]
                        if (placeholder != null) {
                        if (currentLayout == HomeLayout.MODERN) {
                            add(HomeRow.PlaceholderCatalog(
                                catalogKey = placeholder.catalogKey,
                                stableCatalogKey = catalogRowStableKey(
                                    placeholder.addonId,
                                    placeholder.addonBaseUrl,
                                    placeholder.apiType,
                                    placeholder.catalogId
                                ),
                                addonId = placeholder.addonId,
                                addonName = placeholder.addonName,
                                addonBaseUrl = placeholder.addonBaseUrl,
                                catalogId = placeholder.catalogId,
                                catalogName = packRailTitle ?: placeholder.catalogName,
                                apiType = placeholder.apiType,
                                displayTitle = packRailTitle ?: placeholder.displayTitle
                            ))
                        } else {
                            val fakeItems = (0 until 8).map { i ->
                                MetaPreview(
                                    id = "__placeholder_${placeholder.catalogKey}_$i",
                                    type = com.nuvio.tv.domain.model.ContentType.fromString(placeholder.apiType),
                                    rawType = placeholder.apiType,
                                    name = " ",
                                    poster = PLACEHOLDER_IMAGE_URL,
                                    posterShape = com.nuvio.tv.domain.model.PosterShape.POSTER,
                                    background = null,
                                    logo = null,
                                    description = null,
                                    releaseInfo = " ",
                                    imdbRating = null,
                                    genres = emptyList()
                                )
                            }
                            add(HomeRow.Catalog(CatalogRow(
                                addonId = placeholder.addonId,
                                addonName = placeholder.addonName,
                                addonBaseUrl = placeholder.addonBaseUrl,
                                catalogId = placeholder.catalogId,
                                catalogName = packRailTitle ?: placeholder.catalogName,
                                type = com.nuvio.tv.domain.model.ContentType.fromString(placeholder.apiType),
                                rawType = placeholder.apiType,
                                items = fakeItems,
                                isLoading = true,
                                hasMore = false
                            )))
                        }
                    } else if (folderRef != null) {
                        // Kick load; rail appears once catalog arrives.
                        ensureCatalogLoaded(
                            folderRef.addonId,
                            folderRef.type,
                            folderRef.catalogId,
                            extraArgs = folderCatalogExtraArgs(folderRef.genre)
                        )
                    } else if (packActive) {
                        if (packCatalog != null) {
                            ensureCatalogLoaded(
                                packCatalog.addonId,
                                packCatalog.type,
                                packCatalog.catalogId
                            )
                            add(
                                HomeRow.Catalog(
                                    CatalogRow(
                                        addonId = packCatalog.addonId,
                                        addonName = packCatalog.label ?: packCatalog.catalogId,
                                        addonBaseUrl = "",
                                        catalogId = packCatalog.catalogId,
                                        catalogName = packCatalog.label ?: packCatalog.catalogId,
                                        type = com.nuvio.tv.domain.model.ContentType.fromString(
                                            packCatalog.type
                                        ),
                                        rawType = packCatalog.type,
                                        items = emptyList(),
                                        isLoading = true,
                                        hasMore = false
                                    )
                                )
                            )
                        } else {
                            val hub = activeViewPackCollectionHubRefs[key]
                            if (hub != null && addedCollectionIds.add(hub.collectionId)) {
                                add(
                                    HomeRow.CollectionRow(
                                        Collection(
                                            id = hub.collectionId,
                                            title = hub.label ?: "Collection"
                                        )
                                    )
                                )
                            } else {
                                resolveAddonCatalogForHomeKey(key)?.let { (addonId, type, catalogId) ->
                                    ensureCatalogLoaded(addonId, type, catalogId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val nextGridItems = if (currentLayout == HomeLayout.GRID) {
        val posterCardWidthDp = _uiState.value.posterCardWidthDp
        val rowCount = if (posterCardWidthDp <= 104) 2 else 3
        // Provide generous upper bound of items — the Composable layer will trim
        // based on the actual column count from GridCells.Adaptive layout info.
        // We use 8 as safe max columns (widest known config) to avoid cutting too early.
        val safeMaxColumns = 8
        val maxDisplaySlots = safeMaxColumns * rowCount
        buildList {
            if (heroSectionEnabled && baseHeroItems.isNotEmpty()) {
                add(GridItem.Hero(baseHeroItems))
            }
            computedHomeRows.forEach { homeRow ->
                when (homeRow) {
                    is HomeRow.Catalog -> {
                        val row = homeRow.row
                        val isPlaceholderRow = row.isLoading &&
                            row.items.firstOrNull()?.id?.startsWith("__placeholder_") == true
                        if (row.items.isNotEmpty() && !isPlaceholderRow) {
                            add(GridItem.SectionDivider(
                                catalogName = row.catalogName,
                                catalogId = row.catalogId,
                                addonBaseUrl = row.addonBaseUrl,
                                addonId = row.addonId,
                                type = row.apiType
                            ))
                            // Show "See All" if there are more items than fit in the
                            // displayed rows, or the API indicates more pages exist.
                            val showSeeAll = row.hasMore || row.items.size > maxDisplaySlots
                            val rawMax = if (showSeeAll) maxDisplaySlots - 1 else maxDisplaySlots
                            val displayItems = row.items.take(rawMax)
                            displayItems.forEach { item ->
                                add(GridItem.Content(
                                    item = item,
                                    addonBaseUrl = row.addonBaseUrl,
                                    catalogId = row.catalogId,
                                    catalogName = row.catalogName
                                ))
                            }
                            if (showSeeAll) {
                                add(GridItem.SeeAll(
                                    catalogId = row.catalogId,
                                    addonId = row.addonId,
                                    addonBaseUrl = row.addonBaseUrl,
                                    type = row.apiType
                                ))
                            }
                        }
                    }
                    is HomeRow.CollectionRow -> {
                        val col = homeRow.collection
                        add(GridItem.CollectionHeader(
                            collectionId = col.id,
                            title = col.title
                        ))
                        col.folders.forEach { folder ->
                            add(GridItem.CollectionFolder(
                                collectionId = col.id,
                                collectionTitle = col.title,
                                focusGlowEnabled = col.focusGlowEnabled,
                                folder = folder
                            ))
                        }
                    }
                    is HomeRow.PlaceholderCatalog -> {
                        // Grid layout: skip placeholders (grid loads all at once)
                    }
                }
            }
        }.let { replaceGridHeroItemsPipeline(it, baseHeroItems) }
    } else {
        currentGridItems
    }

        computedHomeRows to nextGridItems
    }

    // Clear any stale error when content is now available (e.g., hero
    // catalogs loaded after the initial startup race set an error).
    val hasContent = computedHomeRows.isNotEmpty() || baseHeroItems.isNotEmpty() || displayRows.isNotEmpty()

    val packFeaturedMeta: MetaPreview?
    val packFeaturedAddonBaseUrl: String
    val packFeaturedPreview: HeroPreview?
    if (activeViewPackHeroDataSource != null) {
        val byKey = displayRows.associateBy { it.legacyKey() }
        val resolved = com.nuvio.tv.core.viewpack.resolvePackHeroMeta(
            heroDataSource = activeViewPackHeroDataSource,
            packOrderKeys = activeViewPackOrderKeys,
            catalogRowsByLegacyKey = byKey
        )
        if (resolved != null) {
            val row = byKey.values.firstOrNull { row ->
                row.items.any { it.id == resolved.id && it.apiType == resolved.apiType }
            }
            packFeaturedMeta = resolved
            packFeaturedAddonBaseUrl = row?.addonBaseUrl.orEmpty()
            packFeaturedPreview = heroPreviewFromMeta(
                item = resolved,
                useLandscapePosters = true,
                strTypeMovie = appContext.getString(R.string.type_movie),
                strTypeSeries = appContext.getString(R.string.type_series)
            )
        } else {
            packFeaturedMeta = null
            packFeaturedAddonBaseUrl = ""
            packFeaturedPreview = null
        }
    } else {
        packFeaturedMeta = null
        packFeaturedAddonBaseUrl = ""
        packFeaturedPreview = null
    }

    _uiState.update { state ->
        state.copy(
            catalogRows = if (state.catalogRows == displayRows) state.catalogRows else displayRows,
            heroItems = if (state.heroItems == baseHeroItems) state.heroItems else baseHeroItems,
            gridItems = if (state.gridItems == nextGridItems) state.gridItems else nextGridItems,
            homeRows = if (state.homeRows == computedHomeRows) state.homeRows else computedHomeRows,
            viewPackFeaturedPreview = packFeaturedPreview,
            viewPackFeaturedMeta = packFeaturedMeta,
            viewPackFeaturedAddonBaseUrl = packFeaturedAddonBaseUrl,
            isLoading = false,
            error = if (hasContent) null else state.error
        )
    }

    val tmdbSettings = currentTmdbSettings
    val tmdbEnabledForCurrentLayout = tmdbSettings.enabled &&
        (currentLayout != HomeLayout.MODERN || tmdbSettings.modernHomeEnabled)
    val shouldUseEnrichedHeroItems = tmdbEnabledForCurrentLayout &&
        (tmdbSettings.useArtwork || tmdbSettings.useBasicInfo || tmdbSettings.useDetails || tmdbSettings.useReleaseDates)

    if (shouldUseEnrichedHeroItems && baseHeroItems.isNotEmpty()) {
        heroEnrichmentJob?.cancel()
        heroEnrichmentJob = viewModelScope.launch {
            val enrichmentSignature = heroEnrichmentSignaturePipeline(baseHeroItems, tmdbSettings)
            if (lastHeroEnrichmentSignature == enrichmentSignature) {
                val cached = lastHeroEnrichedItems
                _uiState.update { state ->
                    state.copy(
                        heroItems = if (state.heroItems == cached) state.heroItems else cached,
                        gridItems = if (currentLayout == HomeLayout.GRID) {
                            val enrichedGrid = replaceGridHeroItemsPipeline(state.gridItems, cached)
                            if (state.gridItems == enrichedGrid) state.gridItems else enrichedGrid
                        } else state.gridItems
                    )
                }
            } else {
                val enrichedItems = enrichHeroItemsPipeline(baseHeroItems, tmdbSettings)
                lastHeroEnrichmentSignature = enrichmentSignature
                lastHeroEnrichedItems = enrichedItems
                _uiState.update { state ->
                    state.copy(
                        heroItems = if (state.heroItems == enrichedItems) state.heroItems else enrichedItems,
                        gridItems = if (currentLayout == HomeLayout.GRID) {
                            val enrichedGrid = replaceGridHeroItemsPipeline(state.gridItems, enrichedItems)
                            if (state.gridItems == enrichedGrid) state.gridItems else enrichedGrid
                        } else state.gridItems
                    )
                }
            }
        }
    } else {
        lastHeroEnrichmentSignature = null
        lastHeroEnrichedItems = emptyList()
        heroItemOrder = emptyList()
    }

    schedulePosterStatusReconcilePipeline(displayRows)
}

private fun stableHeroSortKey(
    row: CatalogRow,
    item: MetaPreview
): Int {
    return "${row.addonId}|${row.apiType}|${row.catalogId}|${item.id}".hashCode()
}

internal fun HomeViewModel.schedulePosterStatusReconcilePipeline(rows: List<CatalogRow>) {
    posterStatusReconcileJob?.cancel()
    if (rows.isEmpty()) {
        reconcilePosterStatusObserversPipeline(rows)
        return
    }
    posterStatusReconcileJob = viewModelScope.launch {
        delay(500)
        reconcilePosterStatusObserversPipeline(rows)
    }
}

internal fun HomeViewModel.reconcilePosterStatusObserversPipeline(rows: List<CatalogRow>) {
    val allMovieItemsByKey = linkedMapOf<String, String>()
    rows.asSequence()
        .flatMap { row -> row.items.asSequence() }
        .filter { it.apiType.equals("movie", ignoreCase = true) }
        .forEach { item ->
            val key = homeItemStatusKey(item.id, item.apiType)
            if (key !in allMovieItemsByKey) {
                allMovieItemsByKey[key] = item.id
            }
        }
    val desiredMovieKeys = allMovieItemsByKey.keys

    val allSeriesItemsByKey = linkedMapOf<String, String>()
    rows.asSequence()
        .flatMap { row -> row.items.asSequence() }
        .filter { it.apiType.equals("series", ignoreCase = true) || it.apiType.equals("tv", ignoreCase = true) }
        .forEach { item ->
            val key = homeItemStatusKey(item.id, item.apiType)
            if (key !in allSeriesItemsByKey) {
                allSeriesItemsByKey[key] = item.id
            }
        }


    if (desiredMovieKeys != lastMovieWatchedItemKeys) {
        lastMovieWatchedItemKeys = desiredMovieKeys
        movieWatchedObserverJobs.values.forEach { it.cancel() }
        movieWatchedObserverJobs.clear()
        movieWatchedBatchJob?.cancel()

        if (desiredMovieKeys.isNotEmpty()) {
            movieWatchedBatchJob = viewModelScope.launch {
                watchProgressRepository.observeWatchedMovieIds()
                    .collectLatest { watchedIds ->
                        _uiState.update { state ->
                            val movieStatus = buildMap {
                                allMovieItemsByKey.forEach { (statusKey, contentId) ->
                                    put(statusKey, contentId in watchedIds)
                                }
                            }
                            // Merge with existing status to preserve series entries.
                            val merged = state.movieWatchedStatus
                                .filterKeys { it !in desiredMovieKeys } + movieStatus
                            if (state.movieWatchedStatus == merged) {
                                state
                            } else {
                                state.copy(movieWatchedStatus = merged)
                            }
                        }
                    }
            }
        }
    }

    // Update series watched status from CW pipeline's fully-watched resolution.
    // This piggybacks on the meta lookups CW already performs — no extra network calls.
    if (allSeriesItemsByKey.isNotEmpty()) {
        seriesWatchedObserverJob?.cancel()
        seriesWatchedObserverJob = viewModelScope.launch {
            combine(
                fullyWatchedSeriesIds.fullyWatchedSeriesIds,
                watchProgressRepository.watchedItems
            ) { fullyWatched, watchedItems ->
                fullyWatched to watchedItems
            }.collectLatest { (fullyWatched, watchedItems) ->
                val effectiveFullyWatched = if (
                    watchProgressRepository.activeProviderOwnsCompletedHistoryProjection()
                ) {
                    fullyWatched
                } else {
                    reconcileFullyWatchedFromLocalItems(
                        fullyWatched = fullyWatched,
                        watchedItems = watchedItems,
                        seriesContentIds = allSeriesItemsByKey.values
                    )
                }
                val seriesStatus = buildMap {
                    allSeriesItemsByKey.forEach { (statusKey, contentId) ->
                        put(statusKey, contentId in effectiveFullyWatched)
                    }
                }
                _uiState.update { state ->
                    val merged = state.movieWatchedStatus
                        .filterKeys { it !in allSeriesItemsByKey.keys } + seriesStatus
                    if (state.movieWatchedStatus == merged) state
                    else state.copy(movieWatchedStatus = merged)
                }
            }
        }
    } else {
        seriesWatchedObserverJob?.cancel()
        seriesWatchedObserverJob = null
    }

    _uiState.update { state ->
        val trimmedMovieWatchedPending =
            state.movieWatchedPending.filterTo(linkedSetOf()) { it in desiredMovieKeys }

        if (trimmedMovieWatchedPending == state.movieWatchedPending) {
            state
        } else {
            state.copy(movieWatchedPending = trimmedMovieWatchedPending)
        }
    }
}

private fun HomeViewModel.reconcileFullyWatchedFromLocalItems(
    fullyWatched: Set<String>,
    watchedItems: List<WatchedItem>,
    seriesContentIds: Iterable<String>
): Set<String> {
    val watchedEpisodesByContentId = watchedItems
        .filter { it.season != null && it.episode != null }
        .groupBy { it.contentId }
        .mapValues { (_, items) -> items.map { it.season!! to it.episode!! }.toSet() }
    val cacheResolvedIds = mutableSetOf<String>()
    val cacheResolvedFullyWatched = buildSet {
        seriesContentIds.forEach { contentId ->
            val requiredEpisodes = synchronized(cwBadgeEpisodeCache) {
                cwBadgeEpisodeCache["series:$contentId"] ?: cwBadgeEpisodeCache["tv:$contentId"]
            } ?: return@forEach
            cacheResolvedIds.add(contentId)
            val watchedEpisodes = watchedEpisodesByContentId[contentId].orEmpty()
            if (requiredEpisodes.isNotEmpty() && requiredEpisodes.all { it in watchedEpisodes }) {
                add(contentId)
            }
        }
    }
    if (cacheResolvedIds.isEmpty()) return fullyWatched
    val mergedHolderIds = (fullyWatched - cacheResolvedIds) + cacheResolvedFullyWatched
    if (mergedHolderIds != fullyWatchedSeriesIds.fullyWatchedSeriesIds.value) {
        fullyWatchedSeriesIds.updateWithValidation(mergedHolderIds, cacheResolvedIds)
    }
    return mergedHolderIds
}

private fun com.nuvio.tv.core.viewpack.ViewPack.toNetflixScreenPackState(): NetflixScreenPackState {
    return NetflixScreenPackState(
        orderKeys = com.nuvio.tv.core.viewpack.homeOrderKeysFromPack(this),
        rowScales = com.nuvio.tv.core.viewpack.homeRowScalesFromPack(this),
        rowShowLabels = com.nuvio.tv.core.viewpack.homeRowShowLabelsFromPack(this),
        rowTrailers = com.nuvio.tv.core.viewpack.homeRowTrailersFromPack(this),
        rowPosterGrow = com.nuvio.tv.core.viewpack.homeRowPosterGrowFromPack(this),
        catalogPosterScale = com.nuvio.tv.core.viewpack.normalizePackCardScale(catalogPosterScale),
        collectionLandscapeScale = com.nuvio.tv.core.viewpack.normalizePackCardScale(
            collectionLandscapeScale
        ),
        heroEnabled = com.nuvio.tv.core.viewpack.packHasHero(this),
        heroTrailerEnabled = com.nuvio.tv.core.viewpack.packHeroTrailerEnabled(this),
        heroLabel = com.nuvio.tv.core.viewpack.packHeroLabel(this),
        heroDataSource = com.nuvio.tv.core.viewpack.packHeroDataSource(this),
        featuredHeightPx = com.nuvio.tv.core.viewpack.packHeroHeightPx(this),
        hasContinueWatching = com.nuvio.tv.core.viewpack.packHasContinueWatching(this),
        genreCollectionId = com.nuvio.tv.core.viewpack.packGenreCollectionId(this)
    )
}
