package com.nuvio.tv.core.viewpack

/** Injected home-order key for Studio's genres / genreRail block. */
const val PACK_GENRES_ROW_KEY = "_special_genres"

const val MIN_PACK_CARD_SCALE = 0.7f
const val MAX_PACK_CARD_SCALE = 2f

fun normalizePackCardScale(value: Float?): Float {
    if (value == null || value.isNaN() || value <= 0f) return 1f
    return value.coerceIn(MIN_PACK_CARD_SCALE, MAX_PACK_CARD_SCALE)
}

/**
 * Map a Studio dataSource id to a Nuvio home order key.
 * Returns null for chrome that stock home owns itself (hero, CW, topNav).
 */
fun homeOrderKeyForDataSource(dataSource: String): String? {
    val ds = dataSource.trim()
    when {
        ds.isEmpty() || ds == "none" || ds == "featured" -> return null
        ds == "continueWatching" -> return null
        ds == "genres" -> return PACK_GENRES_ROW_KEY
        ds.startsWith("catalog:") -> {
            // catalog:addonId:type:catalogId  (addonId may contain dots, not colons)
            val parts = ds.removePrefix("catalog:").split(":", limit = 3)
            if (parts.size < 3) return null
            val addonId = parts[0].trim()
            val type = parts[1].trim()
            val catalogId = parts[2].trim()
            if (addonId.isEmpty() || type.isEmpty() || catalogId.isEmpty()) return null
            return "${addonId}_${type}_${catalogId}"
        }
        ds.startsWith("collection:") -> {
            // collection:id  OR  collection:id:folder:folderId (unique folder content rail)
            val rest = ds.removePrefix("collection:")
            val folderIdx = rest.indexOf(":folder:")
            if (folderIdx >= 0) {
                val collectionId = rest.substring(0, folderIdx).trim()
                val folderId = rest.substring(folderIdx + ":folder:".length).trim()
                if (collectionId.isEmpty() || folderId.isEmpty()) return null
                return packFolderOrderKey(collectionId, folderId)
            }
            val collectionId = rest.trim()
            if (collectionId.isEmpty()) return null
            return "collection_${collectionId}"
        }
        else -> return null
    }
}

/**
 * Extract strict home order keys from pack blocks (Y order after shuffle).
 * Dedupes: first occurrence wins. Keeps [PACK_GENRES_ROW_KEY]; skips hero/nav chrome.
 */
fun homeOrderKeysFromPack(pack: ViewPack): List<String> {
    val ordered = pack.blocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))
    val keys = LinkedHashSet<String>()
    for (block in ordered) {
        if (block.type == "topNav" || block.type == "hero" || block.type == "spacer") continue
        val key = homeOrderKeyForDataSource(block.dataSource) ?: continue
        keys.add(key)
    }
    return keys.toList()
}

/** True when the pack includes a Studio hero block. */
fun packHasHero(pack: ViewPack): Boolean =
    pack.blocks.any { it.type == "hero" || it.dataSource.trim() == "featured" }

/** Eyebrow / label for the pack's first hero block. */
fun packHeroLabel(pack: ViewPack): String =
    pack.blocks.firstOrNull { it.type == "hero" || it.dataSource.trim() == "featured" }
        ?.label
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "Featured"

/** dataSource id on the pack's hero block (`featured`, `catalog:…`, etc.). */
fun packHeroDataSource(pack: ViewPack): String? =
    pack.blocks
        .firstOrNull { it.type == "hero" || it.dataSource.trim() == "featured" }
        ?.dataSource
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "none" }

/** Studio canvas height (px) of the pack hero — used to size the TV Featured row. */
fun packHeroHeightPx(pack: ViewPack): Int? =
    pack.blocks
        .firstOrNull { it.type == "hero" || it.dataSource.trim() == "featured" }
        ?.h
        ?.takeIf { it > 0 }

/** True when the pack includes a Studio genres / genreRail block. */
fun packHasGenresRail(pack: ViewPack): Boolean =
    pack.blocks.any {
        it.type == "genreRail" || it.dataSource.trim() == "genres"
    }

