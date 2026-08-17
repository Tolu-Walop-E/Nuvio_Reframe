@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun NetflixContinueWatchingRail(
    railKey: String,
    title: String,
    items: List<ContinueWatchingItem>,
    useEpisodeThumbnails: Boolean,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    onItemClick: (ContinueWatchingItem) -> Unit,
    onItemLongClick: (ContinueWatchingItem) -> Unit,
    onItemFocused: (ContinueWatchingItem) -> Unit,
    onFocusedItemChanged: (Int, String) -> Unit,
    onPendingFocusConsumed: () -> Unit,
    onFirstCardRequesterReady: (FocusRequester) -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val itemRequesters = remember(railKey, items.size) { List(items.size) { FocusRequester() } }
    var focusedItem by remember(items) { mutableStateOf(items.getOrNull(lastFocusedIndex.coerceIn(0, items.lastIndex))) }
    var settledItem by remember(items) { mutableStateOf(focusedItem) }

    LaunchedEffect(focusedItem?.netflixKey()) {
        val candidate = focusedItem
        kotlinx.coroutines.delay(220L)
        if (focusedItem?.netflixKey() == candidate?.netflixKey()) {
            settledItem = candidate
        }
    }

    NetflixRailScaffold(
        title = title,
        railKey = railKey,
        pendingFocusRailKey = pendingFocusRailKey,
        lastFocusedIndex = lastFocusedIndex,
        itemRequesters = itemRequesters,
        onPendingFocusConsumed = onPendingFocusConsumed,
        onFirstCardRequesterReady = onFirstCardRequesterReady,
        modifier = modifier
    ) { rowState, focusedIndex, onCardFocused, onMoveLeft, onMoveRight, railHasFocus ->
        val density = LocalDensity.current
        val continueCacheSizePx = remember(density) {
            IntSize(
                width = with(density) { NetflixHomeTokens.ContinueCardWidth.roundToPx().coerceAtLeast(1) },
                height = with(density) { NetflixHomeTokens.ContinueCardHeight.roundToPx().coerceAtLeast(1) }
            )
        }
        NetflixPivotLazyRow(
            state = rowState,
            selectorVisible = railHasFocus,
            selectorWidth = NetflixHomeTokens.ContinueCardWidth,
            selectorHeight = NetflixHomeTokens.ContinueCardHeight
        ) {
                itemsIndexed(items, key = { _, item -> item.netflixKey() }) { index, item ->
                    val card = item.toNetflixCard(useEpisodeThumbnails = useEpisodeThumbnails)
                    val itemKey = item.netflixKey()
                    NetflixMediaCard(
                        mediaKey = itemKey,
                        title = card.title,
                        subtitle = card.subtitle,
                        imageUrl = card.imageUrl,
                        artworkCacheSizePx = continueCacheSizePx,
                        width = NetflixHomeTokens.ContinueCardWidth,
                        height = NetflixHomeTokens.ContinueCardHeight,
                        progress = card.progress,
                        showLabels = false,
                        showFallbackTitleWhenArtworkMissing = false,
                        focusRequester = itemRequesters[index],
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        onFocus = {
                            onCardFocused(index)
                            focusedItem = item
                            onFocusedItemChanged(index, itemKey)
                            onItemFocused(item)
                        },
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onMoveLeft = onMoveLeft,
                        onMoveRight = onMoveRight,
                        showFocusBorder = false
                    )
                }
        }
        Box(
            modifier = Modifier
                .height(NetflixHomeSpacing.ContinueMetadataHeight)
                .padding(
                    start = NetflixHomeTokens.PageHorizontalPadding,
                    top = 8.dp,
                    end = NetflixHomeTokens.PageHorizontalPadding
                )
        ) {
            Crossfade(
                targetState = settledItem ?: focusedItem,
                animationSpec = tween(durationMillis = 200),
                label = "netflixContinueWatchingMetadata"
            ) { item ->
                item?.let {
                    NetflixContinueWatchingMetadata(
                        item = it,
                        useEpisodeThumbnails = useEpisodeThumbnails
                    )
                }
            }
        }
    }
}

