package com.nuvio.tv.core.viewpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves FinalOffer-class Studio packs parse and bind on vanilla (not only the Debug fork).
 */
class FinalOfferPackContractTest {

    private fun loadFinalOffer(): ViewPack {
        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("viewpack/finaloffer.view.json")
        ) { "Missing test resource viewpack/finaloffer.view.json" }
        val json = stream.bufferedReader().use { it.readText() }
        return parseViewPackJson(json)
    }

    @Test
    fun finalOfferHonorsContractV1Bindings() {
        val pack = loadFinalOffer()

        assertEquals("FinalOffer", pack.name)
        assertTrue(pack.showFocusedPosterInfo)
        assertTrue(packHasHero(pack))
        assertEquals("featured", packHeroDataSource(pack))

        val order = homeOrderKeysFromPack(pack)
        assertTrue(order.isNotEmpty())
        assertFalse(order.any { it.equals("featured", ignoreCase = true) })

        val labels = homeRowShowLabelsFromPack(pack)
        assertTrue(labels.values.any { it })

        val scales = homeRowScalesFromPack(pack)
        assertTrue(scales.isNotEmpty())
        assertTrue(scales.values.all { it in 0.4f..2.6f })

        val openStyles = collectionOpenStylesFromPack(pack)
        assertTrue(openStyles.isNotEmpty())
        assertTrue(openStyles.values.any { it == OPEN_STYLE_REFRAME })

        assertNotNull(packHeroLabel(pack))
    }
}
