package com.nuvio.tv.core.viewpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewPackShuffleTest {

    private fun rail(id: String, y: Int, locked: Boolean? = null) = ViewBlock(
        id = id,
        type = "mediaRail",
        y = y,
        dataSource = "catalog:addon:movie:$id",
        locked = locked
    )

    @Test
    fun lockSlotsKeepLockedIds_unlockedPermute() {
        val blocks = listOf(
            rail("a", 0, locked = true),
            rail("b", 100, locked = false),
            rail("c", 200, locked = false),
            rail("d", 300, locked = true)
        )
        val shuffled = shuffleUnlockedBlocks(blocks, "seed-1")
        val byY = shuffled.sortedBy { it.y }
        assertEquals("a", byY[0].id)
        assertEquals("d", byY[3].id)
        val mid = setOf(byY[1].id, byY[2].id)
        assertEquals(setOf("b", "c"), mid)
    }

    @Test
    fun sameSeedIsDeterministic() {
        val blocks = listOf(
            rail("a", 0, false),
            rail("b", 100, false),
            rail("c", 200, false),
            rail("d", 300, false)
        )
        val once = shuffleUnlockedBlocks(blocks, "stable").map { it.id }
        val twice = shuffleUnlockedBlocks(blocks, "stable").map { it.id }
        assertEquals(once, twice)
    }

    @Test
    fun rotateHonorsInterval() {
        val pack = ViewPack(
            blocks = listOf(rail("a", 0, false), rail("b", 100, false), rail("c", 200, false)),
            rotateUnlocked = true,
            rotateIntervalHours = 12,
            lastShuffleAt = 1_000L,
            shuffleSeed = "first"
        )
        val within = applyUnlockedRotation(pack, nowMs = 1_000L + 11 * 60 * 60 * 1000L)
        assertFalse(within.didShuffle)
        assertEquals("first", within.seed)

        val after = applyUnlockedRotation(pack, nowMs = 1_000L + 13 * 60 * 60 * 1000L)
        assertTrue(after.didShuffle)
        assertNotEquals("first", after.seed)
    }

    @Test
    fun parseAndOrderKeys() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "demo",
              "name": "Demo",
              "canvas": { "width": 1920, "height": 1080 },
              "rotateUnlocked": true,
              "rotateIntervalHours": 24,
              "blocks": [
                { "id": "nav", "type": "topNav", "x": 0, "y": 0, "w": 1920, "h": 72, "dataSource": "none", "trailer": false },
                { "id": "r1", "type": "mediaRail", "x": 0, "y": 200, "w": 1920, "h": 210, "dataSource": "catalog:com.addon:movie:top", "trailer": true, "locked": false },
                { "id": "r2", "type": "collectionRail", "x": 0, "y": 460, "w": 1920, "h": 210, "dataSource": "collection:abc", "trailer": false, "locked": true },
                { "id": "g", "type": "genreRail", "x": 0, "y": 720, "w": 1920, "h": 100, "dataSource": "genres", "trailer": false }
              ]
            }
        """.trimIndent()
        val pack = parseViewPackJson(json)
        assertEquals("Demo", pack.name)
        assertTrue(pack.rotateUnlocked)
        assertEquals(24, pack.rotateIntervalHours)
        val keys = homeOrderKeysFromPack(pack)
        assertEquals(
            listOf("com.addon_movie_top", "collection_abc", "_special_genres"),
            keys
        )
    }
}
