package com.nuvio.tv.core.viewpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewPackScaleTest {

    private fun pack(
        showFocusedPosterInfo: Boolean,
        vararg blocks: ViewBlock
    ) = ViewPack(
        schemaVersion = 1,
        id = "scale-test",
        name = "Scale test",
        blocks = blocks.toList(),
        showFocusedPosterInfo = showFocusedPosterInfo
    )

    @Test
    fun focusedInfoUsesPosterPortionNotFullRailHeight() {
        val pack = pack(
            showFocusedPosterInfo = true,
            ViewBlock(
                id = "cw",
                type = "mediaRail",
                y = 0,
                h = 210,
                dataSource = "continueWatching"
            ),
            ViewBlock(
                id = "rail",
                type = "mediaRail",
                y = 300,
                // Studio labeled max: 32 title + 220 poster + 120 metadata
                h = 372,
                dataSource = "catalog:addon:series:list1"
            )
        )
        // Poster portion = 372 - 32 - 120 = 220 → scale 220/210 ≈ 1.05 (not 372/210 = 1.77)
        assertEquals(220, packRailPosterHeightPx(pack.blocks[1], pack))
        val scale = homeRowScalesFromPack(pack)["addon_series_list1"]!!
        assertEquals(220 / 210f, scale, 0.001f)
        assertTrue("must not double posters from full rail height", scale < 1.2f)
    }

    @Test
    fun withoutFocusedInfoFullHeightStillScales() {
        val pack = pack(
            showFocusedPosterInfo = false,
            ViewBlock(
                id = "cw",
                type = "mediaRail",
                y = 0,
                h = 210,
                dataSource = "continueWatching"
            ),
            ViewBlock(
                id = "rail",
                type = "mediaRail",
                y = 300,
                h = 420,
                dataSource = "catalog:addon:series:list1"
            )
        )
        val scales = homeRowScalesFromPack(pack)
        assertEquals(420 / 210f, scales["addon_series_list1"]!!, 0.001f)
    }

    @Test
    fun finalOfferStyleLabeledRailsDoNotDoublePosters() {
        val pack = pack(
            showFocusedPosterInfo = true,
            ViewBlock(id = "cw", type = "mediaRail", y = 0, h = 210, dataSource = "continueWatching"),
            ViewBlock(
                id = "a",
                type = "mediaRail",
                y = 300,
                h = 372,
                dataSource = "catalog:com.aicat:series:aicat_list_37519"
            ),
            ViewBlock(
                id = "b",
                type = "collectionRail",
                y = 800,
                h = 372,
                dataSource = "collection:76196fcb-f6ef-4783-8399-25626b81e84e"
            )
        )
        // Previously this yielded 2.0× and ate the description.
        val scales = homeRowScalesFromPack(pack)
        scales.values.forEach { scale ->
            assertTrue("labeled FinalOffer rails must stay near CW size, was $scale", scale < 1.2f)
            assertTrue(scale > 0.9f)
        }
    }
}
