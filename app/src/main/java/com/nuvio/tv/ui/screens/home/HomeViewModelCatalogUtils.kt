package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.sync.HOME_GENRES_ROW_KEY
import com.nuvio.tv.core.sync.isFloatingHomeRowKey
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update

internal fun HomeViewModel.catalogKey(addonId: String, type: String, catalogId: String): String {
    return "${addonId}_${type}_${catalogId}"
}

internal fun HomeViewModel.buildHomeCatalogLoadSignature(addons: List<Addon>): String {
    val addonCatalogSignature = addons
        .flatMap { addon ->
            addon.catalogs.map { catalog ->
                val extraSignature = catalog.extra.joinToString(";") { extra ->
                    listOf(
                        extra.name,
                        extra.isRequired.toString(),
                        extra.options.orEmpty().joinToString("|"),
                        extra.defaultValue.orEmpty(),
                        extra.optionsLimit?.toString().orEmpty()
                    ).joinToString(":")
                }
                listOf(
                    addon.id,
                    addon.baseUrl,
                    addon.version,
                    addon.configVersion?.toString().orEmpty(),
                    addon.manifestLanguage.orEmpty(),
                    addon.rawTypes.joinToString("|"),
                    addon.resources.joinToString("|") { resource ->
                        listOf(
                            resource.name,
                            resource.types.joinToString("/"),
                            resource.idPrefixes.orEmpty().joinToString("/")
                        ).joinToString(":")
                    },
                    addon.idPrefixes.joinToString("|"),
                    catalog.apiType,
                    catalog.id,
                    catalog.name,
                    catalog.showInHome.toString(),
                    catalog.hasExplicitShowInHome.toString(),
                    catalog.pageSize?.toString().orEmpty(),
                    catalog.extraSupported.joinToString("|"),
                    catalog.extraRequired.joinToString("|"),
                    extraSignature
                ).joinToString("|")
            }
        }
        .sorted()
        .joinToString(separator = ",")
    val disabledSignature = disabledHomeCatalogKeys
        .asSequence()
        .sorted()
        .joinToString(separator = ",")
    return "$addonCatalogSignature::$disabledSignature"
}

internal fun HomeViewModel.registerCatalogLoadJob(job: Job) {
    synchronized(activeCatalogLoadJobs) {
        activeCatalogLoadJobs.add(job)
    }
    job.invokeOnCompletion {
        synchronized(activeCatalogLoadJobs) {
            activeCatalogLoadJobs.remove(job)
        }
    }
}

internal fun HomeViewModel.cancelInFlightCatalogLoads() {
    val jobsToCancel = synchronized(activeCatalogLoadJobs) {
        activeCatalogLoadJobs.toList().also { activeCatalogLoadJobs.clear() }
    }
    jobsToCancel.forEach { it.cancel() }
}

private fun HomeViewModel.reindexCatalogRow(
    key: String,
    previousRow: CatalogRow?,
    updatedRow: CatalogRow?
) {
    previousRow?.items?.forEach { item ->
        val keys = catalogItemKeyIndex[item.id] ?: return@forEach
        keys.remove(key)
        if (keys.isEmpty()) {
            catalogItemKeyIndex.remove(item.id)
        }
    }

    updatedRow?.items?.forEach { item ->
        catalogItemKeyIndex.getOrPut(item.id) { LinkedHashSet() }.add(key)
    }
}

internal fun HomeViewModel.hasAnyCatalogRows(): Boolean = synchronized(catalogStateLock) {
    catalogsMap.isNotEmpty()
}

internal fun HomeViewModel.isCatalogOrderEmpty(): Boolean = synchronized(catalogStateLock) {
    catalogOrder.isEmpty()
}

internal fun HomeViewModel.hasCatalogOrderEntries(): Boolean = synchronized(catalogStateLock) {
    catalogOrder.isNotEmpty()
}

internal fun HomeViewModel.readCatalogRow(key: String): CatalogRow? = synchronized(catalogStateLock) {
    catalogsMap[key]
}

internal fun HomeViewModel.replaceCatalogRow(key: String, row: CatalogRow) {
    synchronized(catalogStateLock) {
        val previousRow = catalogsMap.put(key, row)
        reindexCatalogRow(key, previousRow, row)
    }
}

