package com.nuvio.tv.ui.screens.home.netflix

import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.ui.navigation.Screen

internal object NetflixHomeFeature {
    /** Master switch — Netflix can be offered as a layout when true. */
    const val AVAILABLE = true

    /** When false, Netflix respects the layout trailer enable setting. */
    const val FORCE_TRAILER_AUTOPLAY = false

    /**
     * True while the active profile's home layout is Netflix.
     * Updated from MainActivity when layout prefs change.
     */
    @Volatile
    var active: Boolean = false
        private set

    /** @deprecated Use [AVAILABLE] / [active]. Kept so existing call sites compile. */
    inline val ENABLED: Boolean get() = active

    fun setActiveFromLayout(layout: HomeLayout) {
        active = AVAILABLE && layout == HomeLayout.NETFLIX
    }

    /**
     * Routes that use Netflix top navigation instead of the classic side drawer.
     */
    fun hidesClassicSidebar(route: String?): Boolean {
        if (!active || route.isNullOrBlank()) return false
        val path = route.substringBefore('?')
        return path == Screen.Home.route ||
            path == Screen.Search.route ||
            path == Screen.Settings.route ||
            path == Screen.Library.route
    }
}
