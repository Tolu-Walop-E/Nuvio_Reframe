package com.nuvio.tv.ui.screens.home.netflix

import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder

/**
 * Decides how Netflix home treats each collection:
 * - Fan-out: expand folders into real title rails (Trending, In Theaters, …)
 * - Showcase: keep as curated hub tiles (Streaming, Studios, Actors, …)
 */
internal object NetflixCollectionLayout {

    private val FAN_OUT_TITLES = setOf(
        "for you & trending",
        "new & latest",
        "anime"
    )

    private val SHOWCASE_TITLES = setOf(
        "streaming services",
        "studios & labels",
        "genres",
        "directors",
        "actors",
        "film collections",
        "by decade"
    )

    fun shouldFanOut(collection: Collection): Boolean {
        val title = collection.title.trim().lowercase()
        if (title in FAN_OUT_TITLES) return true
        if (title in SHOWCASE_TITLES) return false
        // Default: fan out compact content collections; keep large hub boards as showcases.
        return collection.folders.size in 1..16 &&
            collection.folders.any { folder -> folder.primaryAddonSource() != null }
    }

    fun shouldKeepAsShowcase(collection: Collection): Boolean = !shouldFanOut(collection)

    /**
     * Pick the best addon catalog source for a folder given the active content tab.
     * Home prefers the first source; Movies/Shows filter by type and fall back.
     */
    fun pickSource(
        folder: CollectionFolder,
        tab: NetflixContentTab
    ): AddonCatalogCollectionSource? {
        val addonSources = folderAddonSources(folder)
        if (addonSources.isEmpty()) return null

        val wanted = wantedApiType(tab)
        if (wanted != null) {
            addonSources.firstOrNull { it.type.equals(wanted, ignoreCase = true) }?.let { return it }
        }
        return addonSources.first()
    }

    /**
     * Like [pickSource] but Movies/Shows never fall back to the other type — used
     * for the genres strip so Action on Movies only opens movie catalogs.
     */
    fun pickSourceStrict(
        folder: CollectionFolder,
        tab: NetflixContentTab
    ): AddonCatalogCollectionSource? {
        val addonSources = folderAddonSources(folder)
        if (addonSources.isEmpty()) return null
        val wanted = wantedApiType(tab) ?: return addonSources.first()
        return addonSources.firstOrNull { it.type.equals(wanted, ignoreCase = true) }
    }

    /**
     * Best Anime-collection folder + source for the active tab (Trending first).
     */
    fun pickAnimeGenreSource(
        animeCollection: Collection,
        tab: NetflixContentTab
    ): Pair<CollectionFolder, AddonCatalogCollectionSource>? {
        val preferredTitles = when (tab) {
            NetflixContentTab.MOVIES -> listOf(
                "Trending Anime Movies",
                "Best Anime Movies",
                "Latest Anime Movies",
                "Top Rated Anime Movies"
            )
            NetflixContentTab.SHOWS -> listOf(
                "Trending Anime Series",
                "Best Anime Series",
                "Latest Anime Series",
                "Top Rated Anime Series"
            )
            NetflixContentTab.HOME -> listOf(
                "Trending Anime Series",
                "Trending Anime Movies",
                "Best Anime Series",
                "Best Anime Movies"
            )
        }
        preferredTitles.forEach { title ->
            val folder = animeCollection.folders.firstOrNull { it.title.equals(title, ignoreCase = true) }
                ?: return@forEach
            val source = pickSourceStrict(folder, tab) ?: pickSource(folder, tab) ?: return@forEach
            return folder to source
        }
        animeCollection.folders.forEach { folder ->
            val source = pickSourceStrict(folder, tab) ?: return@forEach
            return folder to source
        }
        return null
    }

    fun railKey(collectionId: String, folderId: String, source: AddonCatalogCollectionSource): String {
        return "fanout|$collectionId|$folderId|${source.type}|${source.catalogId}"
    }

    private fun wantedApiType(tab: NetflixContentTab): String? = when (tab) {
        NetflixContentTab.MOVIES -> "movie"
        NetflixContentTab.SHOWS -> "series"
        NetflixContentTab.HOME -> null
    }

    private fun folderAddonSources(folder: CollectionFolder): List<AddonCatalogCollectionSource> {
        return folder.sources.mapNotNull { it as? AddonCatalogCollectionSource }
            .ifEmpty {
                folder.catalogSources.map {
                    AddonCatalogCollectionSource(
                        addonId = it.addonId,
                        type = it.type,
                        catalogId = it.catalogId,
                        genre = it.genre
                    )
                }
            }
    }
}

data class NetflixFolderRailRequest(
    val railKey: String,
    val title: String,
    val source: AddonCatalogCollectionSource
)

internal fun CollectionFolder.primaryAddonSource(): AddonCatalogCollectionSource? {
    sources.filterIsInstance<AddonCatalogCollectionSource>().firstOrNull()?.let { return it }
    val legacy = catalogSources.firstOrNull() ?: return null
    return AddonCatalogCollectionSource(
        addonId = legacy.addonId,
        type = legacy.type,
        catalogId = legacy.catalogId,
        genre = legacy.genre
    )
}
