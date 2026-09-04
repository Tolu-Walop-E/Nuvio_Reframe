package com.nuvio.tv.ui.screens.home.netflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetflixHomeChromeTest {

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