internal inline fun HomeViewModel.updateCatalogRow(
    key: String,
    transform: (CatalogRow) -> CatalogRow
): CatalogRow? {
    return synchronized(catalogStateLock) {
        val currentRow = catalogsMap[key] ?: return@synchronized null
        val updatedRow = transform(currentRow)
        if (updatedRow != currentRow) {
            catalogsMap[key] = updatedRow
            reindexCatalogRow(key, currentRow, updatedRow)
        }
        updatedRow
    }
}

internal fun HomeViewModel.clearCatalogData() {
    synchronized(catalogStateLock) {
        catalogsMap.clear()
        catalogItemKeyIndex.clear()
        truncatedRowCache.clear()
        pendingLazyCatalogs.clear()
        placeholderDescriptors.clear()
    }
    lazyLoadRequestedKeys.clear()
    emptyCatalogRetryKeys.clear()
}

internal fun HomeViewModel.snapshotCatalogKeys(): Set<String> = synchronized(catalogStateLock) {
    catalogsMap.keys.toSet()
}

internal fun HomeViewModel.snapshotCatalogState(): Pair<List<String>, Map<String, CatalogRow>> = synchronized(catalogStateLock) {
    catalogOrder.toList() to catalogsMap.toMap()
}

internal fun HomeViewModel.findCatalogItemById(itemId: String): MetaPreview? = synchronized(catalogStateLock) {
    val rowKeys = catalogItemKeyIndex[itemId]?.toList().orEmpty()
    rowKeys.firstNotNullOfOrNull { key ->
        catalogsMap[key]?.items?.firstOrNull { it.id == itemId }
    }
}

internal inline fun HomeViewModel.updateIndexedCatalogItem(
    itemId: String,
    transform: (MetaPreview) -> MetaPreview
): Boolean {
    return synchronized(catalogStateLock) {
        val rowKeys = catalogItemKeyIndex[itemId]?.toList().orEmpty()
        var changed = false

        rowKeys.forEach { key ->
            val row = catalogsMap[key] ?: return@forEach
            val itemIndex = row.items.indexOfFirst { it.id == itemId }
            if (itemIndex < 0) return@forEach

            val updatedItem = transform(row.items[itemIndex])
            if (updatedItem == row.items[itemIndex]) return@forEach

            val mutableItems = row.items.toMutableList()
            mutableItems[itemIndex] = updatedItem
            catalogsMap[key] = row.copy(items = mutableItems)
            truncatedRowCache.remove(key)
            changed = true
        }

        changed
    }
}

internal fun HomeViewModel.getTruncatedRowCacheEntry(key: String): HomeViewModel.TruncatedRowCacheEntry? = synchronized(catalogStateLock) {
    truncatedRowCache[key]
}

internal fun HomeViewModel.putTruncatedRowCacheEntry(key: String, entry: HomeViewModel.TruncatedRowCacheEntry) {
    synchronized(catalogStateLock) {
        truncatedRowCache[key] = entry
    }
}

internal fun HomeViewModel.removeTruncatedRowCacheEntry(key: String) {
    synchronized(catalogStateLock) {
        truncatedRowCache.remove(key)
    }
}

