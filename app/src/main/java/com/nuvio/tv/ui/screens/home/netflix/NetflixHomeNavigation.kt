package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.runtime.compositionLocalOf

internal val LocalNetflixRouteNavigator = compositionLocalOf<((String) -> Unit)?> { null }
