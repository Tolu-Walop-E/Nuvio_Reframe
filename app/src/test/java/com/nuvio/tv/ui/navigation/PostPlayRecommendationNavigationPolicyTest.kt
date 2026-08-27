package com.nuvio.tv.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class PostPlayRecommendationNavigationPolicyTest {
    @Test
    fun `manual playback removes stream and player`() {
        assertEquals(
            Screen.Stream.route,
            postPlayRecommendationPopUpRoute(Screen.Stream.route)
        )
    }

    @Test
    fun `autoplay playback removes current player`() {
        assertEquals(
            Screen.Player.route,
            postPlayRecommendationPopUpRoute(Screen.Detail.route)
        )
        assertEquals(
            Screen.Player.route,
            postPlayRecommendationPopUpRoute(null)
        )
    }
}