@Composable
internal fun NetflixCatalogRail(
    railKey: String,
    row: CatalogRow,
    useLandscapeCards: Boolean,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    onItemClick: (MetaPreview, String) -> Unit,
    onItemFocused: (MetaPreview) -> Unit,
    onItemLongClick: (MetaPreview, String) -> Unit,
    onLoadMore: (String, String, String) -> Unit,
    onFocusedItemChanged: (Int, String) -> Unit,
    onPendingFocusConsumed: () -> Unit,
    onFirstCardRequesterReady: (FocusRequester) -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    posterLabelsEnabled: Boolean,
    /** Pack-driven card size multiplier against Netflix catalogue geometry. */
    railScale: Float = 1f,
    /** When false (pack with focused-info off), hide the always-on catalogue footer. */
    showFocusedMetadata: Boolean = true,
    /** When false (pack `posterGrow: false`), focused cards keep portrait width. */
    posterGrow: Boolean = true,
    trailerPreviewUrls: Map<String, String> = emptyMap(),
    trailerPreviewAudioUrls: Map<String, String> = emptyMap(),
    trailerEnabled: Boolean = false,
    trailerMuted: Boolean = true,
    onRequestTrailerPreview: (MetaPreview) -> Unit = {},
    /** Keep titled empty rails (Studio pack) instead of collapsing them. */
    allowEmpty: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (row.items.isEmpty()) {
        if (!allowEmpty) return
        val emptyRequester = remember(railKey) { FocusRequester() }
        NetflixRailScaffold(
            title = row.catalogName.replaceFirstChar { it.uppercase() },
            subtitle = null,
            railKey = railKey,
            pendingFocusRailKey = pendingFocusRailKey,
            lastFocusedIndex = 0,
            itemRequesters = listOf(emptyRequester),
            onPendingFocusConsumed = onPendingFocusConsumed,
            onFirstCardRequesterReady = onFirstCardRequesterReady,
            modifier = modifier
        ) { _, _, _, _, _, _ ->
            Box(
                modifier = Modifier
                    .height(140.dp)
                    .fillMaxWidth()
                    .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding)
                    .focusRequester(emptyRequester)
                    .focusable()
                    .onFocusChanged { state ->
                        if (state.isFocused) onFocusedItemChanged(0, railKey)
                    }
            )
        }
        return
    }
    val density = LocalDensity.current
    val itemRequesters = remember(railKey, row.items.size) { List(row.items.size) { FocusRequester() } }
    var focusedMeta by remember(row.items) { mutableStateOf(row.items.getOrNull(lastFocusedIndex)) }
    var settledMeta by remember(row.items) { mutableStateOf(focusedMeta) }
    val playedTrailerIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(focusedMeta?.id) {
        val candidate = focusedMeta
        if (candidate != null && trailerEnabled) {
            onRequestTrailerPreview(candidate)
        }
        kotlinx.coroutines.delay(80L)
        if (focusedMeta?.id == candidate?.id) {
            settledMeta = candidate
        }
    }

    NetflixRailScaffold(
        title = row.catalogName.replaceFirstChar { it.uppercase() },
        subtitle = null,
        railKey = railKey,
        pendingFocusRailKey = pendingFocusRailKey,
        lastFocusedIndex = lastFocusedIndex,
        itemRequesters = itemRequesters,
        onPendingFocusConsumed = onPendingFocusConsumed,
        onFirstCardRequesterReady = onFirstCardRequesterReady,
        modifier = modifier
    ) { rowState, focusedIndex, onCardFocused, onMoveLeft, onMoveRight, railHasFocus ->
        val context = LocalContext.current
        val imageLoader = context.imageLoader
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val usableWidth = (maxWidth - (NetflixHomeTokens.PageHorizontalPadding * 2))
                .coerceAtLeast(0.dp)
            val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
            // Keep facts + ≥2 synopsis lines under posters when the pack footer is on.
            val maxPosterHeight = if (showFocusedMetadata) {
                (
                    screenHeightDp -
                        NetflixHomeSpacing.FocusedMetadataHeight -
                        36.dp - // row title
                        NetflixHomeSpacing.RailTopPadding -
                        NetflixHomeSpacing.RailFocusPadding -
                        48.dp // breathing room / next-rail peek
                    ).coerceAtLeast(220.dp)
            } else {
                null
            }
            val geometry = remember(usableWidth, density, railScale, maxPosterHeight) {
                NetflixHomeDimensions.catalogueRailGeometry(
                    usableWidth = usableWidth,
                    density = density,
                    scale = railScale,
                    maxAbsoluteRailHeight = maxPosterHeight
                )
            }
            // Warm landscape/backdrop bitmaps for the focused card and neighbors so
            // D-pad moves don't wait on a cold Coil fetch when the URL switches.
            // Size to the focused card and skip when already in memory.
            val artworkCacheSizePx = remember(density, geometry.focusedWidth, geometry.railHeight) {
                IntSize(
                    width = with(density) { geometry.focusedWidth.roundToPx().coerceAtLeast(1) },
                    height = with(density) { geometry.railHeight.roundToPx().coerceAtLeast(1) }
                )
            }
            LaunchedEffect(focusedIndex, row.items, useLandscapeCards, artworkCacheSizePx) {
                withContext(Dispatchers.IO) {
                    val lastIndex = row.items.lastIndex
                    if (lastIndex < 0) return@withContext
                    val center = focusedIndex.coerceIn(0, lastIndex)
                    // Nearest neighbours first: the next D-pad move is the one that
                    // must not wait on a decode.
                    val offsets = listOf(0, 1, -1, 2, -2, 3, -3, 4, -4)
                    for (offset in offsets) {
                        val index = center + offset
                        if (index !in 0..lastIndex) continue
                        val item = row.items[index]
                        val landscape = useLandscapeCards || item.posterShape == PosterShape.LANDSCAPE
                        // Warm both states so neither the portrait rest state nor the
                        // focused landscape swap has to decode on the focus frame.
                        val urls = listOf(
                            item.netflixCatalogueArtwork(
                                focused = true,
                                preferLandscapeWhenUnfocused = landscape
                            ),
                            item.netflixCatalogueArtwork(
                                focused = false,
                                preferLandscapeWhenUnfocused = landscape
                            )
                        ).filterNotNull().distinct()
                        for (url in urls) {
                            val cacheKey = netflixArtworkCacheKey(url, artworkCacheSizePx) ?: continue
                            if (imageLoader.memoryCache?.get(MemoryCache.Key(cacheKey)) != null) {
                                continue
                            }
                            imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .memoryCacheKey(cacheKey)
                                    .diskCacheKey(url)
                                    .size(width = artworkCacheSizePx.width, height = artworkCacheSizePx.height)
                                    .build()
                            )
                        }
                    }
                }
            }
            NetflixPivotLazyRow(
                state = rowState,
                selectorVisible = railHasFocus,
                selectorWidth = if (posterGrow) geometry.focusedWidth else geometry.portraitWidth,
                selectorHeight = geometry.railHeight
            ) {
                itemsIndexed(row.items, key = { index, item -> item.netflixCatalogItemKey(row, index) }) { index, item ->
                    val landscape = useLandscapeCards || item.posterShape == PosterShape.LANDSCAPE
                    val focused = index == focusedIndex
                    val itemKey = item.netflixCatalogItemKey(row, index)
                    val portraitArtwork = item.netflixCatalogueArtwork(
                        focused = false,
                        preferLandscapeWhenUnfocused = landscape
                    )
                    val focusArtwork = item.netflixCatalogueArtwork(
                        focused = true,
                        preferLandscapeWhenUnfocused = landscape
                    )
                    val artwork = if (focused) focusArtwork else portraitArtwork
                    val trailerUrl = trailerPreviewUrls[item.id]
                    // Do not gate on rail focusedIndex — every visible rail keeps a
                    // "focused" index, which would start multiple TrailerPlayers and
                    // fight over the shared ExoPlayer. NetflixMediaCard already gates
                    // on its own real focus state.
                    val playTrailer = trailerEnabled &&
                        playedTrailerIds[item.id] != true &&
                        !trailerUrl.isNullOrBlank()
                    if (focused && trailerEnabled) {
                        android.util.Log.i(
                            "NetflixTrailer",
                            "card-focus id=${item.id} urlPresent=${!trailerUrl.isNullOrBlank()}"
                        )
                    }
                    NetflixMediaCard(
                        mediaKey = itemKey,
                        title = item.name,
                        subtitle = item.releaseInfo,
                        imageUrl = artwork,
                        holdUntilReadyImageUrl = if (focused && focusArtwork != portraitArtwork) {
                            portraitArtwork
                        } else {
                            null
                        },
                        artworkCacheSizePx = artworkCacheSizePx,
                        width = if (focused && posterGrow) {
                            geometry.focusedWidth
                        } else {
                            geometry.portraitWidth
                        },
                        height = geometry.railHeight,
                        showLabels = posterLabelsEnabled && NetflixHomeTokens.ShowCataloguePosterLabels,
                        showFallbackTitleWhenArtworkMissing = focused,
                        focusRequester = itemRequesters[index],
                        trailerUrl = trailerUrl,
                        trailerAudioUrl = trailerPreviewAudioUrls[item.id],
                        playTrailer = playTrailer,
                        trailerMuted = trailerMuted,
                        onTrailerEnded = { playedTrailerIds[item.id] = true },
                        onClick = { onItemClick(item, row.addonBaseUrl) },
                        onFocus = {
                            onCardFocused(index)
                            focusedMeta = item
                            onFocusedItemChanged(index, itemKey)
                            onItemFocused(item)
                            if (trailerEnabled) {
                                onRequestTrailerPreview(item)
                            }
                            if (row.hasMore && index >= row.items.lastIndex - 5) {
                                onLoadMore(row.catalogId, row.addonId, row.apiType)
                            }
                        },
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onMoveLeft = onMoveLeft,
                        onMoveRight = onMoveRight,
                        showFocusBorder = false,
                        onLongClick = { onItemLongClick(item, row.addonBaseUrl) }
                    )
                }
            }
        }
        if (showFocusedMetadata) {
            Box(
                modifier = Modifier
                    .height(NetflixHomeSpacing.FocusedMetadataHeight)
                    .padding(
                        start = NetflixHomeTokens.PageHorizontalPadding,
                        top = 12.dp,
                        end = NetflixHomeTokens.PageHorizontalPadding
                    )
            ) {
                Crossfade(
                    targetState = settledMeta,
                    animationSpec = tween(durationMillis = 200),
                    label = "netflixCatalogFocusedMetadata"
                ) { item ->
                    if (item != null) {
                        NetflixFocusedCatalogMetadata(
                            item = item,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NetflixRailScaffold(
    title: String,
    subtitle: String? = null,
    railKey: String,
    pendingFocusRailKey: String?,
    lastFocusedIndex: Int,
    itemRequesters: List<FocusRequester>,
    onPendingFocusConsumed: () -> Unit,
    onFirstCardRequesterReady: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (
        LazyListState,
        Int,
        (Int) -> Unit,
        () -> Boolean,
        () -> Boolean,
        Boolean
    ) -> Unit
) {
    val safeLastFocusedIndex = if (itemRequesters.isEmpty()) {
        0
    } else {
        lastFocusedIndex.coerceIn(0, itemRequesters.lastIndex)
    }
    var focusedIndex by remember(railKey, itemRequesters.size) {
        mutableStateOf(safeLastFocusedIndex)
    }
    var pendingIndex by remember(railKey) { mutableStateOf<Int?>(null) }
    var railHasFocus by remember(railKey) { mutableStateOf(false) }
    val horizontalListState = rememberLazyListState(
        initialFirstVisibleItemIndex = safeLastFocusedIndex
    )

    LaunchedEffect(itemRequesters) {
        itemRequesters.firstOrNull()?.let(onFirstCardRequesterReady)
    }

    LaunchedEffect(pendingIndex, itemRequesters.size) {
        val idx = pendingIndex ?: return@LaunchedEffect
        if (itemRequesters.isEmpty()) return@LaunchedEffect
        val target = idx.coerceIn(0, itemRequesters.lastIndex)
        val visible = horizontalListState.layoutInfo.visibleItemsInfo.any { it.index == target }
        if (!visible) {
            runCatching { horizontalListState.scrollToItem(target) }
        }
        val focused = itemRequesters[target].requestFocusAfterFrames(1)
        if (!focused) {
            runCatching { horizontalListState.scrollToItem(target) }
            if (!itemRequesters[target].requestFocusAfterFrames(2)) {
                val fallback = focusedIndex.coerceIn(0, itemRequesters.lastIndex)
                if (fallback != target) {
                    runCatching { horizontalListState.scrollToItem(fallback) }
                    itemRequesters[fallback].requestFocusAfterFrames(1)
                }
                pendingIndex = null
            }
        }
    }

    LaunchedEffect(pendingFocusRailKey, itemRequesters.size, lastFocusedIndex) {
        if (pendingFocusRailKey != railKey || itemRequesters.isEmpty()) {
            return@LaunchedEffect
        }
        val targetIndex = lastFocusedIndex.coerceIn(0, itemRequesters.lastIndex)
        focusedIndex = targetIndex
        pendingIndex = null
        if (horizontalListState.firstVisibleItemIndex != targetIndex) {
            runCatching { horizontalListState.scrollToItem(targetIndex) }
        }
        val focused = itemRequesters[targetIndex].requestFocusAfterFrames(2)
        if (focused) {
            onPendingFocusConsumed()
        }
    }

    val moveLeft = {
        if (itemRequesters.isNotEmpty()) {
            val from = pendingIndex ?: focusedIndex
            val next = (from - 1).coerceAtLeast(0)
            if (next != from) {
                pendingIndex = next
            }
        }
        true
    }
    val moveRight = {
        if (itemRequesters.isNotEmpty()) {
            val from = pendingIndex ?: focusedIndex
            val next = (from + 1).coerceAtMost(itemRequesters.lastIndex)
            if (next != from) {
                pendingIndex = next
            }
        }
        true
    }

    Column(
        modifier = modifier
            .padding(top = NetflixHomeSpacing.RailTopPadding)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> moveLeft()
                        Key.DirectionRight -> moveRight()
                        else -> false
                    }
                }
            }
            .onFocusChanged { railHasFocus = it.hasFocus }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NetflixHomeTokens.PageHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    color = NetflixThemeChrome.textPrimary,
                    style = NetflixHomeTypography.RowTitle,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = NetflixThemeChrome.textSecondary,
                        style = NetflixHomeTypography.RowSubtitle,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        content(
            horizontalListState,
            focusedIndex,
            { index ->
                focusedIndex = index
                if (pendingIndex == index) {
                    pendingIndex = null
                }
            },
            moveLeft,
            moveRight,
            railHasFocus
        )
    }
}

@Composable
internal fun rememberNetflixPivotBringIntoViewSpec(): BringIntoViewSpec {
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    return remember(defaultBringIntoViewSpec) {
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> =
                defaultBringIntoViewSpec.scrollAnimationSpec
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                // Pin the focused child's leading edge to the start of the
                // clipped row viewport so previous posters cannot peek.
                return offset
            }
        }
    }
}

