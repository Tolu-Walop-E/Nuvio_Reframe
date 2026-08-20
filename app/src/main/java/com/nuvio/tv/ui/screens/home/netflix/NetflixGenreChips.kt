package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.runtime.Immutable
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.enabledAddons

/**
 * Lightweight catalog facts used to derive Netflix genre chips from what the
 * user actually has installed (not a fixed Genres-collection folder list).
 */
@Immutable
data class GenreCatalogCandidate(
    val addonId: String,
    val type: String,
    val catalogId: String,
    val catalogName: String,
    /** Genre-extra options from the manifest (`extra.name == "genre"`). */
    val genreOptions: List<String> = emptyList(),
    /** Label when [catalogId] is a dedicated `genre_*` catalog. */
    val dedicatedGenreLabel: String? = null
)

/**
 * Scan enabled addons for genre-capable catalogs.
 * - Dedicated ids like `genre_action_movies` / `genre_action_series`
 * - Any catalog exposing a `genre` extra with options
 */
internal fun buildGenreCatalogCandidates(addons: List<Addon>): List<GenreCatalogCandidate> {
    val out = ArrayList<GenreCatalogCandidate>()
    for (addon in addons.enabledAddons()) {
        for (catalog in addon.catalogs) {
            if (catalog.isSearchOnlyCatalog()) continue
            val type = catalog.apiType.trim().lowercase()
            if (type != "movie" && type != "series") continue
            val dedicated = parseDedicatedGenreLabel(catalog.id, catalog.name)
            val options = catalog.genreExtraOptions()
            if (dedicated == null && options.isEmpty()) continue
            out.add(
                GenreCatalogCandidate(
                    addonId = addon.id,
                    type = type,
                    catalogId = catalog.id,
                    catalogName = catalog.name,
                    genreOptions = options,
                    dedicatedGenreLabel = dedicated
                )
            )
        }
    }
    return out
}

/**
 * Build tab-aware genre chips from available catalogs.
 *
 * - Home → union of movie + series genres
 * - Movies → movie catalogs only
 * - Shows → series catalogs only
 *
 * Falls back to the Genres/Anime collections only when no catalog-derived chips
 * exist (keeps older setups working).
 */
