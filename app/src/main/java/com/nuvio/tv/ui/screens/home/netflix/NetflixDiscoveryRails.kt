package com.nuvio.tv.ui.screens.home.netflix

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import java.time.LocalDate
import kotlin.random.Random

/**
 * Content tabs surfaced in the top navigation. Movies/Shows filter the loaded
 * rails by type instead of navigating away, so switching feels instant.
 */
internal enum class NetflixContentTab(val navIndex: Int) {
    HOME(1),
    MOVIES(2),
    SHOWS(3);

    companion object {
        fun fromNavIndex(index: Int): NetflixContentTab? = entries.firstOrNull { it.navIndex == index }
    }
}

/**
 * Synthesizes extra "discovery" rails from items already loaded by the real
 * catalogs: top picks, taste-based genre rows, acclaimed titles, hidden gems
 * and genre deep-dives. Everything is derived locally (no network) and the
 * shuffle seed rotates daily so the shelf order feels alive.
 */
internal object NetflixDiscoveryRails {

    private const val MIN_RAIL_ITEMS = 8
    private const val MAX_RAIL_ITEMS = 24
    private const val MAX_GENRE_DEEP_DIVES = 10

    private val genreTitleTemplates = listOf(
        "%s & More",
        "Dive Into %s",
        "Essential %s",
        "%s Worth a Look",
        "The %s Shelf",
        "Big on %s"
    )

    fun build(
        rows: List<CatalogRow>,
        continueWatchingGenres: List<String>,
        tab: NetflixContentTab
    ): List<CatalogRow> {
        val sourced = mutableListOf<Pair<MetaPreview, CatalogRow>>()
        val seenIds = HashSet<String>()
        rows.forEach { row ->
            row.items.forEach { item ->
                val matchesTab = when (tab) {
                    NetflixContentTab.HOME -> true
                    NetflixContentTab.MOVIES -> item.apiType == "movie"
                    NetflixContentTab.SHOWS -> item.apiType == "series"
                }
                if (matchesTab && !item.poster.isNullOrBlank() && seenIds.add(item.id)) {
                    sourced += item to row
                }
            }
        }
        if (sourced.size < MIN_RAIL_ITEMS) return emptyList()

        val random = Random(LocalDate.now().toEpochDay() * 31 + tab.ordinal)
        val rails = mutableListOf<CatalogRow>()

        fun addRail(slug: String, title: String, picks: List<Pair<MetaPreview, CatalogRow>>) {
            if (picks.size < MIN_RAIL_ITEMS) return
            // Cards navigate with their rail's addonBaseUrl, so a synthetic rail
            // must only mix items that share one; keep the dominant group.
            val dominant = picks
                .groupBy { (_, row) -> row.addonBaseUrl }
                .maxByOrNull { it.value.size }
                ?.value
                ?: return
            if (dominant.size < MIN_RAIL_ITEMS) return
            val template = dominant.first().second
            rails += template.copy(
                catalogId = "nuvio_discover_$slug",
                catalogName = title,
                items = dominant.map { it.first }.take(MAX_RAIL_ITEMS),
                isLoading = false,
                hasMore = false,
                currentPage = 0,
                supportsSkip = false,
                nextSkip = 0,
                extraArgs = emptyMap()
            )
        }

        addRail(
            slug = "top_picks",
            title = "Top Picks for You",
            picks = sourced
                .filter { (item, _) -> (item.imdbRating ?: 0f) >= 7f }
                .sortedByDescending { (item, _) -> item.imdbRating }
                .take(48)
                .shuffled(random)
        )

        val affinityGenres = continueWatchingGenres.take(3)
        affinityGenres.forEach { genre ->
            addRail(
                slug = "affinity_${slugify(genre)}",
                title = "Because You're Into $genre",
                picks = sourced
                    .filter { (item, _) -> item.genres.any { it.equals(genre, ignoreCase = true) } }
                    .shuffled(random)
            )
        }

        addRail(
            slug = "acclaimed",
            title = "Critically Acclaimed",
            picks = sourced
                .filter { (item, _) -> (item.imdbRating ?: 0f) >= 8f }
                .shuffled(random)
        )

        addRail(
            slug = "hidden_gems",
            title = "Hidden Gems",
            picks = sourced
                .filter { (item, _) ->
                    val rating = item.imdbRating ?: 0f
                    rating >= 6.4f && rating <= 7.5f
                }
                .shuffled(random)
        )

        val usedGenres = affinityGenres.map { it.lowercase() }.toMutableSet()
        sourced
            .flatMap { (item, _) -> item.genres }
            .map { it.trim() }
            .filter { it.length in 3..18 }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .asSequence()
            .filter { (genreKey, count) -> count >= MIN_RAIL_ITEMS && usedGenres.add(genreKey) }
            .take(MAX_GENRE_DEEP_DIVES)
            .forEach { (genreKey, _) ->
                val label = genreKey.replaceFirstChar { it.uppercase() }
                addRail(
                    slug = "genre_${slugify(genreKey)}",
                    title = genreTitleTemplates[random.nextInt(genreTitleTemplates.size)].format(label),
                    picks = sourced
                        .filter { (item, _) -> item.genres.any { it.equals(genreKey, ignoreCase = true) } }
                        .shuffled(random)
                )
            }

        addRail(
            slug = "shuffle",
            title = "Tonight's Shuffle",
            picks = sourced.shuffled(random)
        )

        return rails
    }

    private fun slugify(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }
}
