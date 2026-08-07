package com.nuvio.tv.ui.screens.home.netflix

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogExtra
import com.nuvio.tv.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetflixGenreChipsTest {

    private fun addonWithCatalogs(vararg catalogs: CatalogDescriptor) = Addon(
        id = "app.xperience.test",
        name = "Xperience",
        version = "1",
        description = null,
        logo = null,
        baseUrl = "https://example.com",
        catalogs = catalogs.toList(),
        types = listOf(ContentType.MOVIE, ContentType.SERIES),
        resources = emptyList()
    )

    private fun dedicated(id: String, type: ContentType, name: String) = CatalogDescriptor(
        type = type,
        id = id,
        name = name,
        showInHome = true,
        hasExplicitShowInHome = true
    )

    @Test
    fun parseDedicatedGenreLabel_pairsMoviesAndSeries() {
        assertEquals("Action", parseDedicatedGenreLabel("genre_action_movies", "Action Movies"))
        assertEquals("Action", parseDedicatedGenreLabel("genre_action_series", "Action Series"))
        assertEquals("K-Drama", parseDedicatedGenreLabel("genre_kdrama_popular_series", "Popular K-Drama"))
    }

    @Test
    fun homeUnion_includesMovieAndSeriesGenres() {
        val addon = addonWithCatalogs(
            dedicated("genre_action_movies", ContentType.MOVIE, "Action Movies"),
            dedicated("genre_action_series", ContentType.SERIES, "Action Series"),
            dedicated("genre_kdrama_popular_series", ContentType.SERIES, "Popular K-Drama")
        )
        val candidates = buildGenreCatalogCandidates(listOf(addon))
        val home = buildGenreChipsFromAvailableCatalogs(candidates, emptyList(), NetflixContentTab.HOME)
        val movies = buildGenreChipsFromAvailableCatalogs(candidates, emptyList(), NetflixContentTab.MOVIES)
        val shows = buildGenreChipsFromAvailableCatalogs(candidates, emptyList(), NetflixContentTab.SHOWS)

        assertTrue(home.any { it.label == "Action" })
        assertTrue(home.any { it.label == "K-Drama" })
        assertEquals("movie", home.first { it.label == "Action" }.type)

        assertTrue(movies.any { it.label == "Action" && it.type == "movie" })
        assertFalse(movies.any { it.label == "K-Drama" })

        assertTrue(shows.any { it.label == "Action" && it.type == "series" })
        assertTrue(shows.any { it.label == "K-Drama" })
    }

    @Test
    fun genreExtraOptions_becomeChips() {
        val addon = addonWithCatalogs(
            CatalogDescriptor(
                type = ContentType.MOVIE,
                id = "top",
                name = "Top",
                showInHome = true,
                hasExplicitShowInHome = true,
                extra = listOf(
                    CatalogExtra(name = "genre", options = listOf("Comedy", "Drama", "None"))
                )
            )
        )
        val candidates = buildGenreCatalogCandidates(listOf(addon))
        val chips = buildGenreChipsFromAvailableCatalogs(
            candidates,
            emptyList(),
            NetflixContentTab.MOVIES
        )
        assertEquals(setOf("Comedy", "Drama"), chips.map { it.label }.toSet())
        assertEquals("Comedy", chips.first { it.label == "Comedy" }.genreFilter)
    }

    @Test
    fun dedicatedChips_doNotPassGenreFilter() {
        val addon = addonWithCatalogs(
            dedicated("genre_horror_movies", ContentType.MOVIE, "Horror Movies")
        )
        val chips = buildGenreChipsFromAvailableCatalogs(
            buildGenreCatalogCandidates(listOf(addon)),
            emptyList(),
            NetflixContentTab.MOVIES
        )
        assertNull(chips.single().genreFilter)
        assertEquals("genre_horror_movies", chips.single().catalogId)
    }
}