/**
 * Studio canvas px reserved for the rail title row when focused poster info is on.
 * Must stay aligned with Reframe Studio's `MAX_LABELED_RAIL_HEIGHT` breakdown.
 */
const val PACK_LABELED_TITLE_RESERVE_PX = 32

/**
 * Studio canvas px reserved under posters for the focused description footer.
 * Must leave room for facts + at least 2 synopsis lines (aligned with Studio
 * `FOCUSED_METADATA_HEIGHT`). TV strips this before scaling poster art.
 */
const val PACK_LABELED_METADATA_RESERVE_PX = 120

/**
 * Poster-art height implied by a Studio rail block.
 * When [ViewPack.showFocusedPosterInfo] is on, [ViewBlock.h] is the full rail slot
 * (title + posters + description) — only the poster portion should drive TV scale.
 */
fun packRailPosterHeightPx(block: ViewBlock, pack: ViewPack): Int {
    val h = block.h.coerceAtLeast(1)
    if (!pack.showFocusedPosterInfo) return h
    val reserve = PACK_LABELED_TITLE_RESERVE_PX + PACK_LABELED_METADATA_RESERVE_PX
    return (h - reserve).coerceAtLeast(1)
}

/**
 * Per-rail size scales from block heights, keyed by home order key.
 * Baseline is Continue Watching's block height so CW stays constant on TV;
 * other rails scale as posterH / cwH. Falls back to median if CW is absent.
 * Near-baseline scales are dropped so untouched rails keep native size.
 *
 * Important: with focused poster info on, Studio's labeled rail height includes
 * the description footer. Scaling from the full `h` (e.g. 420/210 = 2×) made
 * posters fill the TV and clipped the description — so we scale from poster art
 * height only in that mode.
 */
fun homeRowScalesFromPack(pack: ViewPack): Map<String, Float> {
    val railBlocks = pack.blocks.filter {
        val ds = it.dataSource.trim()
        ds.startsWith("catalog:") || ds.startsWith("collection:")
    }
    if (railBlocks.isEmpty()) return emptyMap()
    val cwHeight = pack.blocks
        .firstOrNull { it.dataSource.trim() == "continueWatching" }
        ?.h
        ?.takeIf { it > 0 }
    val baseline = if (cwHeight != null) {
        cwHeight.toFloat()
    } else {
        if (railBlocks.size < 2) return emptyMap()
        val heights = railBlocks.map { packRailPosterHeightPx(it, pack) }.sorted()
        heights[heights.size / 2].toFloat()
    }
    if (baseline <= 0f) return emptyMap()
    val scales = LinkedHashMap<String, Float>()
    for (block in railBlocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))) {
        val key = homeOrderKeyForDataSource(block.dataSource) ?: continue
        if (key in scales) continue
        val posterH = packRailPosterHeightPx(block, pack).toFloat()
        scales[key] = (posterH / baseline).coerceIn(0.55f, 2.5f)
    }
    return scales.filterValues { kotlin.math.abs(it - 1f) > 0.04f }
}

/**
 * Poster focused-info flags from the pack.
 * Prefers pack-level [ViewPack.showFocusedPosterInfo] for all non-CW catalog/collection rails.
 * When the pack flag is off, every pack rail is explicitly `false` so the TV does not fall
 * back to the global poster-labels preference (that fallback was adding a 236dp footer and
 * clipping rails). Legacy per-block `showPosterLabels` can still opt a rail back on.
 */
