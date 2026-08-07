package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.runtime.compositionLocalOf

/**
 * Navigation + Home-tab actions shared by Netflix chrome on Home / Search /
 * Library / Settings.
 */
internal data class NetflixShellController(
    val navigate: (String) -> Unit,
    val openHomeTab: (NetflixContentTab) -> Unit
)

internal val LocalNetflixShellController = compositionLocalOf<NetflixShellController?> { null }

/** Legacy route-only accessor used by older call sites. */
internal val LocalNetflixRouteNavigator = compositionLocalOf<((String) -> Unit)?> { null }

/**
 * Lets off-home chrome request a Movies/Shows/Home tab before navigating Home.
 * Home consumes this on the next composition.
 */
internal object NetflixHomeTabBridge {
    @Volatile
    private var pending: NetflixContentTab? = null

    fun request(tab: NetflixContentTab) {
        pending = tab
    }

    fun consume(): NetflixContentTab? {
        val value = pending
        pending = null
        return value
    }
}
