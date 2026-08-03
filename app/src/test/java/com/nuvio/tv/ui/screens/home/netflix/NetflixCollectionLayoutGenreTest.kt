package com.nuvio.tv.ui.screens.home.netflix

import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetflixCollectionLayoutGenreTest {

    private val actionFolder = CollectionFolder(
        id = "action",
        title = "Action",
        sources = listOf(
            AddonCatalogCollectionSource(
                addonId = "xperience",
                type = "movie",
                catalogId = "genre_action_movies"
            ),
            AddonCatalogCollectionSource(
                addonId = "xperience",
                type = "series",
                catalogId = "genre_action_series"
            )
        )
    )

    private val kDramaFolder = CollectionFolder(
        id = "kdrama",
        title = "K-Drama",
        sources = listOf(
            AddonCatalogCollectionSource(
                addonId = "xperience",
                type = "series",
                catalogId = "genre_kdrama_series"
            )
        )
    )

    private val animeCollection = Collection(
        id = "anime",
        title = "Anime",
        folders = listOf(
            CollectionFolder(
                id = "anime-movies",
                title = "Trending Anime Movies",
                sources = listOf(
                    AddonCatalogCollectionSource(
                        addonId = "xperience",
                        type = "movie",
                        catalogId = "anime_trending_movies"
                    )
                )
            ),
            CollectionFolder(
                id = "anime-series",
                title = "Trending Anime Series",
                sources = listOf(
                    AddonCatalogCollectionSource(
                        addonId = "xperience",
                        type = "series",
                        catalogId = "anime_trending_series"
                    )
                )
            )
        )
    )

    @Test
    fun pickSourceStrict_moviesOnlyReturnsMovieCatalog() {
        val source = NetflixCollectionLayout.pickSourceStrict(actionFolder, NetflixContentTab.MOVIES)
        assertEquals("movie", source?.type)
        assertEquals("genre_action_movies", source?.catalogId)
    }

    @Test
    fun pickSourceStrict_showsOnlyReturnsSeriesCatalog() {
        val source = NetflixCollectionLayout.pickSourceStrict(actionFolder, NetflixContentTab.SHOWS)
        assertEquals("series", source?.type)
        assertEquals("genre_action_series", source?.catalogId)
    }

    @Test
    fun pickSourceStrict_hidesSeriesOnlyFolderOnMovies() {
        assertNull(NetflixCollectionLayout.pickSourceStrict(kDramaFolder, NetflixContentTab.MOVIES))
        assertEquals(
            "genre_kdrama_series",
            NetflixCollectionLayout.pickSourceStrict(kDramaFolder, NetflixContentTab.SHOWS)?.catalogId
        )
    }

    @Test
    fun pickAnimeGenreSource_matchesTabType() {
        val movies = NetflixCollectionLayout.pickAnimeGenreSource(animeCollection, NetflixContentTab.MOVIES)
        assertEquals("anime_trending_movies", movies?.second?.catalogId)
        assertTrue(movies?.second?.type.equals("movie", ignoreCase = true))

        val shows = NetflixCollectionLayout.pickAnimeGenreSource(animeCollection, NetflixContentTab.SHOWS)
        assertEquals("anime_trending_series", shows?.second?.catalogId)
        assertTrue(shows?.second?.type.equals("series", ignoreCase = true))
    }
}
