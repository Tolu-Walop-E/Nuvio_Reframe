package com.nuvio.tv.ui.screens.home.netflix

import org.junit.Assert.assertEquals
import org.junit.Test

class NetflixLogoArtworkTest {

    @Test
    fun `upgrades tmdb sized logo paths to original`() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/abc.png",
            NetflixLogoArtwork.upgradeUrl("https://image.tmdb.org/t/p/w500/abc.png")
        )
        assertEquals(
            "https://image.tmdb.org/t/p/original/abc.png",
            NetflixLogoArtwork.upgradeUrl("https://image.tmdb.org/t/p/w300/abc.png")
        )
    }

    @Test
    fun `leaves original tmdb and non-tmdb urls alone`() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/abc.png",
            NetflixLogoArtwork.upgradeUrl("https://image.tmdb.org/t/p/original/abc.png")
        )
        assertEquals(
            "https://images.metahub.space/logo/large/tt1/img",
            NetflixLogoArtwork.upgradeUrl("https://images.metahub.space/logo/medium/tt1/img")
        )
        assertEquals(
            "https://images.metahub.space/logo/large/tt1/img",
            NetflixLogoArtwork.upgradeUrl("https://images.metahub.space/logo/small/tt1/img")
        )
    }
}