internal fun HomeViewModel.rebuildCatalogOrder(addons: List<Addon>) {
    val defaultOrder = buildDefaultCatalogOrder(addons)
    val collectionKeys = collectionsCache.map { "collection_${it.id}" }
    val floatingKeys = listOf(HOME_GENRES_ROW_KEY) + collectionKeys
    val allAvailable = (defaultOrder + floatingKeys).toSet()

    if (followAddonsOrderEnabled) {
        // In follow addons order mode, addon catalogs always stay in manifest order.
        // Collections are positioned based on their relative position in saved order.
        val savedValid = homeCatalogOrderKeys
            .asSequence()
            .filter { it in allAvailable }
            .distinct()
            .toList()

        val floatingKeysSet = floatingKeys.toSet()

        if (savedValid.isNotEmpty()) {
            val result = mutableListOf<String>()
            if (HOME_GENRES_ROW_KEY !in savedValid) {
                result.add(HOME_GENRES_ROW_KEY)
            }
            var addonPointer = 0

            for (savedKey in savedValid) {
                if (savedKey in floatingKeysSet) {
                    result.add(savedKey)
                } else {
                    // Addon catalog - advance manifest pointer to include all up to this one
                    val targetIdx = defaultOrder.indexOf(savedKey)
                    if (targetIdx >= 0) {
                        while (addonPointer <= targetIdx) {
                            val ak = defaultOrder[addonPointer]
                            if (ak !in result) {
                                result.add(ak)
                            }
                            addonPointer++
                        }
                    }
                }
            }
            // Append remaining addon keys
            while (addonPointer < defaultOrder.size) {
                val ak = defaultOrder[addonPointer]
                if (ak !in result) {
                    result.add(ak)
                }
                addonPointer++
            }
            // Append any synthetic or collection rows not in saved order.
            for (floatingKey in floatingKeys) {
                if (floatingKey !in result) {
                    result.add(floatingKey)
                }
            }

            // Normalize: push collections that ended up mid-addon-block to the block boundary
            val addonKeyToOwner = buildAddonKeyOwnerMap(addons)
            val normalized = normalizeCollectionBoundaries(result, addonKeyToOwner)

            synchronized(catalogStateLock) {
                catalogOrder.clear()
                catalogOrder.addAll(normalized)
            }
        } else {
            // No saved order - manifest order + collections at end
            synchronized(catalogStateLock) {
                catalogOrder.clear()
                catalogOrder.addAll(listOf(HOME_GENRES_ROW_KEY) + defaultOrder + collectionKeys)
            }
        }
    } else {
        val savedValid = homeCatalogOrderKeys
            .asSequence()
            .filter { it in allAvailable }
            .distinct()
            .toList()

        val savedSet = savedValid.toSet()
        val unsavedCatalogs = defaultOrder.filterNot { it in savedSet }
        val unsavedCollections = collectionKeys.filterNot { it in savedSet }
        val mergedOrder = if (HOME_GENRES_ROW_KEY in savedSet) {
            savedValid + unsavedCatalogs + unsavedCollections
        } else {
            listOf(HOME_GENRES_ROW_KEY) + savedValid + unsavedCatalogs + unsavedCollections
        }

        synchronized(catalogStateLock) {
            catalogOrder.clear()
            catalogOrder.addAll(mergedOrder)
        }
    }

    applyActiveViewPackOrderIfNeeded(allAvailable)
}

internal fun HomeViewModel.packCatalogLoadPairs(): List<Pair<Addon, CatalogDescriptor>> {
    if (activeViewPackCatalogRefs.isEmpty() || addonsCache.isEmpty()) return emptyList()
    val out = LinkedHashMap<String, Pair<Addon, CatalogDescriptor>>()
    for (original in activeViewPackCatalogRefs.values) {
        val resolved = resolvedPackCatalogForOrderKey(original.orderKey) ?: original
        val addon = addonsCache.firstOrNull { it.id == resolved.addonId }
            ?: addonsCache.firstOrNull { candidate ->
                candidate.catalogs.any {
                    it.id == resolved.catalogId && it.apiType.equals(resolved.type, ignoreCase = true)
                }
            }
            ?: continue
        val catalog = addon.catalogs.firstOrNull {
            it.id == resolved.catalogId && it.apiType.equals(resolved.type, ignoreCase = true)
        } ?: addon.catalogs.firstOrNull { it.id == resolved.catalogId }
            ?: CatalogDescriptor(
                type = com.nuvio.tv.domain.model.ContentType.fromString(resolved.type),
                rawType = resolved.type,
                id = resolved.catalogId,
                name = resolved.label ?: resolved.catalogId,
                showInHome = false
            )
        val key = catalogKey(addon.id, catalog.apiType, catalog.id)
        if (key !in out) out[key] = addon to catalog
    }
    return out.values.toList()
}

internal fun HomeViewModel.ensureActivePackCatalogsLoaded() {
    if (activeViewPackOrderKeys == null || addonsCache.isEmpty()) return
    packCatalogLoadPairs().forEach { (addon, catalog) ->
        ensureCatalogLoaded(addon.id, catalog.apiType, catalog.id)
    }
}

internal fun HomeViewModel.resolvedPackCatalogForOrderKey(key: String): com.nuvio.tv.core.viewpack.PackCatalogRef? {
    val installed = addonsCache.flatMap { addon ->
        addon.catalogs.map { catalog -> Triple(addon.id, catalog.apiType, catalog.id) }
    }
    activeViewPackCatalogRefs[key]?.let {
        return com.nuvio.tv.core.viewpack.remapPackCatalogRef(it, installed)
    }
    return activeViewPackCatalogRefs.values
        .map { com.nuvio.tv.core.viewpack.remapPackCatalogRef(it, installed) }
        .firstOrNull { it.orderKey == key }
}

/**
 * Resolve `addonId_type_catalogId` against installed addons, or by splitting on a
 * known content-type segment when the catalog is missing from the manifest.
 */