fun homeRowShowLabelsFromPack(pack: ViewPack): Map<String, Boolean> {
    val out = LinkedHashMap<String, Boolean>()
    val ordered = pack.blocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))
    if (pack.showFocusedPosterInfo) {
        for (block in ordered) {
            val ds = block.dataSource.trim()
            if (ds == "continueWatching") continue
            if (block.type == "genreRail" || ds == "genres") continue
            if (block.type != "mediaRail" && block.type != "collectionRail") continue
            val key = homeOrderKeyForDataSource(ds) ?: continue
            if (key !in out) out[key] = true
        }
        return out
    }
    // Pack is active and focused-info is off — pin every rail to false first.
    for (block in ordered) {
        val ds = block.dataSource.trim()
        if (ds == "continueWatching") continue
        if (block.type == "genreRail" || ds == "genres") continue
        if (block.type != "mediaRail" && block.type != "collectionRail") continue
        val key = homeOrderKeyForDataSource(ds) ?: continue
        if (key !in out) out[key] = false
    }
    for (block in ordered) {
        val flag = block.showPosterLabels ?: continue
        val key = when (block.dataSource.trim()) {
            "continueWatching" -> "continue_watching"
            else -> homeOrderKeyForDataSource(block.dataSource)
        } ?: continue
        out[key] = flag
    }
    return out
}

/**
 * Per-rail trailer opt-in from the pack (Studio block `trailer`).
 * Keys match [homeOrderKeysFromPack]. Hero is handled separately via [packHeroTrailerEnabled].
 * Continue Watching is omitted — Netflix CW cards do not host in-card trailers today.
 */
fun homeRowTrailersFromPack(pack: ViewPack): Map<String, Boolean> {
    val out = LinkedHashMap<String, Boolean>()
    for (block in pack.blocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))) {
        if (block.type == "hero" || block.type == "topNav" || block.type == "spacer") continue
        val ds = block.dataSource.trim()
        if (ds == "continueWatching" || ds == "genres") continue
        if (block.type == "genreRail") continue
        val key = homeOrderKeyForDataSource(ds) ?: continue
        if (key !in out) out[key] = block.trailer
    }
    return out
}

/**
 * Per-rail focus grow from the pack (Studio `posterGrow`).
 * `null` / omitted means grow on (Netflix default). Explicit `false` keeps focused width = portrait.
 */
fun homeRowPosterGrowFromPack(pack: ViewPack): Map<String, Boolean> {
    val out = LinkedHashMap<String, Boolean>()
    for (block in pack.blocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))) {
        if (block.type != "mediaRail" && block.type != "collectionRail") continue
        val ds = block.dataSource.trim()
        if (ds == "continueWatching" || ds == "genres") continue
        val key = homeOrderKeyForDataSource(ds) ?: continue
        if (key !in out) out[key] = block.posterGrow != false
    }
    return out
}

/** Whether the pack hero block allows trailer autoplay when focused. */
fun packHeroTrailerEnabled(pack: ViewPack): Boolean {
    val hero = pack.blocks.firstOrNull { it.type == "hero" || it.dataSource.trim() == "featured" }
    return hero?.trailer == true
}

/** Home-order key for a Studio expanded folder content rail. */
fun packFolderOrderKey(collectionId: String, folderId: String): String =
    "folder_${collectionId}_${folderId}"

/**
 * Resolved catalog backing a pack `collection:…:folder:…` rail.
 * Used so expanded folder rails load titles instead of collapsing to the parent collection.
 */
data class PackFolderCatalogRef(
    val orderKey: String,
    val collectionId: String,
    val folderId: String,
    val folderTitle: String,
    val addonId: String,
    val type: String,
    val catalogId: String,
    val genre: String? = null
) {
    val catalogOrderKey: String get() = "${addonId}_${type}_${catalogId}"
}

/**
 * Map pack folder dataSources → primary addon catalog for each unique folder rail.
 */
fun packFolderCatalogRefs(
    pack: ViewPack,
    collectionsById: Map<String, com.nuvio.tv.domain.model.Collection>
): Map<String, PackFolderCatalogRef> {
    val out = LinkedHashMap<String, PackFolderCatalogRef>()
    val ordered = pack.blocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))
    for (block in ordered) {
        val parsed = parsePackFolderDataSource(block.dataSource) ?: continue
        val (collectionId, folderId) = parsed
        val orderKey = packFolderOrderKey(collectionId, folderId)
        if (orderKey in out) continue
        val folder = collectionsById[collectionId]?.folders?.firstOrNull { it.id == folderId }
            ?: continue
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
        out[orderKey] = PackFolderCatalogRef(
            orderKey = orderKey,
            collectionId = collectionId,
            folderId = folderId,
            folderTitle = folder.title.ifBlank { block.label.orEmpty() }.ifBlank { folderId },
            addonId = source.addonId,
            type = source.type,
            catalogId = source.catalogId,
            genre = source.genre
        )
    }
    return out
}

