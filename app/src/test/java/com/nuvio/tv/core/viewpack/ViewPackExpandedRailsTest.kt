package com.nuvio.tv.core.viewpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewPackExpandedRailsTest {

    @Test
    fun packCatalogRefs_extractsExpandedCatalogRails() {
        val pack = ViewPack(
            name = "Expanded",
            blocks = listOf(
                ViewBlock(
                    id = "c1",
                    type = "mediaRail",
                    y = 0,
                    dataSource = "catalog:app.xperience.abc:movie:anime_trending_movies",
                    label = "Trending Anime Movies"
                ),
                ViewBlock(
                    id = "hub",
                    type = "collectionRail",
                    y = 100,
                    dataSource = "collection:76196fcb-f6ef-4783-8399-25626b81e84e",
                    label = "Streaming Services"
                )
            )
        )
        val refs = packCatalogRefs(pack)
        assertEquals(1, refs.size)
        val key = "app.xperience.abc_movie_anime_trending_movies"
        assertTrue(refs.containsKey(key))
        assertEquals("anime_trending_movies", refs.getValue(key).catalogId)
        assertEquals("Trending Anime Movies", refs.getValue(key).label)
        val hubs = packCollectionHubRefs(pack)
        assertEquals(1, hubs.size)
        assertEquals("Streaming Services", hubs.values.single().label)
    }

    @Test
    fun remapPackCatalogRef_matchesInstalledAddonByCatalogId() {
        val ref = PackCatalogRef(
            orderKey = "app.xperience.old_movie_anime_trending_movies",
            addonId = "app.xperience.old",
            type = "movie",
            catalogId = "anime_trending_movies",
            label = "Trending Anime Movies"
        )
        val remapped = remapPackCatalogRef(
            ref,
            listOf(Triple("app.xperience.new", "movie", "anime_trending_movies"))
        )
        assertEquals("app.xperience.new", remapped.addonId)
        assertEquals("app.xperience.new_movie_anime_trending_movies", remapped.orderKey)
    }

    @Test
    fun remapPackOrderKeys_rewritesStudioAddonUuidOntoInstalledAddon() {
        val studioKey = "app.xperience.old_movie_snoak_top100_movies"
        val refs = mapOf(
            studioKey to PackCatalogRef(
                orderKey = studioKey,
                addonId = "app.xperience.old",
                type = "movie",
                catalogId = "snoak_top100_movies",
                label = "For You · Movies"
            )
        )
        val installed = listOf(Triple("app.xperience.new", "movie", "snoak_top100_movies"))
        val remapped = remapPackOrderKeys(
            listOf("_special_genres", studioKey, "collection_hub1"),
            refs,
            installed
        )
        assertEquals(
            listOf(
                "_special_genres",
                "app.xperience.new_movie_snoak_top100_movies",
                "collection_hub1"
            ),
            remapped
        )
    }

    @Test
    fun packOrderKeyMatchesRail_ignoresAddonUuid() {
        assertTrue(
            packOrderKeyMatchesRail(
                "app.xperience.old_movie_snoak_top100_movies",
                "app.xperience.new_movie_snoak_top100_movies"
            )
        )
        assertTrue(
            !packOrderKeyMatchesRail(
                "app.xperience.old_movie_snoak_top100_movies",
                "app.xperience.new_series_snoak_top100_series"
            )
        )
    }

    @Test
    fun applyStrictPackOrder_keepsFolderAndCatalogKeysWhenUnioned() {
        val packKeys = listOf(
            PACK_GENRES_ROW_KEY,
            "folder_col1_fold1",
            "app.x_movie_cat1",
            "collection_col2"
        )
        // Simulate HomeViewModel union: available = defaultHome + packKeys
        val available = setOf("collection_col2") + packKeys
        val ordered = applyStrictPackOrder(packKeys, available)
        assertEquals(packKeys, ordered)
    }

    @Test
    fun applyStrictPackOrder_legacyDropsUnknownWithoutUnion() {
        val packKeys = listOf("folder_col1_fold1", "collection_known")
        val ordered = applyStrictPackOrder(packKeys, setOf("collection_known"))
        assertEquals(listOf("collection_known"), ordered)
    }
}