internal fun HomeViewModel.resolveAddonCatalogForHomeKey(
    key: String
): Triple<String, String, String>? {
    for (addon in addonsCache) {
        for (catalog in addon.catalogs) {
            val candidate = catalogKey(
                addonId = addon.id,
                type = catalog.apiType,
                catalogId = catalog.id
            )
            if (candidate == key) {
                return Triple(addon.id, catalog.apiType, catalog.id)
            }
        }
    }
    for (type in listOf("movie", "series", "channel", "tv", "anime", "other")) {
        val marker = "_${type}_"
        val idx = key.indexOf(marker)
        if (idx <= 0) continue
        val addonId = key.substring(0, idx)
        val catalogId = key.substring(idx + marker.length)
        if (addonId.isNotEmpty() && catalogId.isNotEmpty()) {
            return Triple(addonId, type, catalogId)
        }
    }
    return null
}

private fun HomeViewModel.applyActiveViewPackOrderIfNeeded(allAvailable: Set<String>) {
    val packKeys = activeViewPackOrderKeys ?: return
    val layout = _uiState.value.homeLayout
    if (layout == HomeLayout.GRID || layout == HomeLayout.CLASSIC) {
        return
    }
    val installed = addonsCache.flatMap { addon ->
        addon.catalogs.map { catalog -> Triple(addon.id, catalog.apiType, catalog.id) }
    }
    val remappedKeys = packKeys.map { key ->
        val ref = activeViewPackCatalogRefs[key] ?: return@map key
        com.nuvio.tv.core.viewpack.remapPackCatalogRef(ref, installed).orderKey
    }
    fun remapPackKeys(keys: List<String>?): List<String> =
        keys.orEmpty().map { key ->
            val ref = activeViewPackCatalogRefs[key] ?: return@map key
            com.nuvio.tv.core.viewpack.remapPackCatalogRef(ref, installed).orderKey
        }
    val remappedMovies = remapPackKeys(moviesViewPackOrderKeys)
    val remappedShows = remapPackKeys(showsViewPackOrderKeys)
    activeViewPackRowScales = com.nuvio.tv.core.viewpack.remapPackKeyedMap(
        activeViewPackRowScales,
        activeViewPackCatalogRefs,
        installed
    )
    activeViewPackRowShowLabels = com.nuvio.tv.core.viewpack.remapPackKeyedMap(
        activeViewPackRowShowLabels,
        activeViewPackCatalogRefs,
        installed
    )
    activeViewPackRowTrailers = com.nuvio.tv.core.viewpack.remapPackKeyedMap(
        activeViewPackRowTrailers,
        activeViewPackCatalogRefs,
        installed
    )
    activeViewPackRowPosterGrow = com.nuvio.tv.core.viewpack.remapPackKeyedMap(
        activeViewPackRowPosterGrow,
        activeViewPackCatalogRefs,
        installed
    )
    _uiState.update { state ->
        state.copy(
            viewPackRowScales = activeViewPackRowScales,
            viewPackRowShowLabels = activeViewPackRowShowLabels,
            viewPackRowTrailers = activeViewPackRowTrailers,
            viewPackRowPosterGrow = activeViewPackRowPosterGrow
        )
    }
    val available = buildSet {
        addAll(allAvailable)
        addAll(remappedKeys)
        addAll(remappedMovies)
        addAll(remappedShows)
        if (com.nuvio.tv.core.viewpack.PACK_GENRES_ROW_KEY in packKeys ||
            com.nuvio.tv.core.viewpack.PACK_GENRES_ROW_KEY in remappedMovies ||
            com.nuvio.tv.core.viewpack.PACK_GENRES_ROW_KEY in remappedShows
        ) {
            add(com.nuvio.tv.core.viewpack.PACK_GENRES_ROW_KEY)
        }
    }
    val ordered = com.nuvio.tv.core.viewpack.applyStrictPackOrder(
        packKeys = remappedKeys,
        availableKeys = available
    )
    val extras = com.nuvio.tv.core.viewpack.applyStrictPackOrder(
        packKeys = remappedMovies + remappedShows,
        availableKeys = available
    ).filter { it !in ordered }
    synchronized(catalogStateLock) {
        catalogOrder.clear()
        catalogOrder.addAll(ordered)
        catalogOrder.addAll(extras)
    }
}

private fun HomeViewModel.buildDefaultCatalogOrder(addons: List<Addon>): List<String> {
    val orderedKeys = mutableListOf<String>()
    addons.forEach { addon ->
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
            .forEach { catalog ->
                val key = catalogKey(
                    addonId = addon.id,
                    type = catalog.apiType,
                    catalogId = catalog.id
                )
                if (key !in orderedKeys) {
                    orderedKeys.add(key)
                }
            }
    }
    return orderedKeys
}