/** Parse `collection:cid:folder:fid` → pair, or null. */
fun parsePackFolderDataSource(dataSource: String): Pair<String, String>? {
    val ds = dataSource.trim()
    if (!ds.startsWith("collection:")) return null
    val rest = ds.removePrefix("collection:")
    val folderIdx = rest.indexOf(":folder:")
    if (folderIdx < 0) return null
    val collectionId = rest.substring(0, folderIdx).trim()
    val folderId = rest.substring(folderIdx + ":folder:".length).trim()
    if (collectionId.isEmpty() || folderId.isEmpty()) return null
    return collectionId to folderId
}

/** True when [orderKey] is a pack expanded-folder rail (`folder_collectionId_folderId`). */
fun isPackFolderOrderKey(orderKey: String): Boolean = orderKey.startsWith("folder_")

/** Collection id behind a `collection:id` / `collection:id:folder:fid` dataSource. */
fun packCollectionIdForDataSource(dataSource: String): String? {
    val ds = dataSource.trim()
    if (!ds.startsWith("collection:")) return null
    val rest = ds.removePrefix("collection:")
    val folderIdx = rest.indexOf(":folder:")
    val collectionId = if (folderIdx >= 0) rest.substring(0, folderIdx).trim() else rest.trim()
    return collectionId.takeIf { it.isNotEmpty() }
}

/**
 * Per-collection open style from the pack, keyed by collection id.
 * Studio sets this on collection rails so folders opened from that rail can
 * render in the Reframe (home-style) layout instead of the collection's own
 * view mode. First block wins when a collection appears in several rails.
 */
fun collectionOpenStylesFromPack(pack: ViewPack): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (block in pack.blocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))) {
        val style = block.collectionOpenStyle?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: continue
        val collectionId = packCollectionIdForDataSource(block.dataSource) ?: continue
        if (collectionId !in out) out[collectionId] = style
    }
    return out
}

/**
 * Resolve how [collectionId] should open under [pack].
 * Per-rail style wins; otherwise pack [ViewPack.collectionsOpenInReframe] → reframe.
 */
fun resolveCollectionOpenStyle(pack: ViewPack, collectionId: String): String? {
    val id = collectionId.trim()
    if (id.isEmpty()) return null
    collectionOpenStylesFromPack(pack)[id]?.let { return it }
    if (pack.collectionsOpenInReframe) return OPEN_STYLE_REFRAME
    return null
}

/**
 * Catalog rails authored in a Studio pack (`catalog:addon:type:id`).
 * Used to fetch expanded content rails that may not be in the default home set.
 */
data class PackCatalogRef(
    val orderKey: String,
    val addonId: String,
    val type: String,
    val catalogId: String,
    val label: String? = null
)

/** Pack `collection:id` hub (not an expanded folder rail). */
data class PackCollectionHubRef(
    val orderKey: String,
    val collectionId: String,
    val label: String? = null
)

/**
 * Map pack `catalog:…` dataSources → load refs (first occurrence wins).
 */
fun remapPackCatalogRef(
    ref: PackCatalogRef,
    installed: List<Triple<String, String, String>>
): PackCatalogRef {
    val exact = installed.any { (addonId, type, catalogId) ->
        addonId == ref.addonId &&
            type.equals(ref.type, ignoreCase = true) &&
            catalogId == ref.catalogId
    }
    if (exact) return ref
    val hit = installed.firstOrNull { (_, type, catalogId) ->
        type.equals(ref.type, ignoreCase = true) && catalogId == ref.catalogId
    } ?: installed.firstOrNull { (_, _, catalogId) -> catalogId == ref.catalogId }
        ?: return ref
    return ref.copy(
        addonId = hit.first,
        type = hit.second,
        orderKey = "${hit.first}_${hit.second}_${hit.third}"
    )
}

