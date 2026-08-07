package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.navigation.Screen

internal enum class NetflixShellDestination {
    Home,
    Search,
    Library,
    Settings
}

@Composable
internal fun NetflixOffHomeChrome(
    destination: NetflixShellDestination,
    backdropUrl: String? = null,
    content: @Composable () -> Unit
) {
    if (!NetflixHomeFeature.ENABLED) {
        content()
        return
    }

    val shell = LocalNetflixShellController.current
    val routeNavigator = LocalNetflixRouteNavigator.current ?: shell?.navigate
    val topNavigationRequesters = remember { List(7) { FocusRequester() } }
    // Top-nav intercepts Down; without this requester content can never reclaim focus
    // after Right/Left escapes into the Settings/Search/Library icon.
    val contentFocusRequester = remember { FocusRequester() }
    var topNavFocused by remember { mutableStateOf(false) }
    val selectedIndex = when (destination) {
        NetflixShellDestination.Home -> 1
        NetflixShellDestination.Search -> 5
        NetflixShellDestination.Library -> 4
        NetflixShellDestination.Settings -> 6
    }

    // First Back lands on this screen's top-nav icon (Settings/Search/Library).
    // Only leave for Home once that icon already owns focus.
    BackHandler {
        if (!topNavFocused) {
            runCatching {
                topNavigationRequesters.getOrElse(selectedIndex) {
                    topNavigationRequesters.last()
                }.requestFocus()
            }
        } else {
            routeNavigator?.invoke(Screen.Home.route)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixThemeChrome.background)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NetflixPosterBackdrop(
                imageUrl = backdropUrl,
                modifier = Modifier.fillMaxSize(),
                accentScrim = NetflixThemeChrome.accent
            )
            Column(modifier = Modifier.fillMaxSize()) {
                NetflixTopNavigation(
                    itemFocusRequesters = topNavigationRequesters,
                    selectedIndex = selectedIndex,
                    onMoveDown = {
                        runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)
                    },
                    onFocusedIndexChanged = {},
                    onNavFocusChanged = { topNavFocused = it },
                    selectedTabIndex = -1,
                    onTabSelected = { index ->
                        NetflixContentTab.fromNavIndex(index)?.let { tab ->
                            NetflixHomeTabBridge.request(tab)
                            shell?.openHomeTab(tab)
                                ?: routeNavigator?.invoke(Screen.Home.route)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(contentFocusRequester)
                        .focusRestorer()
                        .focusGroup()
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun NetflixLoadingSkeletonRails(
    modifier: Modifier = Modifier,
    railCount: Int = 3,
    cardsPerRail: Int = 8
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = NetflixHomeTokens.HeroTopGap),
        verticalArrangement = Arrangement.spacedBy(NetflixHomeTokens.RailSpacing)
    ) {
        // Hero placeholder
        Box(
            modifier = Modifier
                .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding)
                .fillMaxWidth()
                .height(NetflixHomeTokens.HeroHeight)
                .clip(RoundedCornerShape(NetflixHomeTokens.HeroCornerRadius))
                .background(Color.White.copy(alpha = 0.08f))
        )
        repeat(railCount) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding)
                        .width(180.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = NetflixHomeTokens.PageHorizontalPadding)
                ) {
                    items(cardsPerRail) {
                        Box(
                            modifier = Modifier
                                .width(NetflixHomeTokens.PortraitCardWidth)
                                .height(NetflixHomeTokens.PortraitCardHeight)
                                .clip(RoundedCornerShape(NetflixHomeTokens.CardCornerRadius))
                                .background(Color.White.copy(alpha = 0.10f))
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