internal fun HomeViewModel.isCatalogDisabled(
    addonBaseUrl: String,
    addonId: String,
    type: String,
    catalogId: String,
    catalogName: String
): Boolean {
    if (disableCatalogKey(addonBaseUrl, type, catalogId, catalogName) in disabledHomeCatalogKeys) {
        return true
    }
    // Backward compatibility with previously stored keys.
    return catalogKey(addonId, type, catalogId) in disabledHomeCatalogKeys
}

internal fun HomeViewModel.disableCatalogKey(
    addonBaseUrl: String,
    type: String,
    catalogId: String,
    catalogName: String
): String {
    return "${addonBaseUrl}_${type}_${catalogId}_${catalogName}"
}

internal fun folderCatalogExtraArgs(genre: String?): Map<String, String> {
    val trimmed = genre?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.equals("None", ignoreCase = true)) return emptyMap()
    return mapOf("genre" to trimmed)
}

internal fun HomeViewModel.genreExtraForCatalogId(catalogId: String): Map<String, String> {
    val wanted = catalogId.trim()
    if (wanted.isEmpty()) return emptyMap()
    for (collection in collectionsCache) {
        for (folder in collection.folders) {
            val sources = folder.sources.filterIsInstance<AddonCatalogCollectionSource>().ifEmpty {
                folder.catalogSources.map { legacy ->
                    AddonCatalogCollectionSource(
                        addonId = legacy.addonId,
                        type = legacy.type,
                        catalogId = legacy.catalogId,
                        genre = legacy.genre
                    )
                }
            }
            val match = sources.firstOrNull { it.catalogId == wanted }
            if (match != null) return folderCatalogExtraArgs(match.genre)
        }
    }
    return emptyMap()
}

internal fun CatalogDescriptor.isSearchOnlyCatalog(): Boolean {
    return extra.any { extra -> extra.name.equals("search", ignoreCase = true) && extra.isRequired }
}

internal fun CatalogDescriptor.shouldShowOnHome(): Boolean {
    if (isSearchOnlyCatalog()) return false
    return !hasExplicitShowInHome || showInHome
}

internal fun MetaPreview.hasHeroArtwork(): Boolean {
    return !background.isNullOrBlank()
}

internal fun HomeViewModel.extractYear(releaseInfo: String?): String? {
    if (releaseInfo.isNullOrBlank()) return null
    return Regex("\\b(19|20)\\d{2}\\b").find(releaseInfo)?.value
}

private fun buildAddonKeyOwnerMap(addons: List<Addon>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    addons.forEach { addon ->
        addon.catalogs.forEach { catalog ->
            val key = "${addon.id}_${catalog.apiType}_${catalog.id}"
            map[key] = addon.id
        }
    }
    return map
}

private fun normalizeCollectionBoundaries(
    order: List<String>,
    addonKeyToOwner: Map<String, String>
): List<String> {
    val result = order.toMutableList()
    var changed = true
    while (changed) {
        changed = false
        var i = 0
        while (i < result.size) {
            val key = result[i]
            if (!isFloatingHomeRowKey(key)) {
                i++
                continue
            }
            val prevOwner = findOwnerBefore(result, i, addonKeyToOwner)
            val nextOwner = findOwnerAfter(result, i, addonKeyToOwner)
            if (prevOwner != null && nextOwner != null && prevOwner == nextOwner) {
                // Collection is mid-block, push to end of this addon block
                result.removeAt(i)
                var insertPos = i
                while (insertPos < result.size &&
                    !isFloatingHomeRowKey(result[insertPos]) &&
                    addonKeyToOwner[result[insertPos]] == prevOwner
                ) {
                    insertPos++
                }
                result.add(insertPos, key)
                if (insertPos != i) changed = true
                i++
            } else {
                i++
            }
        }
    }
    return result
}

private fun findOwnerBefore(order: List<String>, index: Int, owners: Map<String, String>): String? {
    for (j in index - 1 downTo 0) {
        if (!isFloatingHomeRowKey(order[j])) return owners[order[j]]
    }
    return null
}

private fun findOwnerAfter(order: List<String>, index: Int, owners: Map<String, String>): String? {
    for (j in index + 1 until order.size) {
        if (!isFloatingHomeRowKey(order[j])) return owners[order[j]]
    }
    return null
}
