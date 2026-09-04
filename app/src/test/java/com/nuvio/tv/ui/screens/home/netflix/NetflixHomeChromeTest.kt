package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetflixHomeChromeTest {

    /**
     * A focused rail asks bring-into-view for the top fade's headroom, its own
     * height, and a peek of the row below. That has to fit under the nav strip, or
     * rows start clipping again.
     */
    @Test
    fun `focused rail budget fits the shield viewport`() {
        val screenHeight = 540.dp // Shield 1080p at density 2.0
        val poster = NetflixHomeSpacing.focusedPosterCap(screenHeight)
        val requested = NetflixHomeTokens.ScrollTopFade +
            NetflixHomeSpacing.RailTopPadding +
            NetflixHomeSpacing.RailTitleHeight +
            poster +
            NetflixHomeSpacing.RailFocusPadding +
            NetflixHomeSpacing.FocusedMetadataHeight +
            NetflixHomeSpacing.NextRowPeek
        val scrollViewport = screenHeight - NetflixHomeTokens.homeChromeHeight()
        assertTrue(
            "requested $requested must fit $scrollViewport",
            requested <= scrollViewport
        )
        // Guard against the cap collapsing to its floor on a normal TV.
        assertTrue("poster cap collapsed to $poster", poster > 200.dp)
    }

    /**
     * The fade headroom is what keeps the fade off the focused rail's own title,
     * so it can never be shorter than a title.
     */
    @Test
    fun `top fade cannot swallow a row title`() {
        assertTrue(
            "fade ${NetflixHomeTokens.ScrollTopFade} is shorter than a title",
            NetflixHomeTokens.ScrollTopFade >= NetflixHomeSpacing.RailTitleHeight
        )
    }

    /** "Sneak peek" means a whole readable title plus poster, not a cut heading. */
    @Test
    fun `next row peek shows a full title and a poster sliver`() {
        val toNextTitleBottom = NetflixHomeTokens.RailSpacing +
            NetflixHomeSpacing.RailTopPadding +
            NetflixHomeSpacing.RailTitleHeight
        assertTrue(
            "peek ${NetflixHomeSpacing.NextRowPeek} clips the next title",
            NetflixHomeSpacing.NextRowPeek > toNextTitleBottom
        )
    }

    @Test
    fun `full bleed hero leaves the next row title on screen`() {
        val screenHeight = 540.dp
        val below = screenHeight -
            NetflixHomeTokens.homeChromeHeight() -
            NetflixHomeTokens.heroHeightFor(screenHeight)
        assertTrue("only $below below the hero", below >= NetflixHomeSpacing.RailTitleHeight)
    }

    @Test
    fun `bare minute counts get a unit`() {
        assertEquals("23 min", netflixRuntimeLabel("23"))
        assertEquals("114 min", netflixRuntimeLabel(" 114 "))
    }

    @Test
    fun `already formatted runtimes pass through`() {
        assertEquals("1h 54m", netflixRuntimeLabel("1h 54m"))
        assertEquals("176 min", netflixRuntimeLabel("176 min"))
    }

    @Test
    fun `blank and zero runtimes are dropped`() {
        assertNull(netflixRuntimeLabel(null))
        assertNull(netflixRuntimeLabel("   "))
        assertNull(netflixRuntimeLabel("0"))
        assertNull(netflixRuntimeLabel("0 min"))
    }

    @Test
    fun `only unsized secondary rails are demoted`() {
        val secondary = NetflixHomeTokens.SecondaryRailScale
        assertEquals(
            1f,
            NetflixHomeTokens.railHierarchyScale(
                isFeatured = true,
                hasExplicitScale = false,
                secondary = secondary
            )
        )
        assertEquals(
            secondary,
            NetflixHomeTokens.railHierarchyScale(
                isFeatured = false,
                hasExplicitScale = false,
                secondary = secondary
            )
        )
        // A pack / Customize scale must win outright.
        assertEquals(
            1f,
            NetflixHomeTokens.railHierarchyScale(
                isFeatured = false,
                hasExplicitScale = true,
                secondary = secondary
            )
        )
    }
}
