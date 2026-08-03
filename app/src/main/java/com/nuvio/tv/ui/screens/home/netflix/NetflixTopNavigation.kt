package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.navigation.Screen

@Composable
internal fun NetflixTopNavigation(
    itemFocusRequesters: List<FocusRequester>,
    selectedIndex: Int,
    onMoveDown: () -> Unit,
    onFocusedIndexChanged: (Int) -> Unit,
    onNavFocusChanged: (Boolean) -> Unit = {},
    selectedTabIndex: Int = 1,
    onTabSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val routeNavigator = LocalNetflixRouteNavigator.current
    // First three are in-place content tabs; Watchlist routes away.
    val centerItems = listOf(
        "Home" to null,
        "Movies" to null,
        "TV Shows" to null,
        "Watchlist" to Screen.Library.route
    )
    var focusedNavIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(focusedNavIndex) {
        onNavFocusChanged(focusedNavIndex != null)
    }

    fun onItemFocusChanged(focused: Boolean, index: Int) {
        if (focused) {
            focusedNavIndex = index
            onFocusedIndexChanged(index)
        } else if (focusedNavIndex == index) {
            focusedNavIndex = null
        }
    }

    Box(
        modifier = modifier
            .zIndex(8f)
            .fillMaxWidth()
            .background(NetflixHomeTokens.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NetflixHomeTokens.TopNavHeight)
                .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NetflixTopIconButton(
                index = 0,
                focusRequester = itemFocusRequesters.getOrElse(0) { FocusRequester.Default },
                selected = selectedIndex == 0,
                onItemFocusChanged = ::onItemFocusChanged,
                onMoveDown = onMoveDown,
                onClick = { routeNavigator?.invoke(Screen.Settings.route) }
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = NetflixHomeTokens.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                centerItems.forEachIndexed { itemIndex, (label, route) ->
                    val absoluteIndex = itemIndex + 1
                    NetflixTopNavigationItem(
                        index = absoluteIndex,
                        label = label,
                        selected = if (route == null) selectedTabIndex == absoluteIndex else false,
                        focusRequester = itemFocusRequesters.getOrElse(absoluteIndex) { FocusRequester.Default },
                        onItemFocusChanged = ::onItemFocusChanged,
                        onClick = {
                            if (route == null) onTabSelected(absoluteIndex) else routeNavigator?.invoke(route)
                        },
                        onMoveDown = onMoveDown
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NetflixTopIconButton(
                    index = 5,
                    focusRequester = itemFocusRequesters.getOrElse(5) { FocusRequester.Default },
                    selected = selectedIndex == 5,
                    onItemFocusChanged = ::onItemFocusChanged,
                    onMoveDown = onMoveDown,
                    onClick = { routeNavigator?.invoke(Screen.Search.route) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = NetflixHomeTokens.TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                NetflixTopIconButton(
                    index = 6,
                    focusRequester = itemFocusRequesters.getOrElse(6) { FocusRequester.Default },
                    selected = selectedIndex == 6,
                    onItemFocusChanged = ::onItemFocusChanged,
                    onMoveDown = onMoveDown,
                    onClick = { routeNavigator?.invoke(Screen.Settings.route) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = NetflixHomeTokens.TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetflixTopNavigationItem(
    index: Int,
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onItemFocusChanged: (Boolean, Int) -> Unit,
    onClick: () -> Unit,
    onMoveDown: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "netflixTopNavScale"
    )

    Text(
        text = label,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                    onMoveDown()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                onItemFocusChanged(it.isFocused, index)
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (focused || selected) 1f else 0.72f
            }
            .clip(RoundedCornerShape(50))
            .background(if (selected || focused) Color.White.copy(alpha = if (focused) 0.26f else 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = when {
            focused -> NetflixHomeTokens.TextPrimary
            selected -> NetflixHomeTokens.TextPrimary
            else -> NetflixHomeTokens.TextSecondary
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal
    )
}

@Composable
private fun NetflixTopIconButton(
    index: Int,
    focusRequester: FocusRequester,
    selected: Boolean,
    onItemFocusChanged: (Boolean, Int) -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "netflixTopIconScale"
    )
    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                    onMoveDown()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                onItemFocusChanged(it.isFocused, index)
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (focused || selected) 1f else 0.72f
            }
            .size(38.dp)
            .clip(CircleShape)
            .background(if (focused || selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = if (focused) 0.52f else 0.10f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
