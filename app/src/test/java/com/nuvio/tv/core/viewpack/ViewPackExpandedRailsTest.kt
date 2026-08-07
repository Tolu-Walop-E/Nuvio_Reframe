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