internal fun buildGenreChipsFromAvailableCatalogs(
    candidates: List<GenreCatalogCandidate>,
    collections: List<Collection>,
    tab: NetflixContentTab
): List<NetflixGenreChip> {
    val wantedType = when (tab) {
        NetflixContentTab.MOVIES -> "movie"
        NetflixContentTab.SHOWS -> "series"
        NetflixContentTab.HOME -> null
    }
    val typeSuffix = when (tab) {
        NetflixContentTab.HOME -> ""
        NetflixContentTab.MOVIES -> "|movie"
        NetflixContentTab.SHOWS -> "|series"
    }

    val typed = if (wantedType == null) {
        candidates
    } else {
        candidates.filter { it.type.equals(wantedType, ignoreCase = true) }
    }

    val chips = linkedMapOf<String, NetflixGenreChip>()

    // 1) Dedicated genre_* catalogs (strongest signal — already filtered catalogs).
    data class DedicatedPick(
        val label: String,
        val movie: GenreCatalogCandidate?,
        val series: GenreCatalogCandidate?
    )
    val dedicatedByLabel = linkedMapOf<String, DedicatedPick>()
    for (candidate in typed) {
        val label = candidate.dedicatedGenreLabel?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val norm = label.lowercase()
        val existing = dedicatedByLabel[norm]
        if (existing == null) {
            dedicatedByLabel[norm] = DedicatedPick(
                label = label,
                movie = candidate.takeIf { it.type.equals("movie", ignoreCase = true) },
                series = candidate.takeIf { it.type.equals("series", ignoreCase = true) }
            )
        } else {
            dedicatedByLabel[norm] = existing.copy(
                movie = existing.movie ?: candidate.takeIf { it.type.equals("movie", ignoreCase = true) },
                series = existing.series ?: candidate.takeIf { it.type.equals("series", ignoreCase = true) }
            )
        }
    }
    // On Home, also merge the other type from the full candidate list so a
    // movie-only Action chip can still pair with series when both exist.
    if (wantedType == null) {
        for (candidate in candidates) {
            val label = candidate.dedicatedGenreLabel?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val norm = label.lowercase()
            val existing = dedicatedByLabel[norm] ?: continue
            dedicatedByLabel[norm] = existing.copy(
                movie = existing.movie ?: candidate.takeIf { it.type.equals("movie", ignoreCase = true) },
                series = existing.series ?: candidate.takeIf { it.type.equals("series", ignoreCase = true) }
            )
        }
    }

    for ((norm, pick) in dedicatedByLabel) {
        val source = when (tab) {
            NetflixContentTab.MOVIES -> pick.movie
            NetflixContentTab.SHOWS -> pick.series
            NetflixContentTab.HOME -> pick.movie ?: pick.series
        } ?: continue
        val key = "genre|$norm$typeSuffix"
        chips.putIfAbsent(
            key,
            NetflixGenreChip(
                key = key,
                label = pick.label,
                catalogId = source.catalogId,
                addonId = source.addonId,
                type = source.type,
                // Dedicated catalogs are already genre-scoped — don't client-filter.
                genreFilter = null
            )
        )
    }

    // 2) Genre-extra options from remaining catalogs (Cinemeta-style).
    for (candidate in typed) {
        if (candidate.dedicatedGenreLabel != null) continue
        for (option in candidate.genreOptions) {
            val label = option.trim()
            if (label.isEmpty()) continue
            val norm = label.lowercase()
            val key = "genre|$norm$typeSuffix"
            chips.putIfAbsent(
                key,
                NetflixGenreChip(
                    key = key,
                    label = label.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() },
                    catalogId = candidate.catalogId,
                    addonId = candidate.addonId,
                    type = candidate.type,
                    genreFilter = label
                )
            )
        }
    }

    // 3) Anime chip from Anime collection when catalogs exist for the tab.
    val animeCollection = collections.firstOrNull { it.title.equals("Anime", ignoreCase = true) }
    if (animeCollection != null) {
        val match = NetflixCollectionLayout.pickAnimeGenreSource(animeCollection, tab)
        if (match != null) {
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
                    genreFilter = source.genre?.takeIf {
                        it.isNotBlank() && !it.equals("None", ignoreCase = true)
                    },
                    collectionId = animeCollection.id,
                    folderId = folder.id
                )
            )
        }
    }

    if (chips.isNotEmpty()) {
        return chips.values.sortedBy { it.label.lowercase() }
    }

    // 4) Fallback: Genres collection folders whose catalogs are installed.
    return buildGenreChipsFromCollectionsFallback(collections, candidates, tab, typeSuffix)
}

/** Kept for tests / fallback when no catalog genre signals exist. */
internal fun buildGenreChipsFromCollections(
    collections: List<Collection>,
    tab: NetflixContentTab
): List<NetflixGenreChip> {
    return buildGenreChipsFromCollectionsFallback(
        collections = collections,
        candidates = emptyList(),
        tab = tab,
        typeSuffix = when (tab) {
            NetflixContentTab.HOME -> ""
            NetflixContentTab.MOVIES -> "|movie"
            NetflixContentTab.SHOWS -> "|series"
        },
        requireCatalogInstalled = false
    )
}

/**
 * Text pills from a collection's folders (Studio "Turn into text pills").
 * Labels are folder titles; selecting a pill opens that folder.
 */
internal fun buildGenrePillsFromCollection(collection: Collection): List<NetflixGenreChip> {
    return collection.folders.mapNotNull { folder ->
        val label = folder.title.trim().ifBlank { return@mapNotNull null }
        val source = folder.catalogSources.firstOrNull()
        NetflixGenreChip(
            key = "folder|${collection.id}|${folder.id}",
            label = label,
            catalogId = source?.catalogId.orEmpty(),
            addonId = source?.addonId.orEmpty(),
            type = source?.type.orEmpty(),
            genreFilter = source?.genre,
            collectionId = collection.id,
            folderId = folder.id
        )
    }
}