@Composable
internal fun NetflixPivotBringIntoView(content: @Composable () -> Unit) {
    val spec = rememberNetflixPivotBringIntoViewSpec()
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

@Composable
internal fun NetflixPivotLazyRow(
    state: LazyListState,
    selectorVisible: Boolean,
    selectorWidth: Dp,
    selectorHeight: Dp,
    content: LazyListScope.() -> Unit
) {
    val density = LocalDensity.current
    val rowHeight = selectorHeight + (NetflixHomeSpacing.RailFocusPadding * 2)
    val rowSize = remember { mutableStateOf(IntSize.Zero) }
    val parentBringIntoViewResponder = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect {
                val size = rowSize.value
                if (size.width <= 0 || size.height <= 0) return localRect
                return Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
            }

            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                // Horizontal pinning is handled by LocalBringIntoViewSpec.
            }
        }
    }
    NetflixPivotBringIntoView {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Enough trailing space that the last title can still sit in the
            // left pivot slot instead of the selector walking to the right.
            val endPadding = (maxWidth - NetflixHomeTokens.PageHorizontalPadding - selectorWidth)
                .coerceAtLeast(NetflixHomeTokens.PageHorizontalPadding)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .onSizeChanged { rowSize.value = it }
                    .bringIntoViewResponder(parentBringIntoViewResponder)
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = NetflixHomeTokens.PageHorizontalPadding)
                        .fillMaxSize()
                        .clipToBounds()
                ) {
                    LazyRow(
                        state = state,
                        modifier = Modifier
                            .fillMaxHeight()
                            .focusGroup()
                            .focusProperties { canFocus = false },
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(
                            NetflixHomeSpacing.railHorizontalGap(density)
                        ),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            end = endPadding,
                            top = NetflixHomeSpacing.RailFocusPadding,
                            bottom = NetflixHomeSpacing.RailFocusPadding
                        ),
                        content = content
                    )
                }
                NetflixPivotSelector(
                    visible = selectorVisible,
                    width = selectorWidth,
                    height = selectorHeight
                )
            }
        }
    }
}

