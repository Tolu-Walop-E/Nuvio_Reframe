package com.nuvio.tv.core.viewpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionOpenStyleTest {

    private fun packJson(blocks: String): String = """
        {
          "schemaVersion": 1,
          "id": "test",
          "name": "Test",
          "canvas": { "width": 1920, "height": 1080 },
          "blocks": [$blocks]
        }
    """.trimIndent()

    @Test
    fun parsesReframeStyleOnCollectionRail() {
        val pack = parseViewPackJson(
            packJson(
                """
                { "id": "b1", "type": "collectionRail", "y": 0,
                  "dataSource": "collection:abc", "collectionOpenStyle": "reframe" }
                """.trimIndent()
            )
        )
        assertEquals(OPEN_STYLE_REFRAME, pack.blocks.single().collectionOpenStyle)
        assertEquals(mapOf("abc" to OPEN_STYLE_REFRAME), collectionOpenStylesFromPack(pack))
    }

    @Test
    fun unknownStyleIsDropped() {
        val pack = parseViewPackJson(
            packJson(
                """
                { "id": "b1", "type": "collectionRail", "y": 0,
                  "dataSource": "collection:abc", "collectionOpenStyle": "carousel" }
                """.trimIndent()
            )
        )
        assertNull(pack.blocks.single().collectionOpenStyle)
        assertTrue(collectionOpenStylesFromPack(pack).isEmpty())
    }

    @Test
    fun missingStyleLeavesCollectionUntouched() {
        val pack = parseViewPackJson(
            packJson(
                """
                { "id": "b1", "type": "collectionRail", "y": 0, "dataSource": "collection:abc" }
                """.trimIndent()
            )
        )
        assertTrue(collectionOpenStylesFromPack(pack).isEmpty())
    }

    @Test
    fun folderRailResolvesToParentCollection() {
        val pack = parseViewPackJson(
            packJson(
                """
                { "id": "b1", "type": "mediaRail", "y": 0,
                  "dataSource": "collection:abc:folder:f1", "collectionOpenStyle": "rows" }
                """.trimIndent()
            )
        )
        assertEquals(mapOf("abc" to OPEN_STYLE_ROWS), collectionOpenStylesFromPack(pack))
    }

    @Test
    fun nonCollectionRailIsIgnored() {
        val pack = parseViewPackJson(
            packJson(
                """
                { "id": "b1", "type": "mediaRail", "y": 0,
                  "dataSource": "catalog:addon:movie:top", "collectionOpenStyle": "reframe" }
                """.trimIndent()
            )
        )
        assertTrue(collectionOpenStylesFromPack(pack).isEmpty())
    }

    @Test
    fun firstBlockWinsWhenCollectionRepeats() {
        val pack = parseViewPackJson(
            packJson(
                """
                { "id": "b2", "type": "collectionRail", "y": 400,
                  "dataSource": "collection:abc", "collectionOpenStyle": "grid" },
                { "id": "b1", "type": "collectionRail", "y": 0,
                  "dataSource": "collection:abc", "collectionOpenStyle": "reframe" }
                """.trimIndent()
            )
        )
        assertEquals(mapOf("abc" to OPEN_STYLE_REFRAME), collectionOpenStylesFromPack(pack))
    }

    @Test
    fun styleSurvivesSerializeRoundTrip() {
        val pack = parseViewPackJson(
            packJson(
                """
                { "id": "b1", "type": "collectionRail", "y": 0,
                  "dataSource": "collection:abc", "collectionOpenStyle": "reframe" }
                """.trimIndent()
            )
        )
        val reparsed = parseViewPackJson(serializeViewPackJson(pack))
        assertEquals(OPEN_STYLE_REFRAME, reparsed.blocks.single().collectionOpenStyle)
    }

    @Test
    fun globalCollectionsOpenInReframeAppliesWhenNoPerRailStyle() {
        val pack = parseViewPackJson(
            """
            {
              "schemaVersion": 1,
              "id": "test",
              "name": "Test",
              "collectionsOpenInReframe": true,
              "canvas": { "width": 1920, "height": 1080 },
              "blocks": [
                { "id": "b1", "type": "collectionRail", "y": 0, "dataSource": "collection:xyz" }
              ]
            }
            """.trimIndent()
        )
        assertTrue(pack.collectionsOpenInReframe)
        assertEquals(OPEN_STYLE_REFRAME, resolveCollectionOpenStyle(pack, "xyz"))
        // Also applies to collections that are not even on a pack rail.
        assertEquals(OPEN_STYLE_REFRAME, resolveCollectionOpenStyle(pack, "other-collection"))
    }

    @Test
    fun perRailGridOverridesGlobalReframe() {
        val pack = parseViewPackJson(
            """
            {
              "schemaVersion": 1,
              "id": "test",
              "name": "Test",
              "collectionsOpenInReframe": true,
              "canvas": { "width": 1920, "height": 1080 },
              "blocks": [
                { "id": "b1", "type": "collectionRail", "y": 0,
                  "dataSource": "collection:abc", "collectionOpenStyle": "grid" }
              ]
            }
            """.trimIndent()
        )
        assertEquals(OPEN_STYLE_GRID, resolveCollectionOpenStyle(pack, "abc"))
        assertEquals(OPEN_STYLE_REFRAME, resolveCollectionOpenStyle(pack, "other"))
    }

    @Test
    fun globalFlagSurvivesSerializeRoundTrip() {
        val pack = parseViewPackJson(
            """
            {
              "schemaVersion": 1,
              "id": "test",
              "name": "Test",
              "collectionsOpenInReframe": true,
              "canvas": { "width": 1920, "height": 1080 },
              "blocks": []
            }
            """.trimIndent()
        )
        val reparsed = parseViewPackJson(serializeViewPackJson(pack))
        assertTrue(reparsed.collectionsOpenInReframe)
    }

    @Test
    fun shuffleKeepsStyleOnBlocks() {
        val blocks = listOf(
            ViewBlock(
                id = "b1",
                type = "collectionRail",
                y = 0,
                dataSource = "collection:abc",
                collectionOpenStyle = OPEN_STYLE_REFRAME
            ),
            ViewBlock(id = "b2", type = "mediaRail", y = 300, dataSource = "catalog:a:movie:x")
        )
        val shuffled = shuffleUnlockedBlocks(blocks, "seed-1")
        assertEquals(
            OPEN_STYLE_REFRAME,
            shuffled.first { it.id == "b1" }.collectionOpenStyle
        )
    }
}