private fun buildGenreChipsFromCollectionsFallback(
    collections: List<Collection>,
    candidates: List<GenreCatalogCandidate>,
    tab: NetflixContentTab,
    typeSuffix: String,
    requireCatalogInstalled: Boolean = true
): List<NetflixGenreChip> {
    val genresCollection = collections.firstOrNull { it.title.equals("Genres", ignoreCase = true) }
    val animeCollection = collections.firstOrNull { it.title.equals("Anime", ignoreCase = true) }
    if (genresCollection == null && animeCollection == null) return emptyList()

    val installed = candidates.map { "${it.addonId}|${it.type}|${it.catalogId}" }.toHashSet()
    val chips = linkedMapOf<String, NetflixGenreChip>()

    genresCollection?.folders?.forEach { folder ->
        val source = NetflixCollectionLayout.pickSourceStrict(folder, tab) ?: return@forEach
        if (requireCatalogInstalled) {
            val key = "${source.addonId}|${source.type}|${source.catalogId}"
            if (key !in installed &&
                candidates.none {
                    it.catalogId == source.catalogId &&
                        it.type.equals(source.type, ignoreCase = true)
                }
            ) {
                return@forEach
            }
        }
        val label = folder.title.trim().ifBlank { return@forEach }
        val chipKey = "genre|${label.lowercase()}$typeSuffix"
        chips.putIfAbsent(
            chipKey,
            NetflixGenreChip(
                key = chipKey,
                label = label,
                catalogId = source.catalogId,
                addonId = source.addonId,
                type = source.type,
                genreFilter = source.genre?.takeIf {
                    it.isNotBlank() && !it.equals("None", ignoreCase = true)
                },
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
                genreFilter = source.genre?.takeIf {
                    it.isNotBlank() && !it.equals("None", ignoreCase = true)
                },
                collectionId = anime.id,
                folderId = folder.id
            )
        )
    }

    return chips.values.sortedBy { it.label.lowercase() }
}

internal fun parseDedicatedGenreLabel(catalogId: String, catalogName: String): String? {
    val id = catalogId.trim().lowercase()
    if (!id.startsWith("genre_")) return null

    Regex("^genre_([a-z0-9]+)_(movies|movie|series|shows|show|tv)$").find(id)?.let { match ->
        return humanizeGenreToken(match.groupValues[1])
    }

    var cleaned = catalogName.trim()
    if (cleaned.isNotBlank()) {
        cleaned = cleaned
            .replace(Regex("(?i)\\s*(movies|movie|series|shows|tv)\\s*$"), "")
            .replace(Regex("(?i)^(latest|popular|top\\s*rated|best|trending|most\\s*popular)\\s+"), "")
            .trim()
        if (cleaned.isNotBlank()) {
            return cleaned.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
        }
    }

    val token = id.removePrefix("genre_")
        .replace(Regex("_(movies|movie|series|shows|show|tv)$"), "")
        .substringBefore("_popular")
        .substringBefore("_latest")
        .substringBefore("_toprated")
        .substringBefore("_best")
        .substringBefore("_trending")
    return humanizeGenreToken(token).takeIf { it.isNotBlank() }
}

private fun humanizeGenreToken(token: String): String {
    val key = token.trim().lowercase().replace('-', '_')
    SPECIAL_GENRE_LABELS[key]?.let { return it }
    return key.split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
        }
}

private val SPECIAL_GENRE_LABELS = mapOf(
    "scifi" to "Sci-Fi",
    "sci_fi" to "Sci-Fi",
    "kdrama" to "K-Drama",
    "romcom" to "Romantic Comedy",
    "romantic_comedy" to "Romantic Comedy",
    "film_noir" to "Film-Noir",
    "science_fiction" to "Sci-Fi"
)

private fun CatalogDescriptor.genreExtraOptions(): List<String> {
    return extra
        .firstOrNull { it.name.equals("genre", ignoreCase = true) }
        ?.options
        .orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("None", ignoreCase = true) }
        .distinct()
}

private fun CatalogDescriptor.isSearchOnlyCatalog(): Boolean {
    return extra.any { extra ->
        extra.name.equals("search", ignoreCase = true) && extra.isRequired
    }
}