fun packCatalogRefs(pack: ViewPack): Map<String, PackCatalogRef> {
    val out = LinkedHashMap<String, PackCatalogRef>()
    for (block in pack.blocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))) {
        val ds = block.dataSource.trim()
        if (!ds.startsWith("catalog:")) continue
        val parts = ds.removePrefix("catalog:").split(":", limit = 3)
        if (parts.size < 3) continue
        val addonId = parts[0].trim()
        val type = parts[1].trim()
        val catalogId = parts[2].trim()
        if (addonId.isEmpty() || type.isEmpty() || catalogId.isEmpty()) continue
        val orderKey = "${addonId}_${type}_${catalogId}"
        if (orderKey in out) continue
        out[orderKey] = PackCatalogRef(
            orderKey = orderKey,
            addonId = addonId,
            type = type,
            catalogId = catalogId,
            label = block.label?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
    return out
}

/**
 * Map pack `collection:id` hubs (not `:folder:`) so a missing local collection
 * can still occupy its authored slot on Netflix home.
 */
fun packCollectionHubRefs(pack: ViewPack): Map<String, PackCollectionHubRef> {
    val out = LinkedHashMap<String, PackCollectionHubRef>()
    for (block in pack.blocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))) {
        val ds = block.dataSource.trim()
        if (!ds.startsWith("collection:") || ds.contains(":folder:")) continue
        val collectionId = ds.removePrefix("collection:").trim()
        if (collectionId.isEmpty()) continue
        val orderKey = "collection_$collectionId"
        if (orderKey in out) continue
        out[orderKey] = PackCollectionHubRef(
            orderKey = orderKey,
            collectionId = collectionId,
            label = block.label?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
    return out
}

/**
 * Strict filter: only keys present in the pack, in pack order.
 * When [availableKeys] is non-empty, intersection is applied (legacy). Prefer
 * passing `availableKeys = packKeys.toSet()` (or a superset) so expanded Studio
 * rails (`folder_…`, catalogs with showInHome=false) are not silently dropped.
 * [disabledKeys] still filters when provided.
 */
fun applyStrictPackOrder(
    packKeys: List<String>,
    availableKeys: Set<String>,
    disabledKeys: Set<String> = emptySet()
): List<String> {
    return packKeys
        .asSequence()
        .filter { availableKeys.isEmpty() || it in availableKeys }
        .filter { it !in disabledKeys }
        .distinct()
        .toList()
}

/**
 * Resolve the pack hero's fixed spotlight item from loaded catalog rows.
 * - `catalog:…` → first item of that catalog
 * - `featured` → first item of the first catalog rail in pack order (Studio preview board behavior)
 */
fun resolvePackHeroMeta(
    heroDataSource: String?,
    packOrderKeys: List<String>?,
    catalogRowsByLegacyKey: Map<String, com.nuvio.tv.domain.model.CatalogRow>
): com.nuvio.tv.domain.model.MetaPreview? {
    val ds = heroDataSource?.trim().orEmpty()
    if (ds.isEmpty()) return null

    fun firstItemForKey(key: String?): com.nuvio.tv.domain.model.MetaPreview? {
        if (key.isNullOrBlank()) return null
        return catalogRowsByLegacyKey[key]?.items?.firstOrNull { !it.id.startsWith("__placeholder_") }
    }

    when {
        ds.startsWith("catalog:") -> return firstItemForKey(homeOrderKeyForDataSource(ds))
        ds == "featured" -> {
            val order = packOrderKeys.orEmpty()
            for (key in order) {
                if (key == PACK_GENRES_ROW_KEY || key.startsWith("collection_")) continue
                firstItemForKey(key)?.let { return it }
            }
            return catalogRowsByLegacyKey.values
                .asSequence()
                .mapNotNull { row -> row.items.firstOrNull { !it.id.startsWith("__placeholder_") } }
                .firstOrNull()
        }
        else -> return null
    }
}