@Composable
internal fun NetflixPivotSelector(
    visible: Boolean,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Box(
        modifier = modifier
            .padding(
                start = NetflixHomeTokens.PageHorizontalPadding,
                top = NetflixHomeSpacing.RailFocusPadding
            )
            .width(width)
            .height(height)
            .zIndex(1f)
            .border(
                width = NetflixHomeTokens.FocusBorder,
                color = NetflixThemeChrome.focus,
                shape = RoundedCornerShape(NetflixHomeTokens.CardCornerRadius)
            )
    )
}

@Composable
private fun NetflixFocusedCatalogMetadata(
    item: MetaPreview,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth(0.62f)) {
        val density = LocalDensity.current
        val synopsisLineHeight = with(density) {
            NetflixHomeTypography.Synopsis.lineHeight.toDp()
        }
        val metadataLineHeight = with(density) {
            NetflixHomeTypography.Metadata.lineHeight.toDp()
        }
        val synopsisGap = 8.dp
        val availableForSynopsis = (maxHeight - metadataLineHeight - synopsisGap)
            .coerceAtLeast(0.dp)
        // Always keep ≥2 description lines; grow up to 7 when the footer has room.
        val maxSynopsisLines = floor(
            availableForSynopsis / synopsisLineHeight.coerceAtLeast(1.dp)
        ).toInt().coerceIn(2, 7)

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item.imdbRating?.let {
                    Text(
                        text = "${String.format("%.0f", it * 10)}%",
                        color = Color(0xFF31D76B),
                        style = NetflixHomeTypography.Metadata,
                        fontWeight = FontWeight.Bold
                    )
                }
                item.metadataFacts().forEach { fact ->
                    Text(
                        text = fact,
                        color = NetflixThemeChrome.textPrimary,
                        style = NetflixHomeTypography.Metadata,
                        maxLines = 1
                    )
                }
            }
            if (!item.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(synopsisGap))
                Text(
                    text = item.description,
                    color = NetflixThemeChrome.textSecondary,
                    style = NetflixHomeTypography.Synopsis,
                    maxLines = maxSynopsisLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NetflixContinueWatchingMetadata(
    item: ContinueWatchingItem,
    useEpisodeThumbnails: Boolean,
    modifier: Modifier = Modifier
) {
    val card = item.toNetflixCard(useEpisodeThumbnails = useEpisodeThumbnails)
    Column(
        modifier = modifier.fillMaxWidth(0.62f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = card.title,
            color = NetflixThemeChrome.textPrimary,
            style = NetflixHomeTypography.ContinueTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!card.episodeLine.isNullOrBlank()) {
            Text(
                text = card.episodeLine,
                color = NetflixThemeChrome.textSecondary,
                style = NetflixHomeTypography.ContinueSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!card.description.isNullOrBlank()) {
            Text(
                text = card.description,
                color = NetflixThemeChrome.textMuted,
                style = NetflixHomeTypography.ContinueSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class NetflixCardData(
    val title: String,
    val subtitle: String?,
    val episodeLine: String? = null,
    val description: String? = null,
    val imageUrl: String?,
    val progress: Float?
)

private fun ContinueWatchingItem.netflixKey(): String {
    return when (this) {
        is ContinueWatchingItem.InProgress -> "progress|${progress.contentId}|${progress.videoId}"
        is ContinueWatchingItem.NextUp -> "next|${info.contentId}|${info.videoId}"
    }
}

internal fun CatalogRow.netflixRailKey(): String {
    return "catalog|$addonId|$addonBaseUrl|$apiType|$catalogId"
}

private fun MetaPreview.netflixCatalogItemKey(row: CatalogRow, index: Int): String {
    return "${row.addonId}|${row.catalogId}|$apiType|$id|$index"
}

private fun MetaPreview.netflixCatalogueArtwork(
    focused: Boolean,
    preferLandscapeWhenUnfocused: Boolean
): String? {
    return if (focused) {
        background ?: landscapePoster ?: poster
    } else if (preferLandscapeWhenUnfocused) {
        background ?: landscapePoster ?: poster
    } else {
        poster ?: background ?: landscapePoster
    }
}

private fun MetaPreview.metadataFacts(): List<String> {
    return buildList {
        releaseInfo?.let { value ->
            Regex("""\b(19|20)\d{2}\b""").find(value)?.value?.let(::add)
        }
        ageRating?.takeIf { it.isNotBlank() }?.let(::add)
        genres.take(2)
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?.let(::add)
        runtime?.takeIf { it.isNotBlank() && it != "0 min" }?.let(::add)
    }
}

private fun ContinueWatchingItem.toNetflixCard(
    useEpisodeThumbnails: Boolean
): NetflixCardData {
    return when (this) {
        is ContinueWatchingItem.InProgress -> {
            val episodeCode = progress.season?.let { season ->
                progress.episode?.let { episode -> "S${season}:E${episode}" }
            }
            val episodeLine = listOfNotNull(
                episodeCode,
                progress.episodeTitle?.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { null }
            val showArt = if (useEpisodeThumbnails) {
                episodeThumbnail ?: progress.backdrop ?: progress.poster
            } else {
                progress.backdrop ?: progress.poster
            }
            NetflixCardData(
                title = progress.name,
                subtitle = episodeLine,
                episodeLine = episodeLine,
                description = episodeDescription?.takeIf { it.isNotBlank() },
                imageUrl = showArt,
                progress = progress.progressPercentage
            )
        }

        is ContinueWatchingItem.NextUp -> {
            val episodeLine = buildString {
                append("S${info.season}:E${info.episode}")
                info.episodeTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    append(" · ")
                    append(title)
                }
            }
            val showArt = if (useEpisodeThumbnails) {
                info.thumbnail ?: info.backdrop ?: info.poster
            } else {
                info.backdrop ?: info.poster
            }
            NetflixCardData(
                title = info.name,
                subtitle = episodeLine,
                episodeLine = episodeLine,
                description = info.episodeDescription?.takeIf { it.isNotBlank() },
                imageUrl = showArt,
                progress = null
            )
        }
    }
}
