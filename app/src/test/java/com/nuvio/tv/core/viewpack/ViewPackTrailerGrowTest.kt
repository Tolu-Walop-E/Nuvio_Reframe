package com.nuvio.tv.core.viewpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewPackTrailerGrowTest {

    @Test
    fun trailerAndPosterGrowMapsFromBlocks() {
        val pack = ViewPack(
            schemaVersion = 1,
            id = "t",
            name = "t",
            blocks = listOf(
                ViewBlock(
                    id = "hero",
                    type = "hero",
                    dataSource = "featured",
                    trailer = true
                ),
                ViewBlock(
                    id = "a",
                    type = "mediaRail",
                    y = 100,
                    dataSource = "catalog:addon:movie:top",
                    trailer = true,
                    posterGrow = false
                ),
                ViewBlock(
                    id = "b",
                    type = "mediaRail",
                    y = 200,
                    dataSource = "catalog:addon:series:pop",
                    trailer = false,
                    posterGrow = null
                )
            )
        )

        assertTrue(packHeroTrailerEnabled(pack))
        assertEquals(
            mapOf("addon_movie_top" to true, "addon_series_pop" to false),
            homeRowTrailersFromPack(pack)
        )
        assertEquals(
            mapOf("addon_movie_top" to false, "addon_series_pop" to false),
            homeRowPosterGrowFromPack(pack)
        )
        assertFalse(homeRowPosterGrowFromPack(pack)["addon_movie_top"]!!)
        assertFalse(homeRowPosterGrowFromPack(pack)["addon_series_pop"]!!)
    }

    @Test
    fun rowMapsFollowRemappedAddonIds() {
        val pack = ViewPack(
            schemaVersion = 1,
            id = "bola",
            name = "Bola Save",
            showFocusedPosterInfo = true,
            blocks = listOf(
                ViewBlock(
                    id = "rail",
                    type = "mediaRail",
                    dataSource = "catalog:app.xperience.old:movie:trending_most_popular_top20_movies",
                    trailer = true,
                    posterGrow = true
                )
            )
        )
        val refs = packCatalogRefs(pack)
        val installed = listOf(
            Triple(
                "app.xperience.bola",
                "movie",
                "trending_most_popular_top20_movies"
            )
        )
        val labels = remapPackKeyedMap(homeRowShowLabelsFromPack(pack), refs, installed)
        assertTrue(labels["app.xperience.bola_movie_trending_most_popular_top20_movies"] == true)
        val grow = remapPackKeyedMap(homeRowPosterGrowFromPack(pack), refs, installed)
        assertTrue(grow["app.xperience.bola_movie_trending_most_popular_top20_movies"] == true)
    }
}
